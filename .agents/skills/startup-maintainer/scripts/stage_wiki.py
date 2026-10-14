import argparse
from collections import Counter
import json
from pathlib import Path, PurePosixPath
import posixpath
import re
import subprocess
from urllib.parse import quote, unquote, urlsplit

REPO_ROOT = Path(__file__).resolve().parents[4]
REPO_URL = 'https://github.com/kunal26das/startup'
WIKI_URL = REPO_URL + '/wiki'
MAPPING = {
    'README.md': 'Home',
    'docs/android.md': 'Android-Startup',
    'docs/platform-initializers.md': 'Platform-Specific-Initializers',
    'docs/swift.md': 'Swift-and-Objective-C',
    'docs/runtime.md': 'Runtime-and-API',
    'docs/migration.md': 'Migration-Guide',
    'sample/README.md': 'Run-the-Sample',
    'CONTRIBUTING.md': 'Contributing',
    'CHANGELOG.md': 'Changelog',
}
LINK = re.compile(r'(?P<prefix>!?\[)(?P<label>[^\]\n]*)\]\((?P<url>[^\s)]+)\)')
FENCE = re.compile(r'^ {0,3}(`{3,}|~{3,})')


def segments(text):
    pieces, buffer, fence = [], [], None
    for line in text.splitlines(keepends=True):
        marker = FENCE.match(line)
        if fence is None and marker:
            if buffer:
                pieces.append((False, ''.join(buffer)))
                buffer = []
            fence = marker.group(1)
            buffer.append(line)
        elif fence is not None:
            buffer.append(line)
            if marker and marker.group(1)[0] == fence[0] and len(marker.group(1)) >= len(fence):
                pieces.append((True, ''.join(buffer)))
                buffer, fence = [], None
        else:
            buffer.append(line)
    assert fence is None, 'Unclosed fenced example'
    if buffer:
        pieces.append((False, ''.join(buffer)))
    return pieces


def unfenced(text):
    return ''.join(part for fenced, part in segments(text) if not fenced)


def headings(text):
    return re.findall(r'^ {0,3}#{1,6}\s+(.+?)\s*#*\s*$', unfenced(text), re.M)


def anchors(text):
    used, result = Counter(), set()
    for heading in headings(text):
        heading = LINK.sub(lambda match: match.group('label'), heading)
        heading = re.sub(r'<[^>]+>', '', heading).replace('`', '')
        slug = re.sub(r'[^\w\- ]', '', heading.lower()).replace(' ', '-')
        number = used[slug]
        used[slug] += 1
        result.add(slug if number == 0 else f'{slug}-{number}')
    return result


def links(text):
    return [match.group('url') for match in LINK.finditer(unfenced(text))]


def sidebar():
    groups = {
        'Start here': [('Install and get started', 'Home'), ('Run the sample', 'Run-the-Sample')],
        'Integrate': [('Android startup', 'Android-Startup'), ('Platform-specific initializers', 'Platform-Specific-Initializers'), ('Swift and Objective-C', 'Swift-and-Objective-C')],
        'Configure and upgrade': [('Runtime and API guide', 'Runtime-and-API'), ('Migration guide', 'Migration-Guide')],
        'Project': [('Contributing', 'Contributing'), ('Changelog', 'Changelog')],
    }
    sections = [f'### {title}\n\n' + '\n'.join(f'- [{label}]({WIKI_URL}/{page})' for label, page in items) for title, items in groups.items()]
    return '\n\n'.join(sections) + f'\n\n[Source repository]({REPO_URL})\n'


def git(repo, *args):
    return subprocess.check_output(['git', '-C', str(repo), *args], text=True, encoding='utf-8', stderr=subprocess.PIPE)


def generate(repo, source_ref, release, output):
    commit = git(repo, 'rev-parse', f'{source_ref}^{{commit}}').strip()
    ref = f'v{release}'
    try:
        tagged_commit = git(repo, 'rev-parse', '--verify', f'refs/tags/{ref}^{{commit}}').strip()
    except subprocess.CalledProcessError as error:
        raise ValueError(f'Release tag {ref} must exist before staging wiki pages') from error
    if tagged_commit != commit:
        raise ValueError(f'Release tag {ref} resolves to {tagged_commit}, but source {source_ref} resolves to {commit}')
    entries = git(repo, 'ls-tree', '-r', '--name-only', commit).splitlines()
    files = set(entries)
    directories = {str(parent) for item in entries for parent in PurePosixPath(item).parents if str(parent) != '.'}
    source = {path: git(repo, 'show', f'{commit}:{path}') for path in MAPPING}
    generated, converted = {}, []
    source_anchors = {path: anchors(content) for path, content in source.items()}

    def transform(origin, match):
        url = match.group('url')
        parsed = urlsplit(url)
        if parsed.scheme or parsed.netloc:
            return match.group(0)
        assert not parsed.query, (origin, 'Unexpected local URL query', url)
        target = posixpath.normpath(posixpath.join(posixpath.dirname(origin), unquote(parsed.path))) if parsed.path else origin
        assert target in files or target in directories, (origin, 'Missing source target', url, target)
        fragment = unquote(parsed.fragment)
        if fragment:
            assert target.endswith('.md'), (origin, 'Unvalidated non-Markdown fragment', url)
            if target not in source_anchors:
                source_anchors[target] = anchors(git(repo, 'show', f'{commit}:{target}'))
            assert fragment in source_anchors[target], (origin, 'Missing source fragment', url)
        if target in MAPPING:
            rewritten = f'{WIKI_URL}/{MAPPING[target]}'
        else:
            kind = 'blob' if target in files else 'tree'
            rewritten = f'{REPO_URL}/{kind}/{ref}/{quote(target, safe="/")}'
        if parsed.fragment:
            rewritten += '#' + parsed.fragment
        label = match.group('label')
        if target == 'README.md':
            label = {'Back to the README': 'Back to quick start', 'README example': 'quick start example', 'README': 'quick start'}.get(label, label)
        converted.append({'source': origin, 'original': url, 'target': rewritten})
        return f'{match.group("prefix")}{label}]({rewritten})'

    for origin, page in MAPPING.items():
        parts = []
        for fenced, part in segments(source[origin]):
            if not fenced:
                part = LINK.sub(lambda match: transform(origin, match), part)
                if origin == 'sample/README.md':
                    part = part.replace('the **repository root**, not this directory.', 'the **repository root**.')
            parts.append(part)
        body = ''.join(parts)
        assert headings(body) == headings(source[origin]), origin
        assert [part for fenced, part in segments(body) if fenced] == [part for fenced, part in segments(source[origin]) if fenced], origin
        generated[page] = body.rstrip() + f'\n\n---\n\nSource: [{origin} at {ref}]({REPO_URL}/blob/{ref}/{origin}).\n'
    generated['_Sidebar'] = sidebar()
    generated['_Footer'] = f'Documentation for [startup {ref}]({REPO_URL}/tree/{ref}). [Source repository]({REPO_URL}).\n'
    checked = 0
    for page, content in generated.items():
        for target in links(content):
            parsed = urlsplit(target)
            if target.startswith(WIKI_URL + '/'):
                linked_page = unquote(parsed.path.split('/wiki/', 1)[1])
                assert linked_page in generated, (page, target)
                if parsed.fragment:
                    assert unquote(parsed.fragment) in anchors(generated[linked_page]), (page, target)
                checked += 1
            assert parsed.scheme, (page, 'Relative generated link', target)
    for page in ('Home', 'Swift-and-Objective-C'):
        assert set(re.findall(r'io\.github\.kunal26das:startup:([0-9.]+)', generated[page])) == {release}, page
    output.mkdir(parents=True, exist_ok=True)
    for page, content in generated.items():
        (output / f'{page}.md').write_text(content, encoding='utf-8', newline='\n')
    report = {
        'source_ref': source_ref, 'source_commit': commit, 'release': release, 'release_ref': ref,
        'content_pages': len(MAPPING), 'support_pages': 2, 'converted_local_links': len(converted),
        'validated_internal_wiki_links': checked,
        'preserved_fenced_blocks': sum(sum(fenced for fenced, _ in segments(text)) for text in source.values()),
        'preserved_headings': sum(len(headings(text)) for text in source.values()),
        'dependency_coordinates': sorted(set(re.findall(r'io\.github\.kunal26das:startup:[0-9.]+', '\n'.join(generated.values())))),
        'validation': 'passed', 'published': False,
    }
    (output.parent / f'{output.name}-verification.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8', newline='\n')
    (output.parent / f'{output.name}-link-map.json').write_text(json.dumps(converted, indent=2) + '\n', encoding='utf-8', newline='\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--repo', type=Path, default=REPO_ROOT)
    parser.add_argument('--source-ref', required=True)
    parser.add_argument('--version', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    generate(args.repo, args.source_ref, args.version, args.output)
