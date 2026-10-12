package com.otectus.immersivetanning.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * Gameplay configuration. SERVER configs are per world and synced to clients, so the client can read these values
 * once it has joined a world. Every read goes through the helpers at the bottom, which fall back to defaults while
 * no world is loaded.
 */
public final class ServerConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue REPLACE_LEATHER_DROPS;
    public static final ForgeConfigSpec.BooleanValue LOOTING_ADDS_HIDES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CARCASS_ENCHANTMENTS;
    public static final ForgeConfigSpec.BooleanValue REPLACE_LEATHER_LOOT;
    public static final ForgeConfigSpec.BooleanValue REPLACE_LEATHER_IN_TRADES;

    public static final ForgeConfigSpec.BooleanValue REPLACE_LEATHER_EQUIPMENT_RECIPES;
    public static final ForgeConfigSpec.BooleanValue AUTO_DETECT_LEATHER_EQUIPMENT;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> RECIPE_BLACKLIST;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> MOD_BLACKLIST;

    public static final ForgeConfigSpec.IntValue TANNING_TICKS;
    public static final ForgeConfigSpec.IntValue VAT_CAPACITY;
    public static final ForgeConfigSpec.IntValue VAT_MAX_TANNIN;
    public static final ForgeConfigSpec.IntValue BARK_PER_LOG;
    public static final ForgeConfigSpec.IntValue DRYING_TICKS;

    public static final ForgeConfigSpec.IntValue FLESHING_TIME_SECONDS;
    public static final ForgeConfigSpec.DoubleValue MAX_ASSIST_TIME_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue READY_DELAY_MS;
    public static final ForgeConfigSpec.IntValue TIMING_TOLERANCE_MS;
    public static final ForgeConfigSpec.IntValue MAX_LATENCY_COMPENSATION_MS;
    public static final ForgeConfigSpec.DoubleValue MAX_STATION_DISTANCE;

    public static final ForgeConfigSpec.DoubleValue MINIMUM_DURABILITY_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue MAXIMUM_DURABILITY_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue MINIMUM_EFFICACY_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue MAXIMUM_EFFICACY_MULTIPLIER;

    public static final ForgeConfigSpec.DoubleValue SUPPLE_SPEED_PER_PIECE;
    public static final ForgeConfigSpec.DoubleValue SUPPLE_VISIBILITY_PER_PIECE;
    public static final ForgeConfigSpec.BooleanValue SUPPLE_BOOTS_MUFFLE_STEPS;
    public static final ForgeConfigSpec.DoubleValue HARNESS_DURABILITY_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue HARNESS_REPAIR_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue HARDENED_ARMOR_FACTOR;
    public static final ForgeConfigSpec.DoubleValue HARDENED_TOUGHNESS_PER_PIECE;

    public static final ForgeConfigSpec.DoubleValue REPAIR_FRACTION_PER_SCRAP;
    public static final ForgeConfigSpec.IntValue LEATHER_PER_OFFCUT;

    public static final ForgeConfigSpec.BooleanValue ENABLE_AUTOMATION;

    public static final ForgeConfigSpec.BooleanValue ENABLE_VILLAGER_TRADES;
    public static final ForgeConfigSpec.IntValue BENCH_EXPERIENCE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Hide drops. Which animals drop which hide is datapack-driven (immersive_tanning/hide_sources).").push("hides");
        REPLACE_LEATHER_DROPS = b.comment("Remove items in #immersive_tanning:replaced_by_hides (leather, rabbit hide) from animals that drop a hide.")
                .define("replaceLeatherDrops", true);
        LOOTING_ADDS_HIDES = b.comment("Each Looting level can add one more hide.")
                .define("lootingAddsHides", false);
        CARCASS_ENCHANTMENTS = b.comment("A weapon with one of these enchantments butchers a carcass (another mod's skinning path) instead of",
                        "dropping a hide, like the weapons in #immersive_tanning:carcass_tools. Only #immersive_tanning:carcass_animals are affected.")
                .defineListAllowEmpty("carcassEnchantments", List.of("butchery:butcherstouch"), o -> o instanceof String);
        b.pop();

        b.comment("Binding Leather replaces vanilla leather. In recipes this is always on (recipes that took leather take",
                "Binding Leather, recipes that made leather make it); these options cover loot and trades.").push("bindingLeather");
        REPLACE_LEATHER_LOOT = b.comment("Turn vanilla leather in any loot (mob drops, chests, fishing) into Binding Leather.")
                .define("replaceLeatherLoot", true);
        REPLACE_LEATHER_IN_TRADES = b.comment("Villager and wandering-trader offers ask for and pay Binding Leather instead of vanilla leather.")
                .define("replaceLeatherInTrades", true);
        b.pop();

        b.comment("Leatherworking recipes").push("recipes");
        REPLACE_LEATHER_EQUIPMENT_RECIPES = b.comment("Disable crafting-table recipes for leather equipment that the Leatherworker's Bench can make.")
                .define("replaceLeatherEquipmentRecipes", true);
        AUTO_DETECT_LEATHER_EQUIPMENT = b.comment("Generate bench recipes from crafting recipes of #immersive_tanning:leatherwork_equipment and leather armor.")
                .define("autoDetectLeatherEquipment", true);
        RECIPE_BLACKLIST = b.comment("Crafting recipe ids that automatic detection ignores and never disables.")
                .defineListAllowEmpty("recipeBlacklist", List.of(), o -> o instanceof String);
        MOD_BLACKLIST = b.comment("Mod ids whose equipment is never routed through the bench.")
                .defineListAllowEmpty("modBlacklist", List.of(), o -> o instanceof String);
        b.pop();

        b.comment("Passive stages. Progress follows world time, so it continues while nobody is nearby.").push("tannery");
        TANNING_TICKS = b.comment("Ticks a hide steeps in the Tanner's Vat.")
                .defineInRange("tanningTicks", 3600, 20, 72000);
        VAT_CAPACITY = b.comment("Hides one Tanner's Vat holds at once.")
                .defineInRange("vatCapacity", 4, 1, 16);
        VAT_MAX_TANNIN = b.comment("Tannin charges (one per bark) a vat can hold. Values below the largest hide's cost (2, a large hide)",
                        "act as 2, so every hide can start; 1 is still accepted so older configs load unchanged.")
                .defineInRange("vatMaxTannin", 16, 1, 256);
        BARK_PER_LOG = b.comment("Bark gained when a Tanner's Knife strips a log.")
                .defineInRange("barkPerLog", 2, 1, 16);
        DRYING_TICKS = b.comment("Ticks a finished hide dries on the Tanner's Rack.")
                .defineInRange("dryingTicks", 2400, 20, 72000);
        b.pop();

        b.comment("Minigame timing and validation").push("minigames");
        FLESHING_TIME_SECONDS = b.comment("Time allowed to flesh a hide. Running out completes the hide at its current Integrity.")
                .defineInRange("fleshingTimeSeconds", 25, 5, 300);
        MAX_ASSIST_TIME_MULTIPLIER = b.comment("Ceiling on the per-player fleshing time other mods may grant (ITanningAssistSource).",
                        "1 refuses all assistance; 1.5 allows up to half again fleshingTimeSeconds.")
                .defineInRange("maxAssistTimeMultiplier", 1.5, 1.0, 3.0);
        READY_DELAY_MS = b.comment("Countdown before a minigame starts, in milliseconds.")
                .defineInRange("readyDelayMs", 1500, 0, 10000);
        TIMING_TOLERANCE_MS = b.comment("Jitter tolerance applied to client-reported input times.")
                .defineInRange("timingToleranceMs", 120, 0, 2000);
        MAX_LATENCY_COMPENSATION_MS = b.comment("Upper bound of the latency compensation granted to a player's inputs.")
                .defineInRange("maxLatencyCompensationMs", 400, 0, 5000);
        MAX_STATION_DISTANCE = b.comment("Maximum distance in blocks between a player and the station they are working at.")
                .defineInRange("maxStationDistance", 6.0, 2.0, 32.0);
        b.pop();

        b.comment("Craftsmanship multipliers. A score of 50 is the midpoint between minimum and maximum.",
                "Integrity (fleshing) scales durability; Workmanship (stitching) scales armor and finish effects.").push("quality");
        MINIMUM_DURABILITY_MULTIPLIER = b.defineInRange("minimumDurabilityMultiplier", 0.8, 0.05, 10.0);
        MAXIMUM_DURABILITY_MULTIPLIER = b.defineInRange("maximumDurabilityMultiplier", 1.2, 0.05, 10.0);
        MINIMUM_EFFICACY_MULTIPLIER = b.defineInRange("minimumEfficacyMultiplier", 0.85, 0.05, 10.0);
        MAXIMUM_EFFICACY_MULTIPLIER = b.defineInRange("maximumEfficacyMultiplier", 1.15, 0.05, 10.0);
        b.pop();

        b.comment("Finish identities. Every value is per piece, so each piece contributes and a full set is never a threshold.").push("finishes");
        SUPPLE_SPEED_PER_PIECE = b.comment("Supple: movement speed bonus per piece (0.025 = +2.5%).")
                .defineInRange("suppleSpeedPerPiece", 0.025, 0.0, 1.0);
        SUPPLE_VISIBILITY_PER_PIECE = b.comment("Supple: share by which mobs' detection range shrinks per piece.")
                .defineInRange("suppleVisibilityPerPiece", 0.08, 0.0, 0.25);
        SUPPLE_BOOTS_MUFFLE_STEPS = b.comment("Supple boots keep footsteps from triggering sculk sensors and wardens, like wool.")
                .define("suppleBootsMuffleSteps", true);
        HARNESS_DURABILITY_MULTIPLIER = b.comment("Harness: durability multiplier.")
                .defineInRange("harnessDurabilityMultiplier", 1.5, 1.0, 10.0);
        HARNESS_REPAIR_MULTIPLIER = b.comment("Harness: field-repair efficiency multiplier.")
                .defineInRange("harnessRepairMultiplier", 2.0, 1.0, 10.0);
        HARDENED_ARMOR_FACTOR = b.comment("Hardened: extra armor as a share of the piece's own armor.")
                .defineInRange("hardenedArmorFactor", 0.75, 0.0, 5.0);
        HARDENED_TOUGHNESS_PER_PIECE = b.comment("Hardened: armor toughness added per piece.")
                .defineInRange("hardenedToughnessPerPiece", 0.5, 0.0, 10.0);
        b.pop();

        b.comment("Field repair with Leather Scraps and an Awl").push("repair");
        REPAIR_FRACTION_PER_SCRAP = b.comment("Share of maximum durability one scrap restores (before the Harness multiplier).")
                .defineInRange("repairFractionPerScrap", 0.2, 0.01, 1.0);
        LEATHER_PER_OFFCUT = b.comment("One Leather Scrap is left over for every this many leather cut at the bench (0 = none).")
                .defineInRange("leatherPerOffcut", 3, 0, 64);
        b.pop();

        b.comment("Automation can supply the vat but never flesh, finish or stitch.").push("automation");
        ENABLE_AUTOMATION = b.comment("Let hoppers and pipes insert water, bark and scraped hides into the vat and extract tanned hides.")
                .define("enableAutomation", true);
        b.pop();

        b.comment("Villages and progression").push("progression");
        ENABLE_VILLAGER_TRADES = b.comment("Leatherworkers (and More Villagers' hunters) trade tanning tools, hides and village-made leatherwork;",
                        "wandering traders sometimes carry a trophy hide. Checked whenever an offer is generated.")
                .define("enableVillagerTrades", true);
        BENCH_EXPERIENCE = b.comment("Experience for a finished bench piece at Standard quality; Crude gives about half, Masterwork about 1.5x (0 = none).")
                .defineInRange("benchExperience", 4, 0, 100);
        b.pop();

        SPEC = b.build();
    }

    public static boolean get(ForgeConfigSpec.BooleanValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static int get(ForgeConfigSpec.IntValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static double get(ForgeConfigSpec.DoubleValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    @SuppressWarnings("unchecked")
    public static List<String> list(ForgeConfigSpec.ConfigValue<List<? extends String>> value) {
        return (List<String>) (SPEC.isLoaded() ? value.get() : value.getDefault());
    }

    private ServerConfig() {}
}
