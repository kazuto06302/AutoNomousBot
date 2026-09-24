package net.kztmc.mc.autonomousbot.behavior;

import net.kztmc.mc.autonomousbot.perception.BlockSummary;
import net.kztmc.mc.autonomousbot.perception.EntitySummary;
import net.kztmc.mc.autonomousbot.perception.ItemSummary;
import net.kztmc.mc.autonomousbot.perception.WorldState;

import java.util.*;

public final class CandidateActionGenerator {

	public static final double ATTACK_RANGE = 4D;
	public static final double RETREAT_TRIGGER_RANGE = 2.5D;
	public static final float COOLDOWN_READY_THRESHOLD = 0.9f;
	private static final double EAT_SAFE_RADIUS = 6.0D;

	private static final Set<String> FOOD_ITEM_IDS = Set.of(
			"minecraft:apple",
			"minecraft:golden_apple",
			"minecraft:enchanted_golden_apple",
			"minecraft:bread",
			"minecraft:cooked_beef",
			"minecraft:cooked_porkchop",
			"minecraft:cooked_chicken",
			"minecraft:cooked_mutton",
			"minecraft:cooked_rabbit",
			"minecraft:cooked_cod",
			"minecraft:cooked_salmon",
			"minecraft:baked_potato",
			"minecraft:carrot",
			"minecraft:golden_carrot",
			"minecraft:potato",
			"minecraft:beetroot",
			"minecraft:melon_slice",
			"minecraft:sweet_berries",
			"minecraft:glow_berries",
			"minecraft:pumpkin_pie",
			"minecraft:mushroom_stew",
			"minecraft:rabbit_stew",
			"minecraft:beetroot_soup",
			"minecraft:dried_kelp",
			"minecraft:honey_bottle",
			"minecraft:chorus_fruit",
			"minecraft:suspicious_stew"
	);

	private static final int HUNGER_EAT_THRESHOLD = 18; // これ未満なら食べる候補を出す
	private static final int HUNGER_URGENT_THRESHOLD = 6; // これ以下なら緊急扱い

	private CandidateActionGenerator() {
	}

	public static List<Action> generate(WorldState state) {
		List<Action> unordered = new ArrayList<>();

		unordered.add(new Action(null, ActionType.WAIT,
				"Do nothing this cycle and keep observing", null, null));
		unordered.add(new Action(null, ActionType.MOVE_FORWARD,
				"Walk forward in the direction currently facing", null, null));
		if (state.player.onGround) {
			unordered.add(new Action(null, ActionType.JUMP, "Jump straight up", null, null));
		}

		Optional<EntitySummary> nearestHostile = state.nearbyEntities.stream()
				.filter(e -> e.hostile)
				.min((a, b) -> Double.compare(a.distance, b.distance));

		if (nearestHostile.isPresent()) {
			EntitySummary target = nearestHostile.get();
			boolean inRange = target.distance <= ATTACK_RANGE;
			boolean cooldownReady = state.player.attackCooldownProgress >= COOLDOWN_READY_THRESHOLD;
			boolean isFalling = !state.player.onGround && state.player.velocityY < 0;

			long hostilesNearby = state.nearbyEntities.stream()
					.filter(e -> e.hostile)
					.filter(e -> e.distance <= ATTACK_RANGE)
					.count();
			boolean surrounded = hostilesNearby >= 2;
			double retreatRange = surrounded ? ATTACK_RANGE : RETREAT_TRIGGER_RANGE;

			unordered.add(new Action(null, ActionType.LOOK,
					"Turn to face the nearest hostile mob " + target.entityId
							+ " (" + String.format("%.1f", target.distance) + " blocks away)"
							+ (surrounded ? ". You are surrounded by " + hostilesNearby + " hostile mobs - "
							+ "hold your ground and let it come to you rather than approaching." : ""),
					target.entityUuid, null, surrounded));

			if (target.distance <= retreatRange) {
				String urgency = surrounded
						? " You are surrounded by " + hostilesNearby + " hostile mobs at close range - "
						+ "retreating now is strongly advised to avoid taking hits from the others."
						: "";
				unordered.add(new Action(null, ActionType.RETREAT,
						"Back away from " + target.entityId + " to create space instead of trading hits at melee range." + urgency,
						target.entityUuid, null));
			}

			if (inRange && cooldownReady) {
				unordered.add(new Action(null, ActionType.ATTACK,
						"Attack " + target.entityId + " with a normal swing (weapon ready, "
								+ String.format("%.1f", target.distance) + " blocks away)",
						target.entityUuid, null, surrounded));

				unordered.add(new Action(null, ActionType.SPRINT_ATTACK,
						"Sprint in and attack " + target.entityId + " for a sprint/knockback hit",
						target.entityUuid, null, surrounded));

				if (isFalling) {
					unordered.add(new Action(null, ActionType.CRITICAL_ATTACK,
							"You are currently falling - attack " + target.entityId + " now for a critical hit",
							target.entityUuid, null, surrounded));
				}
			}

			findWeaponSlot(state).ifPresent(weapon -> {
				if (weapon.slot != state.player.selectedSlot) {
					unordered.add(new Action(null, ActionType.SELECT_SLOT,
							"Switch to the weapon in hotbar slot " + (weapon.slot + 1)
									+ " (" + weapon.itemId + ") before engaging",
							null, weapon.slot));
				}
			});
		}

		boolean hungryEnough = state.player.food < HUNGER_EAT_THRESHOLD;
		boolean healthLow = state.player.health <= state.player.maxHealth * 0.5f;

		if (hungryEnough || healthLow) {
			// 安全圏内(EAT_SAFE_RADIUS)に敵対Mobがいるなら、食べる前にまず離れる
			// 候補を出す - 食事は約1.6秒(32tick)硬直するので、近くに敵がいる
			// 状態で始めると殴られながら食べることになるため。
			Optional<EntitySummary> threatWhileEating = state.nearbyEntities.stream()
					.filter(e -> e.hostile)
					.filter(e -> e.distance <= EAT_SAFE_RADIUS)
					.min((a, b) -> Double.compare(a.distance, b.distance));

			if (threatWhileEating.isPresent()) {
				EntitySummary threat = threatWhileEating.get();
				unordered.add(new Action(null, ActionType.RETREAT,
						"A hostile mob (" + threat.entityId + ", " + String.format("%.1f", threat.distance)
								+ " blocks away) is nearby and you need to eat - back away to a safe distance first",
						threat.entityUuid, null));
			} else {
				findFoodSlot(state).ifPresent(food -> {
					boolean urgent = state.player.food <= HUNGER_URGENT_THRESHOLD || healthLow;
					unordered.add(new Action(null, ActionType.EAT,
							(urgent
									? "Health/hunger is critically low (HP " + String.format("%.0f", state.player.health)
									+ "/" + String.format("%.0f", state.player.maxHealth) + ", food " + state.player.food
									+ "/20) - eat now: "
									: "Food is not full (" + state.player.food + "/20) - eat ")
									+ food.itemId + " from hotbar slot " + (food.slot + 1),
							null, food.slot));
				});
			}
		}

		addGatheringCandidates(unordered, state);

		Collections.shuffle(unordered);

		List<Action> candidates = new ArrayList<>();
		char nextId = 'A';
		for (Action a : unordered) {
			candidates.add(new Action(String.valueOf(nextId++), a.type, a.label, a.targetEntityUuid, a.targetSlot,
					a.holdGround, a.targetBlockX, a.targetBlockY, a.targetBlockZ, a.recipeId));
		}
		return candidates;
	}

	private static Optional<ItemSummary> findWeaponSlot(WorldState state) {
		Optional<ItemSummary> sword = state.inventory.stream()
				.filter(i -> i.slot >= 0 && i.slot <= 8)
				.filter(i -> i.itemId.endsWith("_sword"))
				.findFirst();
		if (sword.isPresent()) {
			return sword;
		}
		return state.inventory.stream()
				.filter(i -> i.slot >= 0 && i.slot <= 8)
				.filter(i -> i.itemId.endsWith("_axe"))
				.findFirst();
	}

	private static Optional<ItemSummary> findFoodSlot(WorldState state) {
		return state.inventory.stream()
				.filter(i -> i.slot >= 0 && i.slot <= 8)
				.filter(i -> FOOD_ITEM_IDS.contains(i.itemId))
				.findFirst();
	}

	private static final String[] TOOL_PRIORITY = {
			"minecraft:wooden_pickaxe",
			"minecraft:stone_pickaxe",
			"minecraft:stone_axe",
			"minecraft:stone_shovel",
			"minecraft:stone_sword"
	};

	/**
	 * 「石ツール一式」に向けて、まだ持っていない中で最優先のツールを1つ決め、
	 * それを作るために"今すぐやるべき次の一手"だけを候補として1つ出す。
	 * 複数の段取りを同時に提示しない(作業台→棒→材料→完成、を順番に1手ずつ)。
	 */
	private static void addGatheringCandidates(List<Action> unordered, WorldState state) {
		String goalItem = Arrays.stream(TOOL_PRIORITY).filter(candidate -> !hasExactItem(state, candidate)).findFirst().orElse(null);
        if (goalItem == null) {
			return; // 石ツール一式そろった
		}

		boolean tableNearby = state.nearbyBlocks.stream().anyMatch(b -> b.blockId.equals("minecraft:crafting_table"));
		boolean hasTableItem = countItem(state, "minecraft:crafting_table") > 0;
		boolean tableReady = tableNearby || hasTableItem;
		boolean hasAnyLog = state.inventory.stream().anyMatch(i -> i.itemId.endsWith("_log"));
		int oakPlanks = countItem(state, "minecraft:oak_planks");
		int sticks = countItem(state, "minecraft:stick");
		int cobblestone = countItem(state, "minecraft:cobblestone");
		boolean hasAnyPickaxe = hasItemEndingWith(state, "_pickaxe");

		int sticksNeeded = 2;
		int cobbleNeeded = 0;
		int planksNeeded = 0;
		String recipeId;
		switch (goalItem) {
			case "minecraft:wooden_pickaxe" -> { recipeId = "wooden_pickaxe"; planksNeeded = 3; }
			case "minecraft:stone_pickaxe" -> { recipeId = "stone_pickaxe"; cobbleNeeded = 3; }
			case "minecraft:stone_axe" -> { recipeId = "stone_axe"; cobbleNeeded = 3; }
			case "minecraft:stone_shovel" -> { recipeId = "stone_shovel"; cobbleNeeded = 1; }
			default -> { recipeId = "stone_sword"; cobbleNeeded = 2; sticksNeeded = 1; }
		}

		// 1) 作業台の確保が最優先
		if (!tableReady) {
			if (hasTableItem) {
				unordered.add(Action.placeCraftingTable(null, "Place the crafting table - needed to progress toward " + goalItem));
			} else if (oakPlanks >= 4) {
				unordered.add(Action.craft(null, "crafting_table", "Craft a crafting table - needed to progress toward " + goalItem));
			} else if (hasAnyLog) {
				unordered.add(Action.craft(null, "planks", "Craft planks from your log - working toward a crafting table"));
			} else {
				findNearbyBlock(state, id -> id.endsWith("_log")).ifPresent(block ->
						unordered.add(Action.mine(null, block.absX, block.absY, block.absZ,
								"Mine a log - you need a crafting table before you can progress toward " + goalItem)));
			}
			return;
		}

		// 2) 棒が足りなければ確保
		if (sticks < sticksNeeded) {
			if (oakPlanks >= 2) {
				unordered.add(Action.craft(null, "sticks", "Craft sticks - needed for " + goalItem));
			} else if (hasAnyLog) {
				unordered.add(Action.craft(null, "planks", "Craft planks - needed to make sticks for " + goalItem));
			} else {
				findNearbyBlock(state, id -> id.endsWith("_log")).ifPresent(block ->
						unordered.add(Action.mine(null, block.absX, block.absY, block.absZ,
								"Mine a log - needed to eventually make sticks for " + goalItem)));
			}
			return;
		}

		// 3) 最後に本体材料(木のツルハシなら板材、石系ならcobblestone)を確保して完成
		if (goalItem.equals("minecraft:wooden_pickaxe")) {
			if (oakPlanks >= planksNeeded) {
				unordered.add(Action.craft(null, recipeId, "Craft " + goalItem + " (final step, you have everything needed)"));
			} else if (hasAnyLog) {
				unordered.add(Action.craft(null, "planks", "Craft more planks - needed for " + goalItem));
			} else {
				findNearbyBlock(state, id -> id.endsWith("_log")).ifPresent(block ->
						unordered.add(Action.mine(null, block.absX, block.absY, block.absZ,
								"Mine a log - need more planks for " + goalItem)));
			}
		} else if (cobblestone >= cobbleNeeded) {
			unordered.add(Action.craft(null, recipeId, "Craft " + goalItem + " (final step, you have everything needed)"));
		} else if (hasAnyPickaxe) {
			findNearbyBlock(state, id -> id.equals("minecraft:stone") || id.equals("minecraft:cobblestone")
					|| id.startsWith("minecraft:deepslate")).ifPresent(block ->
					unordered.add(Action.mine(null, block.absX, block.absY, block.absZ,
							"Mine stone - need more cobblestone for " + goalItem)));
		}
	}

	private static boolean hasItemEndingWith(WorldState state, String suffix) {
		return state.inventory.stream().anyMatch(i -> i.itemId.endsWith(suffix));
	}

	private static boolean hasExactItem(WorldState state, String itemId) {
		return state.inventory.stream().anyMatch(i -> i.itemId.equals(itemId));
	}

	private static int countItem(WorldState state, String itemId) {
		int total = 0;
		for (ItemSummary item : state.inventory) {
			if (item.itemId.equals(itemId)) {
				total += item.count;
			}
		}
		return total;
	}

	private static Optional<BlockSummary> findNearbyBlock(WorldState state, java.util.function.Predicate<String> matcher) {
		return state.nearbyBlocks.stream()
				.filter(b -> matcher.test(b.blockId))
				.min((a, b) -> Integer.compare(
						a.relX * a.relX + a.relY * a.relY + a.relZ * a.relZ,
						b.relX * b.relX + b.relY * b.relY + b.relZ * b.relZ));
	}
}