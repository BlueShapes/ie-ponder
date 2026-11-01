"""Install a checked main guide JAR; remove only old JARs declaring the same mod ID."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parents[1]
INSTANCE = Path(os.environ.get('APPDATA', Path.home() / 'AppData' / 'Roaming')) / 'PrismLauncher' / 'instances' / '1.21.1 mod creation test'


def metadata(path):
    with zipfile.ZipFile(path) as jar:
        return tomllib.loads(jar.read('META-INF/neoforge.mods.toml').decode('utf-8-sig'))


def own_mod(path):
    try:
        return {mod['modId'] for mod in metadata(path).get('mods', [])} == {'ieponder'}
    except (OSError, KeyError, ValueError, zipfile.BadZipFile):
        return False


def mod_ids(path):
    return {mod['modId'] for mod in metadata(path).get('mods', [])}


def dependency_ranges(path):
    data = metadata(path)
    owner_ids = mod_ids(path)
    if len(owner_ids) != 1:
        return None
    owner = next(iter(owner_ids))
    dependencies = data.get('dependencies', {}).get(owner)
    if not isinstance(dependencies, list):
        return None
    ranges = {}
    for dependency in dependencies:
        if dependency.get('modId') in ('minecraft', 'neoforge'):
            ranges[dependency['modId']] = dependency.get('versionRange')
    if not all(isinstance(ranges.get(mod_id), str) for mod_id in ('minecraft', 'neoforge')):
        return None
    return ranges


def version_parts(version):
    if not isinstance(version, str) or not re.fullmatch(r'\d+(?:\.\d+)*', version):
        return None
    return tuple(int(part) for part in version.split('.'))


def compare_versions(left, right):
    left_parts, right_parts = version_parts(left), version_parts(right)
    if left_parts is None or right_parts is None:
        return None
    width = max(len(left_parts), len(right_parts))
    left_parts += (0,) * (width - len(left_parts))
    right_parts += (0,) * (width - len(right_parts))
    return (left_parts > right_parts) - (left_parts < right_parts)


def range_contains(version_range, version):
    """Return whether a simple NeoForge interval contains a version; None means unsupported."""
    if not isinstance(version_range, str):
        return None
    spec = version_range.strip()
    if len(spec) < 2 or spec[0] not in '[(' or spec[-1] not in '])' or spec.count(',') != 1:
        # Exact-version dependency declarations are unambiguous too.
        comparison = compare_versions(spec, version)
        return comparison == 0 if comparison is not None else None
    lower, upper = (part.strip() for part in spec[1:-1].split(',', 1))
    if not lower and not upper:
        return None
    if lower:
        comparison = compare_versions(version, lower)
        if comparison is None:
            return None
        if comparison < 0 or (comparison == 0 and spec[0] == '('):
            return False
    if upper:
        comparison = compare_versions(version, upper)
        if comparison is None:
            return None
        if comparison > 0 or (comparison == 0 and spec[-1] == ')'):
            return False
    return True


def obsolete_archive_status(path, target_versions):
    """Classify direct children of mods/ without using their names as ownership proof."""
    name_hint = 'ieponder' in path.name.lower()
    if path.is_symlink() or not path.is_file() or not zipfile.is_zipfile(path):
        return 'ambiguous-non-archive' if name_hint else None
    try:
        ids = mod_ids(path)
    except (OSError, KeyError, ValueError, zipfile.BadZipFile, tomllib.TOMLDecodeError):
        return 'ambiguous-metadata' if name_hint else None
    if ids != {'ieponder'}:
        return 'ambiguous-mod-metadata' if 'ieponder' in ids else None
    try:
        ranges = dependency_ranges(path)
    except (OSError, KeyError, ValueError, zipfile.BadZipFile, tomllib.TOMLDecodeError):
        return 'ambiguous-environment-metadata'
    if ranges is None:
        return 'ambiguous-environment-metadata'
    for mod_id in ('minecraft', 'neoforge'):
        matches = range_contains(ranges[mod_id], target_versions.get(
            'net.minecraft' if mod_id == 'minecraft' else 'net.neoforged'))
        if matches is None:
            return 'ambiguous-environment-range'
        if not matches:
            return 'other-environment'
    return 'obsolete-own-guide'


def obsolete_archives(target, target_versions):
    target = target.resolve(strict=True)
    removable, findings = [], []
    for path in target.iterdir():
        if not path.is_file() and not path.is_symlink():
            continue
        status = obsolete_archive_status(path, target_versions)
        if status is None:
            continue
        finding = {'name': path.name, 'status': status}
        findings.append(finding)
        if status == 'obsolete-own-guide':
            resolved = path.resolve(strict=True)
            if path.is_symlink() or resolved.parent != target:
                finding['status'] = 'ambiguous-path'
                continue
            removable.append(path)
    return removable, findings


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def install(source, ie_dependency=None):
    source = source.resolve(strict=True)
    assert source.parent == (ROOT / 'build/libs').resolve(), 'Use this project main distribution directory'
    assert source.suffix == '.jar' and own_mod(source), 'Not a main IE Ponder JAR'
    assert not any(part in source.name for part in ('sources', 'javadoc', 'validation', 'dev-shadow')), source
    components = json.loads((INSTANCE / 'mmc-pack.json').read_text('utf-8'))['components']
    versions = {component['uid']: component.get('version') for component in components}
    assert versions.get('net.minecraft') == '1.21.1', versions
    assert versions.get('net.neoforged', '').startswith('21.1.'), versions
    assert int(versions['net.neoforged'].split('.')[2]) >= 256, 'This guide requires NeoForge21.1.256 or newer'
    with zipfile.ZipFile(source) as jar:
        assert not any('/validation/' in name or name.endswith('.jar') for name in jar.namelist())
    target = (INSTANCE / 'minecraft/mods').resolve(strict=True)
    assert target == INSTANCE.resolve() / 'minecraft/mods', 'Unexpected Prism destination'
    source_ranges = dependency_ranges(source)
    assert source_ranges is not None, 'The source JAR needs explicit Minecraft and NeoForge dependency ranges'
    assert all(range_contains(source_ranges[mod_id], versions[uid]) is True
               for mod_id, uid in (('minecraft', 'net.minecraft'), ('neoforge', 'net.neoforged'))), \
        'The source JAR does not declare compatibility with this Prism environment'
    dependencies_added = []
    has_ie = False
    for path in target.glob('*.jar'):
        try:
            has_ie |= any(mod.get('modId') == 'immersiveengineering' for mod in metadata(path).get('mods', []))
        except (OSError, KeyError, ValueError, zipfile.BadZipFile):
            pass
    if not has_ie:
        assert ie_dependency is not None, 'The destination needs Immersive Engineering; provide its checked dependency JAR'
        dependency = ie_dependency.resolve(strict=True)
        dep_meta = metadata(dependency)
        assert [(mod['modId'], mod['version']) for mod in dep_meta['mods']] == [('immersiveengineering', '12.4.2-194')]
        assert digest(dependency) == '45942985a4a4aebf265b8e22a0c54a96208637471f36f2532ff5d4911322debc'
        dep_target = target / dependency.name
        assert not dep_target.exists(), 'Refusing to overwrite an existing dependency'
        dep_staged = dep_target.with_suffix('.ieponder-dependency-new.tmp')
        assert not dep_staged.exists()
        try:
            shutil.copy2(dependency, dep_staged)
            assert digest(dep_staged) == digest(dependency)
            os.replace(dep_staged, dep_target)
            assert digest(dep_target) == digest(dependency)
        finally:
            if dep_staged.exists():
                dep_staged.unlink()
        dependencies_added.append(dep_target.name)
    old, cleanup_findings = obsolete_archives(target, versions)
    protected = {str(path.relative_to(target)): digest(path)
                 for path in target.rglob('*') if path.is_file() and path not in old
                 and path != target / source.name}
    destination = target / source.name
    if destination.exists():
        assert not destination.is_symlink() and obsolete_archive_status(
            destination, versions) == 'obsolete-own-guide', \
            'Refusing to replace an unrelated or different-environment artifact'
    staged = destination.with_suffix('.ieponder-new.tmp')
    assert not staged.exists(), f'Unexpected staged file: {staged}'
    try:
        shutil.copy2(source, staged)
        assert digest(staged) == digest(source), 'Staged copy hash mismatch'
        os.replace(staged, destination)
        assert digest(destination) == digest(source), 'Installed copy hash mismatch'
    finally:
        if staged.exists():
            staged.unlink()
    removed = []
    for path in old:
        if path != destination:
            resolved = path.resolve(strict=True)
            assert resolved.parent == target and not path.is_symlink()
            assert obsolete_archive_status(path, versions) == 'obsolete-own-guide'
            path.unlink()
            removed.append(path.name)
    record = {'source': str(source), 'installed': str(destination), 'sha256': digest(source),
              'targetVersions': versions, 'removedOldGuideJars': removed,
              'removedObsoleteGuideArchives': removed, 'obsoleteArchiveFindings': cleanup_findings,
              'addedMissingDependencies': dependencies_added}
    after = {str(path.relative_to(target)): digest(path)
             for path in target.rglob('*') if path.is_file() and path != destination}
    changed = {name: {'before': value, 'after': after.get(name)}
               for name, value in protected.items() if after.get(name) != value}
    record.update(protectedFilesBefore=protected, otherFilesChanged=changed,
                  protectedFileCount=len(protected),
                  protectedIndexCount=sum(Path(name).parts[0] == '.index' for name in protected),
                  concurrentAdditions=sorted(after.keys() - protected.keys()))
    (ROOT / 'build/prism-deployment.json').write_text(json.dumps(record, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({key: value for key, value in record.items() if key != 'protectedFilesBefore'}, indent=2))
    assert not changed, f'Other files changed during deployment; see prism-deployment.json: {sorted(changed)}'


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('jar', type=Path)
    parser.add_argument('--ie-dependency', type=Path)
    args = parser.parse_args()
    install(args.jar, args.ie_dependency)
