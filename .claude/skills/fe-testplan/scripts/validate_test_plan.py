#!/usr/bin/env python3
"""
Validate test-plan.json against schema + coverage rules.

Usage:
  python3 validate_test_plan.py <test-plan.json> <business-analysis.yml> [--page PAGE]

Exit codes:
  0  All checks pass
  1  Schema/coverage failure (blocking) → caller should HALT
  2  Warning only (testid not in ux_anchor, etc.) → caller can proceed but should surface in review
"""

import argparse
import json
import re
import sys
from pathlib import Path

try:
    import yaml
except ImportError:
    sys.stderr.write("ERROR: PyYAML not installed. Run: pip install pyyaml\n")
    sys.exit(1)


REQUIRED_TC_FIELDS = {"id", "ac", "category", "title", "priority", "tracks",
                      "setup_group", "testids", "steps", "expected", "source"}
REQUIRED_PLAN_FIELDS = {"page", "phase", "version", "generated_at",
                        "source", "ac_coverage", "test_cases"}
VALID_CATEGORIES = {"happy_path", "error_path", "boundary", "visual", "observable"}
VALID_PRIORITIES = {"P0", "P1", "P2"}
VALID_TRACKS = {"A", "B", "C"}
TC_ID_PATTERN = re.compile(r"^TC-FE-S\d+-\d{3}$")
SETUP_GROUP_PATTERN = re.compile(r"^[a-z0-9]+(-[a-z0-9]+)+$")
MATRIX_REF_PATTERN = re.compile(r"^(happy_path|error_paths|boundary|visual|observable)\.\d+$")


def load_json(path: Path) -> dict:
    with path.open(encoding="utf-8") as f:
        return json.load(f)


def load_yaml(path: Path) -> dict:
    with path.open(encoding="utf-8") as f:
        return yaml.safe_load(f)


def collect_ux_anchor_testids(ac_entry: dict) -> set[str]:
    ux_anchor = ac_entry.get("four_role_slots", {}).get("ux_anchor") or ""
    return set(re.findall(r"[a-z][a-z0-9]*(?:\.[a-z0-9-]+)+", ux_anchor))


def collect_matrix_refs(ac_entry: dict) -> set[str]:
    matrix = ac_entry.get("verification_matrix") or {}
    refs = set()
    for category, items in matrix.items():
        if not items:
            continue
        for item in items:
            item_id = item.get("id", "")
            if "." in item_id:
                refs.add(item_id)
    return refs


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("test_plan", type=Path)
    parser.add_argument("business_analysis", type=Path)
    parser.add_argument("--page", help="Page name for diagnostic context")
    args = parser.parse_args()

    if not args.test_plan.exists():
        sys.stderr.write(f"ERROR: test-plan not found: {args.test_plan}\n")
        return 1
    if not args.business_analysis.exists():
        sys.stderr.write(f"ERROR: business-analysis not found: {args.business_analysis}\n")
        return 1

    plan = load_json(args.test_plan)
    analysis = load_yaml(args.business_analysis)

    blocking: list[str] = []
    warnings: list[str] = []

    missing_top = REQUIRED_PLAN_FIELDS - set(plan.keys())
    if missing_top:
        blocking.append(f"top-level missing fields: {sorted(missing_top)}")

    test_cases = plan.get("test_cases", [])
    if not isinstance(test_cases, list) or not test_cases:
        blocking.append("test_cases must be a non-empty array")
        return _report(blocking, warnings)

    ac_index = {ac["ac_id"]: ac for ac in analysis.get("ac_coverage", [])}
    seen_ids: set[str] = set()
    ac_to_tc_categories: dict[str, set[str]] = {}
    ac_to_p0_count: dict[str, int] = {}

    for idx, tc in enumerate(test_cases):
        loc = f"test_cases[{idx}] ({tc.get('id', '?')})"

        missing = REQUIRED_TC_FIELDS - set(tc.keys())
        if missing:
            blocking.append(f"{loc} missing fields: {sorted(missing)}")
            continue

        if not TC_ID_PATTERN.match(tc["id"]):
            blocking.append(f"{loc} id format invalid (expect TC-FE-S<phase>-NNN)")
        if tc["id"] in seen_ids:
            blocking.append(f"{loc} duplicate id")
        seen_ids.add(tc["id"])

        if tc["category"] not in VALID_CATEGORIES:
            blocking.append(f"{loc} category invalid: {tc['category']}")
        if tc["priority"] not in VALID_PRIORITIES:
            blocking.append(f"{loc} priority invalid: {tc['priority']}")
        tracks = tc.get("tracks") or []
        if not tracks or not set(tracks).issubset(VALID_TRACKS):
            blocking.append(f"{loc} tracks invalid: {tracks}")

        if not SETUP_GROUP_PATTERN.match(tc["setup_group"]):
            warnings.append(f"{loc} setup_group naming non-standard: {tc['setup_group']!r}")

        if not isinstance(tc["testids"], list) or not tc["testids"]:
            blocking.append(f"{loc} testids must be a non-empty array")
        if not isinstance(tc["steps"], list) or not tc["steps"]:
            blocking.append(f"{loc} steps must be a non-empty array")
        if not isinstance(tc["expected"], list) or not tc["expected"]:
            blocking.append(f"{loc} expected must be a non-empty array")

        for assertion in tc.get("expected", []):
            stripped = assertion.strip()
            if stripped in {"渲染正常", "正常显示", "ok", "OK", "正确"}:
                blocking.append(f"{loc} expected has non-atomic assertion: {assertion!r}")

        source = tc.get("source") or {}
        matrix_ref = source.get("matrix_ref")
        if not matrix_ref or not MATRIX_REF_PATTERN.match(matrix_ref):
            blocking.append(f"{loc} source.matrix_ref invalid: {matrix_ref}")

        ac = tc["ac"]
        if ac not in ac_index:
            blocking.append(f"{loc} ac {ac!r} not in business-analysis.ac_coverage")
            continue

        ac_to_tc_categories.setdefault(ac, set()).add(tc["category"])
        if tc["priority"] == "P0":
            ac_to_p0_count[ac] = ac_to_p0_count.get(ac, 0) + 1

        ac_entry = ac_index[ac]
        ux_testids = collect_ux_anchor_testids(ac_entry)
        for tid in tc["testids"]:
            if tid not in ux_testids:
                warnings.append(f"{loc} testid {tid!r} not declared in ux_anchor for {ac}")

        matrix_refs = collect_matrix_refs(ac_entry)
        if matrix_ref and matrix_ref not in matrix_refs:
            warnings.append(f"{loc} matrix_ref {matrix_ref!r} not found under {ac}")

    for ac, ac_entry in ac_index.items():
        if ac not in ac_to_tc_categories:
            continue
        if ac_to_p0_count.get(ac, 0) == 0:
            blocking.append(f"AC {ac} has no P0 TC")
        present = ac_to_tc_categories[ac]
        matrix = ac_entry.get("verification_matrix") or {}
        if matrix.get("happy_path") and "happy_path" not in present:
            blocking.append(f"AC {ac} has happy_path in matrix but no TC of category=happy_path")
        if matrix.get("error_paths") and "error_path" not in present:
            warnings.append(f"AC {ac} has error_paths in matrix but no TC of category=error_path")
        if matrix.get("boundary") and "boundary" not in present:
            warnings.append(f"AC {ac} has boundary in matrix but no TC of category=boundary")

    return _report(blocking, warnings)


def _report(blocking: list[str], warnings: list[str]) -> int:
    if blocking:
        print("❌ BLOCKING errors:")
        for b in blocking:
            print(f"  - {b}")
    if warnings:
        print("⚠️  Warnings:")
        for w in warnings:
            print(f"  - {w}")
    if not blocking and not warnings:
        print("✅ test-plan.json validates clean")
        return 0
    if blocking:
        print(f"\nBLOCKING: {len(blocking)} · WARN: {len(warnings)}")
        return 1
    print(f"\nWARN: {len(warnings)} · no blocking errors")
    return 2


if __name__ == "__main__":
    sys.exit(main())
