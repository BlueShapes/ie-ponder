# Third-party notices

The data-driven catalogue, storyboard builder, tooltip descriptions, progress tracking, command and navigation are adapted from the generic NeoForge runtime in **IF Ponder Guide** by aosankaku and contributors. That runtime is based on **AE2 Ponder Guide** by aosankaku and contributors; its MIT attribution is retained in `LICENSE`. Chapter navigation now uses Ponder's native tag index and tag screens.

The opt-in client validation harness in `src/validation` is adapted from the IF Ponder Guide validation runtime and is covered by the same MIT license and attribution.

The **Ponder** library by The Create Team is a separately installed dependency under the MIT license. Its license is included in `LICENSES/MIT-Ponder.txt`. This project uses Ponder's public API, native caption elements and animations, and small namespaced accessors for course position and localized caption suppliers.

The guide-owned caption element uses the public scene transform and near-target placement to retain the native 180-pixel text layout at the minimum GUI size. It delegates rendering to Ponder. Sentence/continuation boundaries are authored in this project's text; font wrapping does not create new display units.

The native UI source reference in `src/main/translations/native-ui-source.json` contains 22 interface strings from Ponder 1.0.87 under that MIT license. Newly authored language supplements supply missing or untranslated native guide controls and repair two missing mod-name placeholders. They use ordinary language resource merging under the library-owned keys; existing complete upstream translations, configuration UI, and Ponder assets are not copied into this JAR.

**Immersive Engineering** by BluSunrize and contributors is a separately installed runtime dependency. Its code, models, textures, structures and language files are not bundled in this project. The runtime reads multiblock structure data from the installed mod at runtime. Lesson text and guide translations are authored for this project; official localized item names are retained where available. Proposed item-name translations are explicitly labeled when the dependency has no official translation. No manual text, GuideME code or GuideME UI text is included.

Two client-only compatibility injections adjust IE's chunk guards and call its
existing immediate vertex-rendering fallback exclusively in this guide's virtual
scenes. They do not copy IE renderer implementations or change IE configuration.

Sources:

- https://github.com/AoSankaku/ae2-ponder-guide
- https://github.com/Creators-of-Create/Ponder
- https://github.com/BluSunrize/ImmersiveEngineering/tree/1.21.1

The Gradle Wrapper scripts and JAR are build tooling from the Gradle project,
licensed under Apache License 2.0. Their original notices are retained, and the
license is provided in `LICENSES/Apache-2.0.txt`. The wrapper is included only
in the source project, not the mod JAR.
