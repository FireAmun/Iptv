import tempfile
import unittest
from pathlib import Path

from scripts.check_repository import check_markdown_links


class MarkdownLinkChecks(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.document = self.root / "README.md"

    def check(self, text):
        self.document.write_text(text, encoding="utf-8")
        return check_markdown_links(self.root, [self.document])

    def test_reports_a_missing_local_target(self):
        errors = self.check("Read [missing](docs/missing.md).")
        self.assertEqual(len(errors), 1)
        self.assertIn("docs/missing.md", errors[0])

    def test_accepts_existing_relative_target_with_anchor(self):
        (self.root / "guide.md").write_text("# Guide", encoding="utf-8")
        self.assertEqual(self.check("[Guide](guide.md#setup)"), [])

    def test_accepts_encoded_and_angle_bracket_paths(self):
        (self.root / "my guide.md").write_text("Guide", encoding="utf-8")
        self.assertEqual(self.check("[One](my%20guide.md) [Two](<my guide.md>)"), [])

    def test_does_not_fetch_external_or_anchor_links(self):
        self.assertEqual(self.check("[Web](https://example.invalid/a) [Mail](mailto:a@example.invalid) [Here](#setup)"), [])

    def test_ignores_examples_in_fenced_code(self):
        self.assertEqual(self.check("```markdown\n[Example](missing.md)\n```"), [])

    def test_reports_invalid_utf8(self):
        self.document.write_bytes(b"\xff")
        errors = check_markdown_links(self.root, [self.document])
        self.assertEqual(len(errors), 1)
        self.assertIn("UTF-8", errors[0])

    def test_rejects_links_outside_the_repository(self):
        errors = self.check("[Outside](../outside.md)")
        self.assertEqual(len(errors), 1)
        self.assertIn("outside", errors[0].lower())


if __name__ == "__main__":
    unittest.main()
