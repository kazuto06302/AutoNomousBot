package net.kztmc.mc.autonomousbot.memory;

import net.kztmc.mc.autonomousbot.behavior.ActionType;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Keeps a short rolling window of recently executed action types, purely
 * in-memory (cleared on client restart). Used to detect the bot getting
 * stuck repeating the same action ("永久に同じ行動をする" safety case).
 */
public final class ShortTermMemory implements Memory {

	private static final int WINDOW_SIZE = 16;
	private static final int LOOP_THRESHOLD = 10;

	private final Deque<ActionType> recentActions = new ArrayDeque<>(WINDOW_SIZE);

	public void record(ActionType action) {
		recentActions.addLast(action);
		while (recentActions.size() > WINDOW_SIZE) {
			recentActions.removeFirst();
		}
	}

	/**
	 * True if the last {@link #LOOP_THRESHOLD} actions were all the same
	 * non-WAIT action - a simple, cheap heuristic for Phase 1. Later phases
	 * can replace this with a state-change-aware detector.
	 */
	public boolean isLooping() {
		if (recentActions.size() < LOOP_THRESHOLD) {
			return false;
		}
		ActionType first = null;
		int count = 0;
		for (ActionType action : recentActions) {
			if (first == null) {
				first = action;
			}
			if (action == first) {
				count++;
			}
		}
		// WAITに加えて、MINE/CRAFTも意図的に連続することがある正常な行動
		// (例: 石を10個連続で掘る)なので、ループ扱いしない。
		boolean exempt = first == ActionType.WAIT || first == ActionType.MINE || first == ActionType.CRAFT;
		return first != null && !exempt && count >= LOOP_THRESHOLD;
	}

	public void clear() {
		recentActions.clear();
	}
}
