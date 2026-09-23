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

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.util.math.Box;

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

	private long cooldownReadySinceMs = -1;
	private static final long CRIT_WAIT_GRACE_MS = 350L;

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
		reflexAttackTick(client);
		reflexCombatHopTick(client);

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
		// 変更後（シャッフル後はWAITが先頭とは限らないため）
		String fallbackId = candidates.stream()
				.filter(a -> a.type == ActionType.WAIT)
				.findFirst()
				.map(a -> a.id)
				.orElse(candidates.get(0).id);

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
						+ "Only prioritize safety (waiting/retreating) when actually low on health or a hostile mob is close."
						+ " Once a hostile mob is already at melee range or closer, do not keep closing the "
						+ "distance further - either attack from where you are, or retreat to create space.");

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

	private void reflexAttackTick(MinecraftClient client) {
		if (!config.aiEnabled || actionExecutor.isRetreating()) {
			cooldownReadySinceMs = -1;
			return;
		}
		ClientPlayerEntity player = client.player;
		ClientWorld world = client.world;
		if (player == null || world == null) {
			return;
		}

		boolean cooldownReady = player.getAttackCooldownProgress(0.0f) >= CandidateActionGenerator.COOLDOWN_READY_THRESHOLD;
		if (!cooldownReady) {
			cooldownReadySinceMs = -1; // まだ満タンではない - 待機ウィンドウをリセット
			return;
		}

		Box box = player.getBoundingBox().expand(CandidateActionGenerator.ATTACK_RANGE);
		Entity nearest = null;
		double nearestDist = Double.MAX_VALUE;
		for (Entity e : world.getOtherEntities(player, box, ent -> ent instanceof Monster)) {
			double d = player.distanceTo(e);
			if (d <= CandidateActionGenerator.ATTACK_RANGE && d < nearestDist) {
				nearest = e;
				nearestDist = d;
			}
		}
		if (nearest == null) {
			cooldownReadySinceMs = -1;
			return;
		}

		long now = System.currentTimeMillis();
		if (cooldownReadySinceMs < 0) {
			cooldownReadySinceMs = now; // 今tickで満タンになった - 猶予期間スタート
		}

		boolean isFalling = !player.isOnGround() && player.getVelocity().y < 0;
		boolean graceExpired = now - cooldownReadySinceMs >= CRIT_WAIT_GRACE_MS;

		if (!isFalling && !graceExpired) {
			// もう少し待つ。reflexCombatHopTick()が常時ホップさせているので、
			// 猶予期間内に落下中のタイミング(クリティカルの機会)が来るはず。
			return;
		}

		if (isFalling) {
			Action reflex = new Action("REFLEX-ATTACK", ActionType.CRITICAL_ATTACK,
					"Reflex critical attack (falling, in range, cooldown ready)",
					nearest.getUuid().toString());
			actionExecutor.execute(client, reflex);
		} else {
			Action reflex = new Action("REFLEX-ATTACK", ActionType.SPRINT_ATTACK,
					"Reflex sprint attack (grace period expired, in range, cooldown ready)",
					nearest.getUuid().toString());
			actionExecutor.execute(client, reflex);
		}
		cooldownReadySinceMs = -1;
	}

	/**
	 * 常時反射レイヤー：Jevの判断を待たず、毎tick「間合い」を維持する。
	 *   ENGAGE_MIN_RANGE 未満        -> 自動で後退（近すぎる）
	 *   ENGAGE_MIN_RANGE 〜 ATTACK_RANGE -> その場で静止してホップ（クリティカル狙い、いわゆる待ち構え）
	 *   ATTACK_RANGE 〜 APPROACH_RADIUS  -> 自動で接近
	 *   それ以上遠い                  -> 何もしない（Jevの通常判断に任せる）
	 */
	private static final double ENGAGE_MIN_RANGE = 2.0D;

	private void reflexCombatHopTick(MinecraftClient client) {
		if (!config.aiEnabled) {
			return;
		}
		ClientPlayerEntity player = client.player;
		ClientWorld world = client.world;
		if (player == null || world == null) {
			return;
		}

		double approachRadius = CandidateActionGenerator.ATTACK_RANGE + 2.0D;
		Box box = player.getBoundingBox().expand(approachRadius);

		Entity nearest = null;
		double nearestDist = Double.MAX_VALUE;
		for (Entity e : world.getOtherEntities(player, box, ent -> ent instanceof Monster)) {
			double d = player.distanceTo(e);
			if (d < nearestDist) {
				nearest = e;
				nearestDist = d;
			}
		}
		if (nearest == null) {
			actionExecutor.stopApproaching();
			actionExecutor.stopRetreating();
			return;
		}

		if (nearestDist < ENGAGE_MIN_RANGE) {
			// 近すぎる - Jevの判断を待たず自動で離れる
			actionExecutor.reflexRetreat(player, nearest);
			return;
		}

		if (nearestDist <= CandidateActionGenerator.ATTACK_RANGE) {
			// ちょうど良い間合い - 前進も後退もせず、ホップだけしてクリティカルの機会を待つ
			actionExecutor.stopApproaching();
			actionExecutor.stopRetreating();
			if (player.isOnGround()) {
				actionExecutor.execute(client, Action.of("REFLEX-HOP", ActionType.JUMP, "Reflex combat hop"));
			}
			return;
		}

		// 射程外 - 接近する
		actionExecutor.stopRetreating();
		actionExecutor.approachTarget(player, nearest);
	}
}
