package net.kztmc.mc.autonomousbot.perception;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Scans nearby entities within a fixed radius of the player.
 */
public final class EntityScanner {

	private static final double RADIUS = 16.0D;
	private static final int MAX_ENTITIES = 24;

	private EntityScanner() {
	}

	public static List<EntitySummary> scanAround(World world, PlayerEntity player) {
		Box box = player.getBoundingBox().expand(RADIUS);
		List<Entity> nearby = world.getOtherEntities(player, box, entity -> entity != player);

		List<EntitySummary> result = new ArrayList<>();
		for (Entity entity : nearby) {
			double dx = entity.getX() - player.getX();
			double dy = entity.getY() - player.getY();
			double dz = entity.getZ() - player.getZ();
			double distance = entity.distanceTo(player);

			float health = -1f;
			if (entity instanceof LivingEntity living) {
				health = living.getHealth();
			}
			boolean hostile = entity instanceof Monster;

			String id = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
			result.add(new EntitySummary(id, entity.getUuid().toString(), distance, dx, dy, dz, health, hostile));
		}

		result.sort(Comparator.comparingDouble(e -> e.distance));
		if (result.size() > MAX_ENTITIES) {
			return result.subList(0, MAX_ENTITIES);
		}
		return result;
	}
}