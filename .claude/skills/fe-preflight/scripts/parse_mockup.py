#!/usr/bin/env python3
"""
parse_mockup.py — Pre-flight mockup analyzer (no external dependencies)

Extracts CSS color/spacing values from a mockup HTML file and maps them
to the project's design tokens (--tkn-* variables). Outputs a JSON report
that the AI uses to build build-spec.json.

Usage:
    python parse_mockup.py <mockup.html> <tokens.css> [--output <report.json>]

Output JSON structure:
    {
      "mockup_source": "...",
      "color_map": [
        { "raw": "#007AFF", "token": "--tkn-color-primary-default", "match": "exact" },
        { "raw": "#f2f2f7", "token": "--tkn-color-bg-light",        "match": "approx", "delta": 3 },
        { "raw": "#ab1234", "token": null,                          "match": "none" }
      ],
      "spacing_map": [
        { "raw": "16px", "token": "--tkn-spacing-8", "match": "exact" },
        { "raw": "13px", "token": "--tkn-spacing-7", "match": "approx", "delta": 1 }
      ],
      "radius_map": [
        { "raw": "12px", "token": "--tkn-radius-lg", "match": "exact" }
      ],
      "raw_colors_frequency": { "#007AFF": 12, "#f2f2f7": 7 },
      "unmatched_colors": ["#ab1234"],
      "sections_found": ["nav", "filter-bar", "card", "tabbar"]
    }
"""

import re
import sys
import json
import math
import argparse
from pathlib import Path


# ── helpers ──────────────────────────────────────────────────────────────────

def hex_to_rgb(h: str) -> tuple[int, int, int] | None:
    h = h.lstrip('#')
    if len(h) == 3:
        h = ''.join(c * 2 for c in h)
    if len(h) != 6:
        return None
    try:
        return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)
    except ValueError:
        return None


def rgba_to_rgb(s: str) -> tuple[int, int, int] | None:
    """Best-effort rgba(r,g,b,a) → (r,g,b)."""
    m = re.match(r'rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)', s)
    if m:
        return int(m.group(1)), int(m.group(2)), int(m.group(3))
    return None


def color_distance(a: tuple, b: tuple) -> float:
    return math.sqrt(sum((x - y) ** 2 for x, y in zip(a, b)))


# ── token loader ─────────────────────────────────────────────────────────────

def load_tokens(tokens_css: str) -> dict:
    """Parse tokens.css → {'--tkn-color-primary-default': '#0071e3', ...}"""
    result = {}
    for m in re.finditer(r'(--tkn-[\w-]+)\s*:\s*([^;/]+)', tokens_css):
        name = m.group(1).strip()
        value = m.group(2).strip()
        result[name] = value
    return result


def build_color_token_index(tokens: dict) -> list[tuple[str, tuple]]:
    """Return [(token_name, rgb_tuple)] for all color tokens that are hex or rgba."""
    index = []
    for name, val in tokens.items():
        rgb = hex_to_rgb(val) or rgba_to_rgb(val)
        if rgb:
            index.append((name, rgb))
    return index


def build_spacing_index(tokens: dict) -> list[tuple[str, int]]:
    index = []
    for name, val in tokens.items():
        if ('spacing' in name or 'radius' in name) and val.endswith('px'):
            try:
                index.append((name, int(val.replace('px', ''))))
            except ValueError:
                pass
    return sorted(index, key=lambda x: x[1])


# ── mockup extractor ──────────────────────────────────────────────────────────

def extract_colors(html: str) -> dict[str, int]:
    """Extract all color values and their occurrence counts from inline styles + <style> blocks."""
    freq: dict[str, int] = {}

    # hex colors (#rrggbb or #rgb)
    for m in re.finditer(r'#([0-9a-fA-F]{3,6})\b', html):
        raw = m.group(0).upper()
        freq[raw] = freq.get(raw, 0) + 1

    # rgb/rgba() values
    for m in re.finditer(r'rgba?\(\s*\d+\s*,\s*\d+\s*,\s*\d+[^)]*\)', html):
        raw = m.group(0).lower().strip()
        freq[raw] = freq.get(raw, 0) + 1

    # filter out likely non-color fragments (e.g. very short hex inside id attrs)
    return {k: v for k, v in freq.items() if not (k.startswith('#') and len(k) <= 4 and v <= 1)}


def extract_spacing(html: str) -> dict[str, int]:
    """Extract px spacing/radius values."""
    freq: dict[str, int] = {}
    for m in re.finditer(r'\b(\d{1,3})px\b', html):
        val = int(m.group(1))
        if 1 <= val <= 120:  # ignore huge values (width/height)
            key = f'{val}px'
            freq[key] = freq.get(key, 0) + 1
    return freq


def find_sections(html: str) -> list[str]:
    """Heuristic: find likely section identifiers (class names containing layout keywords)."""
    keywords = ['nav', 'bar', 'header', 'card', 'item', 'list', 'filter', 'chip',
                'tab', 'fab', 'footer', 'sheet', 'modal', 'search', 'badge', 'btn', 'button']
    found = set()
    for m in re.finditer(r'class="([^"]+)"', html):
        for cls in m.group(1).split():
            cls_lower = cls.lower()
            for kw in keywords:
                if kw in cls_lower:
                    found.add(cls_lower)
                    break
    # return the most representative ones (deduped, sorted by length)
    return sorted(found, key=len)[:30]


# ── matchers ─────────────────────────────────────────────────────────────────

APPROX_COLOR_THRESHOLD = 25   # RGB Euclidean distance
APPROX_SPACING_THRESHOLD = 4  # px


def match_colors(freq: dict[str, int], color_index: list) -> list[dict]:
    results = []
    for raw, count in sorted(freq.items(), key=lambda x: -x[1]):
        rgb = hex_to_rgb(raw) or rgba_to_rgb(raw)
        if not rgb:
            results.append({'raw': raw, 'count': count, 'token': None, 'match': 'unparseable'})
            continue

        best_name, best_dist = None, float('inf')
        for name, t_rgb in color_index:
            d = color_distance(rgb, t_rgb)
            if d < best_dist:
                best_dist = d
                best_name = name

        if best_dist == 0:
            results.append({'raw': raw, 'count': count, 'token': best_name, 'match': 'exact'})
        elif best_dist <= APPROX_COLOR_THRESHOLD:
            results.append({'raw': raw, 'count': count, 'token': best_name,
                            'match': 'approx', 'delta': round(best_dist, 1)})
        else:
            results.append({'raw': raw, 'count': count, 'token': None, 'match': 'none'})
    return results


def match_spacing(freq: dict[str, int], spacing_index: list, kind: str = 'spacing') -> list[dict]:
    filtered = [(n, v) for n, v in spacing_index if kind in n]
    results = []
    for raw, count in sorted(freq.items(), key=lambda x: -x[1]):
        px = int(raw.replace('px', ''))
        best_name, best_delta = None, float('inf')
        for name, val in filtered:
            d = abs(px - val)
            if d < best_delta:
                best_delta = d
                best_name = name
        if best_delta == 0:
            results.append({'raw': raw, 'count': count, 'token': best_name, 'match': 'exact'})
        elif best_delta <= APPROX_SPACING_THRESHOLD:
            results.append({'raw': raw, 'count': count, 'token': best_name,
                            'match': 'approx', 'delta': best_delta})
        else:
            results.append({'raw': raw, 'count': count, 'token': None, 'match': 'none'})
    return results


# ── main ──────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description='Pre-flight mockup analyzer')
    parser.add_argument('mockup', help='Path to mockup HTML file')
    parser.add_argument('tokens', help='Path to tokens.css file')
    parser.add_argument('--output', '-o', default=None, help='Output JSON file (default: stdout)')
    args = parser.parse_args()

    html = Path(args.mockup).read_text(encoding='utf-8')
    tokens_css = Path(args.tokens).read_text(encoding='utf-8')

    tokens = load_tokens(tokens_css)
    color_index = build_color_token_index(tokens)
    spacing_index = build_spacing_index(tokens)

    color_freq = extract_colors(html)
    spacing_freq = extract_spacing(html)
    sections = find_sections(html)

    color_map = match_colors(color_freq, color_index)
    spacing_map = match_spacing(spacing_freq, spacing_index, kind='spacing')
    radius_map = match_spacing(spacing_freq, spacing_index, kind='radius')

    unmatched_colors = [c['raw'] for c in color_map if c['match'] == 'none']
    approx_colors = [c for c in color_map if c['match'] == 'approx']
    approx_spacing = [s for s in spacing_map if s['match'] == 'approx' or s['match'] == 'none']

    report = {
        'mockup_source': str(args.mockup),
        'tokens_source': str(args.tokens),
        'summary': {
            'total_colors_found': len(color_freq),
            'exact_color_matches': sum(1 for c in color_map if c['match'] == 'exact'),
            'approx_color_matches': len(approx_colors),
            'unmatched_colors': len(unmatched_colors),
            'needs_review': len(approx_colors) + len(unmatched_colors),
        },
        'color_map': color_map,
        'spacing_map': spacing_map,
        'radius_map': [r for r in radius_map if 'radius' in (r.get('token') or '')],
        'sections_found': sections,
        'unmatched_colors': unmatched_colors,
        'review_required': {
            'approx_colors': approx_colors,
            'spacing_issues': approx_spacing,
        },
    }

    output_json = json.dumps(report, ensure_ascii=False, indent=2)
    if args.output:
        Path(args.output).write_text(output_json, encoding='utf-8')
        print(f'✅  Report written to {args.output}', file=sys.stderr)
        print(f'    Colors: {report["summary"]["total_colors_found"]} found, '
              f'{report["summary"]["exact_color_matches"]} exact, '
              f'{report["summary"]["approx_color_matches"]} approx, '
              f'{report["summary"]["unmatched_colors"]} unmatched', file=sys.stderr)
        print(f'    Needs review: {report["summary"]["needs_review"]} items', file=sys.stderr)
    else:
        print(output_json)


if __name__ == '__main__':
    main()
