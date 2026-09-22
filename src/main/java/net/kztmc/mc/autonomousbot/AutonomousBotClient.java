package net.kztmc.mc.autonomousbot;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.kztmc.mc.autonomousbot.ai.DecisionEngine;
import net.kztmc.mc.autonomousbot.command.BotCommands;
import net.kztmc.mc.autonomousbot.config.BotConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Phase 1 entrypoint. Client-only mod - no server-side plugin required
 * (see design constraints). Registers the tick-driven decision loop, the
 * /bot commands and the emergency-stop keybind, and makes sure the AI
 * worker thread is shut down cleanly when the client closes.
 *
 * NOTE on Fabric API surface: this file targets the "classic" (long
 * stable, pre Fabric-API-26.x rename) API names - KeyBindingHelper /
 * registerKeyBinding / ClientTickEvents.END_CLIENT_TICK. If your resolved
 * fabric_version is a 26.x build that already applies the Level/Mapping
 * rename pass, your IDE will point out the couple of renamed symbols
 * (e.g. KeyBindingHelper -> KeyMappingHelper) and they're a mechanical fix.
 */
public final class AutonomousBotClient implements ClientModInitializer {

	public static final String MOD_ID = "autonomousbot";
	private static final Logger LOGGER = LoggerFactory.getLogger("AutonomousBot");

	private static DecisionEngine decisionEngine;
	private KeyBinding startKey;
	private KeyBinding emergencyStopKey;

	@Override
	public void onInitializeClient() {
		BotConfig config = BotConfig.getOrLoad();
		decisionEngine = new DecisionEngine(config);

		startKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.autonomousbot.start",
				GLFW.GLFW_KEY_L,
				KeyBinding.Category.CREATIVE
		));

		emergencyStopKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.autonomousbot.emergency_stop",
			GLFW.GLFW_KEY_P,
			KeyBinding.Category.CREATIVE
		));

		BotCommands.register(config, decisionEngine);

		ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> decisionEngine.shutdown());

		LOGGER.info("AutonomousBot Phase 1 initialized (AI enabled at startup: {})", config.aiEnabled);
	}

	private void onClientTick(MinecraftClient client) {
		while (emergencyStopKey.wasPressed()) {
			decisionEngine.emergencyStop(client);
			if (client.player != null) {
				client.player.sendMessage(Text.literal("[AutonomousBot] EMERGENCY STOP"), false);
			}
		}

		while (startKey.wasPressed()) {
			BotConfig config = BotConfig.getOrLoad();
			config.aiEnabled = !config.aiEnabled;
			config.save();

			if (client.player != null) {
				client.player.sendMessage(Text.literal("[AutonomousBot] Start triggered!"), false);
			}
		}
		decisionEngine.tick(client);
	}

	public static DecisionEngine getDecisionEngine() {
		return decisionEngine;
	}
}
