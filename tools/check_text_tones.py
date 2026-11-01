"""Check authored per-locale Ponder caption and item-description colors."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "src/main/resources/assets/ieponder/guide"
TRANSLATIONS = ROOT / "src/main/translations/content"
TONES = GUIDE / "text_tones.json"
KINDS = {"NORMAL", "WARNING", "HINT"}


def units(value):
    return [line.strip() for line in value.replace("\r\n", "\n").replace("\r", "\n").split("\n")]


def source_texts():
    lessons = sum((json.loads((GUIDE / f"{name}.json").read_text(encoding="utf-8"))
                  for name in ("lessons", "processing")), [])
    result = {}
    for lesson in lessons:
        for index, step in enumerate(lesson["steps"]):
            result[f"ieponder.ponder.shared.{lesson['id']}_{index}"] = step["text"]

    descriptions = json.loads((GUIDE / "item_descriptions.json").read_text(encoding="utf-8"))
    for entry in descriptions:
        result[f"ieponder.description.{entry['id']}"] = entry["text"]
    return lessons, result


def check():
    locales = json.loads((TRANSLATIONS.parent / "languages.json").read_text(encoding="utf-8"))
    canonical_locales = json.loads((ROOT.parent / "docs/languages.json").read_text(encoding="utf-8"))
    assert locales == canonical_locales, "Translation locales must match the shared 22-language list"
    assert len(locales) == 22, f"Expected 22 supported locales, got {len(locales)}"
    lessons, sources = source_texts()
    actual_keys = set(sources)
    caption_count = sum(len(lesson["steps"]) for lesson in lessons)
    description_count = len(json.loads((GUIDE / "item_descriptions.json").read_text(encoding="utf-8")))
    assert caption_count == 140, f"Expected 140 shared caption keys, got {caption_count}"
    assert description_count == 124, f"Expected 124 item-description keys, got {description_count}"
    assert sum(key.startswith("ieponder.ponder.shared.") for key in actual_keys) == 140
    assert sum(key.startswith("ieponder.description.") for key in actual_keys) == description_count
    tones = json.loads(TONES.read_text(encoding="utf-8"))
    assert set(tones) == set(locales), "Tone authoring must explicitly cover all 22 locales"

    translated = {"ja_jp": {key: value["ja"] for key, value in sources.items()},
                  "en_us": {key: value["en"] for key, value in sources.items()}}
    for locale in locales:
        if locale not in translated:
            translated[locale] = json.loads((TRANSLATIONS / f"{locale}.json").read_text(encoding="utf-8"))
        assert set(tones[locale]) == actual_keys, f"Tone key mismatch: {locale}"
        missing = actual_keys - set(translated[locale])
        assert not missing, f"Missing translated source units: {locale}: {sorted(missing)}"
        used = set()
        for key in actual_keys:
            value = translated[locale][key]
            authored_units = units(value)
            authored_tones = tones[locale][key]
            assert all(authored_units), f"Empty authored unit: {locale}/{key}"
            assert len(authored_tones) == len(authored_units), (
                f"Tone/unit count differs: {locale}/{key}: {len(authored_tones)} != {len(authored_units)}")
            assert all(tone in KINDS for tone in authored_tones), f"Unknown tone: {locale}/{key}"
            used.update(authored_tones)
        assert used == KINDS, f"Each locale needs examples of NORMAL, WARNING and HINT: {locale}"

    expected = {
        ("ja_jp", "ieponder.ponder.shared.coke_oven_4"): ["WARNING", "HINT", "NORMAL"],
        ("en_us", "ieponder.ponder.shared.coke_oven_4"): ["WARNING", "HINT", "NORMAL"],
        ("ja_jp", "ieponder.ponder.shared.crusher_8"): ["WARNING", "WARNING", "HINT"],
        ("en_us", "ieponder.ponder.shared.crusher_8"): ["WARNING", "WARNING", "HINT"],
        ("ja_jp", "ieponder.ponder.shared.power_diagnostics_0"): ["HINT", "WARNING"],
        ("en_us", "ieponder.ponder.shared.power_diagnostics_0"): ["HINT", "WARNING"],
        ("ja_jp", "ieponder.ponder.shared.capacitors_3"): ["HINT", "WARNING"],
        ("en_us", "ieponder.ponder.shared.capacitors_3"): ["HINT", "WARNING"],
        ("ja_jp", "ieponder.ponder.shared.thermoelectric_0"): ["NORMAL", "NORMAL"],
        ("en_us", "ieponder.ponder.shared.thermoelectric_0"): ["NORMAL", "NORMAL", "NORMAL"],
        ("ja_jp", "ieponder.description.tools"): ["NORMAL", "NORMAL"],
        ("en_us", "ieponder.description.tools"): ["NORMAL", "NORMAL"],
        ("ja_jp", "ieponder.description.wirecutter"): ["NORMAL", "WARNING"],
        ("en_us", "ieponder.description.wirecutter"): ["NORMAL", "NORMAL"],
        ("ja_jp", "ieponder.description.treated_wood"): ["NORMAL", "WARNING"],
        ("en_us", "ieponder.description.treated_wood"): ["NORMAL", "NORMAL", "WARNING"],
        ("ja_jp", "ieponder.description.crate"): ["NORMAL", "HINT"],
        ("en_us", "ieponder.description.crate"): ["HINT"],
        ("ja_jp", "ieponder.ponder.shared.metal_press_8"): ["NORMAL", "WARNING"],
        ("hi_in", "ieponder.ponder.shared.metal_press_8"): [
            "NORMAL", "WARNING", "HINT", "NORMAL", "HINT", "NORMAL"],
        ("zh_cn", "ieponder.ponder.shared.squeezer_7"): ["NORMAL", "WARNING"],
    }
    for (locale, key), expected_tones in expected.items():
        assert tones[locale][key] == expected_tones, f"Authored semantic regression: {locale}/{key}"

    for locale in locales:
        power_check = tones[locale]["ieponder.ponder.shared.power_diagnostics_0"]
        capacitor_faces = tones[locale]["ieponder.ponder.shared.capacitors_3"]
        thermoelectric_principle = tones[locale]["ieponder.ponder.shared.thermoelectric_0"]
        assert power_check == ["HINT", "WARNING"], f"Diagnostic advice/failure tone: {locale}"
        assert capacitor_faces == ["HINT", "WARNING"], f"Sneak-hammer shortcut/connection warning tone: {locale}"
        assert all(tone == "NORMAL" for tone in thermoelectric_principle), (
            f"Thermoelectric operating-principle tone: {locale}")

    zh_key = "ieponder.ponder.shared.diesel_generator_6"
    zh_units = translated["zh_cn"][zh_key].splitlines()
    en_units = translated["en_us"][zh_key].splitlines()
    assert len(zh_units) != len(en_units), "The independent boundary regression no longer differs"
    assert tones["zh_cn"][zh_key] == ["WARNING"] * len(zh_units), (
        "Every Chinese unit explains a generator failure condition")

    print(f"PASS: {len(locales)} locales, {caption_count} caption keys, {description_count} item descriptions; "
          "unit counts, enums and semantic regressions verified")


if __name__ == "__main__":
    check()
