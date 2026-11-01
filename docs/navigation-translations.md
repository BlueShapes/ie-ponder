# Navigation translation coverage

The chapter navigation change has original translations for the 20 non-Japanese/non-English locales in the shared `../docs/languages.json`. Sources are stored in `src/main/translations/navigation/<locale>.json`; generated language resources must not be edited by hand.

`src/main/translations/languages.json` retains the locale list for standalone builds; when the shared series list is present, generation and source checks require the lists to agree.

Each locale contains exactly 16 keys:

- Guide title.
- World-required command message, unknown chapter/lesson/item message and held-key description hint.
- Six numbered chapter titles and six chapter descriptions.

Covered locales: `hi_in`, `zh_hk`, `zh_tw`, `zh_cn`, `ru_ru`, `pt_br`, `pt_pt`, `es_es`, `fr_fr`, `de_de`, `cs_cz`, `pl_pl`, `ms_my`, `ro_ro`, `ar_sa`, `id_id`, `ko_kr`, `vi_vn`, `tr_tr`, `it_it`.

Text review preserved the source meanings, proper mod name, chapter order, `%s` substitutions and `[%s]` key hint. Hong Kong and Taiwan wording was reviewed separately, as was Brazilian and European Portuguese. Czech uses polite plural instructions; European Portuguese addresses the player with `tu`. Arabic source remains in logical Unicode order.

Version 0.1.3 removes the guide-button label and renames the existing world-required message to `ieponder.command.requires_world` in every locale, retaining it for command errors. The source check covers all 352 entries across 22 locales: exact locale/key coverage, nonempty strings, UTF-8 without replacement characters, placeholder parity and numbering 1–6. Own chapter descriptions omit ordinary final stops while retaining numbering and meaningful dots. These source checks do not establish font rendering or screen layout.

The navigation files cover these 16 keys only. There are also 185 translated content keys per locale under `src/main/translations/content/`; see [content coverage](content-translations.md). Generated language resources combine both sets into 201 keys in every supported locale.

The native tag index is opened by `/ponder-ie`. Native item hover + W retains each component's scene list. Every active lesson's representative icon and components receive the correct category association. Only the active lesson's category is displayed in the native top-left tag strip; native tag screens and back callbacks remain unchanged. A guide-scoped context injection synchronizes native subject/tags at initialization, successful scroll and direct native index changes. It rebuilds widgets only when the tag list changes and restores saved native context on foreign scenes in mixed-plugin courses.
