package net.kztmc.mc.autonomousbot.planner;

/**
 * 最終ゴール(エンダードラゴン討伐)に向けた大まかな段階。Plannerが
 * WorldState(インベントリ・ディメンション)から自動判定する。
 *
 * 正直な現状: 各段階に応じた専用の行動(採掘/クラフト/ポータル設置/
 * エンダーアイ追跡/ドラゴン戦特有の立ち回り)はまだ一切実装されていない。
 * 今のところ段階が変わるとJevへの指示文(instructions)が変わるだけで、
 * 実際にできることは変わらない。各段階を実質的に機能させるには、
 * 対応するPhase(採掘・クラフト・ネザー対応・ストロングホール探索・
 * エンド戦)を別途実装する必要がある。
 */
public enum GoalStage {
	GATHER_BASICS,
	EXPLORE_OVERWORLD,
	PREPARE_NETHER,
	NETHER_FORTRESS,
	FIND_STRONGHOLD,
	PREPARE_END_FIGHT,
	DEFEAT_DRAGON
}