package net.kztmc.mc.autonomousbot.perception;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Scans a small bounding box around the player for non-air blocks.
 * Kept intentionally small (radius + cap) - see the "no full-world dumps"
 * requirement in the design doc.
 */
public final class BlockScanner {

	private static final int HORIZONTAL_RADIUS = 4;
	private static final int VERTICAL_RADIUS_DOWN = 2;
	private static final int VERTICAL_RADIUS_UP = 2;
	private static final int MAX_BLOCKS = 64;

	private BlockScanner() {
	}

	public static List<BlockSummary> scanAround(World world, BlockPos center) {
		List<BlockSummary> result = new ArrayList<>();
		for (int dx = -HORIZONTAL_RADIUS; dx <= HORIZONTAL_RADIUS && result.size() < MAX_BLOCKS; dx++) {
			for (int dz = -HORIZONTAL_RADIUS; dz <= HORIZONTAL_RADIUS && result.size() < MAX_BLOCKS; dz++) {
				for (int dy = -VERTICAL_RADIUS_DOWN; dy <= VERTICAL_RADIUS_UP && result.size() < MAX_BLOCKS; dy++) {
					BlockPos pos = center.add(dx, dy, dz);
					BlockState state = world.getBlockState(pos);
					if (state.isAir() || state.isOf(Blocks.CAVE_AIR) || state.isOf(Blocks.VOID_AIR)) {
						continue;
					}
					String id = Registries.BLOCK.getId(state.getBlock()).toString();
					result.add(new BlockSummary(dx, dy, dz, id, pos.getX(), pos.getY(), pos.getZ()));
				}
			}
		}
		return result;
	}
}
