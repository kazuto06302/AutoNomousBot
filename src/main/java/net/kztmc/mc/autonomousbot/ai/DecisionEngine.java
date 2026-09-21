package net.kztmc.mc.autonomousbot.ai;

import net.kztmc.mc.autonomousbot.behavior.Action;
import net.kztmc.mc.autonomousbot.behavior.ActionExecutor;
import net.kztmc.mc.autonomousbot.behavior.ActionType;
import net.kztmc.mc.autonomousbot.behavior.CandidateActionGenerator;
import net.kztmc.mc.autonomousbot.config.BotConfig;
import net.kztmc.mc.autonomousbot.memory.ShortTermMemory;
import net.kztmc.mc.autonomousbot.perception.WorldState;
import net.kztmc.mc.autonomousbot.perception.WorldStateCollector;
import net.kztmc.mc.autonomousbot.planner.Planner;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs the perception -> Jev -> action pipeline.
 *
 * Threading model (per design doc):
 *   Client Thread -> WorldState snapshot -> AI Worker Thread -> Jev API
 *   -> Decision -> Client Thread -> ActionExecutor
 *
 * {@link #tick(MinecraftClient)} MUST be called every client tick from the
 * client thread. It never blocks: it either starts a new async decision
 * request (if enough time has passed and no request is in flight) or
 * drains a completed decision from the queue and executes it.
 */
public final class DecisionEngine {

	private static final Logger LOGGER = LoggerFactory.getLogger("AutonomousBot/DecisionEngine");

	private final BotConfig config;
	private final JevClient jevClient;
	private final ExecutorService aiWorkerExecutor;
	private final ActionExecutor actionExecutor;
	private final ShortTermMemory shortTermMemory = new ShortTermMemory();
	private final Planner planner = new Planner();

	private final AtomicBoolean requestInFlight = new AtomicBoolean(false);
	private final ConcurrentLinkedQueue<PendingResult> completedDecisions = new ConcurrentLinkedQueue<>();
	private long lastDecisionAtMs = 0L;

	// --- debug/HUD state (only ever written from the client thread) -------
	public volatile String debugCurrentDecisionId = "-";
	public volatile String debugPreviousDecisionId = "-";
	public volatile double debugConfidence = 0.0;
	public volatile long debugLastLatencyMs = 0L;
	public volatile int debugCandidateCount = 0;
	public volatile boolean debugLoopDetected = false;

	public DecisionEngine(BotConfig config) {
		this.config = config;
		this.aiWorkerExecutor = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "AutonomousBot-AI-Worker");
			t.setDaemon(true);
			return t;
		});
		this.jevClient = new JevClient(config, aiWorkerExecutor);
		this.actionExecutor = new ActionExecutor();
	}

	public Planner getPlanner() {
		return planner;
	}

	public void tick(MinecraftClient client) {
		actionExecutor.tick(client);
		drainCompletedDecision(client);

		if (!config.aiEnabled) {
			return;
		}
		ClientPlayerEntity player = client.player;
		ClientWorld world = client.world;
		if (player == null || world == null) {
			return;
		}

		long now = System.currentTimeMillis();
		if (requestInFlight.get() || now - lastDecisionAtMs < config.decisionIntervalMs) {
			return;
		}

		startDecisionCycle(player, world);
	}

	private void startDecisionCycle(ClientPlayerEntity player, ClientWorld world) {
		WorldState state = WorldStateCollector.collect(player, world);
		List<Action> candidates = CandidateActionGenerator.generate(state);
		debugCandidateCount = candidates.size();

		if (candidates.isEmpty()) {
			LOGGER.warn("No candidate actions generated - skipping this cycle");
			return;
		}

		DecisionQuestion question = buildQuestion(state, candidates);
		String fallbackId = candidates.get(0).id; // candidates[0] is always WAIT, see CandidateActionGenerator

		requestInFlight.set(true);
		lastDecisionAtMs = System.currentTimeMillis();

		jevClient.askChoice(state, question, fallbackId).whenCompleteAsync((decision, error) -> {
			// Any exception here means our own future-composition had a bug;
			// JevClient itself already guarantees a non-null fallback Decision.
			if (error != null) {
				LOGGER.error("Unexpected decision pipeline failure", error);
				decision = Decision.fallback(fallbackId);
			}
			completedDecisions.add(new PendingResult(decision, candidates));
			requestInFlight.set(false);
		}, aiWorkerExecutor);
	}

	private DecisionQuestion buildQuestion(WorldState state, List<Action> candidates) {
		StringBuilder instructions = new StringBuilder(
				"You are controlling a Minecraft player bot. Current goal: " + planner.getCurrentGoal()
						+ ". Choose exactly one of the offered actions that best fits the current state. "
						+ "WAIT should only be chosen when none of the other offered actions make sense right now "
						+ "(e.g. genuinely nothing to explore, attack, or move toward). When the situation is calm and "
						+ "no threat is nearby, prefer making progress (moving, looking around, jumping) over waiting. "
						+ "Only prioritize safety (waiting/retreating) when actually low on health or a hostile mob is close.");

		DecisionQuestion question = new DecisionQuestion("action", instructions.toString());
		for (Action candidate : candidates) {
			question.addOption(candidate.id, candidate.type + ": " + candidate.label);
		}
		return question;
	}

	private void drainCompletedDecision(MinecraftClient client) {
		PendingResult result = completedDecisions.poll();
		if (result == null) {
			return;
		}

		Action chosen = resolveChosenAction(result);
		debugPreviousDecisionId = debugCurrentDecisionId;
		debugCurrentDecisionId = chosen.id;
		debugConfidence = result.decision.confidence;
		debugLastLatencyMs = result.decision.latencyMs;

		shortTermMemory.record(chosen.type);
		debugLoopDetected = shortTermMemory.isLooping();
		if (debugLoopDetected) {
			LOGGER.warn("Loop detected ({} repeated) - forcing WAIT this cycle", chosen.type);
			chosen = Action.of("WAIT-loop-guard", ActionType.WAIT, "Loop guard override");
		}

		if (config.debugMode && client.player != null) {
			client.player.sendMessage(
					Text.literal("[AutonomousBot] " + chosen + " conf=" + String.format("%.2f", result.decision.confidence)
							+ " latency=" + result.decision.latencyMs + "ms probs=" + result.decision.probabilities),
				true);
		}

		actionExecutor.execute(client, chosen);
	}

	private Action resolveChosenAction(PendingResult result) {
		Map<String, Action> byId = result.candidatesById();
		Action chosen = byId.get(result.decision.chosenOptionId);
		if (chosen == null) {
			LOGGER.warn("Jev chose unknown option '{}', falling back to WAIT", result.decision.chosenOptionId);
			return Action.of("WAIT-invalid", ActionType.WAIT, "Invalid decision fallback");
		}
		return chosen;
	}

	/** Emergency stop: disables AI and immediately releases any held input. */
	public void emergencyStop(MinecraftClient client) {
		config.aiEnabled = false;
		config.save();
		completedDecisions.clear();
		shortTermMemory.clear();
		actionExecutor.releaseAll(client);
		LOGGER.warn("Emergency stop triggered - AI disabled");
	}

	public void shutdown() {
		aiWorkerExecutor.shutdownNow();
	}

	private record PendingResult(Decision decision, List<Action> candidates) {
		Map<String, Action> candidatesById() {
			Map<String, Action> map = new java.util.LinkedHashMap<>();
			for (Action a : candidates) {
				map.put(a.id, a);
			}
			return map;
		}
	}
}
