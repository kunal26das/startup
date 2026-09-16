"""Check static website structure, local assets, and links without network access."""

from html.parser import HTMLParser
from pathlib import Path
import re
import sys
from urllib.parse import unquote, urlsplit


VOID_TAGS = {"area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr"}
SITE_URL = "https://kunal26das.github.io/startup/"


class Page(HTMLParser):
    """Collect document structure, identifiers, and local references."""

    def __init__(self, path):
        super().__init__(convert_charrefs=True)
        self.path = path
        self.ids = set()
        self.references = []
        self.errors = []
        self.stack = []
        self.counts = {}
        self.doctype = False
        self.title = ""

    def error(self, message):
        self.errors.append(f"{self.path}:{self.getpos()[0]}: {message}")

    def handle_decl(self, declaration):
        self.doctype = declaration.lower() == "doctype html"

    def handle_starttag(self, tag, attributes):
        attributes = dict(attributes)
        if tag != "title" or self.stack == ["html", "head"]:
            self.counts[tag] = self.counts.get(tag, 0) + 1
        if tag not in VOID_TAGS:
            self.stack.append(tag)
        if tag == "html" and not attributes.get("lang"):
            self.error("html needs a lang attribute")
        identifier = attributes.get("id")
        if identifier:
            if identifier in self.ids:
                self.error(f"duplicate id: {identifier}")
            self.ids.add(identifier)
        for attribute in ("href", "src", "poster"):
            if attributes.get(attribute):
                self.references.append((self.getpos()[0], attributes[attribute]))

    def handle_startendtag(self, tag, attributes):
        self.handle_starttag(tag, attributes)
        if tag not in VOID_TAGS:
            self.handle_endtag(tag)

    def handle_endtag(self, tag):
        if not self.stack or self.stack[-1] != tag:
            self.error(f"unexpected closing tag: {tag}")
        else:
            self.stack.pop()

    def handle_data(self, data):
        if self.stack == ["html", "head", "title"]:
            self.title += data

    def check_structure(self):
        if not self.doctype:
            self.error("missing HTML5 doctype")
        for tag in ("html", "head", "body", "title"):
            if self.counts.get(tag) != 1:
                self.error(f"expected exactly one {tag} element")
        if not self.title.strip():
            self.error("title must not be empty")
        if self.stack:
            self.error(f"unclosed elements: {', '.join(self.stack)}")


def check_reference(root, source, line, reference, pages):
    if reference.startswith(SITE_URL):
        reference = "/startup/" + reference[len(SITE_URL):]
    parts = urlsplit(reference)
    if parts.scheme or parts.netloc:
        return None
    path = unquote(parts.path)
    if path.startswith("/"):
        if path != "/startup" and not path.startswith("/startup/"):
            return f"{source}:{line}: local URL escapes /startup/: {reference}"
        target = root / path.removeprefix("/startup").lstrip("/")
    else:
        target = source.parent / path if path else source
    target = target.resolve()
    if not target.is_relative_to(root):
        return f"{source}:{line}: local URL escapes site/: {reference}"
    if target.is_dir():
        target /= "index.html"
    if not target.is_file():
        return f"{source}:{line}: missing local file: {reference}"
    fragment = unquote(parts.fragment)
    if fragment and target in pages and fragment not in pages[target].ids:
        return f"{source}:{line}: missing anchor: {reference}"
    return None


def main():
    root = (Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parents[1] / "site").resolve()
    errors = []
    pages = {}
    if not (root / "index.html").is_file():
        errors.append(f"{root}: missing index.html")
    for path in sorted(root.rglob("*.html")):
        page = Page(path)
        page.feed(path.read_text(encoding="utf-8"))
        page.close()
        page.check_structure()
        errors.extend(page.errors)
        pages[path] = page
    references = [(path, line, reference) for path, page in pages.items() for line, reference in page.references]
    for path in sorted(root.rglob("*.css")):
        text = path.read_text(encoding="utf-8")
        for match in re.finditer(r"url\(\s*(['\"]?)(.*?)\1\s*\)", text):
            references.append((path, text.count("\n", 0, match.start()) + 1, match.group(2)))
    for source, line, reference in references:
        error = check_reference(root, source, line, reference, pages)
        if error:
            errors.append(error)
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print(f"Website check passed: {len(pages)} HTML pages, {len(references)} asset and link references.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
