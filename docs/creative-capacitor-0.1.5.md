# Creative Accumulator entrance audit

2026-10-23. Target: `immersiveengineering:capacitor_creative`, IE `1.21.1-12.4.2-194`, NeoForge.

Authority: `build/research/ImmersiveEngineering-1.21.1-12.4.2-194.jar`, SHA-256 `45942985a4a4aebf265b8e22a0c54a96208637471f36f2532ff5d4911322debc`.
All conclusions below were checked against this JAR's `javap -p -c` output and language JSON. Saved 12.4.3 source was only a navigation aid. No client launch or production edit was performed.

## Decision

This is a real, normally listed Creative-tab item with a distinct useful function, not an internal block. Its existing native item tooltip does **not** explain its unlimited-source role or side conditions. A concise Shift description is warranted; Creative status alone is not a supported exclusion.

Suggested factual content, for the primary author to localize:

- The Creative Accumulator supplies energy without depletion
- A newly placed default stack starts with all six faces set to output
- A hammer changes a face's mode; sneaking targets the opposite face
- Only output faces supply energy to connected consumers

Do not describe it as an ordinary finite LV/MV/HV accumulator or require charging before output. Do not promise unlimited transfer through downstream connectors/wires: their own limits and the receiving device still apply.

## Normal Creative-tab availability

The fixed JAR's `IEBlocks$MetalDevices` static initializer creates `CAPACITOR_CREATIVE` under name `capacitor_creative` (bytecode offsets 103–120), using a plain `IEEntityBlock` (its `lambda$static$4` constructs it with `IEBlockEntities.CAPACITOR_CREATIVE`). There is no Creative-capacitor-specific hidden flag, flavour setup, or custom item factory.

`IEBlocks.init` loops `BlockEntry.ALL_ENTRIES`, registers non-excluded entries into `IEItems.REGISTER` using the block's path, and uses `BlockItemIE` for the default branch. The creative capacitor is not among the explicit exclusions or custom-item cases.

`IECreativeTabs.fillIETab` loops `IEItems.REGISTER.getEntries()`. The potion bucket is the only outright skipped item. BlockItems whose block is an `IEBaseBlock` call the block's `fillCreativeTab`; the creative capacitor's plain `IEEntityBlock` inherits `IEBaseBlock.fillCreativeTab`, which directly invokes `Output.accept(this)` (offsets 0–7). There is no operator-permission or visibility condition for this item in this path. Thus it is listed in IE's normal `immersiveengineering:main` Creative tab.

## Energy and side behavior

| Fixed-JAR class/method | Verified behavior |
| --- | --- |
| `CapacitorCreativeBlockEntity.<init>` | Calls the parent with `CapacitorConfig.CREATIVE`, then loops all directions and sets every `sideConfig` entry to `OUTPUT` (offsets 9–52) |
| `CapacitorCreativeBlockEntity.makeMainEnergyStorage` | Returns `InfiniteEnergyStorage.INSTANCE` |
| `CapacitorCreativeBlockEntity$InfiniteEnergyStorage.receiveEnergy` / `extractEnergy` | Return the requested integer amount directly, without changing stored state |
| Same class, `getEnergyStored` / `getMaxEnergyStored` | Both return `2147483647`; `canExtract` and `canReceive` both return true |
| `IEServerConfig$Machines$CapacitorConfig.<clinit>` | Creative storage/input/output limits are each the constant `2147483647`, rather than the configurable LV/MV/HV settings |
| `CapacitorBlockEntity.transferEnergy` | Immediately returns unless the face is `OUTPUT`; otherwise offers `min(getMaxOutput(), stored)` to the adjacent energy capability and extracts the accepted amount |
| `CapacitorBlockEntity$CapacitorEnergyHandler` | Exposed side capability only extracts through `OUTPUT` and only receives through `INPUT` |
| `CapacitorBlockEntity.toggleSide` | Applies `IOSideConfig.next` to the chosen face, marks changed, and updates the block |
| `IEEnums$IOSideConfig.next` | Cycles `NONE → INPUT → OUTPUT → NONE` |
| `HammerItem.onItemUseFirst` | For `IConfigurableSides`, chooses the clicked face, or its opposite when the player is sneaking, then calls `toggleSide` (the relevant call is at offset 354) |

The parent constructor uses a null energy store before server config is loaded; normal configured gameplay uses the creative infinite store. Also, `CapacitorBlockEntity.onBEPlaced` restores `CAPACITOR_CONFIG` from the placed ItemStack when present. Therefore “all faces output” describes a fresh default stack; preserved side settings can override that on replacement. This is another reason to preserve ItemStack metadata.

## Actual native tooltip

The registered item is the ordinary `BlockItemIE`. Its `appendHoverText` has these branches only:

1. Optional flavour text if `IIEBlock.hasFlavour()` is true
2. The normal superclass block/item tooltip
3. A numeric stored-energy line if the stack has `IEDataComponents.GENERIC_ENERGY`
4. A stored-fluid line if the stack has `IEDataComponents.GENERIC_FLUID`

There is no Creative-capacitor-specific branch, unlimited-source explanation, or side-configuration text. The plain capacitor block is not assigned flavour text; the fixed JAR has no `desc.immersiveengineering.flavour.capacitor_creative` translation either.

Fixed-JAR English text:

- `block.immersiveengineering.capacitor_creative`: `Creative Accumulator`
- `desc.immersiveengineering.info.energyStored`: `Energy stored: %1$s Flux`

Thus a fresh Creative-tab stack receives its ordinary item name, not a functional description. A previously broken stack may also display the numeric stored-energy line; that number still does not explain an inexhaustible source or its usable faces.

The generic client tooltip hook was checked too: fixed-JAR `ClientEventHandler.onItemTooltip` has shader, attached earmuff/powerpack, blast-furnace fuel and optional advanced-tag handling. It has no capacitor reference; its only literal translation keys are `noChargeOnArmor` and `blastFuelTime`.

The capacitor **world overlay**, not the item tooltip, can show `Energy Input`, `No Connection`, or `Energy Output` while targeting it with a hammer and with `IEClientConfig.showTextOverlay` enabled. `CapacitorBlockEntity.getOverlayText` and these `desc.immersiveengineering.info.blockSide.connectEnergy.*` language entries establish that distinction. The world overlay does not fill the missing item-level role explanation.

## Reproduction

Run `javap -classpath <fixed-JAR> -p -c <class>` for each class/method listed above. Quote class names containing `$` literally when invoking from PowerShell. Inspect the same JAR's `assets/immersiveengineering/lang/en_us.json`; no current guide registrations or generated translations are needed to establish these facts.
