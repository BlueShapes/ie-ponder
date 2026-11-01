"""Audit the completed main JAR before placing it in the Prism test instance."""
import argparse
import json
import tomllib
import zipfile
from pathlib import Path

from check_translations import ROOT, originals


def check(path):
    locales = json.loads((ROOT / 'src/main/translations/languages.json').read_text('utf-8'))
    source_keys = originals().keys()
    navigation = json.loads((ROOT / 'src/main/templates/lang/en_us.json').read_text('utf-8'))
    version = next(line.split('=', 1)[1] for line in (ROOT / 'gradle.properties').read_text().splitlines()
                   if line.startswith('mod_version='))
    with zipfile.ZipFile(path) as jar:
        names = set(jar.namelist())
        mods = tomllib.loads(jar.read('META-INF/neoforge.mods.toml').decode('utf-8'))['mods']
        assert [(mod['modId'], mod['version']) for mod in mods] == [('ieponder', version)], 'Wrong release metadata'
        assert not any(name.endswith('.jar') or '/validation/' in name or 'GuideScreen' in name or 'CaptionUnitsTest' in name
                       or 'SearchTextTest' in name
                       or 'GuideClient$GuideButton' in name or 'GuideClientPlatform' in name
                       for name in names), 'Development or obsolete UI content'
        assert not any(name.startswith(('assets/immersiveengineering/', 'data/immersiveengineering/')) for name in names), 'Dependency assets bundled'
        for name in ('LICENSE', 'THIRD_PARTY_NOTICES.md', 'LICENSES/MIT-Ponder.txt', 'LICENSES/Apache-2.0.txt'):
            assert name in names and jar.read(name).strip(), f'Missing license/notice: {name}'
        config = json.loads(jar.read('ieponder.mixins.json'))
        assert config.get('client') and not config.get('mixins') and not config.get('server'), 'Mixins must remain client-only'
        packaged_locales = {Path(name).stem for name in names if name.startswith('assets/ieponder/lang/') and name.endswith('.json')}
        assert packaged_locales == set(locales), 'Wrong packaged locale list'
        for locale in locales:
            content = json.loads(jar.read(f'assets/ieponder/lang/{locale}.json'))
            assert content.keys() == set(source_keys) | navigation.keys(), f'Incomplete locale: {locale}'
            generated = ROOT / f'build/generated/guideResources/assets/ieponder/lang/{locale}.json'
            assert content == json.loads(generated.read_text('utf-8')), f'Stale packaged locale: {locale}'
        native_review = json.loads((ROOT / 'src/main/translations/native-ui-review.json').read_text('utf-8'))
        for locale in native_review['supplement_keys']:
            packaged = json.loads(jar.read(f'assets/ponder/lang/{locale}.json'))
            original = json.loads((ROOT / f'src/main/translations/native-ui/{locale}.json').read_text('utf-8'))
            assert packaged == original, f'Stale native UI supplement: {locale}'
        assert all(name.startswith('assets/ponder/lang/') and name.endswith('.json') for name in names
                   if name.startswith('assets/ponder/') and not name.endswith('/')), 'Only native language supplements may use the upstream namespace'
        for name in ('lessons', 'processing', 'item_descriptions', 'text_tones'):
            relative = f'assets/ieponder/guide/{name}.json'
            assert jar.read(relative) == (ROOT / 'src/main/resources' / relative).read_bytes(), 'Stale canonical data'
        classes = [name for name in names if name.endswith('.class')]
        for name in classes:
            assert int.from_bytes(jar.read(name)[6:8], 'big') == 65, f'Wrong Java target: {name}'
    print(f'PASS: main release {version}, Java21, {len(classes)} classes, {len(locales)} complete locales; licenses present, no validation/dependency assets')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('jar', type=Path)
    check(parser.parse_args().jar)
