"""Reject empty, partial, failed or skipped native suites before process probes."""
import argparse
from pathlib import Path
import sys
from xml.etree import ElementTree as ET


def validate_results(directory: Path, expected_tests: int) -> int:
    reports = list(directory.rglob("TEST-*.xml"))
    if expected_tests <= 0 or len(reports) != 1:
        raise ValueError("Expected one complete native test report.")
    try:
        root = ET.parse(reports[0]).getroot()
    except (OSError, ET.ParseError) as error:
        raise ValueError("Native test report could not be parsed.") from error
    cases = list(root.iter("testcase"))
    if root.tag not in ("testsuite", "testsuites") or len(cases) != expected_tests:
        raise ValueError("Native test count does not match the complete baseline.")
    identities = [(case.get("classname", "").strip(), case.get("name", "").strip()) for case in cases]
    if any(not all(identity) for identity in identities) or len(set(identities)) != expected_tests:
        raise ValueError("Native test identities are missing or duplicated.")
    if any(case.find(tag) is not None for case in cases for tag in ("failure", "error", "skipped")):
        raise ValueError("Native test cases failed, errored or were skipped.")
    for suite in root.iter():
        if suite.tag not in ("testsuite", "testsuites"):
            continue
        try:
            if any(int(suite.get(key, "0")) != 0 for key in ("failures", "errors", "skipped")):
                raise ValueError("Native suite summary is not successful.")
            if "tests" in suite.attrib and int(suite.attrib["tests"]) != len(list(suite.iter("testcase"))):
                raise ValueError("Native suite summary count is inconsistent.")
        except ValueError as error:
            raise ValueError("Native suite summary is not a consistent success.") from error
    return len(cases)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--results-dir", required=True, type=Path)
    parser.add_argument("--expected-tests", required=True, type=int)
    args = parser.parse_args()
    try:
        count = validate_results(args.results_dir, args.expected_tests)
    except ValueError as error:
        print(str(error), file=sys.stderr)
        return 2
    print(f"IPTV_NATIVE_RESULTS_OK tests={count}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
