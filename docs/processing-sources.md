# Processing and power lesson evidence

These notes cover the eight lessons in `processing.json`. Japanese and English captions are original paraphrases, written for Minecraft 1.21.1 / NeoForge / Immersive Engineering **12.4.2-194**.

## Authoritative release

- Research artifact: `build/research/ImmersiveEngineering-1.21.1-12.4.2-194.jar`.
- SHA-256: `45942985a4a4aebf265b8e22a0c54a96208637471f36f2532ff5d4911322debc`.
- Read the release's English manual entries under `assets/immersiveengineering/manual/en_us/`: `improved_blast_furnace.txt`, `metal_press.txt`, `crusher.txt`, `arc_furnace.txt`, `squeezer.txt`, `fermenter.txt`, `refinery.txt`, and `diesel_generator.txt`.
- This release does **not** ship Japanese manual text. Japanese machine, material and tool names were checked against its `assets/immersiveengineering/lang/ja_jp.json`; the release English manual and recipes establish behavior. English registry names were checked against `en_us.json`.
- Assembly layouts and block-state properties were read directly from the compressed NBT resources under `data/immersiveengineering/structure/multiblocks/` using a Python standard-library ZIP/gzip/NBT reader. No IE structure, model, texture, or manual asset is redistributed by these lessons.

The runtime loads those templates from the installed IE dependency. Coordinates below are relative to the IE template origin. The first constructor position is the master offset; the **second** position is the hammer trigger. `javap -p -c` on the exact release's multiblock classes confirmed the positions, rather than relying solely on the checked-out source branch.

| Lesson | Runtime template | Size (x,y,z) | Hammer trigger | Trigger material |
| --- | --- | --- | --- | --- |
| `improved_blast_furnace` | `improved_blast_furnace` | 3,4,3 | 1,1,2 | Reinforced Blast Brick |
| `metal_press` | `metal_press` | 3,3,1 | 1,1,0 | Downward Piston |
| `crusher` | `crusher` | 5,3,3 | 2,1,2 | Steel Fence |
| `arc_furnace` | `arcfurnace` | 5,5,5 | 2,0,4 | Cauldron |
| `squeezer` | `squeezer` | 3,3,3 | 1,1,1 | Wooden Barrel |
| `fermenter` | `fermenter` | 3,3,3 | 1,1,1 | Cauldron |
| `refinery` | `refinery` | 5,3,3 | 2,1,2 | Heavy Engineering Block |
| `diesel_generator` | `diesel_generator` | 3,3,5 | 1,1,4 | Generator Block |

`@layer0` is the template's bottom layer, displayed at scene y=1. Each lesson reveals every occupied layer before its hammer instruction. The larger Arc Furnace uses origin 2,1,2; none of its five layers is omitted. `arcfurnace` is the real structure filename, while `arc_furnace` is the lesson and block name.

## Behavior and ports

Source files used to locate and interpret implementation details are beneath `build/research/source/ImmersiveEngineering-1.21.1/src/main/java/blusunrize/immersiveengineering/common/blocks/multiblocks/`. Because the branch can differ from the release, captions defer to the release manual and recipe resources; multiblock constructor coordinates were additionally checked in release bytecode.

- **Improved Blast Furnace:** `ImprovedBlastfurnaceMultiblock.java` and `logic/AdvBlastFurnaceLogic.java`. The top hopper takes materials and furnace fuel; steel exits the front and slag the back. Optional side preheaters need electricity, while the furnace itself still burns accepted fuel. The implementation contains a front-orientation compatibility adjustment, so source logic coordinates must not be naively equated to the construction model's front. The lesson describes the manual's front/back distinction without exposing implementation offsets.
- **Metal Press:** `MetalPressMultiblock.java` and `logic/MetalPressLogic.java`. A downward Piston, two conveyors moving in the same direction, top Heavy Engineering Block and bottom-center Redstone Engineering Block form the assembly. A mold is installed by right-clicking the formed machine; power enters above it, items move along the conveyors, and bottom-center redstone control is invertible with the Engineer's Screwdriver.
- **Crusher:** `CrusherMultiblock.java` and `logic/CrusherLogic.java`. Formation uses the middle fence on the redstone side. Top drums accept items, the shoulder has the power connector, and the bottom front is the product outlet. Available energy affects progress; redstone control is invertible. The release manual explicitly warns about damage to living entities in the drums.
- **Arc Furnace:** `ArcFurnaceMultiblock.java` and `logic/arcfurnace/ArcFurnaceLogic.java`. Three electrodes require manual replacement; energy enters at the rear, ingredients and additives use separate top hatches, products exit the front basin, and slag exits at the rear. The lesson avoids a fixed lifetime for electrodes. It does not assume all input items or additives have interchangeable recipes.
- **Squeezer:** `SqueezerMultiblock.java` and `logic/SqueezerLogic.java`. Formation uses the central Wooden Barrel from the redstone side. The formed blue hatches take items, the orange hatch outputs items, and the port below it outputs fluids. The engineering-block section takes power from above. Recipes can produce fluid or solid output.
- **Fermenter:** `FermenterMultiblock.java` and `logic/FermenterLogic.java`. The parallel layout uses Cauldrons under Iron Sheetmetal. Formation uses the central Cauldron from the control side. Power, item/fluid outputs and invertible redstone control follow the release manual. Container byproducts must be collected as items.
- **Refinery:** `RefineryMultiblock.java` and `logic/RefineryLogic.java`. The second-layer Heavy Engineering Block is the trigger. Side ports are separate fluid inputs, the lower front is output, and the center rear has top power input. Catalysts are inserted manually and not consumed; they cannot be inserted automatically. Recipes may use one or two fluids.
- **Diesel Generator:** `DieselGeneratorMultiblock.java` and `logic/DieselGeneratorLogic.java`. Lower side ports at the generator end accept fuel. The three upper outputs share total generation; the machine needs an output that can receive energy. The side terminal is invertible redstone control. Powered fuel-production machines need a starting energy source and a fuel reserve.

## Recipe examples checked in the release

Paths are relative to `data/immersiveengineering/recipe/` in the release JAR. Captions deliberately omit configurable processing rates, energy costs, electrode lifetimes and fluid yields.

| Example | Recipe resource | Fact used |
| --- | --- | --- |
| Iron to steel in a blast furnace | `blastfurnace/steel.json` | Iron input, steel and slag output; accepted fuels are separate from this recipe |
| Iron plate | `metalpress/plate_iron.json` | Iron input with `immersiveengineering:mold_plate` |
| Steel in an Arc Furnace | `arcfurnace/steel.json` | Iron input, Coke Dust additive, steel and slag output |
| Plant Oil | `squeezer/hemp_seeds.json`, `squeezer/wheat_seeds.json` | Supported seeds produce Plant Oil |
| HOP Graphite Dust | `squeezer/graphite_dust.json` | Coke Dust produces a solid output |
| Ethanol | `fermenter/apple.json`, `fermenter/potato.json` | Apples and potatoes are accepted examples |
| Container byproduct | `fermenter/honey.json` | Honey Bottles produce Ethanol and return Glass Bottles |
| Biodiesel | `refinery/biodiesel.json` | Plant Oil + Ethanol require **Saltpeter Dust catalyst** in this release |

The catalyst requirement is especially important: the lesson does not teach the older, catalyst-free Biodiesel recipe. Captions remind users that recipe and configuration changes in modpacks take precedence.

## Visual representation and registration

The renderer displays the accurate **unformed construction model** throughout each lesson. Hammer callouts teach the operation to perform in the real world; no caption claims that the displayed model has transformed into an operating machine. After that instruction, captions explicitly describe the *completed* machine's ports. This matters particularly for the Diesel Generator: its upper formed output bar occupies positions that are air in the construction template. Its output explanation focuses the existing generator blocks and describes the bar above them, without adding a fake block or claiming the bar is rendered.

Only the Metal Press lesson uses moving items. Its two captions identify that movement as an illustration of input/output routing, rather than a reproduction of machine throughput or a running formed machine. Tool callouts use actual right-click instructions; material and recipe examples use neutral item callouts.

The eight icons are real obtainable items:

| Lesson | Icon ID |
| --- | --- |
| Improved Blast Furnace | `immersiveengineering:blastbrick_reinforced` |
| Metal Press | `immersiveengineering:heavy_engineering` |
| Crusher | `immersiveengineering:light_engineering` |
| Arc Furnace | `immersiveengineering:graphite_electrode` |
| Squeezer | `immersiveengineering:wooden_barrel` |
| Fermenter | `minecraft:cauldron` |
| Refinery | `immersiveengineering:fluid_pipe` |
| Diesel Generator | `immersiveengineering:generator` |

Related-item registration uses construction ingredients and relevant functional ingredients or outputs. It does not register inaccessible formed-multiblock blocks as items. All 34 unique IE item IDs used by icons, components, callouts and flows were checked for corresponding release item models. Runtime registry validation remains the definitive item-existence check. The data has eight lessons and 80 bilingual steps; anchor, layer, visibility and stage-bound checks can be performed without launching Minecraft.
