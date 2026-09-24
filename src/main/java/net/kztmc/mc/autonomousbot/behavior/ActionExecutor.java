package net.kztmc.mc.autonomousbot.behavior;

import net.kztmc.mc.autonomousbot.crafting.Recipe;
import net.kztmc.mc.autonomousbot.crafting.RecipeBook;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

public final class ActionExecutor {

	private static final Logger LOGGER = LoggerFactory.getLogger("AutonomousBot/ActionExecutor");

	private static final int JUMP_HOLD_TICKS = 2;
	private static final int EAT_HOLD_TICKS = 40;
	private static final double STUCK_MOVE_THRESHOLD = 0.02D;
	private static final int STUCK_TICKS_THRESHOLD = 8;
	private static final int UNSTUCK_DURATION_TICKS = 12;
	private static final int FALL_CHECK_DEPTH = 4;
	private static final int MINING_TIMEOUT_TICKS = 200;
	private static final int CRAFT_SCREEN_TIMEOUT_TICKS = 30;

	private boolean movingForward = false;
	private boolean forwardHeldByBot = false;
	private boolean movingBackward = false;
	private boolean backwardHeldByBot = false;
	private int jumpHoldTicksRemaining = 0;
	private boolean jumpHeldByBot = false;
	private int eatHoldTicksRemaining = 0;
	private boolean useHeldByBot = false;

	private Vec3d lastPos = null;
	private int stuckTicks = 0;
	private int unstuckTicksRemaining = 0;
	private boolean strafeRight = true;
	private boolean strafeHeldByBot = false;

	private BlockPos miningTarget = null;
	private int miningTicks = 0;

	private enum CraftPhase { NONE, ENSURE_TABLE, OPEN_TABLE, WAIT_SCREEN }
	private CraftPhase craftPhase = CraftPhase.NONE;
	private String pendingRecipeId;
	private int craftWaitTicks = 0;

	public void tick(MinecraftClient client) {
		if (client.options == null) {
			return;
		}
		ClientPlayerEntity player = client.player;

		updateStuckDetection(player);

		boolean unstucking = unstuckTicksRemaining > 0;
		boolean busy = miningTarget != null || craftPhase != CraftPhase.NONE;

		boolean wantForward = !unstucking && !busy && movingForward && player != null
				&& !isHazardAhead(client, player, player.getYaw());
		if (wantForward) {
			client.options.forwardKey.setPressed(true);
			forwardHeldByBot = true;
		} else if (forwardHeldByBot) {
			client.options.forwardKey.setPressed(false);
			forwardHeldByBot = false;
		}

		boolean wantBackward = !unstucking && !busy && movingBackward && player != null
				&& !isHazardAhead(client, player, player.getYaw() + 180.0f);
		if (wantBackward) {
			client.options.backKey.setPressed(true);
			backwardHeldByBot = true;
		} else if (backwardHeldByBot) {
			client.options.backKey.setPressed(false);
			backwardHeldByBot = false;
		}

		boolean wantStrafe = unstucking && !busy;
		if (wantStrafe) {
			(strafeRight ? client.options.rightKey : client.options.leftKey).setPressed(true);
			strafeHeldByBot = true;
			unstuckTicksRemaining--;
		} else if (strafeHeldByBot) {
			client.options.leftKey.setPressed(false);
			client.options.rightKey.setPressed(false);
			strafeHeldByBot = false;
		}

		boolean wantJump = (jumpHoldTicksRemaining > 0 || (unstucking && !busy));
		if (wantJump) {
			client.options.jumpKey.setPressed(true);
			jumpHeldByBot = true;
			if (jumpHoldTicksRemaining > 0) {
				jumpHoldTicksRemaining--;
			}
		} else if (jumpHeldByBot) {
			client.options.jumpKey.setPressed(false);
			jumpHeldByBot = false;
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

		if (miningTarget != null) {
			tickMining(client);
		}
		if (craftPhase != CraftPhase.NONE) {
			tickCraft(client);
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
			case MINE -> {
				if (action.targetBlockX != null) {
					startMining(player, new BlockPos(action.targetBlockX, action.targetBlockY, action.targetBlockZ));
				}
			}
			case CRAFT -> {
				if (action.recipeId != null) {
					startCraft(action.recipeId);
				}
			}
			case PLACE_CRAFTING_TABLE -> placeCraftingTable(client, player);
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
		unstuckTicksRemaining = 0;
		strafeHeldByBot = false;
		stuckTicks = 0;
		lastPos = null;
		miningTarget = null;
		craftPhase = CraftPhase.NONE;
		if (client.options != null) {
			client.options.forwardKey.setPressed(false);
			client.options.backKey.setPressed(false);
			client.options.leftKey.setPressed(false);
			client.options.rightKey.setPressed(false);
			client.options.jumpKey.setPressed(false);
			client.options.useKey.setPressed(false);
		}
		if (client.player != null && client.player.currentScreenHandler != client.player.playerScreenHandler) {
			client.player.closeHandledScreen();
		}
	}

	public boolean isRetreating() {
		return movingBackward;
	}

	public boolean isEating() {
		return eatHoldTicksRemaining > 0;
	}

	public boolean isMining() {
		return miningTarget != null;
	}

	public boolean isCrafting() {
		return craftPhase != CraftPhase.NONE;
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

	public void navigateTowards(ClientPlayerEntity player, Vec3d horizontalTarget) {
		Vec3d lookTarget = new Vec3d(horizontalTarget.x, player.getEyeY(), horizontalTarget.z);
		player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, lookTarget);
		movingForward = true;
		movingBackward = false;
	}

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

	// ------------------------------------------------------------------
	// 採掘
	// ------------------------------------------------------------------

	private void startMining(ClientPlayerEntity player, BlockPos pos) {
		craftPhase = CraftPhase.NONE;
		miningTarget = pos;
		miningTicks = 0;
		movingForward = false;
		movingBackward = false;
		player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, Vec3d.ofCenter(pos));
	}

	private void tickMining(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || client.interactionManager == null || miningTarget == null) {
			miningTarget = null;
			return;
		}
		if (client.world.getBlockState(miningTarget).isAir()) {
			miningTarget = null; // 採掘完了
			return;
		}
		if (miningTicks++ > MINING_TIMEOUT_TICKS) {
			client.interactionManager.cancelBlockBreaking();
			miningTarget = null;
			return;
		}
		client.interactionManager.updateBlockBreakingProgress(miningTarget, Direction.UP);
		player.swingHand(Hand.MAIN_HAND);
	}

	// ------------------------------------------------------------------
	// クラフト
	// ------------------------------------------------------------------

	private void startCraft(String recipeId) {
		miningTarget = null;
		pendingRecipeId = recipeId;
		craftPhase = CraftPhase.ENSURE_TABLE;
		craftWaitTicks = 0;
	}

	private void tickCraft(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || client.interactionManager == null) {
			craftPhase = CraftPhase.NONE;
			return;
		}

		switch (craftPhase) {
			case ENSURE_TABLE -> {
				BlockPos table = findNearbyCraftingTable(client, player);
				if (table != null) {
					openCraftingTable(client, player, table);
				} else if (ensureHeldInHotbar(client, player, "minecraft:crafting_table")) {
					placeCraftingTable(client, player);
					craftWaitTicks = 0;
					craftPhase = CraftPhase.OPEN_TABLE;
				} else {
					LOGGER.warn("Cannot craft {}: no crafting table nearby and none in inventory", pendingRecipeId);
					craftPhase = CraftPhase.NONE;
				}
			}
			case OPEN_TABLE -> {
				craftWaitTicks++;
				BlockPos table = findNearbyCraftingTable(client, player);
				if (table != null) {
					openCraftingTable(client, player, table);
				} else if (craftWaitTicks > CRAFT_SCREEN_TIMEOUT_TICKS) {
					LOGGER.warn("Placed crafting table but couldn't find it again for {}", pendingRecipeId);
					craftPhase = CraftPhase.NONE;
				}
			}
			case WAIT_SCREEN -> {
				craftWaitTicks++;
				if (player.currentScreenHandler instanceof CraftingScreenHandler) {
					performCraft(client, player);
					craftPhase = CraftPhase.NONE;
				} else if (craftWaitTicks > CRAFT_SCREEN_TIMEOUT_TICKS) {
					LOGGER.warn("Crafting table screen never opened for {}", pendingRecipeId);
					craftPhase = CraftPhase.NONE;
				}
			}
			default -> craftPhase = CraftPhase.NONE;
		}
	}

	private void openCraftingTable(MinecraftClient client, ClientPlayerEntity player, BlockPos table) {
		player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, Vec3d.ofCenter(table));
		BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(table), Direction.UP, table, false);
		client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
		craftWaitTicks = 0;
		craftPhase = CraftPhase.WAIT_SCREEN;
	}

	private void performCraft(MinecraftClient client, ClientPlayerEntity player) {
		int syncId = player.currentScreenHandler.syncId;

		if ("planks".equals(pendingRecipeId)) {
			int logSlot = findScreenSlot(player, id -> id.endsWith("_log"));
			if (logSlot < 0) {
				LOGGER.warn("No log found to craft planks");
				player.closeHandledScreen();
				return;
			}
			client.interactionManager.clickSlot(syncId, logSlot, 0, SlotActionType.PICKUP, player);
			client.interactionManager.clickSlot(syncId, 5, 0, SlotActionType.PICKUP, player);
		} else {
			Recipe recipe = RecipeBook.get(pendingRecipeId);
			if (recipe == null) {
				LOGGER.warn("Unknown recipe id {}", pendingRecipeId);
				player.closeHandledScreen();
				return;
			}
			for (var entry : recipe.gridSlots().entrySet()) {
				int cell = entry.getKey();
				String itemId = entry.getValue();
				int sourceSlot = findScreenSlot(player, id -> id.equals(itemId));
				if (sourceSlot < 0) {
					LOGGER.warn("Missing ingredient {} for recipe {}", itemId, pendingRecipeId);
					player.closeHandledScreen();
					return;
				}
				client.interactionManager.clickSlot(syncId, sourceSlot, 0, SlotActionType.PICKUP, player);
				client.interactionManager.clickSlot(syncId, cell, 0, SlotActionType.PICKUP, player);
			}
		}

		// 出力スロット(0)をシフトクリックしてクラフト結果をインベントリへ回収
		client.interactionManager.clickSlot(syncId, 0, 0, SlotActionType.QUICK_MOVE, player);
		player.closeHandledScreen();
	}

	/** CraftingScreenHandlerの1-9(グリッド)を除いた10-45(インベントリ+ホットバー)からitemIdを探す。 */
	private int findScreenSlot(ClientPlayerEntity player, java.util.function.Predicate<String> matcher) {
		var slots = player.currentScreenHandler.slots;
		for (int i = 10; i < slots.size() && i < 46; i++) {
			ItemStack stack = slots.get(i).getStack();
			if (!stack.isEmpty() && matcher.test(Registries.ITEM.getId(stack.getItem()).toString())) {
				return i;
			}
		}
		return -1;
	}

	// ------------------------------------------------------------------
	// クラフト台の設置
	// ------------------------------------------------------------------

	private void placeCraftingTable(MinecraftClient client, ClientPlayerEntity player) {
		if (!ensureHeldInHotbar(client, player, "minecraft:crafting_table")) {
			return;
		}
		BlockPos base = findPlaceablePosition(client, player);
		if (base == null) {
			LOGGER.warn("No valid spot found to place crafting table");
			return;
		}
		player.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, Vec3d.ofCenter(base).add(0, 0.5, 0));
		BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(base).add(0, 0.5, 0), Direction.UP, base, false);
		client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
	}

	private BlockPos findPlaceablePosition(MinecraftClient client, ClientPlayerEntity player) {
		BlockPos feet = player.getBlockPos();
		BlockPos[] candidates = {
				feet.offset(player.getHorizontalFacing()).down(),
				feet.offset(player.getHorizontalFacing().rotateYClockwise()).down(),
				feet.offset(player.getHorizontalFacing().rotateYCounterclockwise()).down(),
				feet.offset(player.getHorizontalFacing().getOpposite()).down(),
		};
		for (BlockPos candidate : candidates) {
			if (!client.world.getBlockState(candidate).isAir() && client.world.getBlockState(candidate.up()).isAir()) {
				return candidate;
			}
		}
		return null;
	}

	private BlockPos findNearbyCraftingTable(MinecraftClient client, ClientPlayerEntity player) {
		BlockPos center = player.getBlockPos();
		for (int dx = -3; dx <= 3; dx++) {
			for (int dy = -1; dy <= 2; dy++) {
				for (int dz = -3; dz <= 3; dz++) {
					BlockPos pos = center.add(dx, dy, dz);
					if (client.world.getBlockState(pos).isOf(Blocks.CRAFTING_TABLE)) {
						return pos;
					}
				}
			}
		}
		return null;
	}

	/** 指定itemIdをホットバー(0-8)に持ってくる。既にあればそのスロットを選択するだけ。 */
	private boolean ensureHeldInHotbar(MinecraftClient client, ClientPlayerEntity player, String itemId) {
		PlayerInventory inv = player.getInventory();
		for (int i = 0; i <= 8; i++) {
			if (matches(inv.getMainStacks().get(i), itemId)) {
				inv.setSelectedSlot(i);
				return true;
			}
		}
		for (int i = 9; i < inv.getMainStacks().size(); i++) {
			if (matches(inv.getMainStacks().get(i), itemId)) {
				int hotbarSlot = inv.getSelectedSlot();
				client.interactionManager.clickSlot(player.playerScreenHandler.syncId, i, hotbarSlot, SlotActionType.SWAP, player);
				return true;
			}
		}
		return false;
	}

	private boolean matches(ItemStack stack, String itemId) {
		return !stack.isEmpty() && Registries.ITEM.getId(stack.getItem()).toString().equals(itemId);
	}

	// ------------------------------------------------------------------
	// 既存機能(移動・詰まり検知・危険地形回避・照準)
	// ------------------------------------------------------------------

	private void updateApproach(ClientPlayerEntity player, Entity target, boolean holdGround) {
		boolean shouldApproach = !holdGround && player.distanceTo(target) > CandidateActionGenerator.RETREAT_TRIGGER_RANGE;
		movingForward = shouldApproach;
		movingBackward = false;
	}

	private void updateStuckDetection(ClientPlayerEntity player) {
		if (player == null) {
			lastPos = null;
			stuckTicks = 0;
			return;
		}
		Vec3d currentPos = new Vec3d(player.getX(), player.getY(), player.getZ());

		boolean tryingToMove = (movingForward || movingBackward) && unstuckTicksRemaining <= 0
				&& miningTarget == null && craftPhase == CraftPhase.NONE;
		if (tryingToMove && lastPos != null) {
			double dx = currentPos.x - lastPos.x;
			double dz = currentPos.z - lastPos.z;
			double horizontalMoved = Math.sqrt(dx * dx + dz * dz);
			if (horizontalMoved < STUCK_MOVE_THRESHOLD) {
				stuckTicks++;
			} else {
				stuckTicks = 0;
			}
		} else {
			stuckTicks = 0;
		}

		if (stuckTicks >= STUCK_TICKS_THRESHOLD) {
			unstuckTicksRemaining = UNSTUCK_DURATION_TICKS;
			strafeRight = !strafeRight;
			stuckTicks = 0;
		}

		lastPos = currentPos;
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
				return true;
			}
			if (!client.world.getBlockState(below).isAir()) {
				return false;
			}
		}
		return true;
	}

	private boolean isDangerousBlock(MinecraftClient client, BlockPos pos) {
		var state = client.world.getBlockState(pos);
		return state.getFluidState().isIn(FluidTags.LAVA)
				|| state.isOf(Blocks.FIRE)
				|| state.isOf(Blocks.MAGMA_BLOCK);
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
}