package net.kztmc.mc.autonomousbot.perception;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the player's held inventory/armor/hands into plain DTOs.
 * Must be called on the client thread (it touches live ItemStacks).
 *
 * NOTE: since the equipment rework (armor/offhand moved off of
 * PlayerInventory and onto the generic LivingEntity/EquipmentUser
 * equipment system), armor and both hands are read via
 * {@link PlayerEntity#getEquippedStack(EquipmentSlot)} rather than
 * PlayerInventory's old "armor"/"offHand" fields. Only the 36 general
 * slots (hotbar + main storage) still live on PlayerInventory ("main").
 */
public final class InventoryScanner {

	private static final EquipmentSlot[] ARMOR_SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	private InventoryScanner() {
	}

	public static List<ItemSummary> scanInventory(PlayerEntity player) {
		PlayerInventory inv = player.getInventory();
		List<ItemSummary> result = new ArrayList<>();
		for (int i = 0; i < inv.getMainStacks().size(); i++) {
			ItemStack stack = inv.getMainStacks().get(i);
			if (stack.isEmpty()) {
				continue;
			}
			result.add(toSummary(i, stack));
		}
		return result;
	}

	public static List<ItemSummary> scanArmor(PlayerEntity player) {
		List<ItemSummary> result = new ArrayList<>();
		for (int i = 0; i < ARMOR_SLOTS.length; i++) {
			ItemStack stack = player.getEquippedStack(ARMOR_SLOTS[i]);
			if (stack.isEmpty()) {
				continue;
			}
			result.add(toSummary(i, stack));
		}
		return result;
	}

	public static ItemSummary mainHand(PlayerEntity player) {
		return toSummary(player.getInventory().getSelectedSlot(), player.getEquippedStack(EquipmentSlot.MAINHAND));
	}

	public static ItemSummary offHand(PlayerEntity player) {
		return toSummary(-1, player.getEquippedStack(EquipmentSlot.OFFHAND));
	}

	private static ItemSummary toSummary(int slot, ItemStack stack) {
		if (stack.isEmpty()) {
			return new ItemSummary(slot, "minecraft:air", 0, -1, -1);
		}
		String id = Registries.ITEM.getId(stack.getItem()).toString();
		int damage = stack.isDamageable() ? stack.getDamage() : -1;
		int maxDamage = stack.isDamageable() ? stack.getMaxDamage() : -1;
		return new ItemSummary(slot, id, stack.getCount(), damage, maxDamage);
	}
}
