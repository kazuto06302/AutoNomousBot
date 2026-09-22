package net.kztmc.mc.autonomousbot.behavior;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

public final class ActionExecutor {

	private static final Logger LOGGER = LoggerFactory.getLogger("AutonomousBot/ActionExecutor");

	private static final int JUMP_HOLD_TICKS = 2;

	private boolean movingForward = false;
	private boolean forwardHeldByBot = false;
	private boolean movingBackward = false;
	private boolean backwardHeldByBot = false;
	private int jumpHoldTicksRemaining = 0;
	private boolean jumpHeldByBot = false;

	public void tick(MinecraftClient client) {
		if (client.options == null) {
			return;
		}

		if (movingForward) {
			client.options.forwardKey.setPressed(true);
			forwardHeldByBot = true;
		} else if (forwardHeldByBot) {
			client.options.forwardKey.setPressed(false);
			forwardHeldByBot = false;
		}

		if (movingBackward) {
			client.options.backKey.setPressed(true);
			backwardHeldByBot = true;
		} else if (backwardHeldByBot) {
			client.options.backKey.setPressed(false);
			backwardHeldByBot = false;
		}

		boolean wantJump = jumpHoldTicksRemaining > 0;
		if (wantJump) {
			client.options.jumpKey.setPressed(true);
			jumpHeldByBot = true;
			jumpHoldTicksRemaining--;
		} else if (jumpHeldByBot) {
			client.options.jumpKey.setPressed(false);
			jumpHeldByBot = false;
		}
	}

	public void execute(MinecraftClient client, Action action) {
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			return;
		}

		switch (action.type) {
			case WAIT -> {
				movingForward = false;
				movingBackward = false;
			}
			case MOVE_FORWARD -> {
				movingForward = true;
				movingBackward = false;
			}
			case RETREAT -> {
				movingBackward = true;
				movingForward = false;
				findTarget(client, action.targetEntityUuid)
						.ifPresent(target -> player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, aimPoint(target, player)));
			}
			case JUMP -> {
				if (player.isOnGround()) {
					jumpHoldTicksRemaining = JUMP_HOLD_TICKS;
				}
			}
			case LOOK -> findTarget(client, action.targetEntityUuid).ifPresent(target -> {
				player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, aimPoint(target, player));
				updateApproach(player, target);
			});
			case ATTACK -> findTarget(client, action.targetEntityUuid).ifPresent(target -> {
				player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, aimPoint(target, player));
				if (client.interactionManager != null) {
					client.interactionManager.attackEntity(player, target);
				}
				movingForward = true;
				movingBackward = false;
			});
			case SPRINT_ATTACK -> findTarget(client, action.targetEntityUuid).ifPresent(target -> {
				player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, aimPoint(target, player));
				// Force sprint state right before the swing - the combat
				// system reads isSprinting() at the moment of the attack
				// to grant the extra sprint/knockback bonus.
				player.setSprinting(true);
				if (client.interactionManager != null) {
					client.interactionManager.attackEntity(player, target);
				}
				movingForward = true;
				movingBackward = false;
			});
			case CRITICAL_ATTACK -> findTarget(client, action.targetEntityUuid).ifPresent(target -> {
				player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, aimPoint(target, player));
				if (client.interactionManager != null) {
					client.interactionManager.attackEntity(player, target);
				}
				// NOTE: whether this actually lands as a critical hit depends
				// on the player still being airborne+falling at THIS exact
				// tick - the WorldState snapshot that made Jev choose this
				// action can be ~1 decision-interval stale, so it's a
				// best-effort attempt, not a guarantee. See caveat below.
				movingForward = true;
				movingBackward = false;
			});
			case SELECT_SLOT -> {
				if (action.targetSlot != null) {
					player.getInventory().setSelectedSlot(action.targetSlot);
				}
			}
			default -> LOGGER.warn("No executor implemented for action type {}", action.type);
		}
	}

	public void releaseAll(MinecraftClient client) {
		movingForward = false;
		forwardHeldByBot = false;
		movingBackward = false;
		backwardHeldByBot = false;
		jumpHoldTicksRemaining = 0;
		jumpHeldByBot = false;
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
			return client.world
					.getOtherEntities(player, player.getBoundingBox().expand(32.0D), e -> e.getUuid().equals(uuid))
					.stream()
					.findFirst();
		} catch (IllegalArgumentException e) {
			LOGGER.warn("Invalid target UUID in decision: {}", uuidString);
			return Optional.empty();
		}
	}

	private Vec3d aimPoint(Entity target, ClientPlayerEntity player) {
		if (target.getHeight() >= player.getStandingEyeHeight()) {
			return new Vec3d(target.getX(), player.getEyeY(), target.getZ());
		}
		if (target instanceof LivingEntity living) {
			return living.getEyePos();
		}
		return new Vec3d(target.getX(), target.getY(), target.getZ()).add(0, target.getHeight() * 0.5, 0);
	}

	/**
	 * Keeps closing the distance only while still far from the target.
	 * Once within RETREAT_TRIGGER_RANGE we stop pushing forward - Jev still
	 * has to explicitly choose RETREAT to back off, but at minimum we no
	 * longer walk the player further into the mob on every LOOK/ATTACK cycle.
	 */
	private void updateApproach(ClientPlayerEntity player, Entity target) {
		boolean shouldApproach = player.distanceTo(target) > CandidateActionGenerator.RETREAT_TRIGGER_RANGE;
		movingForward = shouldApproach;
		movingBackward = false;
	}
}