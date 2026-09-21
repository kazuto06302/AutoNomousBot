package net.kztmc.mc.autonomousbot.perception;

public final class EntitySummary {
	public String entityId;
	public String entityUuid;
	public double distance;
	public double relX;
	public double relY;
	public double relZ;
	/** Only populated for living entities; -1 otherwise. */
	public float health;
	public boolean hostile;

	public EntitySummary(String entityId, String entityUuid, double distance,
			double relX, double relY, double relZ, float health, boolean hostile) {
		this.entityId = entityId;
		this.entityUuid = entityUuid;
		this.distance = distance;
		this.relX = relX;
		this.relY = relY;
		this.relZ = relZ;
		this.health = health;
		this.hostile = hostile;
	}
}
