package net.kztmc.mc.autonomousbot.behavior;

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

		Collections.shuffle(unordered);

		List<Action> candidates = new ArrayList<>();
		char nextId = 'A';
		for (Action a : unordered) {
			candidates.add(new Action(String.valueOf(nextId++), a.type, a.label, a.targetEntityUuid, a.targetSlot, a.holdGround));
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
}