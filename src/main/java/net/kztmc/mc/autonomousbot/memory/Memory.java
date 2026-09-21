package net.kztmc.mc.autonomousbot.memory;

/**
 * Marker interface for the bot's memory subsystems. Phase 1 only implements
 * {@link ShortTermMemory} (in-session loop detection). {@link LongTermMemory}
 * is a skeleton for future phases (death locations, known villages,
 * stronghold coordinates, etc. - see design doc "Memory" section).
 */
public interface Memory {
}
