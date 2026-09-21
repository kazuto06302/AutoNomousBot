package net.kztmc.mc.autonomousbot.behavior;

/**
 * A single candidate (or chosen) action. {@code id} is the short token
 * exposed to Jev as a choice option (e.g. "A", "B", ...); {@code label}
 * is the human/AI-readable description used as that option's criteria text.
 */
public final class Action {
	public final String id;
	public final ActionType type;
	public final String label;
	/** UUID string of a target entity, or null if not applicable. */
	public final String targetEntityUuid;

	public Action(String id, ActionType type, String label, String targetEntityUuid) {
		this.id = id;
		this.type = type;
		this.label = label;
		this.targetEntityUuid = targetEntityUuid;
	}

	public static Action of(String id, ActionType type, String label) {
		return new Action(id, type, label, null);
	}

	@Override
	public String toString() {
		return id + "=" + type + (targetEntityUuid != null ? "(" + targetEntityUuid + ")" : "");
	}
}
