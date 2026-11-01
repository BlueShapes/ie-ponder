"""Remove ordinary sentence stops without rewriting authored text or translations."""
import argparse
import base64
import copy
import hashlib
import json
import shutil
import subprocess
from pathlib import Path

from check_translations import ROOT


LOCALES = tuple(json.loads((ROOT / "src/main/translations/languages.json").read_text("utf-8")))
CONTENT_DIR = ROOT / "src/main/translations/content"
TERMINAL_MARKS = ".．。｡।॥۔"
CLOSERS = "\"'”’»」』）)]"
ABBREVIATIONS = {
    "e.g.", "i.e.", "dr.", "mr.", "mrs.", "ms.", "prof.", "sr.", "jr.",
    "vs.", "etc.", "approx.", "fig.", "no.", "z.b.", "d.h.", "m.", "mme.",
    "np.", "itp.", "itd.", "m.in.", "ca.", "bzw.", "u.a.", "usw.", "vgl.",
    "ggf.", "p.ex.", "sra.", "dra.", "env.", "c.-à-d.", "örn.", "vb.",
    "např.", "atd.", "tj.", "tzn.", "ex.", "p.ej.", "aprox.", "mis.",
    "dll.", "dsb.", "cth.", "vd.", "v.v.", "डॉ.",
}
CAPTION_PREFIX = "ieponder.ponder.shared."
OBSOLETE_GUIDE_KEY = "ieponder.button.guide"
OLD_WORLD_HINT_KEY = "ieponder.button.requires_world"
WORLD_HINT_KEY = "ieponder.command.requires_world"


def protected_terminal_dot(text: str, index: int) -> bool:
    """Keep final abbreviation dots and punctuation that belongs to a URL or ID."""
    if index > 0 and text[index - 1] in ".．" or index + 1 < len(text) and text[index + 1] in ".．":
        return True
    if index > 0 and text[index - 1].isalnum() and index + 1 < len(text) and text[index + 1].isalnum():
        return True
    prefix = text[: index + 1].casefold()
    for abbreviation in ABBREVIATIONS:
        start = len(prefix) - len(abbreviation)
        if start >= 0 and prefix.endswith(abbreviation) and (
            start == 0 or prefix[start - 1].isspace() or prefix[start - 1] in '("\'[{«「『'
        ):
            return True
    # A terminal DNS/domain suffix or namespaced identifier is content when the
    # text consists solely of that token; a trailing prose stop after it is not.
    token = text[:index].rsplit(None, 1)[-1].casefold()
    return bool(token.startswith(("http://", "https://", "www.")) or token.endswith("://"))


def strip_sentence_stop(unit: str) -> str:
    if unit.endswith(("…", "...", "⋯")):
        return unit
    end = len(unit) - 1
    while end >= 0 and unit[end] in CLOSERS:
        end -= 1
    if end < 0 or unit[end] not in TERMINAL_MARKS:
        return unit
    if unit[end] in ".．" and protected_terminal_dot(unit, end):
        return unit
    return unit[:end] + unit[end + 1 :]


def java_units(texts: list[str], java_home: Path | None) -> list[str]:
    if not texts:
        return []
    javac = java_home / "bin/javac.exe" if java_home else shutil.which("javac")
    java = java_home / "bin/java.exe" if java_home else shutil.which("java")
    if not javac or not java:
        raise RuntimeError("A JDK is required; pass --java-home")
    output = ROOT / "build/caption-tests"
    output.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        [str(javac), "-encoding", "UTF-8", "-d", str(output),
         str(ROOT / "src/main/java/dev/ieponder/client/CaptionUnits.java"),
         str(ROOT / "src/captionTest/java/dev/ieponder/client/CaptionUnitsTest.java")],
        check=True,
    )
    payload = "\n".join(base64.b64encode(text.encode("utf-8")).decode("ascii") for text in texts)
    result = subprocess.run(
        [str(java), "-Dfile.encoding=UTF-8", "-cp", str(output),
         "dev.ieponder.client.CaptionUnitsTest", "--segment"],
        input=payload, encoding="ascii", stdout=subprocess.PIPE, check=True,
    )
    segments = [base64.b64decode(row).decode("utf-8") for row in result.stdout.splitlines()]
    if len(segments) != len(texts):
        raise AssertionError(f"CaptionUnits returned {len(segments)} units for {len(texts)} inputs")
    return segments


def load_from_git(path: Path, base: str) -> tuple[bytes, dict]:
    relative = path.relative_to(ROOT).as_posix()
    raw = subprocess.check_output(["git", "show", f"{base}:{relative}"], cwd=ROOT)
    return raw, json.loads(raw)


def json_bytes(value: object, line_ending: str) -> bytes:
    rendered = json.dumps(value, ensure_ascii=False, indent=2) + "\n"
    if line_ending == "\r\n":
        rendered = rendered.replace("\n", "\r\n")
    return rendered.encode("utf-8")


def source_fingerprint(lessons: list[dict], descriptions: list[dict]) -> str:
    originals: dict[str, dict[str, str]] = {}
    for lesson in lessons:
        originals[f"ieponder.ponder.{lesson['id']}.header"] = lesson["title"]
        originals[f"ieponder.lesson.{lesson['id']}.summary"] = lesson["summary"]
        for number, step in enumerate(lesson["steps"]):
            originals[f"{CAPTION_PREFIX}{lesson['id']}_{number}"] = step["text"]
    for entry in descriptions:
        originals[f"ieponder.description.{entry['id']}"] = entry["text"]
    chunks = []
    for language in ("en", "ja"):
        chunks.extend(
            f"{language}\0{key}\0{text[language]}\0"
            for key, text in sorted(originals.items())
        )
    return hashlib.sha256("".join(chunks).encode("utf-8")).hexdigest()


def navigation_fingerprint(english: dict, japanese: dict) -> str:
    chunks = []
    for locale, values in (("en_us", english), ("ja_jp", japanese)):
        chunks.extend(f"{locale}\0{key}\0{values[key]}\0" for key in sorted(values))
    return hashlib.sha256("".join(chunks).encode("utf-8")).hexdigest()


def update_localized_text(text: str, split_sentences: bool, segment: dict[str, list[str]]) -> tuple[str, int]:
    if split_sentences:
        units = segment[text]
    else:
        # A subtitle's authored newlines already define its exact unit boundaries.
        units = text.split("\n")
    clean = [strip_sentence_stop(unit) for unit in units]
    return "\n".join(clean), len(clean)


def transform_mode(path: Path, location: tuple) -> bool | None:
    """Return the authorized boundary mode for a prose value, else None."""
    name = path.name
    if name in ("lessons.json", "processing.json") and "/guide/" in path.as_posix():
        if len(location) == 3 and location[1] in ("title", "summary") and location[2] in ("en", "ja"):
            return location[1] == "summary"
        if len(location) == 5 and location[1] == "steps" and location[3] == "text" and location[4] in ("en", "ja"):
            return False
    if name == "item_descriptions.json" and len(location) == 3 and location[1:] == ("text", location[2]):
        return True
    if path.parent.name == "content" and len(location) == 1:
        key = location[0]
        if key.startswith(CAPTION_PREFIX):
            return False
        if key.startswith("ieponder.description."):
            return True
        if key.startswith("ieponder.ponder.") and key.endswith(".header"):
            return False
        if (key.startswith("ieponder.lesson.") and key.endswith(".summary")) or (
            key.startswith("ieponder.ponder.tag.") and key.endswith(".description")
        ):
            return True
    if path.parent.name in ("lang", "navigation") and len(location) == 1:
        key = location[0]
        if key == WORLD_HINT_KEY:
            return False
        if key.startswith("ieponder.ponder.") and key.endswith(".header"):
            return False
        if key.startswith("ieponder.ponder.tag.") and key.endswith(".description"):
            return True
    return None


def prove_only_authored_punctuation(path: Path, original: object, migrated: object,
                                    segment: dict[str, list[str]]) -> int:
    """Compare JSON trees and prove every difference is an approved stop edit/key rename."""
    changed = 0

    def visit(before: object, after: object, location: tuple = ()) -> None:
        nonlocal changed
        if isinstance(before, dict) and isinstance(after, dict):
            removed = set(before) - set(after)
            added = set(after) - set(before)
            is_navigation = path.parent.name in ("lang", "navigation")
            rename_worldhint = is_navigation and OLD_WORLD_HINT_KEY in removed and WORLD_HINT_KEY in added
            if (OLD_WORLD_HINT_KEY in removed) != (WORLD_HINT_KEY in added):
                raise AssertionError(f"World-hint key rename must be paired at {path.relative_to(ROOT)}")
            if added - ({WORLD_HINT_KEY} if rename_worldhint else set()):
                raise AssertionError(f"Unexpected JSON keys added at {path.relative_to(ROOT)}: {sorted(added)}")
            allowed_removed = {OBSOLETE_GUIDE_KEY, OLD_WORLD_HINT_KEY} if is_navigation else set()
            if removed - allowed_removed:
                raise AssertionError(f"Unexpected JSON keys removed at {path.relative_to(ROOT)}: {sorted(removed)}")
            if rename_worldhint:
                source_text = before[OLD_WORLD_HINT_KEY]
                migrated_text = after[WORLD_HINT_KEY]
                expected_text, _ = update_localized_text(source_text, False, segment)
                if migrated_text != expected_text:
                    raise AssertionError(f"World-hint wording changed beyond terminal punctuation at {path.relative_to(ROOT)}")
                changed += migrated_text != source_text
            for key in before.keys() & after.keys():
                visit(before[key], after[key], (*location, key))
            return
        if isinstance(before, list) and isinstance(after, list):
            if len(before) != len(after):
                raise AssertionError(f"Array length changed at {path.relative_to(ROOT)}: {location}")
            for index, (left, right) in enumerate(zip(before, after, strict=True)):
                visit(left, right, (*location, index))
            return
        if before == after:
            return
        mode = transform_mode(path, location) if isinstance(before, str) and isinstance(after, str) else None
        if mode is None:
            raise AssertionError(f"Unexpected change at {path.relative_to(ROOT)}: {location}")
        expected, _ = update_localized_text(before, mode, segment)
        if after != expected:
            raise AssertionError(f"Change exceeds terminal punctuation/separator whitespace at {path.relative_to(ROOT)}: {location}")
        changed += 1

    visit(original, migrated)
    return changed


def rename_worldhint(values: dict, segment: dict[str, list[str]]) -> bool:
    """Rename the obsolete UI hint while preserving its authored wording."""
    if OLD_WORLD_HINT_KEY not in values:
        raise AssertionError(f"Missing expected baseline world-hint key: {OLD_WORLD_HINT_KEY}")
    if WORLD_HINT_KEY in values:
        raise AssertionError(f"World-hint command key already exists: {WORLD_HINT_KEY}")
    renamed = {}
    text_changed = False
    for key, value in values.items():
        if key == OBSOLETE_GUIDE_KEY:
            continue
        if key == OLD_WORLD_HINT_KEY:
            replacement, _ = update_localized_text(value, False, segment)
            renamed[WORLD_HINT_KEY] = replacement
            text_changed = replacement != value
        else:
            renamed[key] = value
    values.clear()
    values.update(renamed)
    return text_changed


def migrate(base: str, java_home: Path | None, apply: bool, verify: bool) -> None:
    canonical_paths = [
        ROOT / "src/main/resources/assets/ieponder/guide/lessons.json",
        ROOT / "src/main/resources/assets/ieponder/guide/processing.json",
        ROOT / "src/main/resources/assets/ieponder/guide/item_descriptions.json",
    ]
    localized_paths = [CONTENT_DIR / f"{locale}.json" for locale in LOCALES if locale not in ("en_us", "ja_jp")]
    navigation_paths = [ROOT / "src/main/templates/lang/en_us.json",
                        ROOT / "src/main/templates/lang/ja_jp.json"] + [
        ROOT / f"src/main/translations/navigation/{locale}.json"
        for locale in LOCALES if locale not in ("en_us", "ja_jp")
    ]
    paths = canonical_paths + localized_paths + navigation_paths
    originals: dict[Path, tuple[bytes, dict]] = {path: load_from_git(path, base) for path in paths}

    # Gather only prose whose complete sentences become authored display units.
    segment_texts: list[str] = []
    for path in canonical_paths[:2]:
        for lesson in originals[path][1]:
            segment_texts.extend(lesson["summary"].values())
    for entry in originals[canonical_paths[2]][1]:
        segment_texts.extend(entry["text"].values())
    for locale in LOCALES:
        if locale in ("en_us", "ja_jp"):
            continue
        content = originals[CONTENT_DIR / f"{locale}.json"][1]
        descriptions = {key: value for key, value in content.items()
                        if key.startswith("ieponder.description.")
                        or key.startswith("ieponder.lesson.") and key.endswith(".summary")
                        or key.startswith("ieponder.ponder.tag.") and key.endswith(".description")}
        segment_texts.extend(descriptions.values())
        navigation = originals[ROOT / f"src/main/translations/navigation/{locale}.json"][1]
        segment_texts.extend(value for key, value in navigation.items()
                             if key.startswith("ieponder.ponder.tag.") and key.endswith(".description"))
    for locale in ("en_us", "ja_jp"):
        template = originals[ROOT / f"src/main/templates/lang/{locale}.json"][1]
        descriptions = {key: value for key, value in template.items()
                        if key.startswith("ieponder.lesson.") and key.endswith(".summary")
                        or key.startswith("ieponder.ponder.tag.") and key.endswith(".description")}
        segment_texts.extend(descriptions.values())
    unique_texts = list(dict.fromkeys(segment_texts))
    split_results = java_units(unique_texts, java_home)
    segment = dict(zip(unique_texts, [value.split("\n") for value in split_results], strict=True))

    expected: dict[Path, dict] = {path: copy.deepcopy(data) for path, (_, data) in originals.items()}
    counts: dict[str, dict[str, int]] = {locale: {"captions": 0, "descriptions": 0, "summaries_and_tags": 0}
                                         for locale in LOCALES}
    description_counts: dict[str, dict[str, int]] = {locale: {} for locale in LOCALES}
    changed_strings = 0

    for path in canonical_paths[:2]:
        for lesson in expected[path]:
            for field in ("title", "summary"):
                old = lesson[field]
                for language in ("ja", "en"):
                    old_value = old[language]
                    value, _ = update_localized_text(old_value, field == "summary", segment)
                    lesson[field][language] = value
                    changed_strings += value != old_value
            for step in lesson["steps"]:
                for language in ("ja", "en"):
                    old = step["text"][language]
                    value, count = update_localized_text(old, False, segment)
                    step["text"][language] = value
                    counts["ja_jp" if language == "ja" else "en_us"]["captions"] += count
                    changed_strings += value != old

    descriptions_path = canonical_paths[2]
    for entry in expected[descriptions_path]:
        for language in ("ja", "en"):
            old = entry["text"][language]
            new, count = update_localized_text(old, True, segment)
            entry["text"][language] = new
            locale = "ja_jp" if language == "ja" else "en_us"
            counts[locale]["descriptions"] += count
            description_counts[locale][entry["id"]] = count
            changed_strings += new != old

    for locale, path in zip((locale for locale in LOCALES if locale not in ("en_us", "ja_jp")), localized_paths, strict=True):
        content = expected[path]
        for key, old in list(content.items()):
            if key.startswith(CAPTION_PREFIX):
                new, count = update_localized_text(old, False, segment)
                counts[locale]["captions"] += count
            elif key.startswith("ieponder.description."):
                new, count = update_localized_text(old, True, segment)
                counts[locale]["descriptions"] += count
                description_counts[locale][key.removeprefix("ieponder.description.")] = count
            elif key.startswith("ieponder.ponder.") and key.endswith(".header"):
                new, count = update_localized_text(old, False, segment)
                counts[locale]["summaries_and_tags"] += count
            elif (key.startswith("ieponder.lesson.") and key.endswith(".summary")) or (
                key.startswith("ieponder.ponder.tag.") and key.endswith(".description")
            ):
                new, count = update_localized_text(old, True, segment)
                counts[locale]["summaries_and_tags"] += count
            else:
                continue
            content[key] = new
            changed_strings += new != old

    for locale, path in zip(("en_us", "ja_jp"), navigation_paths[:2], strict=True):
        values = expected[path]
        changed_strings += rename_worldhint(values, segment)
        for key, old in list(values.items()):
            if (key.startswith("ieponder.ponder.tag.") and key.endswith(".description")) or (
                key.startswith("ieponder.ponder.") and key.endswith(".header")
            ):
                new, count = update_localized_text(old, key.endswith(".description"), segment)
                counts[locale]["summaries_and_tags"] += count
                values[key] = new
                changed_strings += new != old
    for locale, path in zip((locale for locale in LOCALES if locale not in ("en_us", "ja_jp")), navigation_paths[2:], strict=True):
        values = expected[path]
        changed_strings += rename_worldhint(values, segment)
        for key, old in list(values.items()):
            if (key.startswith("ieponder.ponder.tag.") and key.endswith(".description")) or (
                key.startswith("ieponder.ponder.") and key.endswith(".header")
            ):
                new, count = update_localized_text(old, key.endswith(".description"), segment)
                counts[locale]["summaries_and_tags"] += count
                values[key] = new
                changed_strings += new != old

    audited_values = sum(prove_only_authored_punctuation(path, original, expected[path], segment)
                         for path, (_, original) in originals.items())
    renamed_worldhint_keys = sum(
        OLD_WORLD_HINT_KEY in originals[path][1] and WORLD_HINT_KEY in expected[path]
        for path in navigation_paths
    )

    if apply:
        for path, (raw, _) in originals.items():
            current = path.read_bytes()
            line_ending = "\r\n" if b"\r\n" in raw else "\n"
            if current != raw and current != json_bytes(expected[path], line_ending):
                raise RuntimeError(f"Refusing to overwrite an independently changed file: {path.relative_to(ROOT)}")

    content_review_path = ROOT / "src/main/translations/content-review.json"
    navigation_review_path = ROOT / "src/main/translations/navigation-review.json"
    content_review_raw, content_review = load_from_git(content_review_path, base)
    navigation_review_raw, navigation_review = load_from_git(navigation_review_path, base)
    if verify:
        content_review = json.loads(content_review_path.read_bytes())
        navigation_review = json.loads(navigation_review_path.read_bytes())
    if apply and (content_review_path.read_bytes() != content_review_raw
                  or navigation_review_path.read_bytes() != navigation_review_raw):
        raise RuntimeError("Refusing to overwrite independently changed review data")

    if verify:
        for path in paths:
            if json.loads(path.read_bytes()) != expected[path]:
                raise AssertionError(f"Working file differs from exact {base} migration: {path.relative_to(ROOT)}")
        lessons = expected[canonical_paths[0]] + expected[canonical_paths[1]]
        if content_review.get("source_sha256") != source_fingerprint(lessons, expected[canonical_paths[2]]):
            raise AssertionError("Content source review hash is stale")
        if navigation_review.get("source_sha256") != navigation_fingerprint(expected[navigation_paths[0]], expected[navigation_paths[1]]):
            raise AssertionError("Navigation source review hash is stale")
        for locale in LOCALES:
            if locale in ("en_us", "ja_jp"):
                continue
            content_path = CONTENT_DIR / f"{locale}.json"
            nav_path = ROOT / f"src/main/translations/navigation/{locale}.json"
            if content_review.get("translation_sha256", {}).get(locale) != hashlib.sha256(content_path.read_bytes()).hexdigest():
                raise AssertionError(f"Content translation review hash is stale: {locale}")
        print(f"PASS: verified {len(paths)} source files against {base}; {changed_strings} prose values; {audited_values} changed strings proved punctuation-only; {renamed_worldhint_keys} authorized world-hint key renames; reviewed hashes match")
        for locale, values in counts.items():
            print(locale, json.dumps(values, ensure_ascii=False, sort_keys=True))
            print(locale + " description_units", json.dumps(description_counts[locale], ensure_ascii=False, sort_keys=True))
        return

    if not apply:
        print(f"PASS: baseline {base}; {len(paths)} source files; {changed_strings} prose strings changed; {audited_values} changed strings proved punctuation-only; {renamed_worldhint_keys} authorized world-hint key renames; dry run")
        for locale, values in counts.items():
            print(locale, json.dumps(values, ensure_ascii=False, sort_keys=True))
            print(locale + " description_units", json.dumps(description_counts[locale], ensure_ascii=False, sort_keys=True))
        return

    for path in canonical_paths + localized_paths + navigation_paths:
        raw, original = originals[path]
        line_ending = "\r\n" if b"\r\n" in raw else "\n"
        path.write_bytes(json_bytes(expected[path], line_ending))

    content_review_raw, content_review = load_from_git(content_review_path, base)
    navigation_review_raw, navigation_review = load_from_git(navigation_review_path, base)
    lessons = expected[canonical_paths[0]] + expected[canonical_paths[1]]
    content_review["source_sha256"] = source_fingerprint(lessons, expected[descriptions_path])
    content_review["translation_sha256"] = {
        locale: hashlib.sha256((CONTENT_DIR / f"{locale}.json").read_bytes()).hexdigest()
        for locale in LOCALES if locale not in ("en_us", "ja_jp")
    }
    content_review["reviewed_on"] = "2026-10-22"
    content_review["review_notes"].append(
        "0.1.3: Terminal full stops were removed mechanically from authored prose. Multi-sentence summaries and item/tag descriptions gained explicit locale-authored newline boundaries. Semantic text, numbers, IDs, punctuation inside sentences and all existing caption unit order were preserved."
    )
    en_templates, ja_templates = expected[navigation_paths[0]], expected[navigation_paths[1]]
    navigation_review["source_sha256"] = navigation_fingerprint(en_templates, ja_templates)
    for path, raw, data in ((content_review_path, content_review_raw, content_review),
                            (navigation_review_path, navigation_review_raw, navigation_review)):
        path.write_bytes(json_bytes(data, "\r\n" if b"\r\n" in raw else "\n"))
    print(f"PASS: applied byte-baseline-checked migration; {changed_strings} prose values updated; {audited_values} changed strings proved punctuation-only; {renamed_worldhint_keys} authorized world-hint key renames")
    for locale, values in counts.items():
        print(locale, json.dumps(values, ensure_ascii=False, sort_keys=True))
        print(locale + " description_units", json.dumps(description_counts[locale], ensure_ascii=False, sort_keys=True))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", default="HEAD")
    parser.add_argument("--java-home", type=Path)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--apply", action="store_true")
    mode.add_argument("--verify", action="store_true")
    args = parser.parse_args()
    migrate(args.base, args.java_home, args.apply, args.verify)
