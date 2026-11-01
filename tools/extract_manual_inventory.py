#!/usr/bin/env python3
"""Extract an independent IE manual inventory from a fixed release JAR.

Only the supplied JAR is read. No ieponder registration, translation or audit
files are consulted. JSON is written to stdout, for example:

    python -B tools/extract_manual_inventory.py --ie-jar IE.jar

This inventories authored manual associations, not the complete runtime item
registry. Direct displays and positioned recipe layouts are deliberately kept
separate from the outputs indexed by the Engineering Manual's recipe links.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import zipfile
from collections import defaultdict
from pathlib import Path
from typing import Any


NAMESPACE = "immersiveengineering"
MANUAL_PREFIX = f"assets/{NAMESPACE}/manual/"
RECIPE_PREFIX = f"data/{NAMESPACE}/recipe/"
CRAFTING_TYPES = {
    "minecraft:crafting_shaped",
    "minecraft:crafting_shapeless",
    "immersiveengineering:blueprint",
    "immersiveengineering:bottling_machine",
}
VARIANT_PATTERNS = {
    "chutes": r"chute_(?:aluminum|copper|iron|steel|colored_.+)$",
    "metal_scaffolding_forms": r"(?:slab_|stairs_)?(?:steel|alu)_scaffolding_.+$",
    "treated_wood_forms": r"(?:slab_|stairs_)?treated_wood_(?:horizontal|vertical|packaged)$",
    "conveyors": r"conveyor_.+$",
    "press_and_bottling_molds": r"mold_.+$",
    "sheetmetal_all_materials_colors_forms": r"(?:slab_|stairs_)?sheetmetal_.+$",
    "warning_signs": r"warning_sign_.+$",
    "concrete_and_hempcrete_forms": r"(?:slab_|stairs_|wall_)?(?:concrete|hempcrete)(?:_.+)?$",
    "clinker_and_slag_forms": r"(?:slab_|stairs_|wall_)?(?:clinker_brick|slag_brick)(?:_.+)?$",
    "posts_wallmounts": r"(?:treated|alu|steel)_(?:post|wallmount)$",
    "ladders": r"metal_ladder_.+$",
    "catwalks": r"(?:alu|steel|treated)_catwalk(?:_stairs)?$",
    "doors_trapdoors": r"(?:treated|steel)_(?:door|trapdoor)(?:_framed)?$",
    "signs": r"(?:treated_wood|aluminum|steel)_(?:hanging_)?sign$",
    "windows": r"(?:alu|steel|treated|reinforced)_window$",
    "energy_connectors": r"connector_(?:lv|mv|hv)(?:_relay)?$",
    "energy_and_structural_coils": r"wirecoil_.+$",
    "tool_upgrades": r"toolupgrade_.+$",
    "drill_and_saw_heads": r"(?:drillhead_.+|sawblade|rockcutter|grindingdisk)$",
    "revolver_cartridges": r"bullet_.+$",
}


def resource_id(value: str) -> str:
    return value if ":" in value else f"{NAMESPACE}:{value}"


def read_stack(value: Any) -> dict[str, Any] | None:
    """Normalize manual/recipe stack syntax without dropping components."""
    if isinstance(value, str):
        return {"id": resource_id(value)}
    if not isinstance(value, dict):
        return None
    if isinstance(value.get("id"), dict):
        return read_stack(value["id"])
    identifier = value.get("id") or value.get("item")
    if isinstance(identifier, str):
        result = {"id": resource_id(identifier)}
        for key in ("components", "count"):
            if key in value:
                result[key] = value[key]
        return result
    if "base_ingredient" in value:
        return read_stack(value["base_ingredient"])
    return None


def read_outputs(value: Any) -> list[dict[str, Any]]:
    if isinstance(value, list):
        return [stack for element in value for stack in read_outputs(element)]
    stack = read_stack(value)
    return [stack] if stack else []


def unwrap_recipe(recipe: dict[str, Any]) -> dict[str, Any]:
    """IE no_container_item is recursive and stores its output in baseRecipe."""
    while isinstance(recipe.get("baseRecipe"), dict):
        recipe = recipe["baseRecipe"]
    return recipe


def recipe_outputs(recipe: dict[str, Any]) -> list[dict[str, Any]]:
    recipe = unwrap_recipe(recipe)
    return read_outputs(
        recipe.get("result") or recipe.get("results") or recipe.get("output")
    )


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


class Extractor:
    def __init__(self, recipes: dict[str, dict[str, Any]]) -> None:
        self.recipes = recipes
        self.records: list[dict[str, Any]] = []
        self.unresolved: list[dict[str, Any]] = []
        self.multiblocks: list[dict[str, Any]] = []

    def add(
        self,
        entry: str,
        anchor: str,
        kind: str,
        stack: dict[str, Any] | None,
        resource: str,
        native: bool = False,
    ) -> None:
        if stack:
            self.records.append({
                "entry": entry,
                "anchor": anchor,
                "kind": kind,
                "stack": stack,
                "resource": resource,
                "native_recipe_link": native,
            })

    def process_refs(self, entry: str, anchor: str, kind: str, value: Any) -> None:
        manual_resource = f"{MANUAL_PREFIX}{entry}.json"
        if isinstance(value, list):
            positioned = value and all(
                isinstance(element, dict) and "x" in element and "y" in element
                for element in value
            )
            if positioned:
                for element in value:
                    self.add(
                        entry, anchor, "fixed_recipe_display",
                        read_stack(element), manual_resource,
                    )
            else:
                for element in value:
                    self.process_refs(entry, anchor, kind, element)
        elif isinstance(value, str) or isinstance(value, dict) and "recipe" in value:
            reference = value if isinstance(value, str) else value["recipe"]
            if not isinstance(reference, str):
                self.process_refs(entry, anchor, kind, reference)
                return
            namespace, path = resource_id(reference).split(":", 1)
            recipe_resource = f"data/{namespace}/recipe/{path}.json"
            if recipe_resource not in self.recipes:
                self.unresolved.append({
                    "entry": entry, "anchor": anchor, "reference": reference,
                })
                return
            for stack in recipe_outputs(self.recipes[recipe_resource]):
                self.add(
                    entry, anchor, "recipe_output", stack, recipe_resource,
                    kind in {"crafting", "blueprint", "bottling"},
                )
        elif isinstance(value, dict):
            stack = read_stack(value)
            if stack:
                self.add(
                    entry, anchor, "recipe_output_query", stack, manual_resource,
                    kind in {"crafting", "blueprint", "bottling"},
                )
            elif "fluid" in value:
                # Release IE fluids referenced by mixer manual elements all use
                # the corresponding <fluid>_bucket item. This is not a registry
                # ID synthesis for arbitrary blocks or color variants.
                self.add(
                    entry, anchor, "fluid_output_bucket",
                    {"id": resource_id(value["fluid"]) + "_bucket"},
                    manual_resource, kind == "mixer",
                )
            else:
                self.unresolved.append({
                    "entry": entry, "anchor": anchor, "reference": value,
                })

    def walk(self, entry: str, value: Any, anchor: str = "") -> None:
        if isinstance(value, list):
            for index, element in enumerate(value):
                self.walk(entry, element, f"{anchor}[{index}]")
        elif isinstance(value, dict):
            kind = value.get("type")
            if kind in {"crafting", "blueprint", "bottling", "mixer"}:
                for key in ("recipe", "recipes"):
                    if key in value:
                        self.process_refs(entry, anchor, kind, value[key])
            elif kind == "item_display":
                for element in value.get("items", [value.get("id")]):
                    self.add(
                        entry, anchor, "item_display", read_stack(element),
                        f"{MANUAL_PREFIX}{entry}.json",
                    )
            elif kind == "multiblock":
                self.multiblocks.append({
                    "entry": entry, "anchor": anchor, "name": value["name"],
                    "resource": f"{MANUAL_PREFIX}{entry}.json",
                })
            elif kind is None:
                for key, element in value.items():
                    self.walk(entry, element, f"{anchor}.{key}" if anchor else key)

    def expand_output_queries(self) -> None:
        # Match actual recipe outputs, rather than treating ingredients or every
        # string in a JSON document as a provided manual item.
        for record in list(self.records):
            if record["kind"] != "recipe_output_query":
                continue
            for resource, recipe in self.recipes.items():
                if unwrap_recipe(recipe).get("type") not in CRAFTING_TYPES:
                    continue
                for stack in recipe_outputs(recipe):
                    if stack["id"] == record["stack"]["id"]:
                        self.add(
                            record["entry"], record["anchor"],
                            "recipe_output_query_match", stack, resource, True,
                        )


def extract_inventory(jar_path: Path) -> dict[str, Any]:
    with zipfile.ZipFile(jar_path) as jar:
        names = sorted(jar.namelist())
        recipes = {
            name: json.loads(jar.read(name)) for name in names
            if name.startswith(RECIPE_PREFIX) and name.endswith(".json")
        }
        manuals = {
            name[len(MANUAL_PREFIX):-5]: json.loads(jar.read(name))
            for name in names if name.startswith(MANUAL_PREFIX)
            and name.endswith(".json") and name != MANUAL_PREFIX + "autoload.json"
        }
        autoload = json.loads(jar.read(MANUAL_PREFIX + "autoload.json"))

    extractor = Extractor(recipes)
    for entry, definition in manuals.items():
        extractor.walk(entry, definition)
    extractor.expand_output_queries()

    by_id: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for record in extractor.records:
        if record["stack"]["id"].startswith(NAMESPACE + ":"):
            by_id[record["stack"]["id"]].append(record)

    recipe_results: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for resource, recipe in recipes.items():
        for stack in recipe_outputs(recipe):
            if stack["id"].startswith(NAMESPACE + ":"):
                recipe_results[stack["id"]].append({
                    "resource": resource, "stack": stack,
                })

    families = []
    for family, pattern in VARIANT_PATTERNS.items():
        ids = sorted(
            identifier for identifier in recipe_results
            if re.fullmatch(pattern, identifier.split(":", 1)[1])
        )
        families.append({
            "id": family,
            "pattern": pattern,
            "expected_ids": ids,
            "count": len(ids),
            "recipe_evidence": {
                identifier: sorted({case["resource"] for case in recipe_results[identifier]})
                for identifier in ids
            },
        })

    component_cases = []
    for identifier, cases in sorted(recipe_results.items()):
        variants = {
            json.dumps(case["stack"].get("components"), sort_keys=True): case
            for case in cases if case["stack"].get("components")
        }
        if variants:
            if identifier == NAMESPACE + ":bullet_flare":
                # The red recipe has no explicit component patch: the bullet
                # item's runtime default provides red. Keep the actual recipe
                # payload, not an invented explicit component patch.
                for case in cases:
                    if case["resource"].endswith("/bullet_flare_red.json"):
                        variants["red_item_default"] = case
            component_cases.append({
                "id": identifier,
                "recipe_stack_variants": sorted(variants.values(), key=lambda case: case["resource"]),
                "preserve_components": True,
            })

    native_ids = {
        identifier for identifier, records in by_id.items()
        if any(record["native_recipe_link"] for record in records)
    }
    return {
        "schema": "ieponder-fixed-jar-manual-inventory-v1",
        "authority": {"jar": str(jar_path), "sha256": sha256_file(jar_path)},
        "method": [
            "All entry JSON is extracted from the supplied release JAR, independently of ieponder files",
            "Named recipes resolve to fixed-JAR resources; recursive baseRecipe wrappers are unwrapped",
            "Recipe result, item_display and positioned fixed-layout references retain separate provenance kinds",
            "Mixer fluid references map to the corresponding release bucket Item IDs",
            "Twenty selectors choose actual recipe result IDs; they do not synthesize missing variants",
            "Component patches are retained; the red flare's empty patch denotes the actual item's default",
        ],
        "scope_limits": [
            "Not a runtime registry dump or an inventory of every dynamic component value",
            "Manual displays and fixed layouts can include recipe materials; they are not all native recipe links",
            "Some all-recipe result IDs are fluids, so that aggregate is explicitly not an item count",
            "Multiblock assembly association and authored functional classification require separate review",
            "No production registrations or before/after coverage baselines are read or inferred",
        ],
        "counts": {
            "manual_entry_json": len(manuals),
            "manual_record_associations": len(extractor.records),
            "manual_expected_unique_ie_ids": len(by_id),
            "manual_ie_native_output_unique_ids": len(native_ids),
            "manual_ie_display_only_unique_ids": len(by_id) - len(native_ids),
            "variant_families": len(families),
            "component_bearing_item_ids_in_recipes": len(component_cases),
            "all_recipe_unique_result_ie_ids_including_fluid_ids": len(recipe_results),
            "unresolved_named_references": len(extractor.unresolved),
        },
        "manual_expected_ids": sorted(by_id),
        "expected_items": [
            {"id": identifier, "evidence": records}
            for identifier, records in sorted(by_id.items())
        ],
        "non_ie_associations": [
            record for record in extractor.records
            if not record["stack"]["id"].startswith(NAMESPACE + ":")
        ],
        "manual_entries": [
            {
                "id": entry,
                "resource": f"{MANUAL_PREFIX}{entry}.json",
                "gate": definition.get("require_advancement"),
            }
            for entry, definition in manuals.items()
        ],
        "autoload": autoload,
        "variant_families": families,
        "component_stack_cases": component_cases,
        "multiblock_manual_entries": extractor.multiblocks,
        "unresolved_named_references": extractor.unresolved,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ie-jar", required=True, type=Path)
    parser.add_argument(
        "--summary", action="store_true",
        help="Print only authority and counts instead of the complete JSON inventory",
    )
    args = parser.parse_args()
    result = extract_inventory(args.ie_jar)
    if args.summary:
        result = {key: result[key] for key in ("authority", "counts")}
    json.dump(result, sys.stdout, ensure_ascii=False, indent=2)
    sys.stdout.write("\n")


if __name__ == "__main__":
    main()
