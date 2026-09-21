package net.kztmc.mc.autonomousbot.perception;

public final class ItemSummary {
	public int slot;
	public String itemId;
	public int count;
	/** -1 when the item has no durability. */
	public int damage;
	public int maxDamage;

	public ItemSummary(int slot, String itemId, int count, int damage, int maxDamage) {
		this.slot = slot;
		this.itemId = itemId;
		this.count = count;
		this.damage = damage;
		this.maxDamage = maxDamage;
	}
}
