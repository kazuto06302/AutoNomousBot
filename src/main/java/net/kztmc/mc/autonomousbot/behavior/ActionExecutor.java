package net.kztmc.mc.autonomousbot.behavior;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

/**
 * Translates a Decision/Action into actual client-side Minecraft input.
 *
 * Movement (and jumping) is done the same way the game itself reads input:
 * by holding down the configured {@code GameOptions} KeyBinding for a tick,
 * exactly like a held keyboard key would. This intentionally avoids any
 * Mixin into the input/movement internals - see design constraint "avoid
 * unnecessary Mixins, prefer existing Fabric/vanilla API".
 */
public final class ActionExecutor {

	private static final Logger LOGGER = LoggerFactory.getLogger("AutonomousBot/ActionExecutor");

	/** Ticks to hold the forward key down for a single MOVE_FORWARD action. */
	private static final int MOVE_HOLD_TICKS = 4;
	/** Ticks to hold the jump key down for a single JUMP action. */
	private static final int JUMP_HOLD_TICKS = 2;

	private int forwardHoldTicksRemaining = 0;
	private int jumpHoldTicksRemaining = 0;

	/**
	 * Called once per client tick regardless of whether a new decision just
	 * arrived, so held keys get released again after their duration.
	 */
	public void tick(MinecraftClient client) {
		if (client.options == null) {
			return;
		}
		if (forwardHoldTicksRemaining > 0) {
			forwardHoldTicksRemaining--;
			client.options.forwardKey.setPressed(true);
			if (forwardHoldTicksRemaining == 0) {
				client.options.forwardKey.setPressed(false);
			}
		}
		if (jumpHoldTicksRemaining > 0) {
			jumpHoldTicksRemaining--;
			client.options.jumpKey.setPressed(true);
			if (jumpHoldTicksRemaining == 0) {
				client.options.jumpKey.setPressed(false);
			}
		}
	}

	public void execute(MinecraftClient client, Action action) {
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			return;
		}

		switch (action.type) {
			case WAIT -> {
				// intentionally nothing - safe default state
			}
			case MOVE_FORWARD -> forwardHoldTicksRemaining = MOVE_HOLD_TICKS;
			case JUMP -> {
				if (player.isOnGround()) {
					jumpHoldTicksRemaining = JUMP_HOLD_TICKS;
				}
			}
			case LOOK -> findTarget(client, action.targetEntityUuid)
				.ifPresent(target -> player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, new Vec3d(target.getX(), target.getY(), target.getZ())));
			case ATTACK -> findTarget(client, action.targetEntityUuid).ifPresent(target -> {
				player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, new Vec3d(target.getX(), target.getY(), target.getZ()));
				if (client.interactionManager != null) {
					client.interactionManager.attackEntity(player, target);
				}
			});
			default -> LOGGER.warn("No executor implemented for action type {}", action.type);
		}
	}

	/** Immediately stops any held movement keys. Used by the emergency stop. */
	public void releaseAll(MinecraftClient client) {
		forwardHoldTicksRemaining = 0;
		jumpHoldTicksRemaining = 0;
		if (client.options != null) {
			client.options.forwardKey.setPressed(false);
			client.options.backKey.setPressed(false);
			client.options.leftKey.setPressed(false);
			client.options.rightKey.setPressed(false);
			client.options.jumpKey.setPressed(false);
		}
	}

	private Optional<Entity> findTarget(MinecraftClient client, String uuidString) {
		ClientPlayerEntity player = client.player;
		if (uuidString == null || player == null || client.world == null) {
			return Optional.empty();
		}
		try {
			UUID uuid = UUID.fromString(uuidString);
			// Bounded search box (same radius EntityScanner used to find this
			// target in the first place) rather than iterating every loaded entity.
			return client.world
				.getOtherEntities(player, player.getBoundingBox().expand(32.0D), e -> e.getUuid().equals(uuid))
				.stream()
				.findFirst();
		} catch (IllegalArgumentException e) {
			LOGGER.warn("Invalid target UUID in decision: {}", uuidString);
			return Optional.empty();
		}
	}
}
