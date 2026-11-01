"""Draft width aid using cached native advances; final proof requires Minecraft wrapping/shaping."""
import argparse
import json
from pathlib import Path

from check_translations import ROOT, originals


def line_count(text, advances, width=180):
    start = count = 0
    while start < len(text):
        advance, space, end = 0, -1, start
        while end < len(text):
            if text[end] == ' ':
                space = end
            value = advances[str(ord(text[end]))]
            if advance + value > width and end > start:
                break
            advance += value
            end += 1
        start = space + 1 if end < len(text) and space >= start else end
        count += 1
    return count


def check(metrics_path, locales, descriptions=False):
    metrics = json.loads(metrics_path.read_text('utf-8'))
    source = originals()
    overflows, missing, checked, deferred = [], {}, 0, []
    for locale in locales:
        # Logical Arabic advances cannot predict contextual shaping and bidi wrapping.
        if locale == 'ar_sa':
            deferred.append(locale)
            continue
        if locale in ('ja_jp', 'en_us'):
            language = 'ja' if locale == 'ja_jp' else 'en'
            texts = {key: values[language] for key, values in source.items()}
        else:
            texts = json.loads((ROOT / f'src/main/translations/content/{locale}.json').read_text('utf-8'))
        advances = metrics.get(locale, {})
        for key, text in texts.items():
            if not key.startswith('ieponder.description.' if descriptions else 'ieponder.ponder.shared.'):
                continue
            for index, unit in enumerate(text.splitlines()):
                absent = {ord(char) for char in unit if str(ord(char)) not in advances}
                if absent:
                    missing.setdefault(locale, set()).update(absent)
                    continue
                checked += 1
                lines = line_count(unit, advances, 240 if descriptions else 180)
                if lines > 3:
                    overflows.append(dict(locale=locale, key=key, unit=index, lines=lines, text=unit))
    return dict(scope='draft-only; cache font stack must match; actual Minecraft validation required',
                metrics=str(metrics_path), content='descriptions' if descriptions else 'captions',
                width=240 if descriptions else 180, checkedUnits=checked, deferredShaping=deferred,
                missingCodepoints={key: [f'U+{value:04X}' for value in sorted(values)]
                                   for key, values in missing.items()}, overflows=overflows)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('locales', nargs='*')
    parser.add_argument('--metrics', type=Path, default=ROOT / 'build/validation-run/caption-font-metrics.json')
    parser.add_argument('--output', type=Path)
    parser.add_argument('--descriptions', action='store_true', help='Check authored tooltip units at240px')
    args = parser.parse_args()
    locales = args.locales or list(json.loads((ROOT / 'src/main/translations/languages.json').read_text('utf-8')))
    result = check(args.metrics, locales, args.descriptions)
    if args.output:
        args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', 'utf-8')
    print(f"DRAFT: {result['checkedUnits']} measured units, {len(result['overflows'])} possible overflows, "
          f"{len(result['missingCodepoints'])} locales with unmeasured glyphs; Arabic needs actual shaping")
    raise SystemExit(bool(result['overflows'] or result['missingCodepoints']))
