package net.kztmc.mc.autonomousbot.ai;

import java.util.Collections;
import java.util.Map;

/**
 * The outcome of one Jev choice question: which option id was chosen, the
 * full probability distribution, and Jev's own calibrated confidence.
 */
public final class Decision {
	public final String chosenOptionId;
	public final double confidence;
	public final Map<String, Double> probabilities;
	public final String modelId;
	public final long latencyMs;

	public Decision(String chosenOptionId, double confidence, Map<String, Double> probabilities,
			String modelId, long latencyMs) {
		this.chosenOptionId = chosenOptionId;
		this.confidence = confidence;
		this.probabilities = probabilities;
		this.modelId = modelId;
		this.latencyMs = latencyMs;
	}

	/** A safe local fallback used when the API call fails or times out. */
	public static Decision fallback(String fallbackOptionId) {
		return new Decision(fallbackOptionId, 0.0, Collections.emptyMap(), "local-fallback", 0L);
	}
}
