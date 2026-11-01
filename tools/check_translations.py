"""Validate every authored translation against the canonical Japanese/English text."""
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TRANSLATIONS = ROOT / 'src/main/translations'


def originals():
    guide = ROOT / 'src/main/resources/assets/ieponder/guide'
    values = {}
    for name in ('lessons', 'processing'):
        for lesson in json.loads((guide / f'{name}.json').read_text('utf-8')):
            values[f'ieponder.ponder.{lesson["id"]}.header'] = lesson['title']
            values[f'ieponder.lesson.{lesson["id"]}.summary'] = lesson['summary']
            for index, step in enumerate(lesson['steps']):
                values[f'ieponder.ponder.shared.{lesson["id"]}_{index}'] = step['text']
    for entry in json.loads((guide / 'item_descriptions.json').read_text('utf-8')):
        values[f'ieponder.description.{entry["id"]}'] = entry['text']
    return values


def source_digest(source):
    text = ''.join(f'{language}\0{key}\0{source[key][language]}\0'
                   for language in ('en', 'ja') for key in sorted(source))
    return hashlib.sha256(text.encode('utf-8')).hexdigest()


def check():
    source = originals()
    locales = json.loads((TRANSLATIONS / 'languages.json').read_text('utf-8'))
    review = json.loads((TRANSLATIONS / 'content-review.json').read_text('utf-8'))
    assert review['source_sha256'] == source_digest(source), 'Original text changed; review affected translations'
    assert set(review['reviewed_locales']) == set(locales), 'Content review does not cover all 22 locales'
    for locale in locales:
        if locale in ('en_us', 'ja_jp'):
            language = 'en' if locale == 'en_us' else 'ja'
            translated = {key: value[language] for key, value in source.items()}
        else:
            path = TRANSLATIONS / 'content' / f'{locale}.json'
            translated = json.loads(path.read_text('utf-8'))
            assert hashlib.sha256(path.read_bytes()).hexdigest() == review['translation_sha256'][locale], (
                'Translation changed since review', locale)
        assert translated.keys() == source.keys(), f'Content keys differ: {locale}'
        for key, value in translated.items():
            assert isinstance(value, str) and value.strip() and '\ufffd' not in value, (locale, key)
            assert re.findall(r'%(?:\d+\$)?[sd]', value) == re.findall(r'%(?:\d+\$)?[sd]', source[key]['en']), (locale, key)
            if locale not in ('en_us', 'ja_jp'):
                assert not (len(source[key]['en']) > 40 and value == source[key]['en']), ('English copy', locale, key)
            if locale == 'ar_sa':
                assert not any(0xFB50 <= ord(char) <= 0xFDFF or 0xFE70 <= ord(char) <= 0xFEFF for char in value), (
                    'Arabic source must use logical-order letters', key)
    print(f'PASS: {len(locales)} complete content locales, {len(source)} keys each; source and reviewed file hashes current')
    native_source = json.loads((TRANSLATIONS / 'native-ui-source.json').read_text('utf-8'))
    native_review = json.loads((TRANSLATIONS / 'native-ui-review.json').read_text('utf-8'))
    fingerprint = ''.join(f'{key}\0{native_source[key]}\0' for key in sorted(native_source))
    assert native_review['source_sha256'] == hashlib.sha256(fingerprint.encode()).hexdigest(), 'Stale native UI originals'
    assert set(native_review['reviewed_locales']) == set(locales)
    native_count = 0
    for locale, expected_keys in native_review['supplement_keys'].items():
        path = TRANSLATIONS / 'native-ui' / f'{locale}.json'
        values = json.loads(path.read_text('utf-8'))
        assert set(values) == set(expected_keys), ('Native UI keys', locale)
        assert hashlib.sha256(path.read_bytes()).hexdigest() == native_review['translation_sha256'][locale], ('Native UI changed', locale)
        for key, value in values.items():
            assert value.strip() and '\ufffd' not in value, (locale, key)
            original = native_source[key]
            assert re.findall(r'%(?:\d+\$)?[sd]', value) == re.findall(r'%(?:\d+\$)?[sd]', original), (locale, key)
            assert value.count('\n') == original.count('\n'), (locale, key, 'newline')
            assert value != original or key == 'ponder.ponder.shared.ctrl_and', ('Untranslated native UI', locale, key)
        native_count += len(values)
    print(f'PASS: {native_count} native UI supplements; upstream originals and reviewed translations current')


if __name__ == '__main__':
    check()
