package net.kztmc.mc.autonomousbot.planner;

/**
 * Owns the bot's current high-level {@link Goal}.
 *
 * Phase 1 deliberately does NOT decompose goals into Task queues yet -
 * DecisionEngine asks Jev directly every cycle. This class exists purely
 * to hold the current goal and give later phases (Navigation/Mining/
 * Combat controllers) a stable place to plug in task decomposition without
 * having to touch DecisionEngine's wiring.
 */
public final class Planner {

	private Goal currentGoal = Goal.SURVIVE;

	public Goal getCurrentGoal() {
		return currentGoal;
	}

	public void setGoal(Goal goal) {
		this.currentGoal = goal;
	}
}
