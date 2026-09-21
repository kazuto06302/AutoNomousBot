package net.kztmc.mc.autonomousbot.ai;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.kztmc.mc.autonomousbot.config.BotConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

/**
 * Talks to TypeSafe AI's Jev "System One" decision model.
 *
 * Endpoint contract (verified against public TypeSafe/Jev documentation,
 * September 2026):
 *
 * POST https://api.typesafe.ai/v1/systemone
 * Headers: Authorization: Bearer <TYPESAFE_API_KEY>, Content-Type: application/json
 * Body:   { "model": "jev-latest", "state": <string|object|array>,
 *           "questions": { "<name>": { "type": "choice",
 *                                       "instructions": "...",
 *                                       "criteria": { "optA": "desc", ... } } } }
 * Response: { "model": "...", "answers": { "<name>": { "type": "choice",
 *                 "choice": "optA", "probabilities": {...}, "confidence": 0.82 } },
 *             "usage": {...} }
 *
 * All parsing goes through Gson's JsonObject model - no regex/string
 * scraping of the response, per the design constraint.
 */
public final class JevClient {

	private static final Logger LOGGER = LoggerFactory.getLogger("AutonomousBot/JevClient");
	private static final Gson GSON = new Gson();

	private final BotConfig config;
	private final ExecutorService aiWorkerExecutor;
	private final HttpClient httpClient;

	public JevClient(BotConfig config, ExecutorService aiWorkerExecutor) {
		this.config = config;
		this.aiWorkerExecutor = aiWorkerExecutor;
		this.httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.executor(aiWorkerExecutor)
			.build();
	}

	/**
	 * Sends one "choice" decision request. Never throws synchronously -
	 * all failure modes (missing key, network error, timeout, malformed
	 * response, retry exhaustion) resolve the future to a fallback
	 * {@link Decision} rather than propagating an exception onto the
	 * client thread that eventually consumes it.
	 */
	public CompletableFuture<Decision> askChoice(Object state, DecisionQuestion question, String fallbackOptionId) {
		String apiKey = config.resolveApiKey();
		if (apiKey == null) {
			LOGGER.warn("No Jev API key configured (env var {} is unset) - using fallback action.",
				config.apiKeyEnvVar);
			return CompletableFuture.completedFuture(Decision.fallback(fallbackOptionId));
		}

		return attempt(apiKey, state, question, fallbackOptionId, config.maxRetry);
	}

	private CompletableFuture<Decision> attempt(String apiKey, Object state, DecisionQuestion question,
			String fallbackOptionId, int retriesLeft) {
		long startedAt = System.currentTimeMillis();
		HttpRequest request;
		try {
			request = buildRequest(apiKey, state, question);
		} catch (RuntimeException e) {
			LOGGER.error("Failed to build Jev request", e);
			return CompletableFuture.completedFuture(Decision.fallback(fallbackOptionId));
		}

		return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
			.orTimeout(config.decisionTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
			.handleAsync((response, throwable) -> {
				long latency = System.currentTimeMillis() - startedAt;

				if (throwable != null) {
					if (retriesLeft > 0) {
						LOGGER.warn("Jev request failed ({}), retrying ({} left)",
							rootMessage(throwable), retriesLeft);
						return null; // signal retry below
					}
					LOGGER.error("Jev request failed after retries: {}", rootMessage(throwable));
					return Decision.fallback(fallbackOptionId);
				}

				if (response.statusCode() / 100 != 2) {
					LOGGER.warn("Jev API returned HTTP {}: {}", response.statusCode(), truncate(response.body()));
					return Decision.fallback(fallbackOptionId);
				}

				try {
					return parseDecision(response.body(), question.name, latency);
				} catch (RuntimeException e) {
					LOGGER.error("Failed to parse Jev response: {}", truncate(response.body()), e);
					return Decision.fallback(fallbackOptionId);
				}
			}, aiWorkerExecutor)
			.thenCompose(decision -> {
				if (decision == null) {
					return attempt(apiKey, state, question, fallbackOptionId, retriesLeft - 1);
				}
				return CompletableFuture.completedFuture(decision);
			})
			.exceptionally(e -> {
				LOGGER.error("Unexpected error in Jev decision pipeline", e);
				return Decision.fallback(fallbackOptionId);
			});
	}

	private HttpRequest buildRequest(String apiKey, Object state, DecisionQuestion question) {
		JsonObject criteria = new JsonObject();
		question.criteria.forEach(criteria::addProperty);

		JsonObject choiceQuestion = new JsonObject();
		choiceQuestion.addProperty("type", "choice");
		choiceQuestion.addProperty("instructions", question.instructions);
		choiceQuestion.add("criteria", criteria);

		JsonObject questions = new JsonObject();
		questions.add(question.name, choiceQuestion);

		JsonObject body = new JsonObject();
		body.addProperty("model", config.jevModel);
		body.add("state", GSON.toJsonTree(state));
		body.add("questions", questions);

		return HttpRequest.newBuilder()
			.uri(URI.create(config.jevApiEndpoint))
			.timeout(Duration.ofMillis(config.decisionTimeoutMs))
			.header("Content-Type", "application/json")
			.header("Authorization", "Bearer " + apiKey)
			.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
			.build();
	}

	private Decision parseDecision(String responseBody, String questionName, long latencyMs) {
		JsonObject root = GSON.fromJson(responseBody, JsonObject.class);
		String modelId = root.has("model") ? root.get("model").getAsString() : "unknown";

		JsonObject answers = root.getAsJsonObject("answers");
		if (answers == null || !answers.has(questionName)) {
			throw new IllegalStateException("Response missing answers." + questionName);
		}
		JsonObject answer = answers.getAsJsonObject(questionName);
		String choice = answer.get("choice").getAsString();
		double confidence = answer.has("confidence") ? answer.get("confidence").getAsDouble() : 0.0;

		Map<String, Double> probabilities = new LinkedHashMap<>();
		if (answer.has("probabilities")) {
			JsonObject probs = answer.getAsJsonObject("probabilities");
			for (String key : probs.keySet()) {
				probabilities.put(key, probs.get(key).getAsDouble());
			}
		}

		return new Decision(choice, confidence, probabilities, modelId, latencyMs);
	}

	private static String rootMessage(Throwable t) {
		Throwable root = t;
		while (root instanceof CompletionException && root.getCause() != null) {
			root = root.getCause();
		}
		return root.getClass().getSimpleName() + ": " + root.getMessage();
	}

	private static String truncate(String s) {
		if (s == null) {
			return "";
		}
		return s.length() > 300 ? s.substring(0, 300) + "..." : s;
	}
}
