package net.kztmc.mc.autonomousbot.perception;

import java.util.List;

/**
 * A compact, serializable snapshot of "everything the bot currently knows".
 * This is deliberately NOT a dump of the whole world - only what
 * {@link WorldStateCollector} decided is relevant for the current decision.
 */
public final class WorldState {
	public long tick;
	public String dimension;
	public long timeOfDay;
	public boolean isRaining;
	public boolean isThundering;
	public String biome;

	public PlayerState player;
	public List<ItemSummary> inventory;
	public List<ItemSummary> armor;
	public ItemSummary mainHand;
	public ItemSummary offHand;

	public List<BlockSummary> nearbyBlocks;
	public List<EntitySummary> nearbyEntities;
}
