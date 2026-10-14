import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import hashlib
from html.parser import HTMLParser
import json
from pathlib import Path
import re
from urllib.parse import unquote, urlsplit
from urllib.request import Request, urlopen

from stage_wiki import MAPPING, REPO_URL, WIKI_URL, links


class Page(HTMLParser):
    def __init__(self):
        super().__init__()
        self.ids, self.links, self.text = set(), set(), []

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        for key in ('id', 'name'):
            if key in attrs:
                self.ids.add(attrs[key])
        if tag == 'a' and 'href' in attrs:
            self.links.add(attrs['href'])

    def handle_data(self, data):
        self.text.append(data)


def verify(release, pages, output):
    ref = f'v{release}'
    coordinate = f'io.github.kunal26das:startup:{release}'

    def fetch(item):
        origin, page = item
        url = f'{WIKI_URL}/{page}'
        with urlopen(Request(url, headers={'User-Agent': 'startup-documentation-verification'}), timeout=30) as response:
            body = response.read()
            resolved = response.geturl()
            assert response.status == 200 and '/wiki' in resolved, (page, resolved)
        html = body.decode('utf-8')
        assert re.search(r'<title>.*' + re.escape(page.replace('-', ' ')) + r'.*Wiki', html, re.I), page
        parser = Page()
        parser.feed(html)
        text = ''.join(parser.text)
        compact = re.sub(r'\s+', ' ', text)
        assert f'{REPO_URL}/blob/{ref}/{origin}' in parser.links, (page, 'source link')
        assert f'{REPO_URL}/tree/{ref}' in parser.links, (page, 'footer link')
        assert f'Documentation for startup {ref}.' in text, (page, 'version footer')
        source = (pages/f'{page}.md').read_text(encoding='utf-8')
        if page in ('Home', 'Swift-and-Objective-C'):
            assert set(re.findall(r'io\.github\.kunal26das:startup:([0-9.]+)', text)) == {release}, page
        if page == 'Home':
            assert 'https://kunal26das.github.io/startup/' in parser.links
        if page == 'Swift-and-Objective-C':
            assert source.count(coordinate) > 0
            assert text.count(coordinate) == source.count(coordinate)
        if page == 'Changelog':
            current = re.search(r'^## (' + re.escape(release) + r'[^\n]*)', source, re.M)
            assert current and current.group(1) in compact, 'Current changelog heading'
        if page == 'Contributing':
            for name in ('Gradle', 'Kotlin', 'Android Gradle Plugin'):
                expected = re.search(r'^\| ' + name + r' \| ([^|]+) \|', source, re.M)
                assert expected, (page, name, 'toolchain source')
                assert re.search(re.escape(name) + r'\s+' + re.escape(expected.group(1).strip()), compact), (page, name, 'toolchain rendered')
        if page == 'Migration-Guide':
            for historical in set(re.findall(r'io\.github\.kunal26das:startup:([0-9.]+)', source)):
                assert f'io.github.kunal26das:startup:{historical}' in text, ('Historical migration dependency', historical)
        return page, {'url': url, 'resolved_url': resolved, 'status': 200, 'sha256': hashlib.sha256(body).hexdigest(), 'anchors': sorted(parser.ids), 'version_and_source_links': 'passed'}

    with ThreadPoolExecutor(max_workers=3) as executor:
        fetched = dict(executor.map(fetch, MAPPING.items()))
    checked = 0
    for path in pages.glob('*.md'):
        for target in links(path.read_text(encoding='utf-8')):
            if target.startswith(WIKI_URL + '/'):
                parsed = urlsplit(target)
                page = unquote(parsed.path.split('/wiki/', 1)[1])
                assert page in fetched, target
                if parsed.fragment:
                    fragment = unquote(parsed.fragment)
                    ids = fetched[page]['anchors']
                    assert fragment in ids or 'user-content-' + fragment in ids, target
                checked += 1
    result = {'status': 'passed', 'checked_at_utc': datetime.now(timezone.utc).isoformat(), 'release': release, 'published_pages': len(fetched), 'verified_wiki_links': checked, 'pages': fetched}
    output.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8', newline='\n')
    print(f'PASS: {len(fetched)} public wiki pages, {checked} rendered links/anchors, current {release} coordinates, contributor toolchain, historical migrations, and all source/footer links.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--version', required=True)
    parser.add_argument('--pages', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    verify(args.version, args.pages, args.output)
