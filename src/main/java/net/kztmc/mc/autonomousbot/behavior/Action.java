package net.kztmc.mc.autonomousbot.behavior;

public final class Action {
	public final String id;
	public final ActionType type;
	public final String label;
	public final String targetEntityUuid;
	public final Integer targetSlot;
	public final boolean holdGround;

	public Action(String id, ActionType type, String label, String targetEntityUuid, Integer targetSlot, boolean holdGround) {
		this.id = id;
		this.type = type;
		this.label = label;
		this.targetEntityUuid = targetEntityUuid;
		this.targetSlot = targetSlot;
		this.holdGround = holdGround;
	}

	public Action(String id, ActionType type, String label, String targetEntityUuid, Integer targetSlot) {
		this(id, type, label, targetEntityUuid, targetSlot, false);
	}

	public Action(String id, ActionType type, String label, String targetEntityUuid) {
		this(id, type, label, targetEntityUuid, null, false);
	}

	public static Action of(String id, ActionType type, String label) {
		return new Action(id, type, label, null, null, false);
	}

	@Override
	public String toString() {
		return id + "=" + type
				+ (targetEntityUuid != null ? "(" + targetEntityUuid + ")" : "")
				+ (targetSlot != null ? "[slot=" + targetSlot + "]" : "")
				+ (holdGround ? "[hold]" : "");
	}
}