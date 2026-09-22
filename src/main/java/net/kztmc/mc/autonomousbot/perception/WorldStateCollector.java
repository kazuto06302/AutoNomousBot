package net.kztmc.mc.autonomousbot.perception;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

/**
 * Builds a {@link WorldState} snapshot from the live client player/world.
 *
 * IMPORTANT: this must only be called from the Minecraft client thread.
 * The returned WorldState is plain data and safe to hand off to the AI
 * worker thread afterwards.
 */
public final class WorldStateCollector {

	private WorldStateCollector() {
	}

	public static WorldState collect(ClientPlayerEntity player, ClientWorld world) {
		WorldState state = new WorldState();
		state.tick = world.getTime();
		state.dimension = world.getRegistryKey().getValue().toString();
		state.timeOfDay = world.getTimeOfDay() % 24000L;
		state.isRaining = world.isRaining();
		state.isThundering = world.isThundering();
		state.biome = resolveBiomeName(world, player.getBlockPos());

		state.player = collectPlayer(player);
		state.inventory = InventoryScanner.scanInventory(player);
		state.armor = InventoryScanner.scanArmor(player);
		state.mainHand = InventoryScanner.mainHand(player);
		state.offHand = InventoryScanner.offHand(player);

		state.nearbyBlocks = BlockScanner.scanAround(world, player.getBlockPos());
		state.nearbyEntities = EntityScanner.scanAround(world, player);

		return state;
	}

	private static PlayerState collectPlayer(ClientPlayerEntity player) {
		PlayerState ps = new PlayerState();
		ps.x = player.getX();
		ps.y = player.getY();
		ps.z = player.getZ();
		ps.yaw = player.getYaw();
		ps.pitch = player.getPitch();

		ps.health = player.getHealth();
		ps.maxHealth = player.getMaxHealth();
		ps.food = player.getHungerManager().getFoodLevel();
		ps.saturation = player.getHungerManager().getSaturationLevel();

		ps.velocityX = player.getVelocity().x;
		ps.velocityY = player.getVelocity().y;
		ps.velocityZ = player.getVelocity().z;
		ps.onGround = player.isOnGround();

		ps.selectedSlot = player.getInventory().getSelectedSlot();

		ps.attackCooldownProgress = player.getAttackCooldownProgress(0.0f);
		ps.sprinting = player.isSprinting();

		return ps;
	}

	private static String resolveBiomeName(ClientWorld world, BlockPos pos) {
		RegistryEntry<Biome> entry = world.getBiome(pos);
		return entry.getKey()
			.map(key -> key.getValue().toString())
			.orElse("unknown");
	}
}
