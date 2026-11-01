"""Check native-navigation sources without launching Minecraft."""
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CHAPTERS = ('start', 'power', 'metallurgy', 'logistics', 'processing', 'troubleshooting')


def check():
    locales = json.loads((ROOT / 'src/main/translations/languages.json').read_text('utf-8'))
    shared = ROOT.parent / 'docs/languages.json'
    if shared.is_file():
        assert locales.keys() == json.loads(shared.read_text('utf-8')).keys(), 'The series locale list changed'
    templates = {locale: json.loads((ROOT / f'src/main/templates/lang/{locale}.json').read_text('utf-8-sig'))
                 for locale in ('en_us', 'ja_jp')}
    fingerprint = ''.join(f'{locale}\0{key}\0{templates[locale][key]}\0'
                          for locale in ('en_us', 'ja_jp') for key in sorted(templates[locale]))
    review = json.loads((ROOT / 'src/main/translations/navigation-review.json').read_text('utf-8'))
    assert review['source_sha256'] == hashlib.sha256(fingerprint.encode()).hexdigest(), 'Stale navigation source review'
    assert set(review['reviewed_locales']) == set(locales)
    for locale in locales:
        if locale not in templates:
            templates[locale] = json.loads((ROOT / f'src/main/translations/navigation/{locale}.json').read_text('utf-8'))
        values = templates[locale]
        assert values.keys() == templates['en_us'].keys(), f'Navigation key mismatch: {locale}'
        for key, original in templates['en_us'].items():
            assert isinstance(values[key], str) and values[key].strip() and '\ufffd' not in values[key], (locale, key)
            assert re.findall(r'%(?:\d+\$)?[sd]', original) == re.findall(r'%(?:\d+\$)?[sd]', values[key]), (locale, key)
        for number, chapter in enumerate(CHAPTERS, 1):
            assert values[f'ieponder.ponder.tag.{chapter}'].startswith(f'{number}.'), (locale, chapter)
    assert not (ROOT / 'src/main/java/dev/ieponder/client/GuideScreen.java').exists(), 'Custom chapter catalogue remains'
    from check_translations import originals
    known_keys = set(templates['en_us']) | set(originals())
    for path in (ROOT / 'src/main/java').rglob('*.java'):
        references = re.findall(r'Component\.translatable\("(ieponder\.[^"]+)"\s*[,)]', path.read_text('utf-8'))
        assert set(references) <= known_keys, f'Untranslated fixed text references: {path}: {set(references) - known_keys}'
    assert 'ieponder.command.requires_world' in templates['en_us'], 'World-required command error lost its translation'
    assert not any(key.startswith('ieponder.button.') for key in templates['en_us']), 'Obsolete guide button text remains'
    lessons = sum((json.loads((ROOT / f'src/main/resources/assets/ieponder/guide/{name}.json').read_text('utf-8'))
                   for name in ('lessons', 'processing')), [])
    icons = set()
    for lesson in lessons:
        pair = (lesson['chapter'], lesson['icon'])
        assert pair not in icons, f'Ambiguous native tag item: {pair}'
        icons.add(pair)
    print(f'PASS: {len(locales)} navigation locales, {len(templates["en_us"])} keys each; {len(lessons)} unique chapter/item targets')


if __name__ == '__main__':
    check()
