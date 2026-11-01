"""Small regressions for independent manual extraction and stale-proof rejection."""
import json
from pathlib import Path
import tempfile
import unittest

from check_item_coverage import ROOT, bind_runtime, digest
from extract_manual_inventory import Extractor, recipe_outputs


class ManualInventoryTests(unittest.TestCase):
    def test_nested_container_wrappers_keep_output_components(self):
        output = {'id': 'immersiveengineering:blueprint', 'count': 2,
                  'components': {'immersiveengineering:blueprint': 'molds'}}
        recipe = {'type': 'immersiveengineering:no_container_item', 'baseRecipe': {
            'type': 'immersiveengineering:no_container_item', 'baseRecipe': {
                'type': 'minecraft:crafting_shapeless', 'result': output}}}
        self.assertEqual(recipe_outputs(recipe), [output])

    def test_fixed_layout_and_native_outputs_keep_distinct_provenance(self):
        resource = 'data/immersiveengineering/recipe/covered.json'
        cover = {'id': 'immersiveengineering:conveyor_basic',
                 'components': {'immersiveengineering:default_cover': 'minecraft:glass'}}
        extractor = Extractor({resource: {'result': cover}})
        extractor.walk('transport/conveyors', {'specials': [
            {'type': 'crafting', 'recipe': 'covered'},
            {'type': 'crafting', 'recipes': [{'x': 1, 'y': 2, 'id': cover}]},
            {'type': 'item_display', 'items': ['immersiveengineering:conveyor_basic']}]})
        self.assertEqual([row['kind'] for row in extractor.records],
                         ['recipe_output', 'fixed_recipe_display', 'item_display'])
        self.assertEqual([row['native_recipe_link'] for row in extractor.records], [True, False, False])
        self.assertEqual(extractor.records[0]['stack'], cover)
        self.assertEqual(extractor.records[1]['stack'], cover)

    def test_unresolved_recipe_is_reported_without_inventing_item(self):
        extractor = Extractor({})
        extractor.walk('construction', {'type': 'crafting', 'recipe': 'missing'})
        self.assertEqual(extractor.records, [])
        self.assertEqual(extractor.unresolved[0]['reference'], 'missing')


class RuntimeBindingTests(unittest.TestCase):
    def test_failed_result_is_not_bindable(self):
        with tempfile.TemporaryDirectory(dir=ROOT / 'build') as temporary:
            root = Path(temporary)
            fixture = root / 'fixture.json'; fixture.write_text('{}', 'utf-8')
            proof = root / 'proof.json'; proof.write_text('{"passed": false}', 'utf-8')
            with self.assertRaisesRegex(AssertionError, 'successful focused'):
                bind_runtime(proof, root / 'release.jar', fixture, {}, {})

    def test_changed_fixture_and_changed_jar_reject_previously_successful_result(self):
        with tempfile.TemporaryDirectory(dir=ROOT / 'build') as temporary:
            root = Path(temporary)
            fixture = root / 'fixture.json'; fixture.write_text('{}', 'utf-8')
            jar = root / 'release.jar'; jar.write_bytes(b'original release')
            proof = root / 'proof.json'
            values = {'passed': True, 'scope': 'ie-item-coverage-and-component-ui',
                      'expectationsSha256': digest(fixture), 'releaseJarSha256': digest(jar)}
            proof.write_text(json.dumps(values), 'utf-8')
            fixture.write_text('{"changed": true}', 'utf-8')
            with self.assertRaisesRegex(AssertionError, 'different expectation'):
                bind_runtime(proof, jar, fixture, {}, {})
            fixture.write_text('{}', 'utf-8'); jar.write_bytes(b'new release')
            with self.assertRaisesRegex(AssertionError, 'JAR hash differs'):
                bind_runtime(proof, jar, fixture, {}, {})


if __name__ == '__main__':
    unittest.main()
