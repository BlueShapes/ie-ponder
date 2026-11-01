# Immersive Engineering Ponder Guide

Minecraft **1.21.1 / NeoForge** 向けの、Immersive Engineeringを学ぶクライアント用Ponderガイドです。教材本文・題名・要約・アイテム説明・章・操作文を共通の22言語に翻訳し、対象外の言語では英語へフォールバックします。

## 導入と使い方

検証構成は Java 21、NeoForge **21.1.256**、Immersive Engineering **12.4.2-194**、Ponder **1.0.87+mc1.21.1**、Flywheel **1.0.4** です。

1. Immersive Engineering、Ponderとその依存MODを導入します。Create本体やGuideMEは必要ありません。
2. `build/libs/immersive-engineering-ponder-guide-0.1.6-neoforge-1.21.1.jar` をクライアントの `mods` フォルダーへ入れます。
3. 対応アイテムにカーソルを合わせて **Wを長押し**するとPonderを開けます。エンジニアの手引書からは全コースを開けます。
4. クライアントコマンド `/ponder-ie` から、**Ponder標準のタグ一覧**を開けます。Immersive Engineering Ponder Guideの番号付きタグを選ぶと、標準のタグ画面にその章の関連アイテムが並びます。通常の設定画面やインベントリに独自ボタンは追加しません。
5. 標準タグ画面のアイテムを選ぶと、対応する教材から全コースを始められます。Ponder標準の前後移動・一時停止・巻き戻し・戻る操作を利用でき、戻ると起点のタグ画面・タグ一覧へ戻ります。

再生中の左上には、現在の教材のカテゴリーだけを標準タグとして表示します。タグから同じカテゴリーの他教材へ進めます。Wで開く部品アイテムにもカテゴリーを登録し、前後移動で対象アイテムとカテゴリーが切り替わります。

木材や木箱などの短い説明は、アイテムにカーソルを合わせて **Shift** を押すと表示されます。対象MODや他MODの既存ツールチップも保持します。

0.1.5では、固定版IEの手引書・レシピ・実アイテムを照合し、色付きシュートや足場、工具・素材など568種類へ124組のShift説明を用意しました。既存20教材のW対象は69種類です。完成機械11種類から対応する組立教材へ進め、カバー付きコンベアなどのデータ成分も、Ponderと戻る操作の間で保持します。対象別の入口・除外理由は [アイテム監査](docs/item-coverage-0.1.5.md) に記載しています。

| コマンド例 | 開く場所 |
| --- | --- |
| `/ponder-ie` | 標準タグ一覧 |
| `/ponder-ie 2` / `/ponder-ie power` / `/ponder-ie 電力` | 電力の先頭教材から全コース |
| `/ponder-ie metal_press` | 金属プレス機の教材から全コース |
| `/ponder-ie item capacitor_lv` | 低電圧キャパシタに対応する教材 |
| `/ponder-ie item immersiveengineering:capacitor_lv` | 同上。選択言語のアイテム名でも指定できます |
| `/ponder-ie search クレオソート` | 選択言語の題名・要約・字幕を検索し、最初の一致教材から全コース |

従来の `/ieponder` も同じ形式で利用できます。章名は選択言語・英語・安定した章IDを受け付けます。

## 教材

| 章 | レッスンIDと内容 |
| --- | --- |
| はじめに | `coke_oven` コークス炉、`alloy_kiln` 合金窯 |
| 電力 | `basic_wiring` 低圧配線と継電器、`capacitors` 面設定、`thermoelectric` 熱電発電機、`water_wheel` 水車とダイナモ、`diesel_generator` ディーゼル発電機 |
| 冶金 | `crude_blast_furnace` 高炉、`improved_blast_furnace` 改良型高炉、`arc_furnace` アーク炉 |
| 搬送 | `conveyors` コンベア、`fluid_pipes` 液体のパイプ、`item_router` アイテム分配器 |
| 加工 | `garden_cloche` 園芸クローシュ、`metal_press` 金属プレス機、`crusher` 工業用粉砕機、`squeezer` 工業用圧搾機、`fermenter` 工業用発酵槽、`refinery` 工業用精製機 |
| トラブルシューティング | `power_diagnostics` 電力が届かないとき |

20レッスン・140ステップです。章と教材の順序は従来の学習順を維持しています。独自の章ボタン・フィルター・検索カタログは廃止し、標準タグ一覧と標準タグ画面を利用します。本文検索はクライアントコマンドの `search` で利用できます。

教材は操作を促す文の連続を避け、構造・仕組み・状態・条件と結果を自然に説明します。停止時の確認や安全上の注意など、目的のある依頼は残します。全22言語で本文・要約・アイテム説明・章の説明を見直しています。現在の20教材はIE本体の内容で、追加アドオンの教材は含みません。

マルチブロックは、別途導入されたIEの構造NBTを実行時に読み、正確な材料・向き・層・形成位置を示します。模型は**形成前の建築配置**を保持し、ハンマー操作以降の説明は実ワールドで形成した装置の操作として示します。完成機械の稼働シミュレーターではありません。線や動くアイテムは経路を説明する演出で、実際の転送速度や生産量を表しません。レシピや数値が変更されたMODパックでは、その環境の設定を優先してください。

## ビルド

Java 21と、依存関係を取得できるネットワーク環境が必要です。

```powershell
.\gradlew.bat build
```

日英の題名・要約・字幕・アイテム説明は `src/main/resources/assets/ieponder/guide/`、UIの原文は `src/main/templates/lang/` で編集します。言語JSONは `generateGuideResources` が生成します。生成物を直接編集しないでください。

残り20言語の章・操作文は `src/main/translations/navigation/`、教材本文は `src/main/translations/content/` にあります。キー・プレースホルダー・原文と翻訳のレビュー記録を全22言語で検査します。IEに公式訳がないアイテム名には、英語名と仮訳の表示を添えています。

Ponder標準画面の操作文で不足している訳は `src/main/translations/native-ui/` で補います。既存の正常な依存側翻訳を再配布せず、言語リソースの合成で不足分と不正なプレースホルダーだけを補正します。Ponder全体のテキスト処理を置き換えるMixinは使いません。

```powershell
.\gradlew.bat compileJava generateGuideResources
python tools/check_navigation.py
python tools/check_translations.py
python tools/check_captions.py
python tools/check_text_tones.py
python tools/check_guide.py --ie-jar path/to/ImmersiveEngineering-1.21.1-12.4.2-194.jar
python tools/check_item_coverage.py --ie-jar path/to/ImmersiveEngineering-1.21.1-12.4.2-194.jar
python tools/test_item_coverage.py
```

後者は標準ライブラリだけで、配布版JARのブロック・アイテム・構造に対する参照、層の表示順、隠れた対象への注目、吹き出し、ステージ境界を検査します。実際のレジストリとブロックステートは、以下のクライアント検証で確認します。

字幕の原文と訳文では、改行で表示単位を明記します。原則は短く完結した1文です。どうしても長い文を継続する場合だけ、作者が自然な節を選び、前の末尾と次の先頭に `…` を付けます。文をフォント幅・行数・単語数で別の吹き出しへ切りません。1つの吹き出し内では自然に折り返し、全言語で最大3行・できれば2行に推敲します。同じステップの輪郭・材料表示・キーフレームを保ち、各表示単位の長さに応じた読む時間を用意します。

自作の解説・字幕・アイテム説明は、全22言語で通常の文末句点を付けません。小数・版番号・ID・略語などの点と、明示的な継続の `…` は保持します。`text_tones.json` に各言語・各表示単位の意図を指定し、通常は白、警告は薄赤、対処や工夫の助言は薄緑にします。Ponder標準の本文描画とパレットを使い、模型の輪郭色は別に扱います。

字幕枠は標準Ponderの描画を利用し、最小320×240 GUIでも180px幅を確保します。アラビア語の原文やコマンド文字列は論理順のまま保ち、Ponder標準の描画経路で一度だけ字形変換します。`testCaptionUnits` と `tools/check_captions.py` で、文境界、継続記号の対応、略語・小数・版番号・レジストリID、長い入力の内容保持を検査します。

## 描画と操作の検証

```powershell
.\tools\run-validation.ps1
# 字幕変更だけの検証（同じ共有排他を使用）
.\tools\run-validation.ps1 -Captions -LogPath build/caption-validation.log
# 本文・幅を変えず、配置だけを変更したときの代表3場面の検証
.\tools\run-validation.ps1 -Layout -LogPath build/caption-layout-validation.log
# 入口・現在カテゴリー・本文色の変更用（既存の22言語フォント証拠を再利用）
.\tools\run-validation.ps1 -Presentation -LogPath build/presentation-validation.log
# アイテム入口・データ成分・全22言語の短文説明に絞った検証
.\tools\run-validation.ps1 -Coverage -LogPath build/item-coverage-validation.log
```

共有ミューテックス `Local\PonderSeriesValidation` を取得してから、検証専用ワールドを `build/validation-run` に作ります。起動後はウィンドウを非表示にし、検査後に自動終了します。他プロジェクトの検証と同時にMinecraftを起動しません。

引数なしの実行検査は、コマンド→標準タグ一覧→標準タグ画面→Ponder再生、指定位置からのコース開始、標準の前後移動・戻る履歴、コマンド、通常のW導線が使うPonderファクトリーを含みます。Wの物理キー入力を偽装する補正は配布物へ追加しません。通常画面の自作ガイドボタンが0であり、既存の他ボタンを保持することも確認します。

引数なし、または `-Captions` の起動で全22言語を読み込み、キーの網羅、実リソーススタック上の訳、欠落グリフ、字幕行数と内容保持、アラビア語の字形変換後の幅、対象外言語の英語フォールバックを検査します。`-Captions` は未変更の画面遷移試験を省きます。`-Layout` は本文・フォント幅の検査結果を再利用できる配置変更用で、22言語の再読み込みを省きます。字幕用の両モードでは代表3場面を320×240・480×360・960×720 GUIで描画します。実際の実施範囲は [検証記録](docs/verification.md) に記載します。

結果は `build/validation-run/validation-result.json`、画像はその下の `screenshots/` に出力します。Gradleタスクは今回の結果の `passed: true` を必須とし、Minecraftの終了コードだけで成功と判定しません。検証クラスは配布JARには含みません。

## 実装と出典

- [シリーズ共通の制作方針・要件40と41](../docs/mod-guide-authoring.md#r040)
- [IE公式ソース](https://github.com/BluSunrize/ImmersiveEngineering/tree/1.21.1)、[検証対象リリース](https://modrinth.com/mod/immersiveengineering/version/12.4.2-194)
- [加工系教材の根拠](docs/processing-sources.md)
- [検証範囲と結果](docs/verification.md)
- [ライセンスと再利用元](THIRD_PARTY_NOTICES.md)

教材はPonderの仮想ワールド内で動作します。ガイド独自のブロック・アイテム・通信を追加せず、学習済みの記録はローカルに `config/ieponder-progress.json` として保存します。ガイドはサーバーへの導入を必要としない構成ですが、ガイド未導入サーバーへの実接続試験は別の検証項目です。IE自体の通常の導入条件は維持されます。

章編成のための独自画面はありません。Ponder 1.0.87の `PonderChapter` は未実装のため、実際に利用できる標準タグ登録を使います。標準タグ画面の選択を全コース内の開始位置へ接続するファクトリー補正と、コース位置・同一場面の履歴判定だけを行う `GuidePonderScreen` を維持しています。再生中の移動ボタンは標準実装です。

Ponder 1.0.87は前後移動で対象アイテムとタグを更新しないため、このガイドの場面だけを判定する同期を追加しています。カテゴリーが変わったときだけ標準ウィジェットを再生成します。他MODの場面へ移動した場合、まだこのガイドが設定した値だけを元へ戻し、他のガイドが先に更新した値を上書きしません。標準タグ画面と戻る操作の実装は変更しません。履歴では部品別コースと全教材コースを区別し、全教材コース同士も教材IDとコンパイル時の言語を照合します。

AE2 Ponder Guideなどと識別子や保存先を分離し、Ponder全体の字幕処理を置換するMixinは含めていません。このガイド内に限り、IEのチャンク確認と、水車などの遅延描画を仮想ワールドに合わせる補正があります。IEの描画設定は変更しません。同時導入の検証構成と実施範囲は[検証記録](docs/verification.md)に記載します。
