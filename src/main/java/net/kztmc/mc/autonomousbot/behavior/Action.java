package net.kztmc.mc.autonomousbot.behavior;

public final class Action {
	public final String id;
	public final ActionType type;
	public final String label;
	public final String targetEntityUuid;
	public final Integer targetSlot;
	public final boolean holdGround;
	public final Integer targetBlockX;
	public final Integer targetBlockY;
	public final Integer targetBlockZ;
	public final String recipeId;

	public Action(String id, ActionType type, String label, String targetEntityUuid, Integer targetSlot,
				  boolean holdGround, Integer targetBlockX, Integer targetBlockY, Integer targetBlockZ, String recipeId) {
		this.id = id;
		this.type = type;
		this.label = label;
		this.targetEntityUuid = targetEntityUuid;
		this.targetSlot = targetSlot;
		this.holdGround = holdGround;
		this.targetBlockX = targetBlockX;
		this.targetBlockY = targetBlockY;
		this.targetBlockZ = targetBlockZ;
		this.recipeId = recipeId;
	}

	public Action(String id, ActionType type, String label, String targetEntityUuid, Integer targetSlot, boolean holdGround) {
		this(id, type, label, targetEntityUuid, targetSlot, holdGround, null, null, null, null);
	}

	public Action(String id, ActionType type, String label, String targetEntityUuid, Integer targetSlot) {
		this(id, type, label, targetEntityUuid, targetSlot, false, null, null, null, null);
	}

	public Action(String id, ActionType type, String label, String targetEntityUuid) {
		this(id, type, label, targetEntityUuid, null, false, null, null, null, null);
	}

	public static Action of(String id, ActionType type, String label) {
		return new Action(id, type, label, null, null, false, null, null, null, null);
	}

	public static Action mine(String id, int x, int y, int z, String label) {
		return new Action(id, ActionType.MINE, label, null, null, false, x, y, z, null);
	}

	public static Action craft(String id, String recipeId, String label) {
		return new Action(id, ActionType.CRAFT, label, null, null, false, null, null, null, recipeId);
	}

	public static Action placeCraftingTable(String id, String label) {
		return new Action(id, ActionType.PLACE_CRAFTING_TABLE, label, null, null, false, null, null, null, null);
	}

	@Override
	public String toString() {
		return id + "=" + type
				+ (targetEntityUuid != null ? "(" + targetEntityUuid + ")" : "")
				+ (targetSlot != null ? "[slot=" + targetSlot + "]" : "")
				+ (holdGround ? "[hold]" : "")
				+ (targetBlockX != null ? "[block=" + targetBlockX + "," + targetBlockY + "," + targetBlockZ + "]" : "")
				+ (recipeId != null ? "[recipe=" + recipeId + "]" : "");
	}
}