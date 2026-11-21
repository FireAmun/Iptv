"""Builds the GitHub Pages site into a temporary folder and checks its pages, links and safety rules."""
from html.parser import HTMLParser
from pathlib import Path
import sys
import tempfile
import unittest
from urllib.parse import urljoin, urlsplit

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import build_site  # noqa: E402

PAGES = ["index.html", "privacy/index.html", "terms/index.html", "notices/index.html",
         "ar/index.html", "ar/privacy/index.html", "ar/terms/index.html", "404.html"]


class Collector(HTMLParser):
    def __init__(self):
        super().__init__()
        self.refs, self.tags, self.attrs = [], [], {}

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        self.tags.append((tag, attrs))
        if tag == "html":
            self.attrs = attrs
        for key in ("href", "src"):
            if attrs.get(key):
                self.refs.append((tag, key, attrs[key]))


def parse(path):
    collector = Collector()
    collector.feed(path.read_text(encoding="utf-8"))
    return collector


class BuildSiteTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temporary = tempfile.TemporaryDirectory()
        cls.out = Path(cls.temporary.name) / "site"
        cls.written = build_site.build(cls.out)

    @classmethod
    def tearDownClass(cls):
        cls.temporary.cleanup()

    def test_every_page_is_built_with_language_and_direction(self):
        self.assertEqual(sorted(PAGES), sorted(self.written))
        for page in PAGES:
            html = parse(self.out / page).attrs
            arabic = page.startswith("ar/")
            self.assertEqual("ar" if arabic else "en", html["lang"], page)
            self.assertEqual("rtl" if arabic else "ltr", html["dir"], page)

    def test_local_links_resolve_and_only_known_hosts_are_used(self):
        allowed_hosts = {"github.com", "fireamun.github.io", "www.sitemaps.org"}
        for page in PAGES:
            base = "https://fireamun.github.io/Iptv/" + page
            for tag, key, ref in parse(self.out / page).refs:
                target = urlsplit(urljoin(base, ref))
                if target.scheme == "mailto":
                    continue
                self.assertEqual("https", target.scheme, f"{page}: {ref}")
                self.assertIn(target.hostname, allowed_hosts, f"{page}: {ref}")
                if target.hostname == "fireamun.github.io":
                    self.assertTrue(target.path.startswith("/Iptv/"), f"{page}: {ref}")
                    local = self.out / target.path.removeprefix("/Iptv/")
                    if target.path.endswith("/"):
                        local = local / "index.html"
                    self.assertTrue(local.is_file(), f"{page}: {ref} -> {local}")
                if tag in ("script", "img") or (tag == "link" and key == "href" and "stylesheet" in ref):
                    self.assertEqual("fireamun.github.io", target.hostname, f"{page} loads a remote resource: {ref}")

    def test_pages_are_locked_down(self):
        for page in PAGES:
            text = (self.out / page).read_text(encoding="utf-8")
            self.assertIn('http-equiv="Content-Security-Policy"', text, page)
            self.assertIn("connect-src https://api.github.com;", text, page)
            self.assertNotIn("<script>", text, page)
            self.assertNotRegex(text, r'(?:href|src|content)="http://', page)

    def test_legal_pages_carry_every_heading_from_the_bundled_documents(self):
        for language, prefix in (("en", ""), ("ar", "ar/")):
            for name in ("privacy", "terms"):
                text = (self.out / prefix / name / "index.html").read_text(encoding="utf-8")
                source = (build_site.LEGAL / language / f"{name}.md").read_text(encoding="utf-8")
                for kind, heading in build_site.parse_markdown(source):
                    if kind in ("h1", "h2"):
                        self.assertIn(build_site.inline(heading), text, f"{prefix}{name}: {heading}")
        notices = (self.out / "notices" / "index.html").read_text(encoding="utf-8")
        for title, _ in build_site.NOTICE_LICENSES:
            self.assertIn(build_site.inline(title), notices)
        self.assertIn("Apache License", notices)

    def test_english_and_arabic_pages_point_at_each_other(self):
        pairs = [("index.html", "ar/index.html"), ("privacy/index.html", "ar/privacy/index.html"),
                 ("terms/index.html", "ar/terms/index.html")]
        for english, arabic in pairs:
            en = (self.out / english).read_text(encoding="utf-8")
            ar = (self.out / arabic).read_text(encoding="utf-8")
            en_url = "https://fireamun.github.io/Iptv/" + english.removesuffix("index.html")
            ar_url = "https://fireamun.github.io/Iptv/" + arabic.removesuffix("index.html")
            for text in (en, ar):
                self.assertIn(f'hreflang="en" href="{en_url}"', text)
                self.assertIn(f'hreflang="ar" href="{ar_url}"', text)

    def test_download_page_uses_the_stable_latest_release_address(self):
        for page in ("index.html", "ar/index.html"):
            text = (self.out / page).read_text(encoding="utf-8")
            self.assertIn("https://github.com/FireAmun/Iptv/releases/latest/download/amar-iptv.apk", text)
            self.assertIn("assets/download.js", text)
        self.assertTrue((self.out / "assets" / "fonts" / "cairo.ttf").is_file())
        self.assertTrue((self.out / "assets" / "fonts" / "manrope.ttf").is_file())

    def test_app_privacy_address_matches_a_built_page(self):
        source = (build_site.DESIGN / "kotlin" / "com" / "fireamun" / "iptv" / "core" / "designsystem" / "AmarLegal.kt").read_text(encoding="utf-8")
        self.assertIn(f'const val WEBSITE = "{build_site.SITE_URL}"', source)
        for path in ("privacy/index.html", "ar/privacy/index.html"):
            self.assertTrue((self.out / path).is_file(), path)

    def test_sample_playlist_is_published_with_credits_and_secure_streams(self):
        text = (self.out / "samples" / "amar-demo.m3u").read_text(encoding="utf-8")
        lines = text.splitlines()
        self.assertEqual("#EXTM3U", lines[0])
        self.assertIn("Blender Foundation", text)
        self.assertIn("creativecommons.org/licenses/by/3.0", text)
        entries = [line for line in lines if line.startswith("#EXTINF:")]
        streams = [line for line in lines if line and not line.startswith("#")]
        self.assertTrue(entries)
        self.assertEqual(len(entries), len(streams))
        for entry in entries:
            self.assertIn('group-title="', entry)
        for stream in streams:
            self.assertTrue(stream.startswith("https://"), stream)

    def test_parser_matches_the_app_rules(self):
        blocks = build_site.parse_markdown("# Title\n\nOne\ntwo\n\n## Part\n- item\nAfter")
        self.assertEqual([("h1", "Title"), ("p", "One two"), ("h2", "Part"), ("li", "item"), ("p", "After")], blocks)
        self.assertEqual([("license", "a b"), ("license", "c")], build_site.license_blocks("a\n  b\n\n\nc\n"))


if __name__ == "__main__":
    unittest.main()
