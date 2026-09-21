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

	// 変更後
	/** Ticks to hold the jump key down for a single JUMP action (instantaneous tap). */
	private static final int JUMP_HOLD_TICKS = 2;

	// MOVE_FORWARD is continuous: held down every tick until a different
	// action is chosen, instead of a short burst per decision cycle
	// (a short burst was shorter than the decision interval, causing
	// press/release/press/release stutter-stepping).
	private boolean movingForward = false;
	private int jumpHoldTicksRemaining = 0;

	public void tick(MinecraftClient client) {
		if (client.options == null) return;
		client.options.forwardKey.setPressed(movingForward);
		client.options.jumpKey.setPressed(jumpHoldTicksRemaining > 0);
		if (jumpHoldTicksRemaining > 0) jumpHoldTicksRemaining--;
	}

	public void execute(MinecraftClient client, Action action) {
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			return;
		}

		switch (action.type) {
			case WAIT -> {
				movingForward = false;
			}
// 変更後
			case MOVE_FORWARD -> movingForward = true;
			case JUMP -> {
				if (player.isOnGround()) {
					jumpHoldTicksRemaining = JUMP_HOLD_TICKS;
				}
			}
			case LOOK -> findTarget(client, action.targetEntityUuid).ifPresent(target -> {
				player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, aimPoint(target));
				movingForward = false;
			});
			case ATTACK -> findTarget(client, action.targetEntityUuid).ifPresent(target -> {
				player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, aimPoint(target));
				if (client.interactionManager != null) {
					client.interactionManager.attackEntity(player, target);
				}
				movingForward = false;
			});
			default -> LOGGER.warn("No executor implemented for action type {}", action.type);
		}
	}

	/** Immediately stops any held movement keys. Used by the emergency stop. */
	public void releaseAll(MinecraftClient client) {
		movingForward = false;
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

	private net.minecraft.util.math.Vec3d aimPoint(Entity target) {
		if (target instanceof net.minecraft.entity.LivingEntity living) {
			return living.getEyePos();
		}
		Vec3d Pos = new Vec3d(target.getX(), target.getY(), target.getZ());
		return Pos.add(0, target.getHeight() * 0.5, 0);
	}
}
