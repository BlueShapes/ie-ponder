"""Compare independently extracted fixed-IE manual/variant targets to both guide entrances."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile

from extract_manual_inventory import extract_inventory


ROOT = Path(__file__).resolve().parents[1]
GUIDE = 'src/main/resources/assets/ieponder/guide/'


def read_json(path, revision=None):
    if revision:
        return json.loads(subprocess.check_output(['git', 'show', f'{revision}:{path}'], cwd=ROOT))
    return json.loads((ROOT / path).read_text('utf-8'))


def routes(revision=None):
    lessons = sum((read_json(GUIDE + name, revision) for name in ('lessons.json', 'processing.json')), [])
    ponder, shift = {}, {}
    for lesson in lessons:
        for item in dict.fromkeys([lesson['icon'], 'immersiveengineering:manual', *lesson['components']]):
            ponder.setdefault(item, []).append(lesson['id'])
    for entry in read_json(GUIDE + 'item_descriptions.json', revision):
        for item in entry['items']:
            assert item not in shift, f'Duplicate Shift ownership: {item}'
            shift[item] = [entry['id']]
    return lessons, ponder, shift


def entrance(item, ponder, shift):
    return {'ponder': ponder.get(item, []), 'shift': shift.get(item, [])}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def bind_runtime(path, release_jar, fixture_path, ponder, shift):
    proof = json.loads(path.read_text('utf-8'))
    fixture = json.loads(fixture_path.read_text('utf-8'))
    assert proof.get('passed') is True and proof.get('scope') == 'ie-item-coverage-and-component-ui', (
        'A current successful focused runtime result is required')
    assert proof['expectationsSha256'] == digest(fixture_path), 'Runtime used a different expectation fixture'
    assert proof['releaseJarSha256'] == digest(release_jar), 'Runtime release JAR hash differs'
    assert set(proof['ieponderStoryboardItemIds']) == set(ponder), 'Runtime W registrations differ'
    assert set(proof['actualShiftItemIds']) == set(shift), 'Runtime Shift registrations differ'
    assert proof['manualExpectedIds'] == fixture['manualExpectedIds']
    assert proof['variantFamilies'] == fixture['variantFamilies']
    assert proof['newDescriptionKeys'] == fixture['newDescriptionKeys']
    assert all(value is True for value in proof['coverageAssertions'].values()), 'Incomplete runtime assertions'
    assert proof['allIeRegistryItemsAccountedFor'] is True and not proof['unaccountedIeItemIds']
    locales = json.loads((ROOT / 'src/main/translations/languages.json').read_text('utf-8'))
    assert set(proof['fontLocalesChecked']) == set(locales), 'Incomplete actual-font locale coverage'
    assert proof['fontProblemCount'] == 0 and proof['maximumTooltipLinesPerUnit'] <= 3
    assert proof['arabicVisualOrderShapingChecked'] is True
    assert proof['fontDescriptionKeyCount'] == len(read_json(GUIDE + 'item_descriptions.json'))
    ui = proof['uiAssertions']
    command_counts = {'manualCrusherArcFurnaceCommandsOpened', 'manualCrusherArcFurnaceCommandCount'}
    assert all(value is True for key, value in ui.items() if key not in command_counts), 'Incomplete UI assertions'
    assert ui['manualCrusherArcFurnaceCommandsOpened'] == ui['manualCrusherArcFurnaceCommandCount'] == 3
    with zipfile.ZipFile(release_jar) as archive:
        for entry, expected in proof['releaseEntriesSha256'].items():
            assert hashlib.sha256(archive.read(entry)).hexdigest() == expected, ('Runtime/JAR entry differs', entry)
    for name, expected in proof['validationClassSha256'].items():
        current = ROOT / 'build/classes/java/validation/dev/ieponder/validation' / name
        assert digest(current) == expected, ('Runtime validation class changed', name)
    return {'resultFile': str(path.resolve().relative_to(ROOT)) if path.resolve().is_relative_to(ROOT) else str(path),
            'resultSha256': digest(path), 'releaseJar': release_jar.name, 'releaseJarSha256': digest(release_jar),
            'expectationsSha256': digest(fixture_path), 'ieRegistryItemCount': proof['ieRegistryItemCount'],
            'manualExpectedIdsChecked': proof['manualExpectedIdsChecked'],
            'variantFamiliesChecked': proof['variantFamiliesChecked'],
            'fontLocalesChecked': proof['fontLocalesChecked'],
            'fontDescriptionKeyCount': proof['fontDescriptionKeyCount'],
            'maximumTooltipLinesPerUnit': proof['maximumTooltipLinesPerUnit'],
            'allIeRegistryItemsAccountedFor': True}


def check(jar, fixture_path, baseline, report_path, runtime_path=None, release_jar=None):
    inventory = extract_inventory(jar)
    fixture = json.loads(fixture_path.read_text('utf-8'))
    assert inventory['authority']['sha256'] == fixture['sourceJarSha256'], 'Different IE release artifact'
    assert inventory['manual_expected_ids'] == fixture['manualExpectedIds'], 'Independent manual universe changed'
    by_family = {family['id']: family for family in fixture['variantFamilies']}
    for family in inventory['variant_families']:
        assert by_family[family['id']]['expected_ids'] == family['expected_ids'], family['id']
    with zipfile.ZipFile(jar) as archive:
        prefix = 'assets/immersiveengineering/models/item/'
        model_ids = {'immersiveengineering:' + name[len(prefix):-5] for name in archive.namelist()
                     if name.startswith(prefix) and name.endswith('.json') and '/' not in name[len(prefix):]}
    assert model_ids == set(fixture['modelItemIds']), 'Fixed-JAR model inventory changed'
    shaders = sorted(item for item in model_ids if item.startswith('immersiveengineering:shader_')
                     and item != 'immersiveengineering:shader_bag')
    assert shaders == by_family['registered_shader_designs_and_bags']['expected_ids']

    lessons, ponder, shift = routes()
    _, before_ponder, before_shift = routes(baseline)
    owned = set(ponder) | set(shift)
    all_expected = set(inventory['manual_expected_ids'])
    for family in fixture['variantFamilies']:
        all_expected.update(family['expected_ids'])
    all_expected.update(fixture['expectedW'])
    all_expected.update(fixture.get('expectedShift', {}))
    missing = sorted(all_expected - owned)
    assert not missing, f'Independent targets have neither entrance: {missing}'
    assert not (set(item for item in owned if item.startswith('immersiveengineering:')) - model_ids), (
        'Authored IE item has no release item model')
    by_lesson = {lesson['id']: lesson for lesson in lessons}
    for item, wanted in fixture['expectedW'].items():
        assert wanted['lesson'] in ponder.get(item, []), (item, wanted)
        assert by_lesson[wanted['lesson']]['chapter'] == wanted['chapter'], (item, wanted)
    for case in fixture['componentStackCases']:
        item = case['stack']['id']
        assert (case['entrance'] == 'ponder' and item in ponder
                or case['entrance'] == 'shift' and item in shift), case['id']
    for item, expected in fixture.get('expectedShift', {}).items():
        assert shift.get(item) == [expected['description']], (item, expected)
        assert item not in ponder, f'Different function must not use a finite-storage Ponder: {item}'

    # New text cannot silently rewrite the previously accepted lessons or descriptions.
    for filename in ('lessons.json', 'processing.json'):
        old_lessons = read_json(GUIDE + filename, baseline)
        current = read_json(GUIDE + filename)
        assert len(old_lessons) == len(current)
        for old, new in zip(old_lessons, current):
            old = dict(old); new = dict(new)
            old_items = old.pop('components'); new_items = new.pop('components')
            if old['id'] == 'metal_press':
                # 2026-10-30: the search summary relates the two belts to each
                # other, not to the downward piston. No storyboard unit changed.
                before = old['summary']['ja']
                corrected = before.replace('ピストンと同じ向きのコンベア',
                                           'ピストンと、搬送方向をそろえたコンベア')
                assert corrected != before and new['summary']['ja'] == corrected
                old['summary'] = {**old['summary'], 'ja': corrected}
            assert old == new, f'Unrelated storyboard changed: {old["id"]}'
            assert set(old_items) <= set(new_items), f'Existing W item removed: {old["id"]}'
    old_descriptions = {entry['id']: entry for entry in read_json(GUIDE + 'item_descriptions.json', baseline)}
    new_descriptions = read_json(GUIDE + 'item_descriptions.json')
    for entry in new_descriptions:
        if entry['id'] in old_descriptions:
            old = old_descriptions[entry['id']]
            assert old['text'] == entry['text'], f'Old description prose changed: {entry["id"]}'
            assert set(old['items']) <= set(entry['items'])
    expected_new_keys = {'ieponder.description.' + entry['id'] for entry in new_descriptions
                         if entry['id'] not in old_descriptions}
    assert expected_new_keys == set(fixture['newDescriptionKeys'])

    record_by_id = {item['id']: item for item in inventory['expected_items']}
    family_report = []
    for family in fixture['variantFamilies']:
        ids = family['expected_ids']
        family_report.append({'id': family['id'], 'expectedIds': ids, 'expectedCount': len(ids),
                              'beforeUncoveredIds': [item for item in ids if item not in before_ponder and item not in before_shift],
                              'afterUncoveredIds': [item for item in ids if item not in owned]})
    manual_missing_before = sorted(set(inventory['manual_expected_ids']) - set(before_ponder) - set(before_shift))
    result = {
        'schema': 1, 'staticPassed': True, 'runtimeProof': None,
        'scope': 'Independent fixed-JAR manual and variant audit; live registry/UI/font proof is separate',
        'date': '2026-10-23', 'baselineCommit': baseline,
        'source': {'jar': jar.name, 'sha256': inventory['authority']['sha256'],
                   'minecraft': '1.21.1', 'loader': 'NeoForge', 'ieVersion': '12.4.2-194'},
        'sourceCounts': inventory['counts'],
        'before': {'ponderIds': len(before_ponder), 'shiftIds': len(before_shift),
                   'combinedIds': len(set(before_ponder) | set(before_shift)), 'manualUncoveredIds': manual_missing_before},
        'after': {'ponderIds': len(ponder), 'shiftIds': len(shift), 'combinedIds': len(owned),
                  'manualUncoveredIds': [], 'independentExpectedIds': len(all_expected), 'independentUncoveredIds': []},
        'manualItems': [{**record_by_id[item], 'before': entrance(item, before_ponder, before_shift),
                         'after': entrance(item, ponder, shift)} for item in inventory['manual_expected_ids']],
        'variantFamilies': family_report,
        'componentStackCases': fixture['componentStackCases'],
        'registryExclusions': fixture['registryExclusions'],
        'additionalExpectedShift': {item: {**expected,
                                         'before': entrance(item, before_ponder, before_shift),
                                         'after': entrance(item, ponder, shift)}
                                    for item, expected in fixture.get('expectedShift', {}).items()},
        'sourceStaleReferences': inventory['unresolved_named_references'],
        'nonIeManualMaterials': sorted({record['stack']['id'] for record in inventory['non_ie_associations']}),
        'limitations': ['JAR item models are a static proxy; runtime registry inventory is checked separately',
                        'Full assembly tutorials remain the existing20 lessons; other formed-machine items receive role/condition Shift introductions',
                        'Arbitrary user-created component combinations are not exhaustively enumerable',
                        'Unchanged22-locale captions retain the0.1.4 font proof; new description units need fresh font validation'],
    }
    assert (runtime_path is None) == (release_jar is None), 'Supply both --runtime-result and --release-jar'
    if runtime_path is not None:
        result['runtimeProof'] = bind_runtime(runtime_path, release_jar, fixture_path, ponder, shift)
    if report_path:
        report_path.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', 'utf-8')
    print(f'PASS: {len(inventory["manual_expected_ids"])} independent manual IDs, '
          f'{len(family_report)} variant families; {len(manual_missing_before)} baseline manual gaps ->0')
    print(f'STATIC: W {len(before_ponder)}->{len(ponder)}, Shift {len(before_shift)}->{len(shift)}; '
          f'{len(fixture["newDescriptionKeys"])} new prose keys; existing storyboard text/geometry retained')
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ie-jar', required=True, type=Path)
    parser.add_argument('--expectations', type=Path, default=ROOT / 'docs/item-coverage-expectations.json')
    parser.add_argument('--baseline', default='bfee6be')
    parser.add_argument('--report', type=Path)
    parser.add_argument('--runtime-result', type=Path)
    parser.add_argument('--release-jar', type=Path)
    args = parser.parse_args()
    check(args.ie_jar, args.expectations, args.baseline, args.report, args.runtime_result, args.release_jar)
