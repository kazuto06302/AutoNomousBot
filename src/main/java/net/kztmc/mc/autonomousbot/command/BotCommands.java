package net.kztmc.mc.autonomousbot.command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.kztmc.mc.autonomousbot.ai.DecisionEngine;
import net.kztmc.mc.autonomousbot.config.BotConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Registers /bot start, /bot stop and /bot debug.
 *
 * Uses the classic ClientCommandManager entrypoint name - see the note in
 * AutonomousBotClient about the Fabric API 26.x rename (ClientCommandManager
 * -> ClientCommands) if your resolved fabric_version already applies it.
 */
public final class BotCommands {

	private BotCommands() {
	}

	public static void register(BotConfig config, DecisionEngine engine) {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
			registerAll(dispatcher, config, engine));
	}

	private static void registerAll(CommandDispatcher<FabricClientCommandSource> dispatcher,
			BotConfig config, DecisionEngine engine) {
		dispatcher.register(ClientCommandManager.literal("bot")
			.then(ClientCommandManager.literal("start").executes(ctx -> {
				config.aiEnabled = true;
				config.save();
				ctx.getSource().sendFeedback(Text.literal("[AutonomousBot] AI enabled."));
				return 1;
			}))
			.then(ClientCommandManager.literal("stop").executes(ctx -> {
				engine.emergencyStop(MinecraftClient.getInstance());
				ctx.getSource().sendFeedback(Text.literal("[AutonomousBot] AI stopped."));
				return 1;
			}))
			.then(ClientCommandManager.literal("debug").executes(ctx -> {
				config.debugMode = !config.debugMode;
				config.save();
				ctx.getSource().sendFeedback(
					Text.literal("[AutonomousBot] Debug overlay: " + (config.debugMode ? "ON" : "OFF")));
				return 1;
			})));
	}
}
