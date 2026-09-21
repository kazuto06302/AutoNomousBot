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
	ATTACK
}
