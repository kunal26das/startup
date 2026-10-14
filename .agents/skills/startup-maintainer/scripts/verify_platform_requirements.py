"""Check Android floors and Java 11 bytecode in downloaded Central artifacts."""

import argparse
from collections import Counter
from datetime import datetime, timezone
import io
import json
from pathlib import Path
import struct
import xml.etree.ElementTree as ET
import zipfile


def class_versions(data):
    with zipfile.ZipFile(data) as archive:
        versions = {}
        for name in archive.namelist():
            if name.endswith('.class'):
                magic, minor, major = struct.unpack('>IHH', archive.read(name)[:8])
                if magic != 0xCAFEBABE:
                    raise ValueError(f'Invalid class header: {name}')
                versions[name] = major
    return versions


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--version', required=True)
    parser.add_argument('--central-output', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--min-sdk', type=int, default=21)
    parser.add_argument('--min-compile-sdk', type=int, default=34)
    parser.add_argument('--class-major', type=int, default=55)
    args = parser.parse_args()
    version = args.version
    aar = args.central_output / f'startup-android/startup-android-{version}.aar'
    with zipfile.ZipFile(aar) as archive:
        manifest = ET.fromstring(archive.read('AndroidManifest.xml'))
        min_sdk = int(manifest.find('uses-sdk').attrib['{http://schemas.android.com/apk/res/android}minSdkVersion'])
        aar_metadata = archive.read('META-INF/com/android/build/gradle/aar-metadata.properties').decode('utf-8')
        properties = dict(line.strip().split('=', 1) for line in aar_metadata.splitlines() if '=' in line and not line.startswith('#'))
        min_compile_sdk = int(properties['minCompileSdk'])
        android_classes = archive.read('classes.jar')
    jvm_versions = class_versions(args.central_output / f'startup-desktop/startup-desktop-{version}.jar')
    android_versions = class_versions(io.BytesIO(android_classes))
    if min_sdk != args.min_sdk or min_compile_sdk != args.min_compile_sdk:
        raise ValueError(f'Unexpected Android floors: {min_sdk}/{min_compile_sdk}')
    for label, versions in [('JVM', jvm_versions), ('Android', android_versions)]:
        if not versions or set(versions.values()) != {args.class_major}:
            raise ValueError(f'Unexpected {label} class major versions: {dict(Counter(versions.values()))}')
    result = {'checked_at': datetime.now(timezone.utc).isoformat(), 'version': version, 'success': True, 'android_min_sdk': min_sdk, 'android_min_compile_sdk': min_compile_sdk, 'android_aar_metadata': aar_metadata, 'jvm_class_major_version': args.class_major, 'jvm_classes_verified': len(jvm_versions), 'android_class_major_version': args.class_major, 'android_classes_verified': len(android_versions)}
    args.output.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8', newline='\n')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
