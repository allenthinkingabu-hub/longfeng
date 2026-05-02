#!/usr/bin/env python3
"""
Backfill testids referenced in test-plan.json into build-spec.json blocks.

For each block in build-spec.json, finds all TCs with matching `ac` and
union-merges their testids into block.testids (preserving existing testids).

Usage:
  python3 backfill_testids.py \
    --build-spec design/tasks/preflight/<PAGE>-build-spec.json \
    --test-plan  design/tasks/testplan/<PAGE>-test-plan.json \
    --inplace

Exit codes:
  0  success
  1  inputs missing or malformed
"""

import argparse
import json
import sys
from pathlib import Path


def load(path: Path) -> dict:
    with path.open(encoding="utf-8") as f:
        return json.load(f)


def save(path: Path, data: dict) -> None:
    with path.open("w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--build-spec", required=True, type=Path)
    parser.add_argument("--test-plan", required=True, type=Path)
    parser.add_argument("--inplace", action="store_true",
                        help="Write back to build-spec path (default: print only)")
    parser.add_argument("--out", type=Path, help="Alternative output path")
    args = parser.parse_args()

    if not args.build_spec.exists():
        sys.stderr.write(f"ERROR: build-spec not found: {args.build_spec}\n")
        return 1
    if not args.test_plan.exists():
        sys.stderr.write(f"ERROR: test-plan not found: {args.test_plan}\n")
        return 1

    build_spec = load(args.build_spec)
    test_plan = load(args.test_plan)

    blocks = build_spec.get("blocks") or []
    test_cases = test_plan.get("test_cases") or []

    if not isinstance(blocks, list) or not blocks:
        sys.stderr.write("ERROR: build-spec.blocks missing or empty\n")
        return 1

    ac_to_testids: dict[str, set[str]] = {}
    for tc in test_cases:
        ac = tc.get("ac")
        ids = tc.get("testids") or []
        if not ac:
            continue
        ac_to_testids.setdefault(ac, set()).update(ids)

    delta = 0
    block_changes: list[tuple[str, list[str]]] = []
    for block in blocks:
        ac = block.get("ac")
        if not ac or ac not in ac_to_testids:
            continue
        existing = set(block.get("testids") or [])
        merged = sorted(existing | ac_to_testids[ac])
        added = sorted(ac_to_testids[ac] - existing)
        if added:
            block["testids"] = merged
            delta += len(added)
            block_changes.append((block.get("id", "?"), added))

    if args.inplace or args.out:
        out_path = args.out or args.build_spec
        save(out_path, build_spec)
        print(f"✅ build-spec updated: {out_path}")
    else:
        print(json.dumps(build_spec, ensure_ascii=False, indent=2))

    if block_changes:
        print(f"\n📌 {delta} testid(s) added across {len(block_changes)} block(s):")
        for block_id, added in block_changes:
            print(f"  - block={block_id}: +{', '.join(added)}")
    else:
        print("\n📌 no new testids — build-spec already covers all TC references")
    return 0


if __name__ == "__main__":
    sys.exit(main())
