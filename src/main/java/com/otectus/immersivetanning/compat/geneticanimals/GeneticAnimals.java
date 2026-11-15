package com.otectus.immersivetanning.compat.geneticanimals;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.config.ServerConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

/**
 * Genetic Animals (mod id {@code eanimod}) integration: its state and switches. Everything here works from registry
 * ids and config, so nothing loads a Genetic Animals class; the code that reaches into its classes is the optional
 * mixins in {@code mixin/compat/eanimod}, applied only when it is installed (see {@link GeneticAnimalsHooks}).
 *
 * <p>Which of its animals drop which hide is data: {@code hide_sources/compat/eanimod/}. The rest is
 * {@link GeneticAnimalsDrops} (death drops), {@link GeneticAnimalsEquipment} (tack keeps its data when put on) and
 * {@link GeneticAnimalsTrades} (its offers deal in Binding Leather).</p>
 */
public final class GeneticAnimals {
    public static final String MOD_ID = GeneticAnimalsHooks.MOD_ID;
    /** The release this integration was built and tested against (docs/compat/genetic-animals.md). */
    public static final String VERIFIED_VERSION = "0.11.12";

    public static boolean isLoaded() {
        return GeneticAnimalsHooks.present();
    }

    /** The installed version, or null when Genetic Animals is not installed. */
    public static String version() {
        return GeneticAnimalsHooks.version();
    }

    public static boolean verified() {
        return VERIFIED_VERSION.equals(version());
    }

    /** Installed and switched on ({@code compat.geneticAnimals.enabled}). */
    public static boolean enabled() {
        return isLoaded() && ServerConfig.get(ServerConfig.GENETIC_ANIMALS_ENABLED);
    }

    public static HideMode mode() {
        return ServerConfig.get(ServerConfig.GENETIC_ANIMALS_HIDE_MODE);
    }

    /** Whether its leather collar and bridle are made at the bench. */
    public static boolean tackAtBench() {
        return enabled() && ServerConfig.get(ServerConfig.GENETIC_ANIMALS_TACK_AT_BENCH);
    }

    /** A Genetic Animals entity: its hides are decided at death (or not at all while the integration is off). */
    public static boolean owns(EntityType<?> type) {
        return isLoaded() && MOD_ID.equals(EntityType.getKey(type).getNamespace());
    }

    /** A Genetic Animals item. */
    public static boolean owns(Item item) {
        return isLoaded() && MOD_ID.equals(BuiltInRegistries.ITEM.getKey(item).getNamespace());
    }

    /** One line for {@code /immersive_tanning report} and the log. */
    public static String summary() {
        if (!isLoaded()) return "Genetic Animals: not installed";
        String version = version();
        StringBuilder out = new StringBuilder("Genetic Animals ").append(version)
                .append(verified() ? " (the verified release)" : " (not the verified " + VERIFIED_VERSION + "; hooks below are checked as its classes load)");
        if (!enabled()) return out.append(": integration off (compat.geneticAnimals.enabled = false); its drops, tack and equipping are its own").toString();
        out.append(": hides ").append(mode() == HideMode.NATIVE ? "from the skin it drops (NATIVE)" : "by species (TANNING)")
                .append(", leather tack ").append(tackAtBench() ? "at the bench" : "on its own recipes");
        out.append("; hooks: ").append(String.join(", ", hookStates()));
        return out.toString();
    }

    /** Each hook and its state, for diagnostics. Loads the trade class (without initialising it) so its hooks are known. */
    public static List<String> hookStates() {
        if (isLoaded()) {
            try {
                Class.forName(GeneticAnimalsHooks.Hook.VILLAGER_TRADES.targetClass, false, GeneticAnimals.class.getClassLoader());
            } catch (ClassNotFoundException | LinkageError e) {
                ImmersiveTanning.LOGGER.debug("Genetic Animals trade class not loadable: {}", e.toString());
            }
        }
        List<String> out = new ArrayList<>();
        for (GeneticAnimalsHooks.Hook hook : GeneticAnimalsHooks.Hook.values()) {
            out.add(hook.name().toLowerCase(java.util.Locale.ROOT) + " " + GeneticAnimalsHooks.state(hook).name().toLowerCase(java.util.Locale.ROOT));
        }
        return out;
    }

    /** Whether every hook into its classes is in place. */
    public static boolean hooksApplied() {
        hookStates();
        for (GeneticAnimalsHooks.Hook hook : GeneticAnimalsHooks.Hook.values()) {
            if (GeneticAnimalsHooks.state(hook) != GeneticAnimalsHooks.State.APPLIED) return false;
        }
        return true;
    }

    private GeneticAnimals() {}
}
