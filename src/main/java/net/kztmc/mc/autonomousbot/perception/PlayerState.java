package net.kztmc.mc.autonomousbot.perception;

/**
 * Plain, GSON-serializable snapshot of the local player. Intentionally a
 * simple data holder (no Minecraft classes inside) so it can be sent to Jev
 * as-is without leaking engine objects across the client/AI-worker boundary.
 */
public final class PlayerState {
	public double x;
	public double y;
	public double z;
	public float yaw;
	public float pitch;

	public float health;
	public float maxHealth;
	public int food;
	public float saturation;

	public double velocityX;
	public double velocityY;
	public double velocityZ;
	public boolean onGround;

	public int selectedSlot;
}
