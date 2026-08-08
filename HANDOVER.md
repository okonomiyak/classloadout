# classloadout 引継書 (2026-08-04時点、更新版)

このドキュメントは開発を引き継ぐ人(未来の自分を含む)向けのスナップショット。
機能の使い方・設計思想は `README.md` / `README.ja.md` を正とする。ここには
「今どういう状態か」「何が検証済みで何が未検証か」「次に何をすべきか」を書く。

## プロジェクト基本情報

- パス: `C:\Users\tomip\program\java\classloadout`
- GitHub: `git@github-okonomiyak:okonomiyak/classloadout.git`(Public)
- 対象: Minecraft 1.20.1 + Forge 47.3.12(ModDevGradle legacyforge 2.0.141 / Gradle 8.8)
- **Gradle実行にはJDK21が必須**(`gradle.properties`の`org.gradle.java.home`で指定済み、既定Java25では動かない)
- ソフト依存: TACZ(`tacz`)、SuperbWarfare(`superbwarfare`)。`compat/`パッケージで`ModList.isLoaded`ガード、未導入でもクラッシュしない設計

## 現在のgit状態(★重要★)

**2026-08-08、v0.3.3としてコミット・push・リリース済み**(コミット`57cc56f`、タグ`v0.3.3`)。弾薬同期バグの修正・バージョン表示修正・CIビルド追加を含む。詳細は下記「弾薬状態が全員に同期されるバグの修正(2026-08-08)」節を参照。以後の変更は再びこのセクションで追跡すること。

このセッションはLinux機(`/home/iwa/projects/java/classloadout`)で実施。従来のWindows機(`C:\Users\tomip\program\java\classloadout`)とは別PC。**Claude Codeのメモリ(`~/.claude/projects/.../memory/`)はマシンごとに独立していてgit管理外のため、PCを跨ぐと自動では引き継がれない**——このHANDOVER.md(gitでpushされる)が唯一の確実な引継ぎ手段。別PCで作業を再開する際は、まずこのファイルを読むこと。

**⚠️未解決の判断待ち事項**: `v0.3.1`リリース(2026-08-08作成)は、後に誤りと判明した修正(`stripVolatileGunState`、根本原因ではなかった)を含んだままGitHub上に残っている。削除するか残すかユーザーに確認できておらず未対応。次回このリポジトリを触るセッションで確認すること。

## 動作確認の状況(2026-07-21時点)

- `gradlew build` / `gradlew runServer`: 変更のたびに毎回成功・`Done`到達を確認済み(エラー・例外なし)
- `gradlew runClient`でのGUI目視確認はユーザー自身に都度お願いしている(Claude側にMinecraftクライアント操作手段が無いため)。**ユーザーからの確認結果**:
  - 設置型パックの3Dモデル描画(テクスチャ・位置) → **OK**
  - TACZアイテム空気表示バグの修正 → **OK**(「正常動作OK」の返答あり)
  - TACZ弾薬(玉)のGUI選択対応 → **OK**(同上の返答に含まれる、直前の作業への確認)
  - 弾薬付与機能(`AmmoGrantScreen`右クリック→保存→リスポーンでインベントリに入るところまでの一巡)→ **明示的な一巡テストはまだ報告されていない**。上記「OK」が対象に含まれているかは不明瞭なので、次回このスクリーンの動作を触ったときに個別に確認すること

## 今回のセッションでやったこと(時系列)

詳細な経緯・ハマりどころは `classloadout-devlog-2026-07-21.md` に全て記録済み。要点のみ:

### 1. 設置型パックの3Dモデル化

ユーザーがBlockbench製3Dモデル(`mainbox`+`cover`+`hand`の3パーツ)とアイテムアイコンPNGを用意。

- **最初に試して失敗した方式**: `ModelEvent.RegisterAdditional`でエンティティ専用の独立モデルを登録し`ItemRenderer.render(..., BakedModel)`で直接描画。3つの罠にハマった:
  1. `ItemStack.EMPTY`を渡すと`ItemRenderer.render()`内部の`isEmpty()`ガードで描画が丸ごとスキップされ透明になる
  2. `ItemRenderer.render()`が内部で無条件に`(-0.5,-0.5,-0.5)`平行移動するため、自前でも同じ移動を足すと二重ズレ
  3. **Forge 47.3.12では`TextureStitchEvent.Pre`が廃止されており**、独立モデルのテクスチャが自動でブロックアトラスに乗らない(`atlases/blocks.json`を自作しても解消せず、原因不明のまま撤退)
- **最終的に採用した方式**: アイテム自身の登録モデル(`models/item/health_pack.json`/`ammo_pack.json`)を3Dボックスに置き換え、`ItemRenderer.renderStatic()`でそのまま描画(squadtpの`RespawnBeaconRenderer`と同型)。テクスチャは`textures/item/health_pack/{mainbox,cover,hand}.png`配下に配置(バニラの`item/`ディレクトリ丸ごとアトラス収集の恩恵を受けられるため、追加設定不要)。**ユーザー確認OK。**
- **教訓**: エンティティ専用の3Dモデルが欲しい場合でも、「アイテムに紐付かない独立モデル」より「アイテム自身のモデルを3D化してrenderStaticで描画」の方が確実。副作用としてインベントリ/ホットバーのアイコンも同じ3Dモデル表示になる(今回は許容)。
- 旧`textures/item/health_pack.png`・`ammo_pack.png`(フラットアイコン版)は**現在未使用**(アイテムモデルが3Dボックスを直接参照するようになったため)。削除するかどうかは未判断、そのまま残してある。

### 2. `packLifetimeSeconds`のデフォルト変更

ユーザー指示「boxは20秒で消えるようにして」。`Config.java`のデフォルト値と、既存テストワールド2箇所(`run/saves/New World/`、`run-server/world/`)のserverconfig tomlも合わせて書き換え済み。

### 3. ロードアウト経由の弾薬付与機能(新機能)

「ロードアウト選択でインベントリに弾薬を入れれるようにしたい」との要望。設計はAskUserQuestionで確認:
- 新スロットは作らず、既存のホワイトリストエントリ(スロット+アイテム)に「弾薬アイテム+個数」を追加で紐付ける方式
- 個数はOPがホワイトリスト登録時にアイテムごとに指定

実装:
- `loadout/AmmoGrant.java`(新規record): `(ResourceLocation ammoItem, int count)`
- `LoadoutManager`: `ammoGrants: Map<LoadoutSlot, Map<ResourceLocation, AmmoGrant>>`を追加。NBT永続化・`LoadoutSyncPacket`への同期・`removeFromWhitelist`時の連動削除まで対応済み
- コマンド: `/class whitelist ammo <slot> <item> <ammoItem> <count>`(count 0で解除)
- `ServerEvents.onPlayerRespawn`: 装備時に対応する`AmmoGrant`があれば`player.getInventory().add()`で付与(**加算式**、リスポームごとに前回分を消す処理はしていない)。入りきらない分はドロップ
- GUI: `WhitelistEditorScreen`で**右クリック**すると`AmmoGrantScreen`(新規)が開く。未ホワイトリストなら自動登録してから開く。弾薬付与済みのセルはオレンジの角マーカーで表示
- **一巡の実地テストはまだ未実施**(下記「次にやるべきこと」参照)

### 4. TACZアイテムが軒並み空気表示になるバグ修正

ユーザー報告「GUIがtaczのmodern gun以外空気になっている」(ホワイトリストエディタのグリッドで、TACZの`modern_kinetic_gun`以外**ほぼ全アイテム**が空気=透明表示)。

原因: `TaczCompat.buildGunStack(id)`が、渡された`id`が実際に有効なTACZ GunIdかどうかを`isGunId()`でチェックせず、**常に**`GunItemBuilder.create().setId(id).build()`を呼んでいた。そのため`ItemResolver.resolve()`はどんなアイテムID(バニラ・SuperbWarfare・classloadout自作・TACZの他の銃ID含む)に対しても最初にTACZ経由のビルドを試み、無効なGunIdを持つ壊れたItemStackを返す→それが空気のように描画される、という壊れ方。`tacz:modern_kinetic_gun`(汎用アイテム自体のID)だけがたまたま有効に見えるスタックを返していたため単独で表示されていた。

修正: `buildGunStack`の先頭で`TaczGunResolver.isGunId(id)`を確認し、無効なら`null`を返して`ItemResolver`の通常レジストリ参照にフォールバックするよう変更。**ユーザー確認OK。**

このバグは今回のセッションで新規に混入したものではなく、TACZ銃選択機能を実装した際(前回セッション)から潜在していたと思われる(ItemResolverの実装当初からガード漏れ)。

### 5. TACZ弾薬(玉)のGUI選択対応(新機能)

ユーザー「taczの玉をGUIから出せるようにして」。調査の結果、TACZの弾薬(9mm/12ゲージ等)も銃と全く同じデータ駆動方式(`AmmoId`NBTタグを持つ単一汎用アイテム、`AmmoItemBuilder`/`TimelessAPI.getCommonAmmoIndex`/`getAllCommonAmmoIndex`)と判明。

- `compat/tacz/TaczAmmoResolver.java`(新規): `TaczGunResolver`と同型。`isAmmoId`/`buildStack`/`allAmmoIds`
- `TaczCompat`に`isAmmoId`/`buildAmmoStack`(直前のバグ教訓を活かし`isAmmoId`ガード付きで実装)/`allAmmoIds`を追加
- `ItemResolver.resolve()`: 銃→弾薬→通常レジストリの順でフォールバック
- `ItemCatalog.all()`: `TaczCompat.allAmmoIds()`もプールに追加(ホワイトリスト全スロット・プリセットエディタ・弾薬付与ポップアップの弾薬選択、全てのアイテム選択グリッドで個別弾薬IDが選べるようになる)
- **ハマりかけた点**: `AmmoItemBuilder`は`setCount()`を呼ばないと内部countがデフォルト0のままになり、ItemStackが空扱いされて描画されない罠があった。直前の空気バグ修正の教訓を活かし、`TaczAmmoResolver.buildStack`で最初から`setCount(1)`を明示して回避(実際に踏む前に気づけた)。

**ユーザー確認OK。**

## ammo_pack(設置・投擲とも)がTACZで全く弾薬補給しないバグの修正(2026-07-24)

ユーザー報告「弾薬箱が動作しません」(ammo_pack双方、設置/投擲自体はできるが弾薬が全く補給されない)。

**原因**: `TaczAmmoResupplier`は「`IGun.useDummyAmmo()`がtrueの銃だけ内部弾数を補充」という実装だったが、`useDummyAmmo()`は銃アイテムに`DummyAmmo`というNBTタグが実際に付いている場合のみtrueになる(`GunItemDataAccessor`のデフォルト実装を`javap -c`で逆アセンブルして確認)。TACZのAK47等の標準銃はこのタグを持たず、`reload.type`が`"magazine"`(弾倉に実弾薬アイテムを消費してチャージする方式)であり、`useDummyAmmo()`はおろか前回セッションで確認した`useInventoryAmmo()`(`reload.type == "inventory"`)にも該当しない第3のケースだった。つまり標準銃はどの分岐にも入らず、`resupplyHand`が何もせず終了していた。

**修正**: `useDummyAmmo()`がfalseの銃は、`TimelessAPI.getCommonGunIndex(gunId).getGunData().getAmmoId()`でその銃に紐づく実弾薬アイテムID(例: AK47なら`tacz:762x39`、`ammo_data.json`の`"ammo"`フィールドと同じもの)を解決し、`TaczAmmoResolver.buildStack()`でItemStackを作って`player.getInventory().add()`で直接付与する方式に変更(`ServerEvents.grantAmmo`と同じ、最大スタックで分割・入りきらない分はドロップのパターン)。これで"magazine"方式・"inventory"方式のどちらの銃でも、弾薬パックの近くにいれば実弾薬アイテムが手に入るようになった。

- `compat/tacz/TaczAmmoResupplier.java`のみ変更。`TaczAmmoResolver`(既存)をそのまま再利用、新規クラスは追加していない
- `gradlew build`成功
- **実地確認は未実施**(TACZの標準銃を持った状態でammo_pack近くに立ち、弾薬アイテムがインベントリに増えるか、および稀なdummy-ammo方式の銃(該当する銃があれば)で内部弾数が増えるかの両方を確認する必要あり)

## HP1000の遮蔽物エンティティ追加(2026-08-04)

ユーザー指示「HP1000くらいの遮蔽物エンティティを作成してください」。設計はAskUserQuestionで2点確認:
- 寿命: **一定時間で自動消滅**(`coverLifetimeSeconds`、既定600秒=10分。補給パックより意図的にずっと長い——消耗品ではなく防御用構造物のため)
- 形状: **低い壁(しゃがんで隠れる用)**(hitbox `1.0 x 1.25`、見た目はバニラ木材テクスチャのプレースホルダー低い壁ボックス)

実装(新規パッケージ`cover/`、既存の`resupply/AbstractResupplyPackEntity`と同型の「`PathfinderMob`+noAi」パターンを踏襲):
- `cover/CoverEntity.java`: HP1000は**config値ではなくハードコード定数**(`EntityAttributeCreationEvent`はサーバーconfigロードより前に発火するため、`Config.get()`はこの時点では読めない——既存の補給パックのHP1.0ハードコードと同じ理由・同じ教訓)。効果適用ロジックは無し、寿命経過で`discard()`するだけ
- `cover/CoverPlacerItem.java`: 右クリック設置、`cover/CoverRegistry.java`(新規、補給パックの`ResupplyPackRegistry`と同型・別枠)で`maxActiveCoversPerPlayer`を管理
- `friendlyOnlyDestroy`は既存の共通configをそのまま再利用(遮蔽物専用の別フラグは作っていない)
- 描画: 既存`client/ResupplyPackRenderer<T>`のジェネリック境界を`AbstractResupplyPackEntity`から`Entity`へ緩和し、`CoverEntity`にもそのまま再利用(「エンティティ自身の登録アイテムモデルを`renderStatic`で描画」という仕組み自体は元々汎用的だったため)
- アイテムモデル`models/item/cover.json`: Blockbench製アセットがまだ無いため、**バニラの`minecraft:block/oak_planks`テクスチャを使った低い壁ボックス**をプレースホルダーとして自作(from/to `[0,0,6]`〜`[16,12,10]`、幅1×奥行0.25×高さ0.75ブロック相当)。ユーザーが専用3Dモデルを用意したら`health_pack.json`/`ammo_pack.json`と同じ手順で差し替え予定
- Config新設: `cover.coverLifetimeSeconds`(既定600、5〜7200)、`cover.maxActiveCoversPerPlayer`(既定2、1〜20)
- lang: `item.classloadout.cover`(「遮蔽物」/"Cover")、`classloadout.msg.cover_limit_reached`
- README.md/README.ja.mdに新セクション「遮蔽物」/「Cover」・config一覧・2人プレイテスト手順の10番目を追記
- `gradlew build`成功、`gradlew runServer`で新config値(`cover.*`)が正しくデフォルト生成され`Done`まで到達することを確認済み(例外なし)
- **実地確認(GUI操作)は未実施**: `/class whitelist`で`gadget`に`classloadout:cover`を登録→ロードアウト割当→設置→HP1000で簡単に壊れないこと→`friendlyOnlyDestroy`の効き目→`coverLifetimeSeconds`後の自然消滅→`maxActiveCoversPerPlayer`超過時の設置拒否、の一巡は次回確認が必要
- ホワイトリスト/アイテム選択グリッドへの追加作業は不要だった(`ItemCatalog`が`classloadout`名前空間の全登録アイテムを自動列挙するため、新規アイテムは登録するだけで自動的にピッカーに出現する)

## ロードアウトステーションブロックの発見・ドキュメント化(2026-08-04)

ユーザー指示「ロードアウト即時に変更ができるブロックを追加して」を受けて実装に着手する前に確認したところ、`classloadout:loadout_station`ブロック(`ModRegistry.LOADOUT_STATION`/`LOADOUT_STATION_ITEM`)と、それを右クリックすると死亡画面の「ロードアウト」ボタンと全く同じ`LoadoutScreen`を開くクライアント側ハンドラ(`ClientEvents.onRightClickLoadoutStation`)が**既にコード上に完全実装済み**(blockstate/block model/item model/loot table/lang keyもすべて揃っていた)であることが判明した。

**ただしこの実装はHANDOVER.md・README両方に一切記載が無かった**(いつ誰が作ったか経緯不明——おそらく2026-07-21以前の未記録セッションの産物、もしくはそれ以降のセッションでドキュメント更新が漏れたもの)。中身を確認した限り設計・実装ともに健全(死亡画面ボタンと同じ`LoadoutScreen`を開くだけの完全クライアント完結処理、権限チェック無しの意図的なセルフサービス仕様)だったため、**再実装はせず**、動作確認(`gradlew build`成功)の上でREADME.md/README.ja.mdに新セクション「ロードアウトステーション」/「Loadout Station」・イントロ文言・2人プレイテスト手順の11番目を追記してドキュメント化した。

- クラフトレシピは無し(クリエイティブタブ「建築ブロック」から入手、またはOPが`/give`で配布する運用)
- **GUI操作を伴う実地確認(設置→右クリック→ロードアウト画面が開き死なずに変更できるか)はまだ未実施**——次回`runClient`で確認が必要
- **教訓**: このMod開発では「今回のセッションでやったこと」を毎回HANDOVER.mdに書く運用だが、今回のように**書き漏れがあると次のセッションで車輪の再発明をしかけるリスクがある**。今後、ユーザーから新機能を依頼された際は実装前に必ず`grep`等で類似機能が既に存在しないか確認するのが安全(今回はこの確認によって無駄な再実装を回避できた)。

## ロードアウトの即時装備化(2026-08-04)

ロードアウトステーションを発見・ドキュメント化した直後、ユーザーから「いや死ななくてもロードアウトが変更できるようにしてほしい」と指摘。従来の実装は`/class assign`/`select`/`clear`が**ロードアウトデータを保存するだけ**で、実際のホットバーへの反映は`ServerEvents.onPlayerRespawn`(リスポーン時)でしか起きていなかった——つまりロードアウトステーションを設置してGUIで変更しても、実際に死んでリスポーンするまで手持ちの装備は変わらないという、ステーションの存在意義を損なう欠陥があった。

**修正**: `ServerEvents.equipLoadout(ServerPlayer)`(新規public、ホットバー0〜4番への上書きのみ担当)を切り出し、`ClassCommand`の`assign`/`select`/`clear`各ハンドラの成功時に直接呼ぶことで、コマンドが通った瞬間に(死亡・リスポーン不要で)装備が反映されるようにした。

**弾薬付与は意図的にリスポーン限定のまま**(重要な設計判断): 即時装備に弾薬付与のロジックまで含めてしまうと、生存中に同じアイテムを`/class assign`で何度も割り当て直すだけで無コスト・無制限に弾薬を稼げる抜け道になってしまう(死亡という自然なコストが無くなるため)。これを避けるため`equipLoadout`はホットバーの中身を差し替えるだけに留め、弾薬付与ループ(`grantAmmo`)は従来通り`onPlayerRespawn`側だけに残した(`equipLoadout`が装備した5スロットの中身を`ResourceLocation[]`として返し、`onPlayerRespawn`がそれを受け取って弾薬付与を続けて行う2段構成)。

- `README.md`/`README.ja.md`のGUI節・ロードアウトステーション節・2人プレイテスト手順を更新(即時装備の挙動、弾薬付与はリスポーン限定である旨を明記)
- `gradlew build`成功、`gradlew runServer`で例外なく`Done`到達確認済み
- **実地確認は未実施**: `/class assign`等を生存中に実行して即座にホットバーが変わること、弾薬付与付きアイテムを生存中に繰り返し割り当てても弾薬が増えない(リスポーン時のみ増える)ことの両方をGUI操作で確認する必要あり

## 死亡時インベントリクリア+除外アイテム機能の追加(2026-08-05)

ユーザー指示「死ぬたびにインベントリをクリアし、でも指定したアイテムはクリアから除外できるようにしたい」。AskUserQuestionで2点確認: 除外アイテムの登録はGUIエディタも作る、クリア対象は防具・オフハンド含む全インベントリ。

**設計判断**: `keepInventory`ゲームルールが有効な運用(死んでもアイテムを失わない)を前提に、Mod側で「死ぬたびに(除外アイテムを除いて)手動でインベントリをリセットする」機能として実装。`keepInventory`が無効な場合、バニラの通常のドロップ処理がこのMod自身のリスポーン処理より先に走ってしまい、除外指定の有無に関わらずアイテムを失う(地面にドロップされる)ため、除外リストは実質意味を持たない——この点はREADMEに明記した。

実装:
- `Config.CLEAR_INVENTORY_ON_DEATH`(`death.clearInventoryOnDeath`、既定true)を新設
- `LoadoutManager`に4つ目の管理対象として`protectedItems: Set<ResourceLocation>`を追加(ホワイトリストと同じ永続化・同期パターン、ただしスロット別ではなく単一のグローバルセット)。基本アイテム種別で一致判定(NBT/個数は無視)
- コマンド: `/class protect`(エディタを開く)、`/class protect add/remove <item>`(OP限定)
- GUI: `ProtectedItemsEditorScreen`(新規、`WhitelistEditorScreen`と同型のトグルグリッドだが、スロットタブ無し・弾薬付与ポップアップ無しの簡略版)。プリセットエディタ右上に「除外アイテム」ボタンを追加(ホワイトリストボタンの隣)
- **`itemVariants`(OP登録済みの手持ちアイテムNBT込みバリアント)は除外アイテムの選択候補から意図的に除外**: 除外判定はインベントリ内の実スタックの基本アイテム種別で行うため、バリアントの合成ID(`classloadout:variant_<uuid>`)は永久にマッチしない死んだトグルになってしまうため
- `ServerEvents.onPlayerRespawn`: `equipLoadout`の直前に`clearInventoryExceptProtected`(新規private)を実行。`player.getInventory().items`/`armor`/`offhand`の3つのNonNullListを走査し、`ForgeRegistries.ITEMS.getKey(stack.getItem())`が`protectedItems`に無ければ`ItemStack.EMPTY`に置換
- **即時装備機能(`ClassCommand`のassign/select/clear)には一切絡めていない**: クリアはあくまで「死んでリスポーンした時」だけに限定(`onPlayerRespawn`内のみ)。生存中に`/class assign`等でロードアウトを1スロットだけ変更しただけでインベントリ全体が消し飛ぶような事故は起きない設計
- 「ロードアウトに一度も触れていないプレイヤーは放置」という既存の慣例をクリアにも適用(`manager.getPersonalLoadout(uuid) == null`なら即return)
- README.md/README.ja.mdに新セクション「死亡時の挙動」/「Death Behavior」、コマンド表・GUI節・config一覧・design notes・2人プレイテスト手順(12番目)を追記
- `gradlew build`成功、`gradlew runServer`で`death.clearInventoryOnDeath`のデフォルト値生成含め例外なく`Done`到達確認済み
- **実地確認は未実施**: `keepInventory true`環境で、除外指定していないアイテムが死亡でクリアされること・除外指定したアイテムは残ること・ロードアウトの5アイテムは通常通り戻ることの一巡をGUI操作で確認する必要あり

## スポーンキット機能の追加(2026-08-06)

ユーザー指示「リスポーン時に必ず配布されるものを指定できるようにして」。個人ロードアウト(5スロット・ホワイトリスト制・自己割当)とは別軸の、**OPが指定したアイテム/個数のペアを全プレイヤーに無条件でリスポーンのたびに配布する**新機能。ロードアウトを一度も触っていないプレイヤーにも適用される点が、他の全機能(装備・インベントリクリア)の「一度も触れていないプレイヤーは放置」という既存の慣例との明確な違い。

実装:
- `LoadoutManager`に5つ目の管理対象として`spawnKit: Map<ResourceLocation, Integer>`を追加(ホワイトリスト等と同じ永続化・同期パターン)
- コマンド: `/class spawnkit`(エディタを開く)、`/class spawnkit add <item> <count>`(count 0で削除)、`/class spawnkit remove <item>`
- GUI: `SpawnKitEditorScreen`(`ProtectedItemsEditorScreen`と同型の単一グリッド、ただし個数を持つため左クリックで個数1として追加/削除・右クリックで`SpawnKitCountScreen`ポップアップを開いて正確な個数を指定、セル角に個数を表示、青枠でマーク)。プリセットエディタに3つ目のボタンとして追加
- **除外アイテムと異なり、item variants(OP登録済み手持ちアイテムバリアント)はこの機能では選択候補から除外していない**——スポーンキットの配布は`ItemResolver.resolve`経由で実アイテムを組み立てる(弾薬付与と同じ仕組み)ため、除外アイテムのような「実インベントリスタックとの基本型一致判定」という制約が無く、バリアントIDでも問題なく機能するため
- `ServerEvents`: `grantAmmo`が内部で使っていた「アイテムIDを解決してスタック分割しながらgiveOrDropする」ロジックを`giveItem(player, itemId, count, manager)`として汎用化・共有。新規`grantSpawnKit`はこれを`spawnKit`の全エントリに対して呼ぶだけ
- `onPlayerRespawn`の構造を変更: 「ロードアウトを一度も触れていないプレイヤーは放置」のガードを、クリア+装備+弾薬付与のブロックだけに限定するよう`if`で囲み直し、`grantSpawnKit`はそのガードの**外側**(常に実行)に配置。これによりスポーンキットだけは`/class`を一度も触っていない新規プレイヤーにも配布される
- スポーンキットの配布はインベントリクリア+ロードアウト装備の**後**に行われるため、`clearInventoryOnDeath`が有効でもスポーンキットのアイテムはそのリスポーンで必ず残る(除外アイテムリストへの二重登録は不要)
- README.md/README.ja.mdに新セクション「スポーンキット」/「Spawn Kit」、コマンド表・GUI節・design notes・2人プレイテスト手順(13番目)を追記
- `gradlew build`成功、`gradlew runServer`で例外なく`Done`到達確認済み
- **実地確認は未実施**: OPがスポーンキットにアイテムを追加→ロードアウト未設定のプレイヤーも含めてリスポーンで配布されること、個数指定が正しく効くこと、既存のアイテムにスタックすること、を`runClient`で確認する必要あり

## ハンマー範囲破壊機能の追加(2026-08-07)

ユーザー指示「ハンマーで爆破のように指定されたブロックだけを破壊できるようにして」→「ハンマーはSuperBのを使って」(SuperbWarfareの既存ハンマーアイテムを使う指示)。実装前に`javap`でSuperbWarfareの`HammerItem`(`com.atsuishio.superbwarfare.item.weapon.HammerItem`、`SwordItem`継承)を調査したところ、**採掘・範囲破壊ロジックは一切無い**(純粋な近接武器、bettercombat連携のみ)ことを確認。つまり範囲破壊機能はclassloadout側で新規実装する必要があった。

**重要な発見**: SuperbWarfareのハンマー各種(`hammer`/`golden_hammer`/`steel_hammer`/`diamond_hammer`/`cemented_carbide_hammer`/`netherite_hammer`)は全て汎用アイテムタグ`#forge:tools/hammer`(データ駆動、SuperbWarfare本体が定義)に登録済み。このタグは**データパック由来でJavaクラス参照が不要**なため、`compat/SuperbWarfareCompat`のような`ModList.isLoaded`ガード無しで安全に参照できる(SuperbWarfare未導入ならタグが単に空になるだけ)。既存のTACZ/SuperbWarfare連携とは異なる、より軽量な連携方法。

設計(ユーザーへの確認は省略、既存パターンから妥当な既定値で実装):
- OP-curated「ハンマー範囲破壊の対象ブロック」ホワイトリスト(除外アイテムと同型、ブロック版)
- 破壊したブロック自体もホワイトリスト登録されている必要がある(でなければ通常のハンマーとして振る舞う)
- 範囲は破壊地点中心の立方体(`hammerAoeRadius`、既定1=3x3x3)——「爆破のように」という表現に合わせて方向性の無い対称な範囲にした
- ドロップはハンマー本体のエンチャント(幸運/シルクタッチ等)を反映(`Level.destroyBlock(pos, true, player)`がplayerの手持ちアイテムを見て計算するため自然に実現)

実装:
- `Config.HAMMER_AOE_RADIUS`(`hammer.hammerAoeRadius`、既定1、0〜4、0で無効化)
- `LoadoutManager`に6つ目の管理対象として`hammerBlocks: Set<ResourceLocation>`(除外アイテムと全く同じ永続化・同期パターン、ただしブロックの登録名)
- コマンド: `/class hammerblocks`(エディタを開く)、`/class hammerblocks add/remove <block>`
- GUI: `client/gui/BlockCatalog.java`(新規、`ItemCatalog`のブロック版——アイテムと違い全namespace許可、`block.asItem() != AIR`のものだけ収録)+ `HammerBlocksEditorScreen`(`ProtectedItemsEditorScreen`と同型、赤枠でマーク)。プリセットエディタのナビゲーションボタンが4つ目に増えたため、パネル幅を420→520・ボタン幅90→84に調整
- `ServerEvents.onBlockBreak`(新規`@SubscribeEvent`、`BlockEvent.BreakEvent`購読): プレイヤーのメインハンドが`#forge:tools/hammer`タグ持ち、かつ破壊したブロックがホワイトリスト登録済みなら、半径内の他のホワイトリスト登録ブロックも`serverLevel.destroyBlock(pos, true, player)`で破壊。`Level.destroyBlock()`は`BlockEvent.BreakEvent`を再発火しないため、無限連鎖のリスクは無い
- README.md/README.ja.mdに新セクション「ハンマー範囲破壊」/「Hammer Area-of-Effect」、コマンド表・GUI節・config一覧・design notes・2人プレイテスト手順(14番目)を追記
- `gradlew build`成功、`gradlew runServer`で例外なく`Done`到達確認済み
- **実地確認は未実施**: SuperbWarfare導入環境で、ホワイトリスト登録ブロックをハンマーで破壊した際に範囲内の登録ブロックも一緒に壊れること・未登録ブロックは無傷であること・登録ブロックを起点にしない限り何も起きないことをGUI操作で確認する必要あり

**余談**: 同じ会話内で「エンティティのはしご」(ラダーをエンティティとして実装できないか)も依頼されたが、Minecraftのクライミング判定がブロック状態(BlockState)ベースであり、エンティティ単体に同じ判定を持たせるのは技術的にかなり無理があること(プレイヤー移動処理を毎tick自前で書き換える必要がある等)を説明したところ、ユーザーが「諦めます」と撤回。実装はしていない。

## 遮蔽物: 誰でも破壊可能化+HPのconfig化(2026-08-07)

ユーザー指示「Coverを全員が壊せるようにして あとHPをconfigから変えられるようにして」。

1. **誰でも破壊可能化**: `CoverEntity.hurt()`のオーバーライド(`friendlyOnlyDestroy`チェック)を丸ごと削除。デフォルトの`Mob`/`LivingEntity`の`hurt()`挙動(誰の攻撃でもダメージが通る)にフォールバックするだけ。遮蔽物は「個人の所有物」ではなく「戦場の中立な地形」という位置づけなので、補給パックと違って友軍制限を適用しない、という設計判断。

2. **HPのconfig化**: 従来「`EntityAttributeCreationEvent`はサーバーconfigロードより前に発火するためConfig値が読めない」という理由でHP1000をハードコードしていたが、**この制約は`createAttributes()`(mod読み込み時に一度だけ呼ばれるテンプレート)にのみ当てはまり、エンティティの**コンストラクタ**(実際にゲーム中でインスタンスが作られる際、config読み込み後)には当てはまらない**ことに気づき、そちらで上書きする方式に変更。`CoverEntity`のコンストラクタで`this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(Config.COVER_MAX_HEALTH.get())`+`this.setHealth(...)`を実行。`createAttributes()`側のテンプレート値(1000)は単なる初期値で、コンストラクタで即座に上書きされるため実質使われない。
   - `Config.COVER_MAX_HEALTH`(`cover.coverMaxHealth`、既定1000、範囲1〜1024)を新設。**上限1024はバニラの`max_health`属性自体が持つハード上限**(全エンティティ共通、`RangedAttribute`で1.0〜1024.0と定義)のため、それより大きい値を設定してもクランプされてしまう——config側でも同じ上限にしておくことで「設定したのに反映されない」という混乱を防いだ
   - 既存の設置済み遮蔽物(NBTに保存済み)は、バニラの`Attributes`タグ経由で設置時点のHPがそのまま復元されるため、**config変更は新規設置分にのみ反映**され、既存のものは遡って変わらない(意図通りの挙動、config変更で世界の状態が突然変わらないようにするため)
- README.md/README.ja.mdの「遮蔽物」節・イントロ文言・config一覧・2人プレイテスト手順(10番目)を更新
- `gradlew build`成功、`gradlew runServer`で`cover.coverMaxHealth`のデフォルト値生成含め例外なく`Done`到達確認済み
- **実地確認は未実施**: 遮蔽物を設置した状態でOP以外のプレイヤー(設置者以外)が攻撃しても通ること、`coverMaxHealth`を変更して新規設置分にのみ反映されることをGUI操作で確認する必要あり

## 補給パック(health_pack/ammo_pack)も誰でも破壊可能化+friendlyOnlyDestroy設定を完全撤去(2026-08-07)

ユーザー指示「他のやつも他人が壊せるように」(直前のCoverの変更に続き、補給パックにも同じ変更を、という指示)。

- `AbstractResupplyPackEntity.hurt()`のオーバーライド(`friendlyOnlyDestroy`チェック)を丸ごと削除。Coverと全く同じ変更(health_pack/ammo_packはこのクラスを継承しているため1ファイルの変更で両方に反映)
- この時点で`Config.FRIENDLY_ONLY_DESTROY`(`resupply.friendlyOnlyDestroy`)を参照するコードが完全に無くなった(Coverも補給パックも使わなくなったため)ので、**config自体も削除**(未使用configを残さない方針)。既存の`serverconfig`ファイルに残る`friendlyOnlyDestroy = true`の行は起動時に無視されるだけで実害は無い
- `CoverEntity.java`のjavadocコメント内に残っていた`Config.FRIENDLY_ONLY_DESTROY`への言及も削除済みの設定を指さないよう修正
- README.md/README.ja.mdの補給パック節・遮蔽物節・config一覧・design notes・2人プレイテスト手順(7番目)から`friendlyOnlyDestroy`関連の記述を全て削除、「誰でも破壊できる」という記述に統一
- HANDOVER.mdの「既知の制限事項」から該当項目を削除
- これで**このMod内で設置される全てのプロップ(補給パック・投擲パック・遮蔽物)が友軍制限なしで誰でも破壊できる**、という一貫した設計になった
- `gradlew build`成功、`gradlew runServer`で例外なく`Done`到達確認済み
- **実地確認は未実施**: 補給パックを設置者以外が攻撃して壊せることをGUI操作で確認する必要あり

## 弾薬状態が全員に同期されるバグの修正(2026-08-08)

ユーザー報告「手持ちを登録を行うとロードアウトの銃をNBTごとコピーしてるせいでUUIDが重複して全員の弾薬の状態が同期されてる」。ユーザー自身の見立ては「症状からの推測」(実際にNBT上でUUIDタグを確認したわけではない)とのこと。

**調査で遠回りした点(教訓として記録)**: 最初にTACZ本体(`tacz-1.20.1-1.1.8-hotfix.jar`)を`javap`で逆コンパイルし、ガンや弾薬のNBTスキーマにUUIDタグが存在するか徹底的に調査した(`GunItemDataAccessor`/`AmmoBoxItemDataAccessor`等)。結果、TACZ本体にはガン/弾薬インスタンスを識別するUUIDタグは一切無いことを確認——つまりユーザーの「UUID」という言葉は文字通りのNBTタグではなく症状からの比喩的表現だった。この調査自体は無駄ではなかった(TACZのNBTスキーマの正確な理解は得られた)が、最初にユーザーに「どこで確認したか」を聞いていればもっと早く本題に入れた。

この調査中に立てた**誤った仮説**: 「`LoadoutManager.addHeldItemToWhitelist`/`registerItemVariant`が`held.save(new CompoundTag())`でOPの手持ちアイテムの瞬間のNBT(TACZの`GunCurrentAmmoCount`等の実行時カウンタ含む)をそのまま保存テンプレートに焼き付けているせいで、全員が同じ固定値を受け取っている」という説。これに基づき`stripVolatileGunState`(登録時にTACZの実行時状態タグを除去する関数)を実装・コミット・v0.3.1としてリリースまでしてしまった。

**ユーザーの指摘で判明した真因**: 「アイテムを渡すときはコピーしないといけないんだって」「`ItemStack.copy()`」という一言で、実際の原因は別にあると判明。`LoadoutManager.itemVariants`(`Map<ResourceLocation, CompoundTag>`)は各バリアントにつき単一の`CompoundTag`オブジェクトを保持しており、`ItemResolver.resolve(id, variants)`はそれを`ItemStack.of(saved)`に**そのまま**渡していた。**MinecraftのItemStack.of(CompoundTag)はタグをディープコピーせず参照をそのまま保持する**ため、このメソッドを複数回呼ぶ(=複数プレイヤーがそれぞれリスポーンして装備する)たびに生成される「別々の」ItemStackが、内部的には全く同じNBTオブジェクトを共有してしまっていた。TACZが誰か一人の弾薬数をNBTに書き込むと、その場で全員の「別々の」武器の弾薬数が変わって見える——これが「同期されている」ように見えた実際の仕組み。

なお同じファイル内の`ServerEvents.giveItem`(弾薬付与・スポーンキット用)は既に`template.copy()`/`template.copyWithCount()`を使っており正しく実装されていた。装備側(`equipLoadout`が使う`ItemResolver.resolve`)だけがこのコピー漏れを持っていた、という非対称性が実際のバグ。

**修正**:
1. `stripVolatileGunState`関連の変更は`git checkout <直前コミット> -- LoadoutManager.java`で完全にrevert(手動で書き戻すのではなくgit操作でやり直す方が確実、というのが今回得た教訓)
2. `ItemResolver.resolve(ResourceLocation id, Map<ResourceLocation, CompoundTag> variants)`の戻り値を`ItemStack.of(saved)`→`ItemStack.of(saved).copy()`に変更。これだけで解決(コミット`bf1ae51`)

**教訓**: 「複数プレイヤー間で状態が同期される」系のバグを見たら、まず依存Mod(TACZ等)側の独自NBTタグを疑う前に、**自分のコード側でCompoundTag/ItemStackの参照が使い回されていないか**(特に「テンプレートを保持するMapから`ItemStack.of()`/コンストラクタで直接組み立てている箇所」)を先に確認すべき。同じ処理をする既存の別メソッドが`.copy()`しているかどうかを比較するのが手っ取り早い(今回は`giveItem`との非対称性が答えだった)。

### ついでに見つかった別バグ: バージョン表示が0.1.0固定

上記の調査・リリース作業中に、`src/main/resources/META-INF/mods.toml`の`version`が`"0.1.0"`と**ハードコード**されていることが発覚(`gradle.properties`の`mod_version`をいくら上げてもMod一覧の表示等には一切反映されない状態だった)。

- `mods.toml`: `version="0.1.0"` → `version="${file.jarVersion}"`(Forgeがjarマニフェストの`Implementation-Version`から解決する標準プレースホルダー)に変更
- ただし`build.gradle`の`jar`タスクは元々`Implementation-Version`をマニフェストに書き込んでいなかった(NeoForgeの`legacyforge`プラグインは自動で付けてくれない)ため、`${file.jarVersion}`だけ直しても実際には解決されない状態のまま気づかずv0.3.2をタグ付けしてしまい、jarのMANIFEST.MFを確認して発覚→v0.3.2は未リリースのままタグ削除し、`build.gradle`に以下を追加してv0.3.3として仕切り直した:

```gradle
tasks.named('jar', Jar) {
    manifest {
        attributes(['Implementation-Version': project.version])
    }
}
```

v0.3.3のjarでは`unzip -p ... META-INF/MANIFEST.MF`で`Implementation-Version: 0.3.3`が入ることを確認済み(Forge起動時に`${file.jarVersion}`が実際に0.3.3へ解決されるかどうかまでは、Minecraftクライアント/サーバーを起動する実地確認が必要——未実施)。

### CIビルドの新規追加

このリポジトリには`.github/workflows/`が存在しなかった(ビルド確認は開発者のローカル実行のみに依存)。今回`.github/workflows/build.yml`を新設し、push/PRのたびに`./gradlew build`が通ることをGitHub Actions上で検証するようにした。

- `gradle.properties`の`org.gradle.java.home`がWindowsのローカルパス(`C:/Program Files/Java/jdk-21.0.10`)にハードコードされておりLinux/CI上ではそのままでは動かない。**この行はコミットされたファイルなので不用意に書き換えない**こと(Windows機での開発者本人のビルドを壊すため)。CI側では`actions/setup-java`でJDK21を用意した上で`./gradlew build -Dorg.gradle.java.home="$JAVA_HOME"`という**システムプロパティ(`-D`)での上書き**を使っている(`-P`ではGradleの`org.gradle.java.home`は上書きできない——これはプロジェクトプロパティではなくGradleプロパティなので、コマンドラインからは`-D`が必要、というのも今回の副産物的な学び)
- ローカル(Linux機)でこのプロジェクトを一時的にビルドしたい場合も同じ理屈で、`gradle.properties`を直接編集するかシステムプロパティで上書きする必要がある。JDK21は`/usr/lib/jvm/java-21-openjdk`にインストール済み

### リリース作業メモ

GitHub push認証がこの環境のHTTPS `origin`では通らなかった(`could not read Username for 'https://github.com'`)。SSHのホストエイリアス`github-okonomiyak`(`~/.ssh/config`に定義済み、鍵は`~/.ssh/id_ed25519_okonomiyaki`)に向けて`git remote set-url origin git@github-okonomiyak:okonomiyak/classloadout.git`してpushした。セッション途中から`gh` CLIも使えるようになった(`okonomiyak`としてログイン済み)ので、以降はGitHub Actionsの実行状況確認・リリース作成は`gh run watch`/`gh run download`/`gh release create`を使った。

- v0.2.0・v0.3.0: 今回のセッション以前からの既存リリース
- v0.3.1: 誤った修正(`stripVolatileGunState`)を含むリリース。**削除するかどうか未確認、そのまま残っている**(上記「未解決の判断待ち事項」参照)
- v0.3.2: タグは作ったがリリースはしていない(マニフェストの`Implementation-Version`欠落に気づいて即座にタグ削除。GitHub上には残っていない)
- v0.3.3: 弾薬同期バグの正しい修正+バージョン表示修正+CI追加、を含む最新の正式リリース

## 次にやるべきこと(優先順)

0a. **v0.3.1リリースを削除するかどうかユーザーに確認する**(誤った修正を含んだまま公開されている、上記参照)——最優先、次にこのリポジトリを触るセッションの冒頭で確認すること
0b. **弾薬同期バグ修正(v0.3.3)の実地確認**: 2人以上のプレイヤーで同じ武器をロードアウトに割り当て、片方が発砲して弾薬を消費しても、もう片方の弾薬数が変わらないことを`runClient`(2クライアント同時起動可能、`client`/`client2`のrun設定あり)で確認する。あわせてMod一覧画面でバージョンが`0.3.3`と表示されることも確認(`${file.jarVersion}`が実際に解決されるかの実地確認、上記参照)——優先度最高(今回のセッションの主目的)
1. 上記の補給パック誰でも破壊可能化の実地確認(GUI操作を伴うため`runClient`でのユーザー確認待ち)
1v. 上記の遮蔽物(誰でも破壊可能化+HP config化)の実地確認(GUI操作を伴うため`runClient`でのユーザー確認待ち)
1w. 上記のハンマー範囲破壊機能の実地確認(SuperbWarfare導入環境でのGUI操作を伴うため`runClient`でのユーザー確認待ち)
1x. 上記のスポーンキット機能の実地確認(ロードアウト未設定のプレイヤーにも配布されるか含め、GUI操作を伴うため`runClient`でのユーザー確認待ち)
1y. 上記の死亡時インベントリクリア+除外アイテム機能の実地確認(`keepInventory true`環境での一巡、GUI操作を伴うため`runClient`でのユーザー確認待ち)
1z. 上記のロードアウト即時装備化の実地確認(生存中の即時反映+弾薬付与がリスポーン限定であることの両方、GUI操作を伴うため`runClient`でのユーザー確認待ち)
1a. 上記の遮蔽物エンティティの実地確認(設置→耐久→寿命→friendlyOnlyDestroy→上限、GUI操作を伴うため`runClient`でのユーザー確認待ち)
1b. ロードアウトステーションブロックの実地確認(設置→右クリック→死なずにロードアウト画面が開いて変更が反映されるか、GUI操作を伴うため`runClient`でのユーザー確認待ち)
2. 上記のammo_pack修正の実地確認(TACZの標準銃で弾薬アイテムがインベントリに増えるか)
3. **ユーザーにコミット・push可否を確認する**(このセッションの変更は全て未コミット。動作確認は主要部分完了)
4. 弾薬付与機能(ロードアウトのammo grant)の一巡の実地テストがまだ明示的に報告されていないので、機会があれば個別に確認する:
   - `/class whitelist`でメイン武器スロットにガンを1つ登録 → 右クリックでammo grant設定 → 保存 → プレイヤー側でそのアイテムをロードアウトに割り当て → リスポーン → インベントリに弾薬が入るか
   - `count`に0を入れてClearしたときに正しく解除されるか
   - 複数回リスポーンした際、弾薬が加算され続ける挙動が意図通りか(ゲームバランス的に問題なければこのままでよい。もし「リスポームごとに一定量にリセットしたい」という要望が出たら、`grantAmmo`実行前に同じアイテムをインベントリから除去する処理を追加する)
5. 余裕があれば: `textures/item/health_pack.png`・`ammo_pack.png`(旧フラットアイコン、現在未参照)の扱いを決める(削除 or 別用途に転用 or そのまま放置)
6. 遮蔽物の見た目は現状バニラ木材のプレースホルダーなので、ユーザーがBlockbench製3Dモデルを用意したら`health_pack.json`と同じ手順(3パーツテクスチャ+モデルJSON差し替え)で本番用に置き換える

## 既知の制限事項(README記載分の再掲)

- TACZの弾薬補給(設置/投擲パック、ammo pack由来のもの): dummy ammo方式の銃は内部弾数を直接補充、それ以外(標準銃含む)は対応する実弾薬アイテムをインベントリに付与(2026-07-24修正、上記参照)
- TACZの汎用「素の銃」アイテム(例: `tacz:modern_kinetic_gun`)は個別銃IDと並んでアイテム選択グリッドに出現する(見た目上の重複、フィルタしていない)。弾薬も同様に汎用アイテムと個別弾薬IDが並んで出現する

## 参考ファイル

- `README.md` / `README.ja.md`: 機能・コマンド・GUI・config・2人プレイテスト手順の正式ドキュメント
- `classloadout-devlog-2026-07-21.md`: 開発経緯の詳細ログ(ハマった点・原因調査の過程を含む)
- メモリ: `C:\Users\tomip\.claude\projects\C--Users-tomip-program-java\memory\classloadout-project.md`(Claude Code用、次回セッション以降に自動参照される)
