"""Check UTF-8 Markdown and inline local links without making network requests."""

import argparse
import re
import subprocess
from pathlib import Path
from urllib.parse import unquote, urlsplit


LINK = re.compile(r"(?<!!)\[[^\]\n]*\]\(\s*(?:<([^>\n]+)>|([^\s)]+))\s*\)")


def check_markdown_links(root, documents):
    root = Path(root).resolve()
    errors = []
    for document in documents:
        document = Path(document).resolve()
        try:
            text = document.read_text(encoding="utf-8")
        except UnicodeError:
            errors.append(f"{document.name}: invalid UTF-8")
            continue
        fence = None
        for number, line in enumerate(text.splitlines(), 1):
            marker = re.match(r"^\s*(`{3,}|~{3,})", line)
            if marker:
                value = marker.group(1)
                if fence is None:
                    fence = value
                elif value[0] == fence[0] and len(value) >= len(fence):
                    fence = None
                continue
            if fence is not None:
                continue
            for match in LINK.finditer(line):
                target = match.group(1) or match.group(2)
                parsed = urlsplit(target)
                if parsed.scheme or target.startswith(("#", "//")):
                    continue
                path = unquote(parsed.path)
                if not path:
                    continue
                resolved = (document.parent / path).resolve()
                location = f"{document.relative_to(root)}:{number}"
                if not resolved.is_relative_to(root):
                    errors.append(f"{location}: link outside repository: {target}")
                elif not resolved.exists():
                    errors.append(f"{location}: missing local target: {target}")
    return errors


def markdown_documents(root):
    result = subprocess.run(
        ["git", "ls-files", "-z", "--cached", "--others", "--exclude-standard", "--", "*.md"],
        cwd=root, check=True, capture_output=True,
    )
    return [root / name for name in sorted(set(result.stdout.decode("utf-8").split("\0"))) if name]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    root = parser.parse_args().root.resolve()
    documents = markdown_documents(root)
    errors = check_markdown_links(root, documents)
    if errors:
        print("\n".join(errors))
        return 1
    print(f"PASS: {len(documents)} Markdown documents; inline local links resolve. External URLs and anchors are not fetched or validated.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
