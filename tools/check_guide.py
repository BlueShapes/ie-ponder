"""Audit lesson references against the exact installed IE JAR before launching Minecraft.

Usage: python tools/check_guide.py --ie-jar path/to/ImmersiveEngineering.jar
"""
import argparse
import json
import zipfile
from pathlib import Path

from nbt import read

ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / 'src/main/resources/assets/ieponder/guide'


def check(ie_jar):
    lessons = sum((json.loads((GUIDE / name).read_text('utf-8'))
                   for name in ('lessons.json', 'processing.json')), [])
    ids = set()
    total_nodes = total_steps = total_callouts = 0
    with zipfile.ZipFile(ie_jar) as jar:
        resources = set(jar.namelist())

        def item(resource):
            namespace, name = resource.split(':', 1)
            assert namespace in ('minecraft', 'immersiveengineering'), resource
            if namespace == 'immersiveengineering':
                assert f'assets/{namespace}/models/item/{name}.json' in resources, f'Unknown item {resource}'

        for lesson in lessons:
            label = lesson['id']
            assert label not in ids, f'Duplicate lesson {label}'
            ids.add(label)
            item(lesson['icon'])
            for component in lesson['components']:
                item(component)
            nodes = {node['id']: node for node in lesson.get('nodes', [])}
            assert len(nodes) == len(lesson.get('nodes', [])), f'Duplicate node: {label}'
            groups = {'@all': []}
            if structure := lesson.get('structure'):
                template = read(jar.read('data/immersiveengineering/structure/multiblocks/'
                                         + structure['template'] + '.nbt'))
                for block in template['blocks']:
                    state = template['palette'][block['state']]
                    if state['Name'] in ('minecraft:air', 'minecraft:cave_air', 'minecraft:void_air'):
                        continue
                    x, y, z = block['pos']
                    name = f'mb_{x}_{y}_{z}'
                    assert name not in nodes, f'Overlapping ID {label}/{name}'
                    nodes[name] = dict(id=name, block=state['Name'],
                                       pos=[a+b for a,b in zip(block['pos'], structure['origin'])])
                    groups['@all'].append(name)
                    groups.setdefault(f'@layer{y}', []).append(name)

            def refs(values):
                expanded = []
                for value in values:
                    if value.startswith('@'):
                        assert value in groups and groups[value], f'Unknown group: {label}/{value}'
                        expanded.extend(groups[value])
                    else:
                        assert value in nodes, f'Unknown node: {label}/{value}'
                        expanded.append(value)
                return expanded

            coordinates = set()
            for node in nodes.values():
                x, y, z = node['pos']
                assert 0 <= x <= 8 and 1 <= y <= 6 and 0 <= z <= 8, f'Out of stage: {label}/{node}'
                assert tuple(node['pos']) not in coordinates, f'Overlapping node: {label}/{node}'
                coordinates.add(tuple(node['pos']))
                namespace, name = node['block'].split(':', 1)
                if namespace == 'immersiveengineering':
                    assert f'assets/{namespace}/blockstates/{name}.json' in resources, f'Unknown block: {node}'
            visible, revealed = set(), set()
            for index, step in enumerate(lesson['steps']):
                show = refs(step.get('show', []))
                hide = refs(step.get('hide', []))
                assert not revealed.intersection(show), f'Repeated reveal: {label}/{index}'
                visible.difference_update(hide)
                visible.update(show)
                revealed.update(show)
                assert set(refs(step.get('focus', []))) <= visible, f'Hidden focus: {label}/{index}'
                assert set(refs(step.get('nbt', {}))) <= visible, f'Hidden NBT change: {label}/{index}'
                for bubble in step.get('callouts', []):
                    assert bubble['anchor'] in visible, f'Hidden callout: {label}/{index}'
                    item(bubble['item'])
                    total_callouts += 1
                if flow := step.get('flow'):
                    assert flow['from'] in visible and flow['to'] in visible, f'Hidden flow: {label}/{index}'
                    item(flow['item'])
                for locale in ('ja', 'en'):
                    assert step['text'][locale].strip(), f'Empty caption: {label}/{index}/{locale}'
            assert revealed == nodes.keys(), f'Unrevealed blocks: {label}/{nodes.keys()-revealed}'
            total_nodes += len(nodes)
            total_steps += len(lesson['steps'])
        for description in json.loads((GUIDE / 'item_descriptions.json').read_text('utf-8')):
            for resource in description['items']:
                item(resource)
    stage = read((ROOT / 'src/main/resources/assets/ieponder/ponder/stage.nbt').read_bytes())
    assert stage['size'] == [9, 7, 9]
    assert len(stage['blocks']) == 567, 'Missing explicit air bounds'
    print(f'PASS: {len(lessons)} lessons, {total_nodes} nodes, {total_steps} steps, {total_callouts} callouts; exact IE items and templates verified')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ie-jar', type=Path, required=True)
    check(parser.parse_args().ie_jar)
