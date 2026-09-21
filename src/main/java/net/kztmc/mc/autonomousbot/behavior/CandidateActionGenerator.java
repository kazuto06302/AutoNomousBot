package net.kztmc.mc.autonomousbot.behavior;

import net.kztmc.mc.autonomousbot.perception.EntitySummary;
import net.kztmc.mc.autonomousbot.perception.WorldState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Turns a WorldState into a small, bounded list of legal candidate actions.
 * Jev picks among these - it never invents its own action.
 */
public final class CandidateActionGenerator {

	private static final double ATTACK_RANGE = 3.5D;

	private CandidateActionGenerator() {
	}

	public static List<Action> generate(WorldState state) {
		List<Action> candidates = new ArrayList<>();
		char nextId = 'A';

		candidates.add(new Action(String.valueOf(nextId++), ActionType.WAIT,
			"Do nothing this cycle and keep observing", null));

		candidates.add(new Action(String.valueOf(nextId++), ActionType.MOVE_FORWARD,
			"Walk forward in the direction currently facing", null));

		if (state.player.onGround) {
			candidates.add(new Action(String.valueOf(nextId++), ActionType.JUMP,
				"Jump straight up", null));
		}

		Optional<EntitySummary> nearestHostile = state.nearbyEntities.stream()
			.filter(e -> e.hostile)
			.min((a, b) -> Double.compare(a.distance, b.distance));

		if (nearestHostile.isPresent()) {
			EntitySummary target = nearestHostile.get();
			candidates.add(new Action(String.valueOf(nextId++), ActionType.LOOK,
				"Turn to face the nearby hostile mob " + target.entityId
					+ " (" + String.format("%.1f", target.distance) + " blocks away)",
				target.entityUuid));

			if (target.distance <= ATTACK_RANGE) {
				candidates.add(new Action(String.valueOf(nextId++), ActionType.ATTACK,
					"Attack the nearby hostile mob " + target.entityId
						+ " (" + String.format("%.1f", target.distance) + " blocks away)",
					target.entityUuid));
			}
		}

		return candidates;
	}
}
