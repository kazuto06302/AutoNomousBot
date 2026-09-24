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

	private static void addGatheringCandidates(List<Action> unordered, WorldState state) {
		boolean hasPickaxe = hasItemEndingWith(state, "_pickaxe");
		boolean hasStonePickaxe = hasExactItem(state, "minecraft:stone_pickaxe");
		int oakPlanks = countItem(state, "minecraft:oak_planks");
		int sticks = countItem(state, "minecraft:stick");
		int cobblestone = countItem(state, "minecraft:cobblestone");
		boolean hasTableItem = countItem(state, "minecraft:crafting_table") > 0;
		boolean hasAnyLog = state.inventory.stream().anyMatch(i -> i.itemId.endsWith("_log"));
		boolean tableNearby = state.nearbyBlocks.stream().anyMatch(b -> b.blockId.equals("minecraft:crafting_table"));
		boolean tableReady = tableNearby || hasTableItem;

		if (!hasAnyLog && oakPlanks < 4 && !hasPickaxe) {
			findNearbyBlock(state, id -> id.endsWith("_log")).ifPresent(block ->
					unordered.add(Action.mine(null, block.absX, block.absY, block.absZ,
							"Mine the nearby log block (" + block.blockId + ") to gather wood")));
		}

		if (hasAnyLog && oakPlanks < 4) {
			unordered.add(Action.craft(null, "planks", "Craft planks from the log you're carrying"));
		}

		if (!tableNearby && !hasTableItem && oakPlanks >= 4) {
			unordered.add(Action.craft(null, "crafting_table", "Craft a crafting table"));
		}
		if (!tableNearby && hasTableItem) {
			unordered.add(Action.placeCraftingTable(null, "Place the crafting table on the ground"));
		}

		if (tableReady && sticks < 2 && oakPlanks >= 2) {
			unordered.add(Action.craft(null, "sticks", "Craft sticks from oak planks"));
		}

		if (tableReady && !hasPickaxe && oakPlanks >= 3 && sticks >= 2) {
			unordered.add(Action.craft(null, "wooden_pickaxe", "Craft a wooden pickaxe (needed to mine stone)"));
		}

		if (hasPickaxe && cobblestone < 9) {
			findNearbyBlock(state, id -> id.equals("minecraft:stone") || id.equals("minecraft:cobblestone")
					|| id.startsWith("minecraft:deepslate")).ifPresent(block ->
					unordered.add(Action.mine(null, block.absX, block.absY, block.absZ,
							"Mine the nearby stone block (" + block.blockId + ") to gather cobblestone")));
		}

		if (tableReady && !hasStonePickaxe && cobblestone >= 3 && sticks >= 2) {
			unordered.add(Action.craft(null, "stone_pickaxe", "Craft a stone pickaxe"));
		}
		if (tableReady && !hasExactItem(state, "minecraft:stone_axe") && cobblestone >= 3 && sticks >= 2) {
			unordered.add(Action.craft(null, "stone_axe", "Craft a stone axe"));
		}
		if (tableReady && !hasExactItem(state, "minecraft:stone_shovel") && cobblestone >= 1 && sticks >= 2) {
			unordered.add(Action.craft(null, "stone_shovel", "Craft a stone shovel"));
		}
		if (tableReady && !hasExactItem(state, "minecraft:stone_sword") && cobblestone >= 2 && sticks >= 1) {
			unordered.add(Action.craft(null, "stone_sword", "Craft a stone sword"));
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