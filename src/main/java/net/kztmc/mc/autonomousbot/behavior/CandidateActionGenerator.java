package net.kztmc.mc.autonomousbot.behavior;

import net.kztmc.mc.autonomousbot.perception.EntitySummary;
import net.kztmc.mc.autonomousbot.perception.WorldState;

import java.util.ArrayList;
import java.util.Collections;
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

	// 変更後（末尾に追加する形は同じだが、いったんラベル付きの「素の」候補として集め、
// シャッフルしてから A/B/C... を割り振る。WAITだけ常に先頭に来ないようにする）
	public static List<Action> generate(WorldState state) {
		List<Action> unordered = new ArrayList<>();

		unordered.add(new Action(null, ActionType.WAIT,
				"Do nothing this cycle and keep observing", null));
		unordered.add(new Action(null, ActionType.MOVE_FORWARD,
				"Walk forward in the direction currently facing", null));
		if (state.player.onGround) {
			unordered.add(new Action(null, ActionType.JUMP, "Jump straight up", null));
		}

		Optional<EntitySummary> nearestHostile = state.nearbyEntities.stream()
				.filter(e -> e.hostile)
				.min((a, b) -> Double.compare(a.distance, b.distance));
		if (nearestHostile.isPresent()) {
			EntitySummary target = nearestHostile.get();
			unordered.add(new Action(null, ActionType.LOOK,
					"Turn to face the nearby hostile mob " + target.entityId
							+ " (" + String.format("%.1f", target.distance) + " blocks away)",
					target.entityUuid));
			if (target.distance <= ATTACK_RANGE) {
				unordered.add(new Action(null, ActionType.ATTACK,
						"Attack the nearby hostile mob " + target.entityId
								+ " (" + String.format("%.1f", target.distance) + " blocks away)",
						target.entityUuid));
			}
		}

		Collections.shuffle(unordered);

		List<Action> candidates = new ArrayList<>();
		char nextId = 'A';
		for (Action a : unordered) {
			candidates.add(new Action(String.valueOf(nextId++), a.type, a.label, a.targetEntityUuid));
		}
		return candidates;
	}
}
