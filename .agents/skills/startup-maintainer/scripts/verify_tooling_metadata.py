"""Verify Kotlin tooling metadata, toolchain versions, target coverage, and its SHA1 sidecar."""

import argparse
from collections import Counter
import json
from pathlib import Path
import sys
from verify_central import DEFAULT_METADATA, artifact_url, fetch_with_sha1, now, prepare_plan, require


def expected_target_coverage(root_metadata):
    by_artifact = {}
    for variant in root_metadata['variants']:
        if 'available-at' not in variant:
            continue
        attrs = variant['attributes']
        platform = attrs['org.jetbrains.kotlin.platform.type']
        signature = f'native:{attrs["org.jetbrains.kotlin.native.target"]}' if platform == 'native' else platform
        artifact = variant['available-at']['module']
        require(by_artifact.setdefault(artifact, signature) == signature, f'Conflicting platform attributes for {artifact}')
    return Counter(['common', *by_artifact.values()])


def validate_metadata(metadata, expected, kotlin_version, gradle_version):
    require(metadata.get('schemaVersion') == '1.1.0', f'Unsupported schema: {metadata.get("schemaVersion")}')
    require(metadata.get('buildSystem') == 'Gradle', 'Unexpected build system')
    require(metadata.get('buildSystemVersion') == gradle_version, f'Unexpected Gradle version: {metadata.get("buildSystemVersion")}')
    require(metadata.get('buildPlugin') == 'org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper', 'Unexpected Kotlin build plugin')
    require(metadata.get('buildPluginVersion') == kotlin_version, f'Unexpected Kotlin version: {metadata.get("buildPluginVersion")}')
    actual = Counter()
    for target in metadata['projectTargets']:
        platform = target['platformType']
        if platform == 'native':
            native = target['extras']['native']
            signature = f'native:{native["konanTarget"]}'
            require(native['konanVersion'] == kotlin_version, f'Unexpected compiler version for {signature}: {native["konanVersion"]}')
        else:
            signature = platform
        actual[signature] += 1
    require(actual == expected, f'Target coverage mismatch: found {dict(actual)}, expected {dict(expected)}')
    return dict(sorted(actual.items()))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--version', required=True)
    parser.add_argument('--kotlin-version', required=True)
    parser.add_argument('--gradle-version', required=True)
    parser.add_argument('--group', default='io.github.kunal26das')
    parser.add_argument('--artifact', default='startup')
    parser.add_argument('--metadata', type=Path, default=Path(DEFAULT_METADATA))
    parser.add_argument('--expected-targets', type=int)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--plan-only', action='store_true')
    args = parser.parse_args()
    coords = args.group, args.artifact, args.version
    root_metadata, targets = prepare_plan(args.metadata, coords, args.expected_targets)
    expected = expected_target_coverage(root_metadata)
    url = artifact_url(coords, 'pom').removesuffix('.pom') + '-kotlin-tooling-metadata.json'
    plan = {'url': url, 'sha1_url': url + '.sha1', 'expected_schema_version': '1.1.0', 'expected_kotlin_version': args.kotlin_version, 'expected_gradle_version': args.gradle_version, 'expected_target_coverage': dict(sorted(expected.items())), 'published_target_count': len(targets), 'tooling_target_count_including_common': sum(expected.values()), 'maximum_http_requests': 2, 'retries': 0}
    if args.plan_only:
        print(json.dumps(plan, indent=2))
        return 0
    require(args.output is not None, '--output is required for downloads')
    args.output.mkdir(parents=True, exist_ok=False)
    (args.output / 'plan.json').write_text(json.dumps(plan, indent=2) + '\n', encoding='utf-8', newline='\n')
    result = {'started_at': now(), 'requests': [], 'plan': plan}
    try:
        body = fetch_with_sha1(url, args.output / url.rsplit('/', 1)[1], result['requests'])
        metadata = json.loads(body)
        coverage = validate_metadata(metadata, expected, args.kotlin_version, args.gradle_version)
        result.update(success=True, public_sha1_sidecars_verified=1, target_coverage_verified=coverage, published_target_count=len(targets), tooling_target_count_including_common=sum(expected.values()), build_plugin_version=metadata['buildPluginVersion'], build_system_version=metadata['buildSystemVersion'])
    except Exception as error:
        result.update(success=False, error=str(error))
    result['finished_at'] = now()
    (args.output / 'results.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8', newline='\n')
    print(json.dumps(result, indent=2))
    return 0 if result['success'] else 1


if __name__ == '__main__':
    try:
        sys.exit(main())
    except Exception as error:
        print(f'Verification refused: {error}', file=sys.stderr)
        sys.exit(2)
