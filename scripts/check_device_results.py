#!/usr/bin/env python3
"""Require complete native XML before a Gradle success may run process probes."""
import argparse
from collections import Counter
from pathlib import Path
import sys
from xml.etree import ElementTree as ET

PACKAGE = "com.fireamun.iptv.prototype."
SCOPES = {
    "full": (109, None),
    "player": (25, {"PlayerTracksTest": 12, "PlayerPictureInPictureTest": 6, "PlaybackCheckpointTest": 7}),
    "tracks": (12, {"PlayerTracksTest": 12}),
    "pip": (6, {"PlayerPictureInPictureTest": 6}),
    "forms": (13, {"CategoryPickerFlowTest": 2, "UrlVisibilityTest": 4, "XtreamFlowTest": 7}),
}


def validate_results(folder: Path, scope: str) -> int:
    expected, classes = SCOPES[scope]
    reports = list(folder.rglob("TEST-*.xml"))
    if len(reports) != 1:
        raise ValueError(f"Expected one native XML report; found {len(reports)}")
    root = ET.parse(reports[0]).getroot()
    cases = list(root.iter("testcase"))
    if root.tag not in ("testsuite", "testsuites") or len(cases) != expected or int(root.get("tests", "-1")) != expected:
        raise ValueError(f"Expected {expected} cases for {scope}; found {len(cases)}")
    for suite in root.iter():
        if suite.tag not in ("testsuite", "testsuites"):
            continue
        if int(suite.get("tests", "-1")) != len(list(suite.iter("testcase"))):
            raise ValueError("Native suite summary disagrees with its actual cases")
        if any(int(suite.get(key, "0")) != 0 for key in ("failures", "errors", "skipped")):
            raise ValueError("Native summary contains failures, errors or skips")
    if any(node.tag in ("failure", "error", "skipped") for node in root.iter()):
        raise ValueError("Native results contain failures, errors or skips")
    identities = [(case.get("classname", ""), case.get("name", "")) for case in cases]
    if len(set(identities)) != expected or any(not cls.startswith(PACKAGE) or not name for cls, name in identities):
        raise ValueError("Native cases have duplicate or invalid identities")
    if classes is not None:
        actual = Counter(cls.removeprefix(PACKAGE) for cls, _ in identities)
        if actual != classes:
            raise ValueError("Native classes do not match the requested diagnostic scope")
    return expected


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scope", choices=SCOPES, required=True)
    parser.add_argument("--results-dir", type=Path, required=True)
    args = parser.parse_args()
    try:
        count = validate_results(args.results_dir, args.scope)
    except (OSError, ValueError, ET.ParseError) as error:
        print(f"Native test evidence rejected: {error}", file=sys.stderr)
        return 1
    print(f"IPTV_NATIVE_RESULTS_OK scope={args.scope} tests={count}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
