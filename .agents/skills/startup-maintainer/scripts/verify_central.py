"""Verify public Kotlin Multiplatform publications, metadata, checksums, and archives."""

import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import urljoin
import xml.etree.ElementTree as ET
import zipfile

CENTRAL = 'https://repo.maven.apache.org/maven2/'
DEFAULT_METADATA = Path(__file__).resolve().parents[4] / 'startup/build/publications/kotlinMultiplatform/module.json'


def now():
    return datetime.now(timezone.utc).isoformat()


def require(condition, message):
    if not condition:
        raise ValueError(message)


def coordinate(data):
    return tuple(data[k] for k in ('group', 'module', 'version'))


def artifact_url(coords, extension):
    group, artifact, version = coords
    return f'{CENTRAL}{group.replace(".", "/")}/{artifact}/{version}/{artifact}-{version}.{extension}'


def targets(metadata):
    return sorted({coordinate(v['available-at']) for v in metadata['variants'] if 'available-at' in v})


def validate_links(metadata, root_coords, expected_targets):
    require(coordinate(metadata['component']) == root_coords, 'Root module coordinates differ')
    require(targets(metadata) == expected_targets, 'Root target coordinates differ')
    for variant in metadata['variants']:
        if 'available-at' in variant:
            link = variant['available-at']
            require(urljoin(artifact_url(root_coords, 'module'), link['url']) == artifact_url(coordinate(link), 'module'), f'Wrong target link in {variant["name"]}')


def prepare_plan(path, root_coords, expected_count):
    metadata = json.loads(path.read_text(encoding='utf-8'))
    require(coordinate(metadata['component']) == root_coords, f'Local metadata is {coordinate(metadata["component"])}, expected {root_coords}; regenerate publication metadata first')
    expected = targets(metadata)
    require(bool(expected), 'No target publications in metadata')
    if expected_count is not None:
        require(len(expected) == expected_count, f'Expected {expected_count} targets, found {len(expected)}')
    require(all(c[0] == root_coords[0] and c[2] == root_coords[2] and c != root_coords for c in expected), 'Wrong target group/version or root self-reference')
    validate_links(metadata, root_coords, expected)
    return metadata, expected


def fetch(url, path, requests):
    record = {'url': url, 'destination': str(path), 'started_at': now()}
    requests.append(record)
    try:
        p = subprocess.run(['curl', '--silent', '--show-error', '--connect-timeout', '5', '--max-time', '20', '--retry', '0', '--output', str(path), '--write-out', '%{http_code}', url], capture_output=True, text=True, encoding='utf-8', timeout=25)
        status = int(p.stdout) if p.stdout.isdigit() else None
        record.update(http_status=status, returncode=p.returncode, stderr=p.stderr)
        require(p.returncode == 0 and status == 200, f'Download failed: HTTP {status}, exit {p.returncode}, {url}: {p.stderr.strip()}')
        body = path.read_bytes()
        require(bool(body), f'Empty response: {url}')
        record.update(success=True, bytes=len(body), sha1=hashlib.sha1(body).hexdigest(), sha256=hashlib.sha256(body).hexdigest())
        return body
    except Exception as error:
        record.update(success=False, error=str(error))
        raise
    finally:
        record['finished_at'] = now()


def fetch_with_sha1(url, path, requests):
    body = fetch(url, path, requests)
    sidecar = fetch(url + '.sha1', path.with_name(path.name + '.sha1'), requests).decode('ascii').strip()
    require(re.fullmatch(r'[0-9a-fA-F]{40}', sidecar) is not None, f'Malformed SHA1 sidecar: {url}')
    require(hashlib.sha1(body).hexdigest() == sidecar.lower(), f'SHA1 sidecar mismatch: {url}')
    return body


def principal_binary(metadata, coords, root_coords):
    files = {}
    for variant in metadata['variants']:
        attrs = variant.get('attributes', {})
        if attrs.get('org.gradle.category') != 'library':
            continue
        if coords != root_coords and attrs.get('org.gradle.usage') == 'kotlin-metadata':
            continue
        for entry in variant.get('files', []):
            require(files.setdefault(entry['url'], entry) == entry, f'Conflicting binary metadata: {entry["url"]}')
    require(len(files) == 1, f'Expected one principal binary for {coords}; found {list(files)}')
    filename, entry = next(iter(files.items()))
    require(filename in {f'{coords[1]}-{coords[2]}.{ext}' for ext in ('jar', 'aar', 'klib')}, f'Unexpected binary path: {filename}')
    require(isinstance(entry.get('size'), int) and entry['size'] > 0, f'Missing binary size: {filename}')
    require(re.fullmatch(r'[0-9a-f]{64}', entry.get('sha256', '')) is not None, f'Missing binary SHA256: {filename}')
    return filename, entry


def verify_publication(coords, root_coords, expected_targets, output):
    result = {'coordinates': dict(zip(('group', 'artifact', 'version'), coords)), 'requests': [], 'started_at': now()}
    folder = output / coords[1]
    folder.mkdir()
    try:
        pom_url = artifact_url(coords, 'pom')
        pom = ET.fromstring(fetch_with_sha1(pom_url, folder / pom_url.rsplit('/', 1)[1], result['requests']))
        ns = '{http://maven.apache.org/POM/4.0.0}' if pom.tag.startswith('{') else ''
        actual_coords = tuple(pom.findtext(ns + k) for k in ('groupId', 'artifactId', 'version'))
        require(actual_coords == coords, f'Wrong POM coordinates: {actual_coords}')
        result['pom_coordinates_verified'] = True
        module_url = artifact_url(coords, 'module')
        metadata = json.loads(fetch_with_sha1(module_url, folder / module_url.rsplit('/', 1)[1], result['requests']))
        require(coordinate(metadata['component']) == root_coords, f'Wrong module root coordinates: {metadata["component"]}')
        if coords == root_coords:
            validate_links(metadata, root_coords, expected_targets)
            result['target_count_verified'] = len(expected_targets)
        else:
            require(urljoin(module_url, metadata['component']['url']) == artifact_url(root_coords, 'module'), 'Target does not link back to root module')
        result['module_coordinates_and_links_verified'] = True
        filename, entry = principal_binary(metadata, coords, root_coords)
        binary_path = folder / filename
        body = fetch_with_sha1(urljoin(module_url, filename), binary_path, result['requests'])
        sha256 = hashlib.sha256(body).hexdigest()
        require(len(body) == entry['size'], f'Binary size mismatch: {filename}')
        require(sha256 == entry['sha256'], f'Binary SHA256 mismatch: {filename}')
        if 'sha1' in entry:
            require(hashlib.sha1(body).hexdigest() == entry['sha1'], f'Module SHA1 mismatch: {filename}')
        with zipfile.ZipFile(binary_path) as z:
            require(bool(z.infolist()), f'Empty archive: {filename}')
            require(z.testzip() is None, f'Archive integrity failure: {filename}')
            member_count = len(z.infolist())
        result.update(success=True, public_sha1_sidecars_verified=3, binary={'filename': filename, 'bytes': len(body), 'sha256': sha256, 'module_size_and_sha256_verified': True, 'archive_integrity_verified': True, 'archive_members': member_count})
    except Exception as error:
        result.update(success=False, error=str(error))
    result['finished_at'] = now()
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--version', required=True)
    parser.add_argument('--group', default='io.github.kunal26das')
    parser.add_argument('--artifact', default='startup')
    parser.add_argument('--metadata', type=Path, default=Path(DEFAULT_METADATA))
    parser.add_argument('--expected-targets', type=int)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--plan-only', action='store_true')
    args = parser.parse_args()
    for value in (args.group, args.artifact, args.version):
        require(re.fullmatch(r'[A-Za-z0-9_.-]+', value) is not None and value not in ('.', '..'), 'Invalid coordinate segment')
    root_coords = args.group, args.artifact, args.version
    local_metadata, expected_targets = prepare_plan(args.metadata, root_coords, args.expected_targets)
    coords = [root_coords, *expected_targets]
    plan = {'root_coordinates': root_coords, 'local_metadata': str(args.metadata.resolve()), 'publication_count': len(coords), 'maximum_http_requests': len(coords) * 6, 'retries': 0, 'parallel_workers': 6, 'request_timeout_seconds': 20, 'publications': [{'coordinates': c, 'pom_url': artifact_url(c, 'pom'), 'module_url': artifact_url(c, 'module')} for c in coords]}
    if args.plan_only:
        print(json.dumps(plan, indent=2))
        return 0
    require(args.output is not None, '--output is required for downloads')
    args.output.mkdir(parents=True, exist_ok=False)
    (args.output / 'local-root-module.json').write_text(json.dumps(local_metadata, indent=2) + '\n', encoding='utf-8', newline='\n')
    (args.output / 'plan.json').write_text(json.dumps(plan, indent=2) + '\n', encoding='utf-8', newline='\n')
    started_at = now()
    with ThreadPoolExecutor(max_workers=6) as executor:
        results = list(executor.map(lambda c: verify_publication(c, root_coords, expected_targets, args.output), coords))
    requests = [request for result in results for request in result['requests']]
    summary = {'started_at': started_at, 'finished_at': now(), 'version': args.version, 'central': CENTRAL, 'success': all(r['success'] for r in results), 'publications_passed': sum(r['success'] for r in results), 'publications_total': len(results), 'http_requests': len(requests), 'http_200_responses': sum(r.get('http_status') == 200 for r in requests), 'public_sha1_sidecars_verified': sum(r.get('public_sha1_sidecars_verified', 0) for r in results), 'results': results}
    (args.output / 'results.json').write_text(json.dumps(summary, indent=2) + '\n', encoding='utf-8', newline='\n')
    print(json.dumps({k: v for k, v in summary.items() if k != 'results'}, indent=2))
    for result in results:
        if not result['success']:
            print(f'{result["coordinates"]["artifact"]}: {result["error"]}', file=sys.stderr)
    return 0 if summary['success'] else 1


if __name__ == '__main__':
    try:
        sys.exit(main())
    except Exception as error:
        print(f'Verification refused: {error}', file=sys.stderr)
        sys.exit(2)
