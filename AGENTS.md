# Project scope

1. This is an AI-driven (`AI駆動`) project for Minecraft 1.21.1 / NeoForge / Java 21.

2. The guide must support all 22 languages in `../docs/languages.json`. Update every affected translation in the same change.

3. Japanese explanations use です・ます and describe operations, mechanisms, states, and conditions/results naturally. Reserve してください for purposeful checks, safety advice, or troubleshooting; do not replace endings mechanically. Check IE's official localized item names (shared requirements 12 and 73).

4. Canonical lessons are `src/main/resources/assets/ieponder/guide/lessons.json` and `processing.json`; UI originals live in `src/main/templates/lang`. Do not hand-edit generated language resources.

5. Read assembly templates from the separately installed IE JAR. Do not copy dependency JARs, art, models, structures, or manual text into the distribution.

6. Keep Ponder operations inside its virtual world. Keep guide IDs, commands, progress and mixins scoped to `ieponder`.

7. Do not add global Ponder/GuideME redirects copied from another guide. The existing IE chunk-utility injection must remain restricted to this guide's virtual scenes.

8. Verify cheaply with `compileJava generateGuideResources`, and `tools/check_guide.py --ie-jar ...` for content changes. Client launch is reserved for rendering/API/navigation changes that need it.

9. `-PvalidateGuide runValidationClient` is opt-in and must require the current result JSON's `passed: true`. Validation code must stay out of release JARs.

## Shared series policy

10. Read [the shared authoring policy](../docs/mod-guide-authoring.md) and [the 22-language list](../docs/languages.json). Complete supported translations; do not treat English fallback as support for those languages.

## Numbered common requirements

11. Read the numbered requirements in [the shared policy](../docs/mod-guide-authoring.md). Shared requirement numbers are global references within that document; the numbers in this file are local references. This includes autonomous local commits, completed-JAR placement, English fallback, the three-line maximum, client commands, standard Ponder UI, and implementation-versus-guide conflict handling.

12. Caption text uses explicit newlines between authored display units in every locale. Prefer one concise complete sentence per unit. Never split a sentence by font width, line count or word count. An unavoidable continuation must use natural authored clauses with matching suffix/prefix `…` (shared requirements 19, 20, 66 and 67). Retain full content at runtime and report oversized units in authoring/font checks. Run `tools/check_captions.py` and `testCaptionUnits` for sentence/continuation changes.

13. Do not register a guide button on ordinary screens. Keep native item hover + W, `/ponder-ie`, standard Ponder tags and controls (shared requirements 68 and 71).

14. Own in-game prose omits ordinary sentence-final stops in all 22 locales while preserving meaningful dots and paired `…`. A missing final stop does not imply an incomplete unit. Keep structural fragment/multiple-sentence/continuation checks (shared requirement 69).

15. Author every localized caption and description unit's NORMAL/WARNING/HINT intent in `text_tones.json`; do not infer tone from keywords or another locale's indices. Use native WHITE/RED/GREEN body palettes independently of outline colors. Validate coverage with `tools/check_text_tones.py` and resource generation (shared requirement 70).

16. Register category tags for each active lesson's icon and real components. The guide-scoped native context injection synchronizes current subject/category at init, successful scroll and native direct-index changes, restoring untouched context on foreign scenes. Keep the null-tag W factory and native tag/back callbacks; avoid duplicate context updates or all-chapter lists.

17. Audit actual addon content before grouping chapters. Each supported addon needs its own active standard chapter/tag; omit absent or empty addon chapters. The current 20 lessons cover IE core only, and this task does not request new IE addon support (shared requirement 74).

18. Audit item entrances against the fixed IE JAR's manual associations, real registry and component variants, independently of guide registrations. Keep W/Shift ownership separate from representative cards; preserve original ItemStack data in guide-scoped native Ponder contexts. Record short-description coverage and evidenced exclusions, and use `tools/check_item_coverage.py` plus opt-in `-Coverage` validation for affected entrances (shared requirement 75).
