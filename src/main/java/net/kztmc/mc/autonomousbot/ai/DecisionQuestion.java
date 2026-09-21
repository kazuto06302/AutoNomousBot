package net.kztmc.mc.autonomousbot.ai;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A single "choice" question to hand to Jev: instructions plus the set of
 * option ids -> human-readable descriptions. Mirrors the TypeSafe/Jev
 * System One "choice" question primitive
 * (POST https://api.typesafe.ai/v1/systemone, questions[name] = {type, instructions, criteria}).
 */
public final class DecisionQuestion {
	public final String name;
	public final String instructions;
	/** option id -> description, insertion order preserved for readability/logging. */
	public final Map<String, String> criteria = new LinkedHashMap<>();

	public DecisionQuestion(String name, String instructions) {
		this.name = name;
		this.instructions = instructions;
	}

	public DecisionQuestion addOption(String id, String description) {
		criteria.put(id, description);
		return this;
	}
}
