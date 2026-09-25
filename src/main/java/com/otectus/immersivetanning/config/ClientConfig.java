package com.otectus.immersivetanning.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Presentation settings. None of these change scoring. */
public final class ClientConfig {
    public static final ForgeConfigSpec SPEC;

    public enum ParticleAmount { NORMAL, REDUCED, MINIMAL }

    public static final ForgeConfigSpec.BooleanValue SHOW_NUMERIC_SCORES;
    public static final ForgeConfigSpec.BooleanValue SHOW_GRAIN_HINTS;
    public static final ForgeConfigSpec.EnumValue<ParticleAmount> PARTICLE_AMOUNT;
    public static final ForgeConfigSpec.BooleanValue HIGH_CONTRAST_MINIGAMES;
    public static final ForgeConfigSpec.BooleanValue COLORBLIND_SAFE_COLORS;
    public static final ForgeConfigSpec.BooleanValue LARGE_MINIGAME_MARKERS;
    public static final ForgeConfigSpec.BooleanValue TIMING_CUE_SOUNDS;
    public static final ForgeConfigSpec.DoubleValue SOUND_CUE_VOLUME;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("tooltips");
        SHOW_NUMERIC_SCORES = b.comment("Show Integrity and Workmanship as 0-100 numbers next to their grade.")
                .define("showNumericScores", true);
        b.pop();
        b.comment("Accessibility. These options only change presentation; they never widen scoring windows.").push("accessibility");
        SHOW_GRAIN_HINTS = b.comment("Draw faint grain lines over the flesh while fleshing. Scraping along the grain removes more per pass.")
                .define("showGrainHints", true);
        PARTICLE_AMOUNT = b.defineEnum("particleAmount", ParticleAmount.NORMAL);
        HIGH_CONTRAST_MINIGAMES = b.define("highContrastMinigames", false);
        COLORBLIND_SAFE_COLORS = b.comment("Use a blue/orange palette instead of green/red.")
                .define("colorblindSafeColors", false);
        LARGE_MINIGAME_MARKERS = b.comment("Draw stitch markers larger. The timing window is unchanged.")
                .define("largeMinigameMarkers", false);
        TIMING_CUE_SOUNDS = b.define("timingCueSounds", true);
        SOUND_CUE_VOLUME = b.defineInRange("soundCueVolume", 0.8, 0.0, 1.0);
        b.pop();
        SPEC = b.build();
    }

    public static boolean get(ForgeConfigSpec.BooleanValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static double get(ForgeConfigSpec.DoubleValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static ParticleAmount particleAmount() {
        return SPEC.isLoaded() ? PARTICLE_AMOUNT.get() : PARTICLE_AMOUNT.getDefault();
    }

    /** Scales a particle count by the configured amount, keeping at least one when count > 0 unless minimal. */
    public static int scaleParticles(int count) {
        return switch (particleAmount()) {
            case NORMAL -> count;
            case REDUCED -> Math.max(count > 0 ? 1 : 0, count / 2);
            case MINIMAL -> count > 0 ? Math.min(1, count) : 0;
        };
    }

    private ClientConfig() {}
}
