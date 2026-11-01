# 第二言語範囲の確認（2026-10-30）

対象20言語・地域の全キーを原本・生成物・JARと構造照合しました。各言語12キーの代表意味レビューを新たに行い、残る既存意味レビューは0.1.5との値/表示単位/色/クラスの一致で再利用しています。今回すべての文を新規母語校閲したものではありません。

日英と同じ最終JAR0.1.6、SHA-256 `6110d88ccf942eef90f46fb1efa4fd529967d56639595f7995f3caf447a8af54` へ結び付けました。日英と規則1〜75の判定は [第一言語記録](jaen-compliance-20261030.md)、詳細キー/例/字形証拠は [JSON](second-scope-compliance-20261030.json) にあります。

| ロケール | 字幕キー/単位 | 短文キー/単位 | 代表判断 |
| --- | --- | --- | --- |
| hi_in | 140/399 | 124/227 | Fresh Asia10 independent content review found conditions/negation/order/names/tones intact; exact evidence attached |
| zh_hk | 140/343 | 124/219 | Fresh Asia10 independent content review found conditions/negation/order/names/tones intact; exact evidence attached |
| zh_tw | 140/342 | 124/219 | Fresh Asia10 independent content review found conditions/negation/order/names/tones intact; exact evidence attached |
| zh_cn | 140/283 | 124/219 | Fresh Asia10 independent content review found conditions/negation/order/names/tones intact; exact evidence attached |
| ru_ru | 140/359 | 124/219 | удобрение необязательно and наоборот увеличивают урон preserve negation and reversal; fixed IE terms retained |
| pt_br | 140/357 | 124/219 | Fertilizante é opcional; high-power damage retained; Segure/Esvazie retain Brazilian usage |
| pt_pt | 140/358 | 124/219 | fertilizante aceite/por predefinição and Mantém…premido/Esvazia-o retain European usage |
| es_es | 140/359 | 124/219 | Cultivar no exige fertilizante preserves optionality; aumentan el daño recibido preserves warning; native coil/pump names match |
| fr_fr | 140/359 | 124/220 | L’engrais reste facultatif and augmentent au contraire les dégâts subis preserve optionality and reversal |
| de_de | 140/362 | 124/221 | Optional cultivation fertilizer and high-power damage reversal retained; LV-Drahtspule/Flüssigkeitspumpe match fixed IE |
| cs_cz | 140/353 | 124/220 | hnojivo volitelné and naopak preserve conditions/reversal; Vyprázdněte/Podržte/vstupte retain polite plural |
| pl_pl | 140/433 | 124/222 | Nawóz jest opcjonalny; danger retained; English names+propozycja distinguish proposals without an IE PL resource |
| ms_my | 140/479 | 124/220 | Fresh Asia10 independent content review found conditions/negation/order/names/tones intact; exact evidence attached |
| ro_ro | 140/438 | 124/222 | Îngrășământul este opțional and măresc însă daunele preserve optionality/reversal; propunere marks names without an IE RO resource |
| ar_sa | 140/350 | 124/220 | Fresh Asia10 independent content review found conditions/negation/order/names/tones intact; exact evidence attached |
| id_id | 140/356 | 124/221 | Fresh Asia10 independent content review found conditions/negation/order/names/tones intact; exact evidence attached |
| ko_kr | 140/289 | 124/219 | Fresh Asia10 independent content review found conditions/negation/order/names/tones intact; exact evidence attached |
| vi_vn | 140/368 | 124/220 | Fresh Asia10 independent content review found conditions/negation/order/names/tones intact; exact evidence attached |
| tr_tr | 140/342 | 124/219 | Fresh Asia10 independent content review found conditions/negation/order/names/tones intact; exact evidence attached |
| it_it | 140/356 | 124/219 | è facoltativo per coltivare and aumentano invece i danni preserve optionality and reversal; mold and energy conditions retained |

全20ロケールとも本文304+導線16=320生成キー、20題名・20要約・6章名・6章説明・展開ヒント・操作条件/エラーを確認し、欠落/空欄/キー露出/プレースホルダ不整合は0でした。

台湾/香港、ブラジル/ポルトガルは既存の地域語と呼びかけを別に確認しました。差を作る目的の改稿はしていません。チェコ語の丁寧な複数形、英語固有名+翻訳案、肥料の任意性、低/高出力で逆になるFaraday条件、全6面の初期状態・切替順・出力面だけの送電を保持しています。マレー語等の異なる単位数には、その言語自身の役割配列を照合しました。

韓国語の金属プレス機要約だけは、コンベアをピストンと同じ向きにする誤解を除き、互いに搬送方向が同じコンベアという表現へ修正しました。他の19言語は値を変えていません。字幕/短文/章・操作やエラーの値・色・表示は全20言語とも前回と一致します。

アラビア語の新規確認は論理順原文と意味です。描画/字形/幅は不変入力に有効な既存の実visual-order検査を再利用し、新しい実行を装っていません。トルコ語I/İと結合文字は今回の13純粋回帰と7040キー検査で確認しました。7,039キーは旧検索文字列と一致し、アラビア語の園芸クローシュ1キーだけは検索用の結合記号順がNFCとなります。元の字幕やJAR表示文は変えていません。

新たなMinecraft起動・全22言語再描画は行わず、第一言語記録と同じ成功JAR・配置ハッシュ・旧コピー0に結び付けました。実キー押下、全8同時起動、サーバー接続、任意の成分/版の全組合せ等は未検証のままです。
