# 日英対応・共通規則監査（2026-10-30）

日英を重点確認し、既存22言語を維持しました。判定は**対象範囲内で合格・一部条件付き**です。全規則の全実行環境での完全検証は主張しません。

最終主JARは `immersive-engineering-ponder-guide-0.1.6-neoforge-1.21.1.jar`、SHA-256 `6110d88ccf942eef90f46fb1efa4fd529967d56639595f7995f3caf447a8af54` です。

## 確認範囲と修正

日英とも20題名・20要約・140字幕・124短文・16導線キー、各320生成キーを原本→生成物→JARで一致確認しました。字幕の作者単位は日本語303・英語348、短文は各220単位です。6章名・6章説明・展開ヒント1・操作条件/エラー2・ガイド名1を含み、native操作は従来の22ラベル実リソーススタック確認へ結び付けています。追加アドオン教材・使用ガイドページは0です。

金属プレス機の要約だけを修正しました。旧日本語「ピストンと同じ向きのコンベア」は下向きピストンと搬送方向を混同します。「ピストンと、搬送方向をそろえたコンベア」へ直し、同じ誤解があった韓国語も修正しました。英語と残る19訳の同じ要約は意味を確認して維持しています。要約の利用先は `GuideNavigation.findLessonForSearch` の論理順検索だけで、字幕・描画本文の消費先はありません。

配置ツールも拡張しました。拡張子に依存せずZIPメタデータから自MODを識別し、MC/NeoForge範囲と絶対パスを確認して、コピー後SHA一致の後で旧版を削除します。異なる環境・曖昧な所有・非ZIP・別MODは保持して報告します。互換な旧NeoForge下限を持つコピーの除去も含む6つのファイル操作回帰に成功しました。実配置後は隠れた/改名ファイルを含む再帰ZIP確認で自身の現行1コピーだけでした。

## 意味のレビュー例

### `ieponder.ponder.shared.basic_wiring_2`

日本語: 低圧ワイヤーの束で端子、継電器の順に右クリックすると接続します / 線は接続経路を示す説明用の表示です

English: An LV Wire Coil connects a connector and relay when each is right-clicked in turn / The animated line illustrates the connection path

判断: 操作・条件・否定・同一性・順序を保持し、独立した完結単位と、その言語の色意図を確認しました。

### `ieponder.ponder.shared.coke_oven_4`

日本語: 油が満杯になると処理が止まります / 容器や液体ポンプで回収してください / 油は防腐木材の材料になります

English: A full oil tank stops processing / Drain it with containers or a Fluid Pump / The creosote is used to make treated wood

判断: 操作・条件・否定・同一性・順序を保持し、独立した完結単位と、その言語の色意図を確認しました。

### `ieponder.ponder.shared.garden_cloche_2`

日本語: 対応する肥料を加えると成長を速められます / 肥料は栽培の必須条件ではありません

English: A supported fertilizer speeds up growth / Fertilizer is optional for cultivation

判断: 操作・条件・否定・同一性・順序を保持し、独立した完結単位と、その言語の色意図を確認しました。

### `ieponder.description.faraday`

日本語: 全身装備で、非絶縁ワイヤーや低出力テスラコイルの感電から身を守ります / 高出力のテスラコイルでは、逆に受けるダメージが増えます

English: A full set protects against uninsulated wires and low-power Tesla coils / High-power Tesla coils instead increase damage to the wearer

判断: 操作・条件・否定・同一性・順序を保持し、独立した完結単位と、その言語の色意図を確認しました。

### `ieponder.description.lanterns`

日本語: 周囲を照らす照明です / Cage Lamp（訳案：ケージランプ）は通常、レッドストーン信号で点灯します

English: These lamps illuminate their surroundings / A Cage Lamp requires a redstone signal by default

判断: 操作・条件・否定・同一性・順序を保持し、独立した完結単位と、その言語の色意図を確認しました。

### `ieponder.description.creative_capacitor`

日本語: 電力が尽きない、クリエイティブ用の電源です / 初期は全6面が出力で、ハンマーで無効・入力・出力に切り替えます / 出力に設定した面からだけ、電力を送り出します

English: This creative source supplies unlimited energy / All six faces output by default; a hammer cycles disabled, input and output / Energy leaves only through faces set to output

判断: 操作・条件・否定・同一性・順序を保持し、独立した完結単位と、その言語の色意図を確認しました。

### `ieponder.lesson.metal_press.summary`

日本語: ピストンと、搬送方向をそろえたコンベアでプレス機を組み、金型・電力・材料の条件を確認します

English: A piston and aligned conveyors press ingredients with a matching mold and energy

判断: 操作・条件・否定・同一性・順序を保持し、独立した完結単位と、その言語の色意図を確認しました。

通常の接続結果はNORMAL、油満杯で停止はWARNING、回収の対処はHINTです。肥料が任意である否定、Faradayの全身/低出力保護と高出力被害増加、全6面の初期出力と設定後の出力面限定を維持しています。Cage Lampの日本語名は公式名と混同せず、英語+訳案を表示します。依頼語の件数で合否を決めていません。

## 証拠再利用と今回の検査

今回はMinecraftを起動していません。0.1.5から本番43クラスはバイト一致します。変更は検索正規化のGuideNavigation/新規SearchText、バージョンmetadata・原本の日要約・生成された日韓の同じ要約キーだけです。GuideNavigationも正規化メソッドのreturn以外は旧ソースと一致します。全124短文・役割・他の全言語値は不変なので、0.1.5の4,843単位/22言語の字形・幅・W/Shift/tag/back/成分保持検証を再利用します。旧JSONを0.1.6で実行した結果と書き換えていません。

字幕/題名全22言語、模型配置/steps/timing/orderと7描画クラスは0.1.4とも一致します。0.1.4の7,936字幕単位・180px・最大3行、20教材compile/replay・代表3場面/3GUIサイズ/45配置確認を再利用します。3本文色と通常画面ボタン0は0.1.3の対象証拠を利用します。新しい日韓の検索句は生成データと一意性を静的照合しました。NFC正規化も13純粋回帰・現行7040キーで確認し、ゲーム内の新しい句の入力は未実施です。各証拠のSHA・不変入力の対応は [JSON](jaen-compliance-20261030.json) に保存しました。

今回実行: translations/navigation/captions/tones/guide/coverage/releaseのPython検査、`compileJava generateGuideResources jar`、配置のハッシュ/旧コピー確認。caption検査は54回帰を含みます。変更に無関係な全テスト・22言語再描画・全教材実行は繰り返していません。

検索の追加修正: トルコ語Kömürüに対する分解形Kömürüを一致させるため、検索語と検索対象の一時文字列だけをNFC→言語別小文字化→NFCとします。I/ıとİ/i、分解形I+dot、英語アクセント、日本語濁点、Arabic hamza/論理順をテストしました。原本・レジストリID・コマンドID自体は書き換えません。

## 規則別判定

| 要件 | 判定 | 根拠と範囲 |
| --- | --- | --- |
| 1, 2, 3, 4, 54, 56, 57, 58, 72 | pass | 運用・範囲: 独立Git/所有範囲、現在の指示、安い検査、過去証拠のハッシュ対応を確認。今回はMinecraft等の長期プロセスを開始せず、既存プロセスも操作していない |
| 5, 6, 8, 9, 10 | pass | 教材と短文の使い分け: 20教材と124短文を同じ定義から生成。無限キャパシタ等は有用なShift説明とし、無関係な有限機械教材へ登録しない |
| 7, 14, 15, 16, 35 | pass | 対象版・用語・条件: 固定IE12.4.2-194のJAR/レシピ/NBTと公式名を使用。Cage Lampは英語名+訳案を明示。既知の実装対ガイド不具合を正しい挙動として隠していない。全設定/版の網羅試験ではない |
| 11, 18, 21, 22, 34 | pass | 模型と表示順: 仮想scene.worldのみ、安定IDと作者順、151材料/操作吹き出し、keyframe/読む時間を保持。転送演出を生産速度保証としない |
| 12, 20, 66, 69, 70, 73 | pass | 文体・単位・配色: 日英全原本と代表内容を見直し、句点/単位/局所のNORMAL-WARNING-HINT意図を検査。催促語数だけで合否を決めず、故障対処/安全の依頼を維持。本文色の実証は不変クラスへ限定再利用 |
| 13 | conditional | 各言語の語調: 日英に加え第二20言語を代表12キーずつ新規レビューし、残りは値/色/描画クラスが不変の既存意味レビューを再利用。全翻訳の今回新規母語校閲ではない |
| 17 | conditional | 到達点・前提: 章順/要約/基本→操作→条件の代表内容を確認。明示的な目的/前提メタデータはなく、全20教材の教育効果を実ユーザーで実験したものではない |
| 19, 33, 36, 37, 42 | conditional | 実フォント・配置・可視性: 全22字幕と124短文の実フォントは有効範囲へ再利用しmax3。模型/配置は日本語代表3場面/3GUIサイズ、静的な全参照を確認。全20場面の全フレーム/遮蔽/全タグカード画面寸法を目視したものではない |
| 23, 24, 25, 26, 27, 28, 29, 30 | pass | 言語と生成: 22localeを維持、304本文+16導線=320各生成キー。全キー/空欄/露出/プレースホルダ/元言語対応とJARを照合。日韓要約だけ修正し英語+他19要約は意味を確認して維持。最終fallbackは英語 |
| 31 | conditional | 検索: 選択言語の論理順検索へNFC→locale lowercase→NFCを適用。現行7040キーと13純粋回帰で結合文字/Turkish I/İ/Arabic論理順を検証。変更JA/KO検索データも静的照合。新コードのゲーム内入力は未実施 |
| 32 | pass | RTL: アラビア語原文は論理順で検索。描画は既存実visual-order字形幅/グリフ検査を、全文/表示定義/クラス不変の条件で再利用。二重整形しない |
| 38, 39, 40, 41, 44, 45, 46, 52, 68, 71 | pass | 標準入口・クライアント導線: 標準W/Shift、ponder-ie/ieponder、章番号/名称/アイテム、nativeタグ/前後/戻るを維持。追加画面ボタン/特殊キー登録なし。不変43本番クラスと検索正規化以外のGuideNavigationソース一致に基づくnative公開factory/UI/履歴証拠で、物理キー押下は未実施 |
| 43 | conditional | クライアント専用: 空common entryとDist.CLIENT hooks、client-only mixins、独自registry/packet/servercommandなしを確認。専用サーバー起動とガイド未導入サーバーへの実接続は未検証 |
| 47, 48, 49, 74 | not_applicable | アドオン: 現行はIE本体20教材のみでaddon章/短文/任意依存は0。今回IEへの追加拡張は対象外。空章を作らず、将来のaddon導入/未導入動作を検証済みにしない |
| 50, 51 | pass | 版・ローダー: 現行対象はMC1.21.1/NeoForge/Java21。小さなPlatform橋、別出力/開発world、メタデータ/bytecode/mixin境界を照合。他版/loaderへの対応を主張しない |
| 53 | conditional | 共存: globalRedirect複製なし、own-scene guard/foreign所有復元と既存AE2 fixture共存を再利用。Prism全8ガイド同時起動は未検証 |
| 55 | pass | 構造検査: 20教材363node/140step/151calloutのID/参照/表示順/visibility、固定JARのitem/block/template、全locale対応を検査 |
| 59, 60 | pass | 権利・配布境界: LICENSE/THIRD_PARTY_NOTICES/MIT-Ponder/Apache本文とmetadataをJAR内確認。検証class/依存JAR/IE画像・模型・構造・手引書本文を同梱しない |
| 61, 62, 63, 64 | pass | 配置と旧コピー: 成功した0.1.6完成JARのSHAを配置先と照合。metadata確認済み旧0.1.5を除去し、suffixを問わない再帰ZIP読取で自身の現行1コピーだけを確認。開発run dirsにも自身の旧コピー0。他版/他MODを保持 |
| 65 | conditional | 依存と構成: 指定PrismのIE/Ponder/Flywheel既存版とSHAを前回と照合し再利用。MC1.21.1/NeoForge21.1.262 metadataは適合。Prism全MOD構成の同時起動/他担当addonの動作は未検証 |
| 67 | pass | 明示継続: 現行日英の作者文に継続マーカーは0。句の途中を自動分割せず、必要な場合のpaired…は既存54構造回帰/testCaptionUnitsで対応を保持 |
| 75 | pass | 派生形と元ガイド: 642実registry、独立408手引書ID/21群、W69/Shift568/和集合625（vanillaW3含む）、除外20を既存成功証拠と同じ定義/クラスへ結び付けた。任意成分の全組合せ試験は別 |

## 配置と未検証範囲

指定Prism（MC1.21.1/NeoForge21.1.262）に0.1.6を配置し、旧0.1.5を除去しました。配布元・配置先のSHAは一致し、他78ファイル（.index 32件）の変更・削除は0でした。今回の開始基準は79ファイルであり、以前の46/32を固定流用していません。

開発用run/runs/build/validation-runにも自MODの旧JARコピーは0でした。release成果物保存用build/libsは実行環境と区別し、過去の証拠に結び付く配布物を保持しています。Minecraftの利用には通常の起動/再起動が必要ですが、今回ユーザーのプロセスは操作していません。

- No new Minecraft startup, all22 redraw or all-scene replay was performed
- No physical W/Shift key press, full Prism eight-guide coexistence startup, dedicated-server startup or guide-absent server connection
- No exhaustive arbitrary component/configuration/accepted-version matrix or all-frame occlusion/visual review
- Fresh semantic second-scope samples are representative12keys/locale; full previous corpus review is reused by value equivalence, not native-speaker certification
- Manual resolves in item commands but is not advertised in current autocomplete; not a failed entrance

第二言語範囲の結果は [別記録](second-scope-compliance-20261030.md) に保持しています。
