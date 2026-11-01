"""Cheap filesystem-only tests for Prism guide deployment cleanup."""
import json
import io
from pathlib import Path
import tempfile
import unittest
from contextlib import redirect_stdout
from unittest.mock import patch
import zipfile

import install_prism


def archive(path, mod_id='ieponder', minecraft='[1.21.1,1.21.2)', neoforge='[21.1.256,)'):
    path.parent.mkdir(parents=True, exist_ok=True)
    toml = f'''modLoader="javafml"
loaderVersion="[1,)"
license="All rights reserved"

[[mods]]
modId="{mod_id}"
version="1.0.0"
displayName="Synthetic test artifact"

[[dependencies.{mod_id}]]
modId="neoforge"
mandatory=true
versionRange="{neoforge}"
ordering="NONE"
side="BOTH"

[[dependencies.{mod_id}]]
modId="minecraft"
mandatory=true
versionRange="{minecraft}"
ordering="NONE"
side="BOTH"
'''
    with zipfile.ZipFile(path, 'w') as jar:
        jar.writestr('META-INF/neoforge.mods.toml', toml)
        if mod_id == 'ieponder':
            jar.writestr('assets/ieponder/guide/lessons.json', '[]')


class InstallPrismCleanupTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(dir=Path(__file__).resolve().parents[1])
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / 'project'
        self.instance = Path(self.temp.name) / 'Prism instance'
        self.source_dir = self.root / 'build' / 'libs'
        self.source = self.source_dir / 'guide-1.21.1.jar'
        self.target = self.instance / 'minecraft' / 'mods'
        self.source_dir.mkdir(parents=True)
        self.target.mkdir(parents=True)
        (self.instance / 'mmc-pack.json').write_text(json.dumps({'components': [
            {'uid': 'net.minecraft', 'version': '1.21.1'},
            {'uid': 'net.neoforged', 'version': '21.1.256'}]}), encoding='utf-8')
        archive(self.source)
        archive(self.target / 'immersiveengineering.jar', 'immersiveengineering')
        self.root.mkdir(exist_ok=True)
        self.root.joinpath('build').mkdir(exist_ok=True)
        self.root.joinpath('build', 'libs').mkdir(exist_ok=True)
        self.patches = [
            patch.object(install_prism, 'ROOT', self.root),
            patch.object(install_prism, 'INSTANCE', self.instance),
        ]
        for scoped_patch in self.patches:
            scoped_patch.start()
            self.addCleanup(scoped_patch.stop)

    def test_removes_renamed_disabled_copy_after_verified_install(self):
        old = self.target / 'guide-previous.jar.disabled'
        archive(old)

        with redirect_stdout(io.StringIO()):
            install_prism.install(self.source)

        self.assertFalse(old.exists())
        self.assertTrue((self.target / self.source.name).is_file())
        result = json.loads((self.root / 'build' / 'prism-deployment.json').read_text('utf-8'))
        self.assertEqual(result['removedObsoleteGuideArchives'], [old.name])

    def test_preserves_other_mods_and_ambiguous_non_zip_files(self):
        other = self.target / 'othermod.jar.old'
        ambiguous = self.target / 'ieponder-copy.jar.bak'
        archive(other, 'othermod')
        ambiguous.write_text('not a zip archive', encoding='utf-8')

        with redirect_stdout(io.StringIO()):
            install_prism.install(self.source)

        self.assertTrue(other.exists())
        self.assertTrue(ambiguous.exists())
        result = json.loads((self.root / 'build' / 'prism-deployment.json').read_text('utf-8'))
        findings = {entry['name']: entry['status'] for entry in result['obsoleteArchiveFindings']}
        self.assertEqual(findings[ambiguous.name], 'ambiguous-non-archive')
        self.assertNotIn(other.name, findings)

    def test_removes_compatible_older_minimum_loader_copy_after_verified_install(self):
        old = self.target / 'guide-previous.jar.old'
        archive(old, neoforge='[21.1.206,)')

        with redirect_stdout(io.StringIO()):
            install_prism.install(self.source)

        self.assertFalse(old.exists())
        self.assertEqual(install_prism.digest(self.target / self.source.name),
                         install_prism.digest(self.source))
        result = json.loads((self.root / 'build' / 'prism-deployment.json').read_text('utf-8'))
        self.assertEqual(result['removedObsoleteGuideArchives'], [old.name])

    def test_preserves_own_archives_for_other_minecraft_or_loader(self):
        other_mc = self.target / 'ieponder-mc-other.jar.disabled'
        other_loader = self.target / 'ieponder-loader-other.jar.old'
        archive(other_mc, minecraft='[1.20.1,1.20.2)')
        archive(other_loader, neoforge='[20.4.0,20.5.0)')

        with redirect_stdout(io.StringIO()):
            install_prism.install(self.source)

        self.assertTrue(other_mc.exists())
        self.assertTrue(other_loader.exists())
        result = json.loads((self.root / 'build' / 'prism-deployment.json').read_text('utf-8'))
        findings = {entry['name']: entry['status'] for entry in result['obsoleteArchiveFindings']}
        self.assertEqual(findings[other_mc.name], 'other-environment')
        self.assertEqual(findings[other_loader.name], 'other-environment')

    def test_copy_failure_does_not_remove_old_archive(self):
        old = self.target / 'old-guide.jar.disabled'
        archive(old)

        with patch.object(install_prism.shutil, 'copy2', side_effect=OSError('synthetic copy failure')):
            with self.assertRaisesRegex(OSError, 'synthetic copy failure'):
                install_prism.install(self.source)

        self.assertTrue(old.exists())

    def test_hash_failure_does_not_remove_old_archive(self):
        old = self.target / 'old-guide.jar.disabled'
        archive(old)
        real_digest = install_prism.digest

        def wrong_staged_hash(path):
            if path.name.endswith('.ieponder-new.tmp'):
                return '0' * 64
            return real_digest(path)

        with patch.object(install_prism, 'digest', side_effect=wrong_staged_hash):
            with self.assertRaisesRegex(AssertionError, 'Staged copy hash mismatch'):
                install_prism.install(self.source)

        self.assertTrue(old.exists())


if __name__ == '__main__':
    unittest.main()
