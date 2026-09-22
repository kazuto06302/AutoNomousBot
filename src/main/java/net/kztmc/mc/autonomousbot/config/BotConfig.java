package net.kztmc.mc.autonomousbot.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persistent settings for the bot, stored at config/autonomousbot.json.
 *
 * The API key itself is never written to this file by default. It is read
 * from the environment variable named by {@link #apiKeyEnvVar} (default
 * "TYPESAFE_API_KEY"). A fallback plaintext field exists for convenience
 * during local development, but it is intentionally left blank here and a
 * warning is logged if it is ever populated, since that file can end up
 * committed to a repo by accident.
 */
public final class BotConfig {

	private static final Logger LOGGER = LoggerFactory.getLogger("AutonomousBot/Config");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	// --- persisted fields -------------------------------------------------
	public String jevApiEndpoint = "https://api.typesafe.ai/v1/systemone";
	public String jevModel = "jev-latest";
	public String apiKeyEnvVar = "TYPESAFE_API_KEY";
	/** Discouraged fallback - prefer the environment variable above. */
	public String apiKeyPlaintextFallback = "";

	public long decisionIntervalMs = 250L;
	public long decisionTimeoutMs = 5000L;
	public int maxRetry = 2;

	public boolean aiEnabled = false;
	public boolean debugMode = true;

	/** GLFW key name, see org.lwjgl.glfw.GLFW / GLFW.GLFW_KEY_* constants. */
	public String emergencyStopKeyTranslationKey = "key.keyboard.p";

	// -----------------------------------------------------------------------

	private static volatile BotConfig instance;
	private static Path configPath;

	public static synchronized BotConfig getOrLoad() {
		if (instance != null) {
			return instance;
		}
		configPath = FabricLoader.getInstance().getConfigDir().resolve("autonomousbot.json");
		instance = load(configPath);
		return instance;
	}

	private static BotConfig load(Path path) {
		if (Files.exists(path)) {
			try {
				String json = Files.readString(path, StandardCharsets.UTF_8);
				BotConfig cfg = GSON.fromJson(json, BotConfig.class);
				if (cfg == null) {
					cfg = new BotConfig();
				}
				cfg.warnIfPlaintextKeyPresent();
				return cfg;
			} catch (IOException e) {
				LOGGER.error("Failed to read {}, falling back to defaults", path, e);
			}
		}
		BotConfig fresh = new BotConfig();
		fresh.save();
		return fresh;
	}

	public void save() {
		try {
			Files.createDirectories(configPath.getParent());
			Files.writeString(configPath, GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOGGER.error("Failed to save {}", configPath, e);
		}
	}

	private void warnIfPlaintextKeyPresent() {
		if (apiKeyPlaintextFallback != null && !apiKeyPlaintextFallback.isBlank()) {
			LOGGER.warn("autonomousbot.json contains a plaintext API key fallback. "
				+ "Prefer setting the {} environment variable instead, and make sure "
				+ "config/autonomousbot.json is excluded from version control.", apiKeyEnvVar);
		}
	}

	/**
	 * Resolves the Jev API key. Never logs the value itself.
	 * Returns null if no key could be found anywhere.
	 */
	public String resolveApiKey() {
		String fromEnv = System.getenv(apiKeyEnvVar);
		if (fromEnv != null && !fromEnv.isBlank()) {
			return fromEnv;
		}
		if (apiKeyPlaintextFallback != null && !apiKeyPlaintextFallback.isBlank()) {
			return apiKeyPlaintextFallback;
		}
		return null;
	}
}
