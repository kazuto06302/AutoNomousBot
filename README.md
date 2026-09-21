# AutonomousBot (Phase 1)

Minecraft 1.21.11 / Fabric / Java 21 上で動作する、完全自律型AI Botの基盤（Phase 1）。
Jev (TypeSafe AI) にWorldStateと候補行動を渡し、選んだ結果をJava側で実行する。

## セットアップ

1. `TYPESAFE_API_KEY` 環境変数にJevのAPIキーを設定する（ソースやconfigに平文で書かない）。
2. `./gradlew build` （初回はMinecraft/Fabric APIのダウンロードで数分かかる）。
3. `./gradlew runClient` で起動。
4. ワールドに入った状態で `/bot start` を実行するとAIが動き出す。
5. `/bot stop`、またはデフォルトで **Pキー** を押すと即座に緊急停止する（`key.categories.autonomousbot` から変更可）。
6. `/bot debug` でデバッグ表示（アクションバーへの状態表示）のON/OFF切り替え。

設定ファイルは `config/autonomousbot.json` に生成される。APIキー自体はデフォルトでは書き込まれない
（`apiKeyEnvVar` で指定した環境変数から読む）。`.gitignore` で除外済み。

## アーキテクチャ

```
perception/  WorldStateCollector + BlockScanner/EntityScanner/InventoryScanner
                -> WorldState (プレーンなDTO、Minecraft型を含まない)
behavior/    CandidateActionGenerator (WorldState -> 候補Action一覧)
             ActionExecutor (Action -> 実際のクライアント操作)
ai/          DecisionQuestion / Decision / JevClient (非同期HTTP) / DecisionEngine (全体のtickループ)
planner/     Goal / Task / Planner (Phase 1はSURVIVE固定のスケルトン)
memory/      ShortTermMemory (ループ検知) / LongTermMemory (永続化スケルトン、未配線)
config/      BotConfig (config/autonomousbot.json の読み書き、APIキー解決)
command/     /bot start|stop|debug
```

非同期フロー:
`ClientTickEvents.END_CLIENT_TICK` → `DecisionEngine.tick()` →
（interval経過 & リクエスト未実行なら）`WorldStateCollector.collect()` をクライアントスレッドで実行 →
`JevClient.askChoice()` をAIワーカースレッド（専用シングルスレッドExecutor）で非同期実行 →
完了したら `ConcurrentLinkedQueue` に結果を積む → 次のtickでクライアントスレッドが取り出して
`ActionExecutor.execute()` を実行。Jev呼び出し中でもMinecraftのメインスレッドはブロックされない。

## Jev API仕様（実装時に確認したもの）

```
POST https://api.typesafe.ai/v1/systemone
Authorization: Bearer <TYPESAFE_API_KEY>
Content-Type: application/json

{
  "model": "jev-latest",
  "state": { ...WorldStateのJSON... },
  "questions": {
    "action": {
      "type": "choice",
      "instructions": "...",
      "criteria": { "A": "WAIT: ...", "B": "MOVE_FORWARD: ...", ... }
    }
  }
}
```

レスポンスの `answers.action.choice` / `.confidence` / `.probabilities` をGsonの `JsonObject` で
明示的にパースしている（正規表現によるJSON解析は行っていない）。

## 安全機構（実装済み）

- Decision timeout（`decisionTimeoutMs`、デフォルト5秒）+ リトライ（`maxRetry`、デフォルト2回）
- 全リトライ失敗時は必ずWAITにフォールバック（`Decision.fallback(...)`）、例外はクライアントスレッドまで伝播しない
- Jevが候補リストにないIDを返した場合もWAITにフォールバック
- 簡易ループ検知（`ShortTermMemory`）: 直近16件中10件以上同一の非WAITアクションが続いたら強制WAIT
- 緊急停止（Pキー / `/bot stop`）: AI無効化 + 移動系キーを即座にすべて解放
- 決定リクエストは同時に1つまで（前回のレスポンス待ち中は新規送信しない）

## Phase 1 完成条件チェックリスト（対応状況）

1. Fabric 1.21.11 MODが起動する — 実装済み（未コンパイル確認、下記「要確認事項」参照）
2. WorldState取得 — 実装済み（`WorldStateCollector`）
3. Player/Inventory/Entity/Blockの基本情報取得 — 実装済み
4. CandidateAction生成 — 実装済み（WAIT/MOVE_FORWARD/JUMP/LOOK/ATTACK）
5. JevClient実装 — 実装済み
6. Jev APIへの非同期通信 — 実装済み（専用AIワーカースレッド + `java.net.http.HttpClient`）
7. Decisionの受信 — 実装済み
8. Decision→Action変換 — 実装済み
9. ActionExecutorでの基本操作実行 — 実装済み（移動/ジャンプ/視点/攻撃）
10. Debug表示 — 一部実装（アクションバーへのテキスト表示。専用HUDオーバーレイは未実装、下記参照）
11. APIエラー時にクラッシュしない — 実装済み（全経路でフォールバックDecisionを返す設計）
12. `/bot stop` での即時停止 — 実装済み
13. Gradle buildが成功する — **未確認**（下記参照）

## 要確認事項（正直に報告します）

このサンドボックス環境はMinecraft/Fabricのmavenリポジトリにネットワークアクセスできないため、
実際に `./gradlew build` を通してコンパイル確認ができていません。1.21.11はごく最近のバージョンで
かつFabric APIが大規模なリネーム（World→Level、公式Mojangマッピングへの移行）を行った直後のため、
特に以下は手元の環境で最初にビルドして確認してください:

- **Loom/Loader/Fabric APIのバージョン番号**（`gradle.properties`）: 変更頻度が高いため、
  https://fabricmc.net/develop で最新値に置き換えてください。
- **マッピング**: `build.gradle` では `loom.officialMojangMappings()` を使う前提にしています。
  もし環境がまだYarnベースなら該当箇所のコメントに従ってYarnに切り替えてください。
- **デバッグHUD**: 本実装はアクションバー表示（`displayClientMessage(..., true)`）のみで、
  常時表示のオーバーレイ（`HudElementRegistry`等）は未実装です。存在しないAPIを推測で
  書くよりは安全側に倒しました。次のステップとして実装可能です。
- `Entity#onGround()` / `Options` のキー名フィールド（`keyUp`等）/ `MultiPlayerGameMode#attack(...)` は
  公式ドキュメント・実装経験に基づいていますが、1.21.11時点の正式シグネチャはIDE上で一度確認してください。

コンパイルエラーが出た場合は、エラーメッセージを教えていただければ該当ファイルだけ修正します。

## 未実装（意図的にPhase 1の範囲外）

- Navigation / Mining / Combat専用コントローラ
- Behavior Learning（実況者動画からのプロファイル抽出）
- LongTermMemoryの実際の読み書き配線（スケルトンのみ）
- エンダードラゴン討伐に向けた高レベルGoal分解
