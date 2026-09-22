package net.kztmc.mc.autonomousbot.behavior;

public final class Action {
	public final String id;
	public final ActionType type;
	public final String label;
	public final String targetEntityUuid;
	/** Hotbar slot index (0-8). Only set for SELECT_SLOT actions. */
	public final Integer targetSlot;

	public Action(String id, ActionType type, String label, String targetEntityUuid, Integer targetSlot) {
		this.id = id;
		this.type = type;
		this.label = label;
		this.targetEntityUuid = targetEntityUuid;
		this.targetSlot = targetSlot;
	}

	public Action(String id, ActionType type, String label, String targetEntityUuid) {
		this(id, type, label, targetEntityUuid, null);
	}

	public static Action of(String id, ActionType type, String label) {
		return new Action(id, type, label, null, null);
	}

	@Override
	public String toString() {
		return id + "=" + type
				+ (targetEntityUuid != null ? "(" + targetEntityUuid + ")" : "")
				+ (targetSlot != null ? "[slot=" + targetSlot + "]" : "");
	}
}