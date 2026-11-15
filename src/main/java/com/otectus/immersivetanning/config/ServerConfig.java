package com.otectus.immersivetanning.config;

import com.otectus.immersivetanning.compat.geneticanimals.HideMode;
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

    public static final ForgeConfigSpec.DoubleValue SCALE_MINIMUM_EFFICACY_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue SCALE_MAXIMUM_EFFICACY_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue SCALE_MINIMUM_DURABILITY_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue SCALE_MAXIMUM_DURABILITY_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue SCALE_SUPPLE_SPEED_PER_PIECE;
    public static final ForgeConfigSpec.DoubleValue SCALE_SUPPLE_VISIBILITY_PER_PIECE;
    public static final ForgeConfigSpec.DoubleValue SCALE_HARNESS_DURABILITY_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue SCALE_HARDENED_ARMOR_PER_PIECE;
    public static final ForgeConfigSpec.DoubleValue SCALE_HARDENED_TOUGHNESS_PER_PIECE;
    public static final ForgeConfigSpec.BooleanValue SCALE_ALLOW_SCRAP_REPAIR;

    public static final ForgeConfigSpec.DoubleValue REPAIR_FRACTION_PER_SCRAP;
    public static final ForgeConfigSpec.IntValue LEATHER_PER_OFFCUT;

    public static final ForgeConfigSpec.BooleanValue ENABLE_AUTOMATION;

    public static final ForgeConfigSpec.BooleanValue ENABLE_VILLAGER_TRADES;
    public static final ForgeConfigSpec.IntValue BENCH_EXPERIENCE;

    public static final ForgeConfigSpec.BooleanValue CHALLENGE_TIMING;
    public static final ForgeConfigSpec.DoubleValue VAT_TENDING_CREDIT;
    public static final ForgeConfigSpec.IntValue LEASE_IDLE_SECONDS;
    public static final ForgeConfigSpec.BooleanValue ALLOW_ASSISTED_PROFILE;
    public static final ForgeConfigSpec.BooleanValue OVERRIDE_QUALITY_WEIGHTS;
    public static final ForgeConfigSpec.DoubleValue CUTTING_WEIGHT;
    public static final ForgeConfigSpec.DoubleValue ASSEMBLY_WEIGHT;
    public static final ForgeConfigSpec.DoubleValue STITCHING_WEIGHT;

    public static final ForgeConfigSpec.BooleanValue GENETIC_ANIMALS_ENABLED;
    public static final ForgeConfigSpec.EnumValue<HideMode> GENETIC_ANIMALS_HIDE_MODE;
    public static final ForgeConfigSpec.BooleanValue GENETIC_ANIMALS_TACK_AT_BENCH;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Hide drops. Which animals drop which hide is datapack-driven (immersive_tanning/hide_sources).").push("hides");
        REPLACE_LEATHER_DROPS = b.comment("Remove items in #immersive_tanning:replaced_by_hides (leather, rabbit hide) from animals that drop a hide.",
                        "Off keeps that leather or rabbit hide next to the hide, so such an animal yields both; it does not stop hides.")
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
        REPLACE_LEATHER_EQUIPMENT_RECIPES = b.comment("Disable crafting-table recipes for leather equipment that the Leatherworker's Bench can make,",
                        "and the crafting recipes a bench recipe names in \"replaces\" (such as Ice and Fire's dragon-scale armor).",
                        "Off keeps every crafting recipe next to the bench recipes. Applies at the next /reload or restart.")
                .define("replaceLeatherEquipmentRecipes", true);
        AUTO_DETECT_LEATHER_EQUIPMENT = b.comment("Generate bench recipes from crafting recipes of #immersive_tanning:leatherwork_equipment and leather armor.")
                .define("autoDetectLeatherEquipment", true);
        RECIPE_BLACKLIST = b.comment("Crafting recipe ids that automatic detection ignores and that are never disabled, even when a bench recipe",
                        "names them in \"replaces\" (both recipes then stay).")
                .defineListAllowEmpty("recipeBlacklist", List.of(), o -> o instanceof String);
        MOD_BLACKLIST = b.comment("Mod ids whose equipment is never routed through the bench: their bench recipes are disabled and their",
                        "crafting recipes stay. \"iceandfire\" turns the dragon-scale armor recipes off.")
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

        b.comment("Hands-on work at the stations (1.3). Work is untimed by default: a hide is done when it is clean, a piece",
                "when its seams are tied. The old fleshing timer is the optional challenge below.").push("work");
        CHALLENGE_TIMING = b.comment("Fleshing has a time allowance (minigames.fleshingTimeSeconds, lengthened by ITanningAssistSource",
                        "progression mods up to minigames.maxAssistTimeMultiplier); running out finishes the hide at its current Integrity.",
                        "Off (default): no deadline, and assistance has nothing to lengthen. Time counts only while someone works the hide.")
                .define("challengeTiming", false);
        VAT_TENDING_CREDIT = b.comment("One slow paddle sweep through a vat moves each steeping hide this share of its tanning time closer",
                        "to done, once per hide ever (0 = sweeping is only for show). Untended hides finish at the normal time, same quality.")
                .defineInRange("vatTendingCredit", 0.1, 0.0, 0.5);
        LEASE_IDLE_SECONDS = b.comment("A player who stops interacting at a station for this long lets go of it (the work stays).")
                .defineInRange("leaseIdleSeconds", 90, 10, 3600);
        ALLOW_ASSISTED_PROFILE = b.comment("Players may ask for the assisted interaction profile (a client accessibility option): wider cut lines,",
                        "holes and seating guides and a wider clean-pull band. The best possible quality is the same.")
                .define("allowAssistedProfile", true);
        OVERRIDE_QUALITY_WEIGHTS = b.comment("Use the three weights below for every bench piece instead of each work pattern's own quality_weights.")
                .define("overrideQualityWeights", false);
        CUTTING_WEIGHT = b.defineInRange("cuttingWeight", 0.25, 0.0, 1.0);
        ASSEMBLY_WEIGHT = b.defineInRange("assemblyWeight", 0.15, 0.0, 1.0);
        STITCHING_WEIGHT = b.defineInRange("stitchingWeight", 0.6, 0.0, 1.0);
        b.pop();

        b.comment("Kept for packs from before 1.3. Only work.challengeTiming = true gives the fleshing timer and assistance any effect;",
                "the ready countdown and the timing keys no longer apply (there is no rhythm or countdown).").push("minigames");
        FLESHING_TIME_SECONDS = b.comment("Challenge timing only: time allowed to flesh a hide. Running out completes the hide at its current Integrity.")
                .defineInRange("fleshingTimeSeconds", 25, 5, 300);
        MAX_ASSIST_TIME_MULTIPLIER = b.comment("Challenge timing only: ceiling on the per-player fleshing time other mods may grant (ITanningAssistSource).",
                        "1 refuses all assistance; 1.5 allows up to half again fleshingTimeSeconds.")
                .defineInRange("maxAssistTimeMultiplier", 1.5, 1.0, 3.0);
        READY_DELAY_MS = b.comment("No longer used (1.3 has no countdown). Kept so older configs load unchanged.")
                .defineInRange("readyDelayMs", 1500, 0, 10000);
        TIMING_TOLERANCE_MS = b.comment("No longer used (1.3 judges where the tools went, not when). Kept so older configs load unchanged.")
                .defineInRange("timingToleranceMs", 120, 0, 2000);
        MAX_LATENCY_COMPENSATION_MS = b.comment("No longer used. Kept so older configs load unchanged.")
                .defineInRange("maxLatencyCompensationMs", 400, 0, 5000);
        MAX_STATION_DISTANCE = b.comment("Maximum distance in blocks between a player's body and the station they work at (the camera may move closer).")
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

        b.comment("Scale-backed leatherwork: another mod's armor made at the bench from finished leather (the backing) and that",
                "mod's own materials, such as Ice and Fire dragon-scale armor (#immersive_tanning:scale_backed). Its own armor",
                "does the work, so finish and quality act on it far less than on ordinary leather; hide traits act as usual.",
                "A minimum above its maximum leaves scale-backed pieces without finish and quality effects (with a warning),",
                "never with the ordinary leather values. Supple boots muffle footsteps as set in the finishes section.").push("scaleBacked");
        SCALE_MINIMUM_EFFICACY_MULTIPLIER = b.comment("Workmanship 0: multiplier of the piece's own armor and of its finish effects.")
                .defineInRange("minimumEfficacyMultiplier", 0.95, 0.05, 10.0);
        SCALE_MAXIMUM_EFFICACY_MULTIPLIER = b.comment("Workmanship 100: multiplier of the piece's own armor and of its finish effects.")
                .defineInRange("maximumEfficacyMultiplier", 1.05, 0.05, 10.0);
        SCALE_MINIMUM_DURABILITY_MULTIPLIER = b.comment("Integrity 0: effective durability multiplier.")
                .defineInRange("minimumDurabilityMultiplier", 0.9, 0.05, 10.0);
        SCALE_MAXIMUM_DURABILITY_MULTIPLIER = b.comment("Integrity 100: effective durability multiplier.")
                .defineInRange("maximumDurabilityMultiplier", 1.1, 0.05, 10.0);
        SCALE_SUPPLE_SPEED_PER_PIECE = b.comment("Supple: movement speed bonus per piece (0.01 = +1%).")
                .defineInRange("suppleSpeedPerPiece", 0.01, 0.0, 1.0);
        SCALE_SUPPLE_VISIBILITY_PER_PIECE = b.comment("Supple: share by which mobs' detection range shrinks per piece.")
                .defineInRange("suppleVisibilityPerPiece", 0.04, 0.0, 0.25);
        SCALE_HARNESS_DURABILITY_MULTIPLIER = b.comment("Harness: effective durability multiplier, on top of Integrity's.")
                .defineInRange("harnessDurabilityMultiplier", 1.2, 1.0, 10.0);
        SCALE_HARDENED_ARMOR_PER_PIECE = b.comment("Hardened: flat armor added per piece (scaled by Workmanship), in place of a share of the piece's armor.")
                .defineInRange("hardenedArmorPerPiece", 0.5, 0.0, 10.0);
        SCALE_HARDENED_TOUGHNESS_PER_PIECE = b.comment("Hardened: flat armor toughness added per piece (scaled by Workmanship).")
                .defineInRange("hardenedToughnessPerPiece", 0.25, 0.0, 10.0);
        SCALE_ALLOW_SCRAP_REPAIR = b.comment("Let Leather Scraps and an Awl patch scale-backed pieces, by the repair section's amounts.",
                        "Off: they are mended the way their own mod mends them (an anvil, their own materials).")
                .define("allowScrapRepair", false);
        b.pop();

        b.comment("Field repair with Leather Scraps and an Awl").push("repair");
        REPAIR_FRACTION_PER_SCRAP = b.comment("Share of maximum durability one scrap restores (before the Harness multiplier).")
                .defineInRange("repairFractionPerScrap", 0.2, 0.01, 1.0);
        LEATHER_PER_OFFCUT = b.comment("One Leather Scrap is left over for every this many leather cut at the bench (0 = none).")
                .defineInRange("leatherPerOffcut", 3, 0, 64);
        b.pop();

        b.comment("Automation can supply the vat but never flesh, finish or stitch, and never takes a reserved bench job.").push("automation");
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

        b.comment("Other mods. Each section acts only when its mod is installed; without it the settings do nothing.").push("compat");
        b.comment("Genetic Animals (eanimod). Its cows, mooshrooms, moobloom, llamas, sheep, pigs and rabbits drop raw hides, its",
                "leather collar and bridle are made at the bench, and tack keeps its Immersive Tanning data when it is put on an animal.",
                "Binding Leather in its drops and trades follows the bindingLeather section either way.").push("geneticAnimals");
        GENETIC_ANIMALS_ENABLED = b.comment("Off: Genetic Animals' drops, tack and equipping are left exactly as that mod makes them; no hide is added.")
                .define("enabled", true);
        GENETIC_ANIMALS_HIDE_MODE = b.comment("NATIVE: the leather and rabbit hide an animal really drops become raw hides (each leather is one",
                        "leather's worth: 4 leather from a cow is a large and a small hide). Genes, coat, age and fire decide, as they decide",
                        "Genetic Animals' own drops, so a wool sheep, a burning cow or a pig drops none. Looting acts only as that mod lets it.",
                        "TANNING: every adult drops its species' hide (large: cow, mooshroom, moobloom, llama; medium: sheep, pig; small:",
                        "rabbit), even with no leather or with wool; lootingAddsHides applies once. A hide source's size and count decide",
                        "in both modes (NATIVE only uses the size as the largest hide); size \"none\" or count 0 stops hides in both.")
                .defineEnum("hideMode", HideMode.NATIVE);
        GENETIC_ANIMALS_TACK_AT_BENCH = b.comment("Make the leather collar and leather bridle at the Leatherworker's Bench (from their usual leather and iron",
                        "nuggets) in place of the crafting table. Saddles, cloth tack and the ringed, belled and decorated pieces keep the",
                        "trades they come from. Applies at the next /reload or restart.")
                .define("leatherTackAtBench", true);
        b.pop(2);

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

    public static <E extends Enum<E>> E get(ForgeConfigSpec.EnumValue<E> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    @SuppressWarnings("unchecked")
    public static List<String> list(ForgeConfigSpec.ConfigValue<List<? extends String>> value) {
        return (List<String>) (SPEC.isLoaded() ? value.get() : value.getDefault());
    }

    private ServerConfig() {}
}
