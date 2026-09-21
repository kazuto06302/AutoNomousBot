package net.kztmc.mc.autonomousbot.perception;

public final class BlockSummary {
	public int relX;
	public int relY;
	public int relZ;
	public String blockId;

	public BlockSummary(int relX, int relY, int relZ, String blockId) {
		this.relX = relX;
		this.relY = relY;
		this.relZ = relZ;
		this.blockId = blockId;
	}
}
