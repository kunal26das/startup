"""Exercise maintenance helpers using local fixtures without network requests."""

from collections import Counter
from contextlib import redirect_stdout
import hashlib
import html
import io
import json
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import stage_wiki
from verify_central import prepare_plan, verify_publication
from verify_platform_requirements import class_versions
from verify_tooling_metadata import validate_metadata
import verify_wiki


class HelperTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='startup-helper-tests-')
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.release = '1.2.3'
        self.coordinate = ('io.github.kunal26das', 'startup', self.release)

    def write_text(self, path, content):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding='utf-8', newline='\n')

    def git(self, repo, *args):
        return stage_wiki.git(repo, '-c', 'commit.gpgsign=false', '-c', 'tag.gpgsign=false', *args)

    def wiki_repository(self, tagged=True):
        repo = self.root / 'repo'
        repo.mkdir()
        self.git(repo, 'init', '--quiet')
        self.git(repo, 'config', 'user.name', 'Helper Fixture')
        self.git(repo, 'config', 'user.email', 'fixture@example.invalid')
        self.git(repo, 'config', 'core.hooksPath', str(repo / 'no-hooks'))
        for origin in stage_wiki.MAPPING:
            content = f'# Example\n\nCafé — “ready” → 完了\n\nio.github.kunal26das:startup:{self.release}\n'
            content += '\n```kotlin\nval message = "café → ready"\n```\n'
            if origin == 'README.md':
                content += '\n[Runtime](docs/runtime.md#example)\n'
                content += '\n[Website](https://kunal26das.github.io/startup/)\n'
            if origin == 'CONTRIBUTING.md':
                content += '\n| Gradle | 9.8.1 |\n| Kotlin | 2.4.21 |\n| Android Gradle Plugin | 9.4.1 |\n'
            if origin == 'CHANGELOG.md':
                content += f'\n## {self.release}\n'
            self.write_text(repo / origin, content)
        self.git(repo, 'add', '.')
        self.git(repo, 'commit', '--quiet', '-m', 'Fixture release')
        if tagged:
            self.git(repo, 'tag', f'v{self.release}')
        return repo

    def test_unicode_git_and_markdown_round_trip_with_matching_tag(self):
        repo = self.wiki_repository()
        output = self.root / 'pages'
        with patch('locale.getpreferredencoding', return_value='cp1252'), \
                patch('locale.getencoding', return_value='cp1252', create=True), \
                redirect_stdout(io.StringIO()):
            stage_wiki.generate(repo, 'HEAD', self.release, output)
        source = self.git(repo, 'show', 'HEAD:README.md')
        rendered = (output / 'Home.md').read_text(encoding='utf-8')
        self.assertIn('Café — “ready” → 完了', rendered)
        self.assertEqual(
            [part for fenced, part in stage_wiki.segments(source) if fenced],
            [part for fenced, part in stage_wiki.segments(rendered) if fenced],
        )
        self.assertIn(f'/blob/v{self.release}/README.md', rendered)
        report = json.loads((self.root / 'pages-verification.json').read_text(encoding='utf-8'))
        self.assertEqual(report['source_commit'], self.git(repo, 'rev-parse', 'HEAD').strip())
        self.assertEqual(report['validation'], 'passed')
        for path in output.glob('*.md'):
            self.assertNotIn(b'\r', path.read_bytes())

    def test_wiki_staging_refuses_missing_release_tag_before_writing(self):
        repo = self.wiki_repository(tagged=False)
        output = self.root / 'pages'
        with self.assertRaisesRegex(ValueError, 'must exist'):
            stage_wiki.generate(repo, 'HEAD', self.release, output)
        self.assertFalse(output.exists())

    def test_wiki_staging_refuses_source_that_differs_from_release_tag(self):
        repo = self.wiki_repository()
        readme = repo / 'README.md'
        self.write_text(readme, readme.read_text(encoding='utf-8') + '\nLater documentation.\n')
        self.git(repo, 'add', 'README.md')
        self.git(repo, 'commit', '--quiet', '-m', 'Later documentation')
        output = self.root / 'pages'
        with self.assertRaisesRegex(ValueError, 'resolves to'):
            stage_wiki.generate(repo, 'HEAD', self.release, output)
        self.assertFalse(output.exists())

    def test_live_wiki_verifier_reads_unicode_staging_without_network(self):
        repo = self.wiki_repository()
        pages = self.root / 'pages'
        with redirect_stdout(io.StringIO()):
            stage_wiki.generate(repo, 'HEAD', self.release, pages)
        release = self.release
        origins = {page: origin for origin, page in stage_wiki.MAPPING.items()}

        class Response:
            status = 200

            def __init__(self, request):
                self.url = request.full_url

            def __enter__(self):
                return self

            def __exit__(self, *args):
                return False

            def geturl(self):
                return self.url

            def read(self):
                page = self.url.rsplit('/', 1)[1]
                origin = origins[page]
                text = (
                    f'<title>{page.replace("-", " ")} Wiki</title>'
                    '<h1 id="example">Café — “ready” → 完了</h1>'
                    f'<p>Documentation for startup v{release}.</p>'
                    f'<p>io.github.kunal26das:startup:{release}</p>'
                    '<p>Gradle 9.8.1 Kotlin 2.4.21 Android Gradle Plugin 9.4.1</p>'
                    f'<a href="{stage_wiki.REPO_URL}/blob/v{release}/{html.escape(origin)}">Source</a>'
                    f'<a href="{stage_wiki.REPO_URL}/tree/v{release}">Release</a>'
                    '<a href="https://kunal26das.github.io/startup/">Website</a>'
                )
                return text.encode('utf-8')

        output = self.root / 'wiki-result.json'
        with patch.object(verify_wiki, 'urlopen', side_effect=lambda request, timeout: Response(request)) as fetch, \
                patch('locale.getpreferredencoding', return_value='cp1252'), \
                patch('locale.getencoding', return_value='cp1252', create=True), \
                redirect_stdout(io.StringIO()):
            verify_wiki.verify(release, pages, output)
        self.assertEqual(fetch.call_count, len(stage_wiki.MAPPING))
        self.assertEqual(json.loads(output.read_text(encoding='utf-8'))['status'], 'passed')
        self.assertNotIn(b'\r', output.read_bytes())

    def root_metadata(self):
        group, module, version = self.coordinate
        return {
            'component': {'group': group, 'module': module, 'version': version},
            'variants': [{
                'name': 'desktop',
                'attributes': {'org.jetbrains.kotlin.platform.type': 'jvm'},
                'available-at': {
                    'group': group, 'module': 'startup-desktop', 'version': version,
                    'url': f'../../startup-desktop/{version}/startup-desktop-{version}.module',
                },
            }],
        }

    def test_publication_plan_rejects_wrong_release_and_target_count(self):
        path = self.root / 'module.json'
        self.write_text(path, json.dumps(self.root_metadata()))
        self.assertEqual(len(prepare_plan(path, self.coordinate, 1)[1]), 1)
        with self.assertRaisesRegex(ValueError, 'regenerate publication metadata'):
            prepare_plan(path, (*self.coordinate[:2], '1.2.4'), 1)
        with self.assertRaisesRegex(ValueError, 'Expected 2 targets'):
            prepare_plan(path, self.coordinate, 2)

    def test_tooling_metadata_rejects_wrong_versions_and_coverage(self):
        metadata = {
            'schemaVersion': '1.1.0',
            'buildSystem': 'Gradle',
            'buildSystemVersion': '9.8.1',
            'buildPlugin': 'org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper',
            'buildPluginVersion': '2.4.21',
            'projectTargets': [{'platformType': 'common'}, {'platformType': 'jvm'}],
        }
        expected = Counter(['common', 'jvm'])
        self.assertEqual(validate_metadata(metadata, expected, '2.4.21', '9.8.1'), dict(expected))
        for kotlin, gradle, coverage in [('2.4.20', '9.8.1', expected), ('2.4.21', '9.8.0', expected), ('2.4.21', '9.8.1', Counter(['common']))]:
            with self.subTest(kotlin=kotlin, gradle=gradle, coverage=coverage), self.assertRaises(ValueError):
                validate_metadata(metadata, coverage, kotlin, gradle)

    def test_publication_rejects_malformed_archive_after_matching_checksums(self):
        body = b'This is not a ZIP archive.'
        group, module, version = self.coordinate
        metadata = {
            'component': {'group': group, 'module': module, 'version': version},
            'variants': [{
                'name': 'metadataApiElements',
                'attributes': {'org.gradle.category': 'library'},
                'files': [{'url': f'{module}-{version}.jar', 'size': len(body), 'sha256': hashlib.sha256(body).hexdigest()}],
            }],
        }
        pom = f'<project><groupId>{group}</groupId><artifactId>{module}</artifactId><version>{version}</version></project>'.encode('utf-8')

        def fetch(url, path, requests):
            content = pom if url.endswith('.pom') else json.dumps(metadata).encode('utf-8') if url.endswith('.module') else body
            path.write_bytes(content)
            return content

        with patch('verify_central.fetch_with_sha1', side_effect=fetch) as download:
            result = verify_publication(self.coordinate, self.coordinate, [], self.root)
        self.assertEqual(download.call_count, 3)
        self.assertFalse(result['success'])
        self.assertIn('not a zip file', result['error'].lower())

    def test_class_verifier_rejects_malformed_archive_and_class(self):
        with self.assertRaises(zipfile.BadZipFile):
            class_versions(io.BytesIO(b'not a ZIP'))
        for header, valid in [(struct.pack('>IHH', 0xCAFEBABE, 0, 55), True), (struct.pack('>IHH', 0, 0, 55), False)]:
            stream = io.BytesIO()
            with zipfile.ZipFile(stream, 'w') as archive:
                archive.writestr('Example.class', header)
            stream.seek(0)
            with self.subTest(valid=valid):
                if valid:
                    self.assertEqual(class_versions(stream), {'Example.class': 55})
                else:
                    with self.assertRaisesRegex(ValueError, 'Invalid class header'):
                        class_versions(stream)


if __name__ == '__main__':
    unittest.main()
