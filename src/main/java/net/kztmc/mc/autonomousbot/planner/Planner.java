package net.kztmc.mc.autonomousbot.planner;

import net.kztmc.mc.autonomousbot.perception.ItemSummary;
import net.kztmc.mc.autonomousbot.perception.WorldState;

import java.util.List;

public final class Planner {

	private GoalStage currentStage = GoalStage.GATHER_BASICS;
	private Double waypointX;
	private Double waypointZ;

	public GoalStage getCurrentStage() {
		return currentStage;
	}

	/**
	 * インベントリとディメンションだけを見て、現在の段階を機械的に
	 * 再評価する。ユーザー指定ではなく、持ち物から逆算する方式。
	 * 段階が変わったら探索の目的地(waypoint)も一旦リセットする
	 * (前の段階のための移動先を引きずらないように)。
	 */
	public void updateStage(WorldState state) {
		GoalStage next = evaluate(state);
		if (next != currentStage) {
			currentStage = next;
			clearWaypoint();
		}
	}

	private GoalStage evaluate(WorldState state) {
		if ("minecraft:the_end".equals(state.dimension)) {
			return GoalStage.DEFEAT_DRAGON;
		}
		if ("minecraft:the_nether".equals(state.dimension)) {
			return countItem(state, "minecraft:blaze_rod") > 0 ? GoalStage.FIND_STRONGHOLD : GoalStage.NETHER_FORTRESS;
		}

		boolean hasEnderEye = countItem(state, "minecraft:ender_eye") > 0;
		if (hasEnderEye) {
			return GoalStage.FIND_STRONGHOLD;
		}

		boolean canBuildPortal = countItem(state, "minecraft:obsidian") >= 10
				&& hasItem(state, "minecraft:flint_and_steel");
		if (canBuildPortal) {
			return GoalStage.PREPARE_NETHER;
		}

		boolean hasBasicGear = hasItemEndingWith(state, "_sword") || hasItemEndingWith(state, "_pickaxe");
		return hasBasicGear ? GoalStage.EXPLORE_OVERWORLD : GoalStage.GATHER_BASICS;
	}

	private static boolean hasItem(WorldState state, String itemId) {
		return countItem(state, itemId) > 0;
	}

	private static int countItem(WorldState state, String itemId) {
		List<ItemSummary> inv = state.inventory;
		int total = 0;
		for (ItemSummary item : inv) {
			if (item.itemId.equals(itemId)) {
				total += item.count;
			}
		}
		return total;
	}

	private static boolean hasItemEndingWith(WorldState state, String suffix) {
		for (ItemSummary item : state.inventory) {
			if (item.itemId.endsWith(suffix)) {
				return true;
			}
		}
		return false;
	}

	public boolean hasWaypoint() {
		return waypointX != null && waypointZ != null;
	}

	public void setWaypoint(double x, double z) {
		this.waypointX = x;
		this.waypointZ = z;
	}

	public void clearWaypoint() {
		this.waypointX = null;
		this.waypointZ = null;
	}

	public Double getWaypointX() {
		return waypointX;
	}

	public Double getWaypointZ() {
		return waypointZ;
	}
}