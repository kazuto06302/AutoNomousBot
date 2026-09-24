package net.kztmc.mc.autonomousbot.planner;

/**
 * A single unit of work under a {@link GoalStage}. Phase 1 does not decompose
 * goals into multi-step task queues yet - Jev's per-cycle choice IS the
 * task for now. This class exists so Planner has somewhere to grow into
 * without a breaking API change later (e.g. "walk to stronghold" as a
 * multi-tick Task that persists across several decision cycles).
 */
public final class Task {
	public final String description;
	public boolean complete = false;

	public Task(String description) {
		this.description = description;
	}
}
