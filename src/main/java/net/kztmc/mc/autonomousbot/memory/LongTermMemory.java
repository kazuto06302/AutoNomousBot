package net.kztmc.mc.autonomousbot.memory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Skeleton for the bot's own cross-session experience: death locations,
 * dangerous areas, known villages, stronghold/fortress coordinates,
 * successful/failed action outcomes, etc. (see design doc "Memory" section).
 *
 * Phase 1 only implements load/save plumbing to
 * config/autonomousbot/long_term_memory.json - it is intentionally NOT
 * wired into the decision loop yet. Later phases (Navigation/Combat/
 * Survival) should read from and write to this as they're implemented.
 */
public final class LongTermMemory implements Memory {

	private static final Logger LOGGER = LoggerFactory.getLogger("AutonomousBot/LongTermMemory");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public List<NamedLocation> deathLocations = new ArrayList<>();
	public List<NamedLocation> dangerousAreas = new ArrayList<>();
	public List<NamedLocation> knownVillages = new ArrayList<>();
	public NamedLocation strongholdLocation;

	public static final class NamedLocation {
		public String dimension;
		public double x;
		public double y;
		public double z;
		public String note;
	}

	public static LongTermMemory load() {
		Path path = filePath();
		if (Files.exists(path)) {
			try {
				String json = Files.readString(path, StandardCharsets.UTF_8);
				LongTermMemory mem = GSON.fromJson(json, LongTermMemory.class);
				return mem != null ? mem : new LongTermMemory();
			} catch (IOException e) {
				LOGGER.error("Failed to read long-term memory, starting fresh", e);
			}
		}
		return new LongTermMemory();
	}

	public void save() {
		Path path = filePath();
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOGGER.error("Failed to save long-term memory", e);
		}
	}

	private static Path filePath() {
		return FabricLoader.getInstance().getConfigDir()
			.resolve("autonomousbot")
			.resolve("long_term_memory.json");
	}
}
