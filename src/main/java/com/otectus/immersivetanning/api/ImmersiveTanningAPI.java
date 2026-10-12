package com.otectus.immersivetanning.api;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.leather.LeatherworkData;
import com.otectus.immersivetanning.recipe.LeatherworkingRecipe;
import com.otectus.immersivetanning.trait.TraitEffects;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Public entry points for other mods. Everything else in Immersive Tanning is implementation detail.
 * <ul>
 *     <li>Hides, traits, seams and bench recipes are data: see the README for the datapack formats.</li>
 *     <li>Custom trait behaviour is code: register a {@link TraitEffect} and name it in a hide trait JSON.</li>
 * </ul>
 */
public final class ImmersiveTanningAPI {

    /** Registers a trait effect under {@code id}. Call during mod construction or common setup. */
    public static void registerTraitEffect(ResourceLocation id, TraitEffect effect) {
        TraitEffects.register(id, effect);
    }

    /** The finish, Integrity, Workmanship and trait of a piece made at the Leatherworker's Bench, if any. */
    public static Optional<LeatherworkData> leatherwork(ItemStack stack) {
        return LeatherworkData.get(stack);
    }

    /**
     * Whether a player stitched the piece at the Leatherworker's Bench. Villager and loot leatherwork also carry a
     * crafter's name (an MCA villager's, for instance), so the name alone does not tell.
     */
    public static boolean playerMade(ItemStack stack) {
        return LeatherworkData.crafterId(stack).isPresent();
    }

    // ---- 1.1.0: recipe gates and assistance (progression hooks) ---------------------------------------------------

    private static final List<ILeatherworkRecipeGate> RECIPE_GATES = new CopyOnWriteArrayList<>();
    private static final List<ITanningAssistSource> ASSIST_SOURCES = new CopyOnWriteArrayList<>();

    /** Registers a gate over bench recipes. Call during mod construction or common setup. */
    public static void registerRecipeGate(ILeatherworkRecipeGate gate) { if (gate != null) RECIPE_GATES.add(gate); }
    public static void unregisterRecipeGate(ILeatherworkRecipeGate gate) { RECIPE_GATES.remove(gate); }
    /** Registers a per-player fleshing time source. Call during mod construction or common setup. */
    public static void registerAssistSource(ITanningAssistSource source) { if (source != null) ASSIST_SOURCES.add(source); }
    public static void unregisterAssistSource(ITanningAssistSource source) { ASSIST_SOURCES.remove(source); }
    public static List<ILeatherworkRecipeGate> recipeGates() { return Collections.unmodifiableList(RECIPE_GATES); }
    public static List<ITanningAssistSource> assistSources() { return Collections.unmodifiableList(ASSIST_SOURCES); }

    /** The first registered reason to refuse {@code recipe} to {@code player}, if any. A gate that throws is skipped. */
    public static Optional<Component> recipeDenial(ServerPlayer player, LeatherworkingRecipe recipe) {
        for (ILeatherworkRecipeGate gate : RECIPE_GATES) {
            try {
                Optional<Component> reason = gate.deny(player, recipe);
                if (reason != null && reason.isPresent()) return reason;
            } catch (RuntimeException e) {
                ImmersiveTanning.LOGGER.error("Recipe gate {} failed for {}", gate.getClass().getName(), recipe.getId(), e);
            }
        }
        return Optional.empty();
    }

    /**
     * The fleshing time allowance for {@code player}: {@code baseMs} times every source's answer, the product clamped to
     * {@code 1.0..maxAssistTimeMultiplier}. Exactly {@code baseMs} with no sources.
     */
    public static int assistedFleshingMs(ServerPlayer player, int baseMs) {
        double product = 1.0;
        for (ITanningAssistSource source : ASSIST_SOURCES) {
            try {
                double value = source.fleshingTimeMultiplier(player);
                if (Double.isFinite(value) && value > 0) product *= value;
            } catch (RuntimeException e) {
                ImmersiveTanning.LOGGER.error("Assist source {} failed for {}", source.getClass().getName(), player.getGameProfile().getName(), e);
            }
        }
        double ceiling = Math.max(1.0, ServerConfig.get(ServerConfig.MAX_ASSIST_TIME_MULTIPLIER));
        return (int) Math.round(baseMs * Math.max(1.0, Math.min(ceiling, product)));
    }

    private ImmersiveTanningAPI() {}
}
