*[English README](README.md)*

# Class Loadout (classloadout)

Minecraft 1.20.1 / Forge 47.x 向けの独立Mod。分隊/パーティー系Modへの依存も、TACZ/SuperbWarfareへの依存も一切ない。
管理者はゲーム内GUIエディタで装備「クラス」(名前・アイコン・5スロット: メイン武器/サイドアーム/投擲物/ガジェット/近接武器)を定義する。プレイヤーは死亡画面から1つ選び、リスポーンのたびにホットバー0〜4番へ自動装備される。

## ライセンス

GNU General Public License v3.0 (GPL-3.0-only)。全文は [`LICENSE`](LICENSE) を参照。

## コマンド

| コマンド | 説明 | 権限 |
|---|---|---|
| `/class editor` | クラスエディタGUIを開く | OP (レベル2+) |
| `/class save <id> <icon> <main> <sidearm> <throwable> <gadget> <melee> <name...>` | クラスの作成/上書き(エディタの[保存]ボタンが送信、手打ち不要) | OP (レベル2+) |
| `/class delete <id>` | クラスを削除 | OP (レベル2+) |
| `/class select <id>` | ロードアウトクラスを選択(クラス選択画面が送信) | - |
| `/class clear` | 選択を解除 | - |

`<icon>`・各スロット引数は登録済みアイテムIDを受け付ける。`minecraft:air` が「未設定」のセンチネル。

## GUI

- **エディタ**(`/class editor`、OP限定): 左列に既存クラス一覧([編集]/[削除])、右列で選択中クラスの名前・アイコン・5スロットを編集。各スロットをクリックするとアイテム選択グリッド(検索欄+スクロール一覧)が開き、`tacz`/`superbwarfare`/`minecraft`名前空間の登録済みアイテムから選べる。[保存]を押すまではサーバーに何も送信されず、押した瞬間に1コマンドでクラス全体が確定する。
- **選択**: バニラ死亡画面の右上「クラス変更」ボタンからクラス一覧(名前+5スロットのアイコン横並び)を開いて選ぶ。
- **リスポーン時の自動装備**: 選択中クラスの5アイテムを**ホットバー0〜4番**へ(メイン/サイド/投擲/ガジェット/近接の順で)毎回上書き設置する。リスポーンのたびに無条件で行われるため`keepInventory`有効時の重複も起きない。アイテムが見つからない場合(該当Mod未導入など)はクラッシュせず黙ってそのスロットを空にする。

## 設計メモ

- **サーバー権威・C2Sパケットなし**: クラス定義とプレイヤーごとの選択はオーバーワールドに永続化される`SavedData`。保存/削除/選択/解除の全操作は`/class`コマンド経由でサーバー側検証されるため、なりすませるクライアント→サーバーパケットが存在しない。アイテム一覧もサーバー往復不要: ログイン後クライアントのアイテムレジストリは既に完全なので、`ItemPickerScreen`は`ForgeRegistries.ITEMS`をその場でフィルタするだけ。
- **唯一の例外**: エディタ画面を開く操作。`/class editor`はサーバー側で実行(そこで権限チェック)され、その1クライアントにだけ小さな`OpenClassEditorPacket`を送り返して画面を開かせる(クライアント側で権限レベルを再導出させない設計)。
- **他Modへの依存ゼロ**: コンパイル時・実行時とも他Modへの依存なし。TACZ/SuperbWarfareはアイテムピッカーの名前空間フィルタ(`tacz:`/`superbwarfare:`)として文字列的に参照されるだけで、`ForgeRegistries.ITEMS`に実際に何が登録されているかにのみ依存する。

## ビルド・実行

要件: JDK 21(Gradle実行用。`gradle.properties`の`org.gradle.java.home`で指定)。コンパイルはJava 17 toolchain。

```
gradlew build         # -> build/libs/classloadout-<version>.jar
gradlew runServer      # 開発用サーバー(run-server/)
gradlew runClient      # 開発用クライアント(ユーザー名 "Dev1")
gradlew runClient2     # 2人目の開発用クライアント(ユーザー名 "Dev2"、別ゲームディレクトリ run2/)
```

### 2プレイヤーテスト手順

1. `gradlew runServer`、続けて`gradlew runClient`と`gradlew runClient2`を起動。両アカウントをOPにする(サーバーコンソールで`op Dev1`/`op Dev2`、または`ops.json`を事前に用意)。
2. Dev1側: `/class editor` → クラスを作成し各スロットにアイテムを割り当てて保存。
3. Dev2側: 死亡→死亡画面の「クラス変更」→Dev1が作成したクラスを選択→リスポーンしてホットバー0〜4番に装備が入ることを確認。
4. Dev2がそのクラスを選択したままエディタから削除し、再度リスポーンしてもクラッシュせずスロットが空のままになることを確認。

`build.gradle`はアイテムピッカーで実際の`tacz:`/`superbwarfare:`アイテムをテスト表示できるよう、TACZ/SuperbWarfare(と依存するKotlin/GeckoLib/Curios)を**開発実行時限定**の依存として取り込んでいる。ビルド・配布には一切不要。
