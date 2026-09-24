package net.kztmc.mc.autonomousbot.perception;

public final class BlockSummary {
	public int relX;
	public int relY;
	public int relZ;
	public String blockId;

	public int absX;
	public int absY;
	public int absZ;

	public BlockSummary(int relX, int relY, int relZ, String blockId, int absX, int absY, int absZ) {
		this.relX = relX;
		this.relY = relY;
		this.relZ = relZ;
		this.blockId = blockId;
		this.absX = absX;
		this.absY = absY;
		this.absZ = absZ;
	}
}
