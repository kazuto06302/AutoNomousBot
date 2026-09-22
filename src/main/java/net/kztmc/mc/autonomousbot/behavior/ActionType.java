package net.kztmc.mc.autonomousbot.behavior;

/**
 * Phase 1 action vocabulary. Deliberately small - see design doc for the
 * larger future set (MINE, PLACE_BLOCK, CRAFT, RETREAT, EXPLORE, ...).
 */
public enum ActionType {
	WAIT,
	MOVE_FORWARD,
	JUMP,
	LOOK,
	ATTACK,
	SELECT_SLOT,
	RETREAT,          // 追加：敵から離れる
	SPRINT_ATTACK,     // 追加：スプリント状態で攻撃（ダッシュ斬り＝ノックバック増）
	CRITICAL_ATTACK
}
