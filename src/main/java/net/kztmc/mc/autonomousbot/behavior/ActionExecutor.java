package net.kztmc.mc.autonomousbot.behavior;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

public final class ActionExecutor {

	private static final Logger LOGGER = LoggerFactory.getLogger("AutonomousBot/ActionExecutor");

	private static final int JUMP_HOLD_TICKS = 2;
	private static final int FALL_CHECK_DEPTH = 4;

	private boolean movingForward = false;
	private boolean forwardHeldByBot = false;
	private boolean movingBackward = false;
	private boolean backwardHeldByBot = false;
	private int jumpHoldTicksRemaining = 0;
	private boolean jumpHeldByBot = false;
	private static final int EAT_HOLD_TICKS = 40;
	private int eatHoldTicksRemaining = 0;
	private boolean useHeldByBot = false;

	public void tick(MinecraftClient client) {
		if (client.options == null) {
			return;
		}

		ClientPlayerEntity player = client.player;

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

		boolean wantForward = movingForward && player != null
				&& !isHazardAhead(client, player, player.getYaw());
		if (wantForward) {
			client.options.forwardKey.setPressed(true);
			forwardHeldByBot = true;
		} else if (forwardHeldByBot) {
			client.options.forwardKey.setPressed(false);
			forwardHeldByBot = false;
		}

		boolean wantBackward = movingBackward && player != null
				&& !isHazardAhead(client, player, player.getYaw() + 180.0f);
		if (wantBackward) {
			client.options.backKey.setPressed(true);
			backwardHeldByBot = true;
		} else if (backwardHeldByBot) {
			client.options.backKey.setPressed(false);
			backwardHeldByBot = false;
		}

		boolean wantEat = eatHoldTicksRemaining > 0;
		if (wantEat) {
			client.options.useKey.setPressed(true);
			useHeldByBot = true;
			eatHoldTicksRemaining--;
		} else if (useHeldByBot) {
			client.options.useKey.setPressed(false);
			useHeldByBot = false;
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
				updateApproach(player, target, action.holdGround);
			});
			case ATTACK -> findTarget(client, action.targetEntityUuid).ifPresent(target -> {
				player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, aimPoint(target, player));
				if (client.interactionManager != null) {
					client.interactionManager.attackEntity(player, target);
				}
				updateApproach(player, target, action.holdGround);
			});
			case SPRINT_ATTACK -> findTarget(client, action.targetEntityUuid).ifPresent(target -> {
				player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, aimPoint(target, player));
				player.setSprinting(true);
				if (client.interactionManager != null) {
					client.interactionManager.attackEntity(player, target);
				}
				updateApproach(player, target, action.holdGround);
			});
			case CRITICAL_ATTACK -> findTarget(client, action.targetEntityUuid).ifPresent(target -> {
				movingForward = false;
				movingBackward = false;
				// allowHorizontalOverride=false: ジャンプ中は水平合わせせず、
				// 素直に敵の実座標を狙う（水平合わせだと空中で明後日の方向を向く）。
				player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, aimPoint(target, player, false));
				if (client.interactionManager != null) {
					client.interactionManager.attackEntity(player, target);
				}
			});
			case SELECT_SLOT -> {
				if (action.targetSlot != null) {
					player.getInventory().setSelectedSlot(action.targetSlot);
				}
			}
			case EAT -> {
				if (action.targetSlot != null) {
					player.getInventory().setSelectedSlot(action.targetSlot);
				}
				eatHoldTicksRemaining = EAT_HOLD_TICKS;
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
		eatHoldTicksRemaining = 0;
		useHeldByBot = false;
		if (client.options != null) {
			client.options.forwardKey.setPressed(false);
			client.options.backKey.setPressed(false);
			client.options.leftKey.setPressed(false);
			client.options.rightKey.setPressed(false);
			client.options.jumpKey.setPressed(false);
		}
	}

	public boolean isRetreating() {
		return movingBackward;
	}

	public boolean isEating() {
		return eatHoldTicksRemaining > 0;
	}

	public void approachTarget(ClientPlayerEntity player, Entity target) {
		player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, aimPoint(target, player));
		player.setSprinting(true);
		movingForward = true;
		movingBackward = false;
	}

	public void stopApproaching() {
		movingForward = false;
	}

	public void reflexRetreat(ClientPlayerEntity player, Entity target) {
		player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, aimPoint(target, player));
		movingBackward = true;
		movingForward = false;
	}

	public void stopRetreating() {
		movingBackward = false;
	}

	/**
	 * プレイヤーの目線からaimPoint()への直線が、実際に相手の当たり判定
	 * ボックスと交差するかを確認する。射程内かどうかだけでなく、
	 * 「その姿勢で振ったら本当に当たるか」をチェックしたい場合に使う。
	 */
	public boolean wouldHit(ClientPlayerEntity player, Entity target, boolean allowHorizontalOverride) {
		Vec3d eye = player.getEyePos();
		Vec3d aim = aimPoint(target, player, allowHorizontalOverride);
		Vec3d toAim = aim.subtract(eye);
		double dist = toAim.length();
		if (dist < 0.0001D) {
			return true;
		}
		Vec3d rayEnd = eye.add(toAim.normalize().multiply(dist + 0.5D));
		return target.getBoundingBox().raycast(eye, rayEnd).isPresent();
	}

	private void updateApproach(ClientPlayerEntity player, Entity target, boolean holdGround) {
		boolean shouldApproach = !holdGround && player.distanceTo(target) > CandidateActionGenerator.RETREAT_TRIGGER_RANGE;
		movingForward = shouldApproach;
		movingBackward = false;
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
		return aimPoint(target, player, true);
	}

	private Vec3d aimPoint(Entity target, ClientPlayerEntity player, boolean allowHorizontalOverride) {
		if (allowHorizontalOverride && target.getHeight() >= player.getStandingEyeHeight()) {
			return new Vec3d(target.getX(), player.getEyeY(), target.getZ());
		}
		if (target instanceof LivingEntity living) {
			return living.getEyePos();
		}
		return new Vec3d(target.getX(), target.getY(), target.getZ()).add(0, target.getHeight() * 0.5, 0);
	}

	private boolean isHazardAhead(MinecraftClient client, ClientPlayerEntity player, double yawDegrees) {
		if (client.world == null) {
			return false;
		}
		double yawRad = Math.toRadians(yawDegrees);
		int dx = (int) Math.round(-Math.sin(yawRad));
		int dz = (int) Math.round(Math.cos(yawRad));
		if (dx == 0 && dz == 0) {
			return false;
		}

		BlockPos ahead = player.getBlockPos().add(dx, 0, dz);

		if (isDangerousBlock(client, ahead) || isDangerousBlock(client, ahead.up())) {
			return true;
		}

		for (int dy = 0; dy > -FALL_CHECK_DEPTH; dy--) {
			BlockPos below = ahead.add(0, dy, 0);
			if (isDangerousBlock(client, below)) {
				return true; // 落下の途中に溶岩などがあっても危険
			}
			if (!client.world.getBlockState(below).isAir()) {
				return false; // FALL_CHECK_DEPTH以内に着地できる地面がある
			}
		}
		return true; // 地面が見つからない = 危険な落下
	}

	private boolean isDangerousBlock(MinecraftClient client, BlockPos pos) {
		var state = client.world.getBlockState(pos);
		return state.getFluidState().isIn(FluidTags.LAVA)
				|| state.isOf(Blocks.FIRE)
				|| state.isOf(Blocks.MAGMA_BLOCK);
	}
}