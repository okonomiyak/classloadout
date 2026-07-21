*[English README](README.md)*

# Class Loadout (classloadout)

Minecraft 1.20.1 / Forge 47.x 向けの独立Mod。分隊/パーティー系Modへの依存は一切ない。
プレイヤーは死亡画面から、5つのスロット(メイン武器/サイドアーム/投擲物/ガジェット/近接武器)それぞれに好きなアイテムを自分で割り当てて「マイロードアウト」を作る——ただし割り当てられるのは**OPがそのスロット用にホワイトリスト登録したアイテムのみ**。管理者はさらにGUIエディタで「プリセット」を定義でき、プレイヤーはそれを自分のロードアウトへの出発点として適用し、その後もスロット単位で自由に手を加えられる。リスポーンのたびに、その時点のマイロードアウトがホットバー0〜4番へ自動装備される。

ガジェットスロットには「補給パック」も割り当てられる。2種類あり、**設置型**(右クリックで置く、強め、壊されるか寿命まで残る)と**投擲型**(スノーボールのように投げる、弱め、着弾地点に留まる)。どちらも範囲内のプレイヤーを時間経過で回復/弾薬補充する。弾薬補給はTACZ/SuperbWarfareが導入されていれば連携する(詳細は[設計メモ](#設計メモ)参照)。TACZについては**特定の銃(汎用アイテムではなく)**をロードアウトのスロットに直接選べる。

## ライセンス

GNU General Public License v3.0 (GPL-3.0-only)。全文は [`LICENSE`](LICENSE) を参照。

## コマンド

| コマンド | 説明 | 権限 |
|---|---|---|
| `/class assign <slot> <item>` | 自分のロードアウトの1スロット(`main`/`sidearm`/`throwable`/`gadget`/`melee`)をホワイトリスト内のアイテムに設定。`minecraft:air`で解除 | - |
| `/class select <id>` | プリセットの5アイテムを自分のロードアウトへ出発点として反映 | - |
| `/class clear` | 自分のロードアウト全体をクリア | - |
| `/class editor` | プリセットエディタGUIを開く | OP (レベル2+) |
| `/class save <id> <icon> <main> <sidearm> <throwable> <gadget> <melee> <name...>` | プリセットの作成/上書き(エディタの[保存]ボタンが送信、手打ち不要) | OP (レベル2+) |
| `/class delete <id>` | プリセットを削除 | OP (レベル2+) |
| `/class whitelist` | スロット別ホワイトリストエディタGUIを開く | OP (レベル2+) |
| `/class whitelist add <slot> <item>` | あるスロットのホワイトリストにアイテムを追加(エディタが送信、手打ち不要) | OP (レベル2+) |
| `/class whitelist remove <slot> <item>` | ホワイトリストからアイテムを削除 | OP (レベル2+) |

アイテム引数は登録済みアイテムIDを受け付ける。`minecraft:air` が「未設定」のセンチネル。

**全スロットのホワイトリストは初期状態では空**で、OPがホワイトリストエディタで最低1つアイテムを追加するまで、そのスロットには何も自主割り当てできない。プリセットはホワイトリストの制約を受けない(プリセットエディタに到達できる時点でOPは既にどんなアイテムでも自由に使えるため)。

## GUI

- **ロードアウト画面**(バニラ死亡画面右上の「ロードアウト」ボタン): 「マイロードアウト」に自分の5スロットがアイコンとして並び、クリックするとアイテム選択グリッド(検索欄+スクロール一覧、そのスロットのホワイトリストに限定)が開いて直接割り当てられる。下部の「プリセット」には管理者が定義したものが並び、それぞれの[適用]ボタンで5アイテムを自分のロードアウトへコピーできる(適用後もスロットごとに自由に上書き可能)。
- **プリセットエディタ**(`/class editor`、OP限定): 左列に既存プリセット一覧([編集]/[削除])、右列で選択中プリセットの名前・アイコン・5スロットを編集。プリセットエディタのアイテム選択グリッドは**制限なし**(`tacz`/`superbwarfare`/`minecraft`の全アイテム) — ここに到達できる時点でOP権限を持っているため。[保存]を押すまではサーバーに何も送信されず、押した瞬間に1コマンドでプリセット全体が確定する。
- **ホワイトリストエディタ**(`/class whitelist`、OP限定。プリセットエディタからもボタンで直接遷移可): 上部に5つのスロットタブ。スロットを選んでグリッド内のアイテムをクリックするとそのスロットのホワイトリストにON/OFFトグル(登録済みは緑枠で表示)。ここで設定した内容が、プレイヤー向けロードアウト画面のアイテム選択グリッドに直接反映される。
- **リスポーン時の自動装備**: 自分のロードアウトの5アイテムを**ホットバー0〜4番**へ(メイン/サイド/投擲/ガジェット/近接の順で)毎回上書き設置する。一度でもロードアウトに触れた(assign/select/clearのいずれか)プレイヤーはリスポーンのたびに無条件で反映されるため`keepInventory`有効時の重複も起きない。一度も触れていないプレイヤーはそのまま放置される。アイテムが見つからない場合(該当Mod未導入など)はクラッシュせず黙ってそのスロットを空にする。

## 補給パック

`classloadout:health_pack`と`classloadout:ammo_pack`の2種類のブロック。他のアイテムと同じく、OPが`gadget`スロット用にホワイトリスト登録して初めてプレイヤーが割り当て・設置できる。

- 右クリックで設置(通常のブロックアイテムと同じ)。`resupplyIntervalSeconds`秒ごとに、`resupplyRadius`ブロック以内の全プレイヤーを回復(回復パック)または弾薬補給(弾薬パック)する。`packLifetimeSeconds`秒で何もドロップせず自然消滅。
- 1人が同時に設置できるのは`maxActivePacksPerPlayer`個まで(回復・弾薬合算)。超える設置は拒否される。
- `friendlyOnlyDestroy`(既定true): 設置した本人以外は破壊できない(classloadoutに分隊/チーム概念は無いため、「味方」=「設置者本人」)。
- **弾薬補給**: TACZは「dummy ammo」方式(`IGun.useDummyAmmo()`)の銃のみ補充対象(インベントリの弾薬箱アイテムを消費する方式の銃は今回は非対応)。SuperbWarfareは持っている武器の種類を問わず5種類の弾薬プール(handgun/rifle/shotgun/sniper/heavy)全てを補充する。どちらも非導入なら弾薬パックは何もしない(クラッシュしない)。

## 投擲式補給パック

`classloadout:thrown_health_pack`と`classloadout:thrown_ammo_pack`の2種類の投擲アイテム。上記の設置型より意図的に弱め・気軽な代替として位置づけ(gadgetスロットのホワイトリスト条件は同じ)。

- スノーボール/エンダーパールのように投げる。着弾するとその場に留まり、パーティクルを出して周囲のプレイヤーへ効果を及ぼし始める(設置型より半径は狭く・間隔は長く・寿命は短い、`throwable`セクションのconfig参照)。効果を受けているプレイヤーには毎回action bar通知(「回復中...」/「弾薬補給中...」)が出る。
- **アイテムは消費されない** — `throwCooldownSeconds`(バニラのエンダーパールと同種のアイテムクールダウン)だけが連投を制限するので、クールダウンさえ空けば何度でも投げ直せる。
- 着弾後は設置型と**同じ`maxActivePacksPerPlayer`の枠**を消費する(別枠ではなく合算)。

## 設定 (`world/serverconfig/classloadout-server.toml`)

- `resupply.resupplyRadius`(既定4) — パック周囲の効果範囲(ブロック)
- `resupply.resupplyIntervalSeconds`(既定2) — 回復/弾薬補給の間隔(秒)
- `resupply.resupplyHealthPerTick`(既定1) — 回復パック1回あたりの回復量(ハーフハート単位)
- `resupply.resupplyAmmoPerTick`(既定10) — 弾薬パック1回あたりの補充量(武器Modごとの単位は[補給パック](#補給パック)参照)
- `resupply.packLifetimeSeconds`(既定60) — 設置から自然消滅までの秒数
- `resupply.maxActivePacksPerPlayer`(既定1) — 1人が同時に持てる上限(回復+弾薬・設置+投擲すべて合算)
- `resupply.friendlyOnlyDestroy`(既定true) — 設置者本人以外は破壊不可
- `throwable.throwPackRadius`(既定2) — 意図的に`resupplyRadius`より狭い
- `throwable.throwPackIntervalSeconds`(既定3)
- `throwable.throwPackHealthPerTick`(既定1)
- `throwable.throwPackAmmoPerTick`(既定5) — `resupplyAmmoPerTick`の半分が既定
- `throwable.throwPackLifetimeSeconds`(既定25) — 意図的に`packLifetimeSeconds`より短い
- `throwable.throwCooldownSeconds`(既定15) — プレイヤー・アイテムごとの投擲間隔

## 設計メモ

- **独立した3つの概念、2つのOPツール、1つのプレイヤー画面**: プレイヤー自身のロードアウト(セルフサービス、装備の実体は常にこちら)、OP管理のプリセット(「出発点として読み込む」ための便利ライブラリ、制限なし)、OPが管理するスロット別ホワイトリスト(プレイヤーが自主割り当てできる範囲)。プリセット自体が装備されることはなく、適用は常にプレイヤー自身のロードアウトへのコピーで、ホワイトリストのチェックも受けない(作成できる時点でOP権限を持っているため)。
- **サーバー側での実効的な検証(GUIのフィルタだけではない)**: `/class assign`はサーバー側でそのスロットのホワイトリストと再照合してから受け付けるため、手打ちコマンドでもアイテム選択グリッドの制限を回避できない。
- **サーバー権威・C2Sパケットなし**: プリセット・ホワイトリスト・プレイヤーごとのロードアウトはオーバーワールドに永続化される`SavedData`。`assign`/`select`/`clear`/`save`/`delete`/`whitelist add`/`whitelist remove`の全操作は`/class`コマンド経由でサーバー側検証されるため、なりすませるクライアント→サーバーパケットが存在しない。アイテム一覧もサーバー往復不要: ログイン後クライアントのアイテムレジストリは既に完全なので、`ForgeRegistries.ITEMS`(またはsync済みホワイトリスト)をその場でフィルタするだけ。
- **唯一の例外**: プリセット/ホワイトリストエディタ画面を開く操作。`/class editor`と`/class whitelist`はサーバー側で実行(そこで権限チェック)され、その1クライアントにだけ小さなトリガーパケットを送り返して画面を開かせる。
- **ロードアウト/ホワイトリスト機構: squadtpと同じ隔離方式のソフト依存**。`compat/TaczCompat`/`compat/SuperbWarfareCompat`は`ModList.isLoaded(...)`の確認のみでTACZ/SW側の型を一切参照しない。実際のAPI呼び出しは`compat/tacz/*`/`compat/superbwarfare/*`だけが持ち、`isLoaded`の分岐内からしかクラスロードされない。この理由で`build.gradle`は両方を`modCompileOnly`としても宣言している(同梱・必須化はしない。純粋な名前空間の文字列判定ではなくなった、という点だけが変化)。
- **TACZの銃は登録アイテムではない——`ItemResolver`が橋渡しする**。TACZは銃1つにつき1アイテムを登録しておらず、全ての銃が同じ汎用アイテム(例: `tacz:modern_kinetic_gun`)で、実際の銃種はNBTの`GunId`タグ経由でTACZのデータ駆動な銃インデックス(`TimelessAPI`)から解決される。つまりTACZの銃としてプリセット/ロードアウト/ホワイトリストに保存される「アイテムID」は実質「銃ID」であり、保存済み`ResourceLocation`を実際の`ItemStack`に変換する箇所(アイコン描画・リスポーン時装備・アイテムピッカーのグリッド自体)は全て`ItemResolver.resolve()`を経由する(TACZの`GunItemBuilder`で銃IDから組み立てを試み、ダメなら通常の登録アイテムとして解決)。`ItemCatalog`もTACZの全銃ID(`TimelessAPI.getAllCommonGunIndex()`)を選択可能なプールに追加している。SuperbWarfareの武器は普通の登録アイテムなので、この橋渡しは不要。
- **弾薬補給**: 同じ`compat/tacz`/`compat/superbwarfare`モジュールが補給パックの弾薬補充(`TaczCompat.resupply`/`SuperbWarfareCompat.resupply`)も担っており、上記と同じガード付きクラスロード方式を共有している。

## ビルド・実行

要件: JDK 21(Gradle実行用。`gradle.properties`の`org.gradle.java.home`で指定)。コンパイルはJava 17 toolchain。

```
gradlew build         # -> build/libs/classloadout-<version>.jar
gradlew runServer      # 開発用サーバー(run-server/)
gradlew runClient      # 開発用クライアント(ユーザー名 "Dev1")
gradlew runClient2     # 2人目の開発用クライアント(ユーザー名 "Dev2"、別ゲームディレクトリ run2/)
```

### 2プレイヤーテスト手順

1. `gradlew runServer`、続けて`gradlew runClient`と`gradlew runClient2`を起動。Dev1をOPにする(`op Dev1`、両エディタに必要)。
2. Dev1側: `/class whitelist` → `main`タブでいくつかアイテムをクリックしてホワイトリスト登録。他のスロットでも1つ以上登録しておく。
3. Dev2側: 死亡→「ロードアウト」→`main`スロットのアイコンをクリックし、Dev1が登録したアイテムだけが表示されること(未登録スロットは「まだ何も許可されていません」の空表示になること)を確認。1つ割り当ててリスポーンし、ホットバー0番に入ることを確認。
4. Dev1側: `/class editor` → プリセットを作成(このエディタのアイテム選択グリッドはホワイトリストに関係なく全アイテムが出ることを確認)、保存。
5. Dev2側: 「ロードアウト」→そのプリセットの[適用]→リスポーンして、ホワイトリスト未登録のアイテムが含まれていてもホットバー0〜4番に反映されることを確認(プリセットは仕様上ホワイトリストを迂回する)。
6. Dev1側: 先ほどホワイトリスト登録した中の1つを`/class whitelist`で削除し、Dev2のロードアウト画面のピッカーからそのアイテムが消えること(既存の割り当て自体は遡って解除されず、今後選べなくなるだけ)を確認。
7. `gadget`スロットに`classloadout:health_pack`/`classloadout:ammo_pack`をホワイトリスト登録し、Dev2のロードアウトに割り当ててリスポーン→設置。周囲のプレイヤーが設定間隔で回復/弾薬補給されること、`packLifetimeSeconds`後に消滅すること、`maxActivePacksPerPlayer`を超える設置が拒否されること、`friendlyOnlyDestroy`有効時にDev1がDev2のパックを破壊できないことを確認。
8. `classloadout:thrown_health_pack`/`classloadout:thrown_ammo_pack`も`gadget`にホワイトリスト登録→割当→リスポーン→投擲。着弾して効果範囲・間隔が設置型より明らかに控えめであること、action bar通知が出ることを確認。すぐもう一度投げようとして`throwCooldownSeconds`が経過するまでブロックされること、アイテム自体は消費されないことを確認。
9. TACZ導入環境で: `/class whitelist` → `main`タブ → 汎用アイテムだけでなく個々の銃名(例: `tacz:ak47`)がグリッドに並びホワイトリスト登録できることを確認。1つをロードアウトに割り当ててリスポーンし、空の未設定銃ではなく正しい特定の銃がホットバー0番に入ることを確認。

`build.gradle`はTACZ/SuperbWarfareを`modRuntimeOnly`(開発クライアント/サーバーに実際にロードさせてテストするため)と`modCompileOnly`(compatクラスが実APIに対してコンパイルできるようにするため)の両方で取り込んでいる。どちらもビルド・配布には不要。
