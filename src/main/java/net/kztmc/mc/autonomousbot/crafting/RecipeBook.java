package net.kztmc.mc.autonomousbot.crafting;

import java.util.HashMap;
import java.util.Map;

public final class RecipeBook {

    private static final Map<String, Recipe> RECIPES = new HashMap<>();

    private static void register(String id, String result, Map<Integer, String> grid) {
        RECIPES.put(id, new Recipe(id, result, grid));
    }

    static {
        String planks = "minecraft:oak_planks";
        String stick = "minecraft:stick";
        String cobble = "minecraft:cobblestone";

        register("sticks", "minecraft:stick", Map.of(2, planks, 5, planks));
        register("crafting_table", "minecraft:crafting_table", Map.of(1, planks, 2, planks, 4, planks, 5, planks));

        register("wooden_pickaxe", "minecraft:wooden_pickaxe", Map.of(1, planks, 2, planks, 3, planks, 5, stick, 8, stick));
        register("wooden_axe", "minecraft:wooden_axe", Map.of(1, planks, 2, planks, 4, planks, 5, stick, 8, stick));
        register("wooden_shovel", "minecraft:wooden_shovel", Map.of(2, planks, 5, stick, 8, stick));
        register("wooden_sword", "minecraft:wooden_sword", Map.of(2, planks, 5, planks, 8, stick));

        register("stone_pickaxe", "minecraft:stone_pickaxe", Map.of(1, cobble, 2, cobble, 3, cobble, 5, stick, 8, stick));
        register("stone_axe", "minecraft:stone_axe", Map.of(1, cobble, 2, cobble, 4, cobble, 5, stick, 8, stick));
        register("stone_shovel", "minecraft:stone_shovel", Map.of(2, cobble, 5, stick, 8, stick));
        register("stone_sword", "minecraft:stone_sword", Map.of(2, cobble, 5, stick, 8, stick));
    }

    public static Recipe get(String id) {
        return RECIPES.get(id);
    }
}