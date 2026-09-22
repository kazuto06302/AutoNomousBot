package net.kztmc.mc.autonomousbot.behavior;

import net.kztmc.mc.autonomousbot.perception.EntitySummary;
import net.kztmc.mc.autonomousbot.perception.ItemSummary;
import net.kztmc.mc.autonomousbot.perception.WorldState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class CandidateActionGenerator {

	private static final double ATTACK_RANGE = 3.5D;
	/** Closer than this, prefer creating space over standing and trading hits. */
	private static final double RETREAT_TRIGGER_RANGE = 2.0D;
	/** Weapon must be at least this charged (getAttackCooldownProgress) to swing. */
	private static final float COOLDOWN_READY_THRESHOLD = 0.9f;

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

			unordered.add(new Action(null, ActionType.LOOK,
					"Turn to face (and approach) the nearby hostile mob " + target.entityId
							+ " (" + String.format("%.1f", target.distance) + " blocks away)",
					target.entityUuid, null));

			if (target.distance <= RETREAT_TRIGGER_RANGE) {
				unordered.add(new Action(null, ActionType.RETREAT,
						"Back away from " + target.entityId + " to create space instead of trading hits at melee range",
						target.entityUuid, null));
			}

			if (inRange && cooldownReady) {
				unordered.add(new Action(null, ActionType.ATTACK,
						"Attack " + target.entityId + " with a normal swing (weapon is ready, "
								+ String.format("%.1f", target.distance) + " blocks away)",
						target.entityUuid, null));

				if (!state.player.sprinting) {
					unordered.add(new Action(null, ActionType.SPRINT_ATTACK,
							"Sprint in and attack " + target.entityId + " for a sprint/knockback hit",
							target.entityUuid, null));
				}

				if (isFalling) {
					unordered.add(new Action(null, ActionType.CRITICAL_ATTACK,
							"You are currently falling - attack " + target.entityId + " now for a critical hit",
							target.entityUuid, null));
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

		Collections.shuffle(unordered);

		List<Action> candidates = new ArrayList<>();
		char nextId = 'A';
		for (Action a : unordered) {
			candidates.add(new Action(String.valueOf(nextId++), a.type, a.label, a.targetEntityUuid, a.targetSlot));
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
}