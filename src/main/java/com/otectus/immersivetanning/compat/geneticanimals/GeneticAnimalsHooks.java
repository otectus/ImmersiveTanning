package com.otectus.immersivetanning.compat.geneticanimals;

import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.forgespi.language.IModFileInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The code hooks into Genetic Animals' own classes and whether each one took. The mixin plugin fills this in as those
 * classes load (it may not touch Minecraft classes, so neither does this); diagnostics read it.
 */
public final class GeneticAnimalsHooks {
    public static final String MOD_ID = "eanimod";

    public enum Hook {
        /**
         * {@code LivingEntity#dropAllDeathLoot}: the brackets around its death loot and its inventory that tell skin from
         * cargo. Vanilla's, so it applies with or without Genetic Animals; another mod rewriting that method can stop it.
         */
        DEATH_DROPS("net.minecraft.world.entity.LivingEntity", null, "immersive_tanning$openDeathLoot", "immersive_tanning$closeDeathLoot",
                "immersive_tanning$openCargo", "immersive_tanning$closeCargo"),
        /** {@code EnhancedAnimalAbstract#getReplacementItemWithColour}: bridles and Genetic Animals saddles put on by hand. */
        EQUIPMENT("mokiyoki.enhancedanimals.entity.EnhancedAnimalAbstract", "getReplacementItemWithColour", "immersive_tanning$keepEquipment"),
        /** {@code EnhancedAnimalRideableAbstract#saddleAnimal}: the vanilla saddle put on by hand. */
        SADDLE("mokiyoki.enhancedanimals.entity.EnhancedAnimalRideableAbstract", "saddleAnimal", "immersive_tanning$keepSaddle"),
        /** {@code EanimodVillagerTrades#getEanimodTrade}: offers added to leatherworkers and shepherds. */
        VILLAGER_TRADES("mokiyoki.enhancedanimals.util.EanimodVillagerTrades", "getEanimodTrade", "immersive_tanning$bindingLeatherOffer"),
        /** {@code EanimodVillagerTrades#getWanderingEanimodTrade}: offers added to wandering traders. */
        WANDERING_TRADES("mokiyoki.enhancedanimals.util.EanimodVillagerTrades", "getWanderingEanimodTrade", "immersive_tanning$bindingLeatherWanderingOffer");

        public final String targetClass;
        /** The method that must call the handlers, or null for any (a vanilla method has another name in production). */
        public final String targetMethod;
        public final String[] handlers;

        Hook(String targetClass, String targetMethod, String... handlers) {
            this.targetClass = targetClass;
            this.targetMethod = targetMethod;
            this.handlers = handlers;
        }
    }

    public enum State {
        /** Genetic Animals is not installed. */
        ABSENT,
        /** Installed, but the class has not loaded yet, so the hook is not in place yet. */
        PENDING,
        APPLIED,
        /** The class loaded and the hook is not in it: that path keeps Genetic Animals' own behaviour. */
        FAILED
    }

    private static final Map<Hook, State> STATES = new ConcurrentHashMap<>();
    private static volatile Boolean present;
    private static volatile String version;

    /** Whether Genetic Animals is installed. Safe from the mixin plugin, before any mod has constructed. */
    public static boolean present() {
        if (present == null) {
            LoadingModList mods = LoadingModList.get();
            IModFileInfo file = mods == null ? null : mods.getModFileById(MOD_ID);
            version = file == null || file.getMods().isEmpty() ? null : file.getMods().get(0).getVersion().toString();
            present = file != null;
        }
        return present;
    }

    /** The installed version, or null when absent. */
    public static String version() {
        return present() ? version : null;
    }

    public static State state(Hook hook) {
        if (!present()) return State.ABSENT;
        return STATES.getOrDefault(hook, State.PENDING);
    }

    /** Called by the mixin plugin once the hook's class has been transformed. */
    public static void record(Hook hook, boolean applied) {
        STATES.put(hook, applied ? State.APPLIED : State.FAILED);
    }

    private GeneticAnimalsHooks() {}
}
