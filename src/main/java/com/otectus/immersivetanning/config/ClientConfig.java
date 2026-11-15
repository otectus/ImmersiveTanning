package com.otectus.immersivetanning.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Presentation settings. None of these change scoring. */
public final class ClientConfig {
    public static final ForgeConfigSpec SPEC;

    public enum ParticleAmount { NORMAL, REDUCED, MINIMAL }

    public enum CameraMode { CINEMATIC, REDUCED_MOTION, STATIONARY }

    public enum GestureMode { HOLD, TOGGLE }

    public enum HandVisuals { HANDS_AND_TOOLS, TOOLS_ONLY }

    public static final ForgeConfigSpec.EnumValue<CameraMode> CAMERA_MODE;
    public static final ForgeConfigSpec.DoubleValue TRANSITION_SECONDS;
    public static final ForgeConfigSpec.IntValue FOCUS_FOV;
    public static final ForgeConfigSpec.BooleanValue CAMERA_SWAY;
    public static final ForgeConfigSpec.EnumValue<GestureMode> GESTURE_MODE;
    public static final ForgeConfigSpec.DoubleValue POINTER_SENSITIVITY;
    public static final ForgeConfigSpec.DoubleValue TOOL_SMOOTHING;
    public static final ForgeConfigSpec.EnumValue<HandVisuals> HAND_VISUALS;
    public static final ForgeConfigSpec.DoubleValue ARM_OPACITY;
    public static final ForgeConfigSpec.BooleanValue SUPPORTING_HAND;
    public static final ForgeConfigSpec.BooleanValue EDGE_SWITCHING;
    public static final ForgeConfigSpec.IntValue EDGE_SWITCH_DELAY_MS;
    public static final ForgeConfigSpec.BooleanValue ASSISTED_PROFILE;
    public static final ForgeConfigSpec.BooleanValue LARGE_TARGETS;
    public static final ForgeConfigSpec.BooleanValue SHOW_HINTS;
    public static final ForgeConfigSpec.BooleanValue TENSION_INDICATOR;
    public static final ForgeConfigSpec.BooleanValue JADE_IN_FOCUS;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> FAVORITE_PATTERNS;

    public static final ForgeConfigSpec.BooleanValue SHOW_NUMERIC_SCORES;
    public static final ForgeConfigSpec.BooleanValue SHOW_GRAIN_HINTS;
    public static final ForgeConfigSpec.EnumValue<ParticleAmount> PARTICLE_AMOUNT;
    public static final ForgeConfigSpec.BooleanValue HIGH_CONTRAST_MINIGAMES;
    public static final ForgeConfigSpec.BooleanValue COLORBLIND_SAFE_COLORS;
    public static final ForgeConfigSpec.BooleanValue LARGE_MINIGAME_MARKERS;
    public static final ForgeConfigSpec.BooleanValue TIMING_CUE_SOUNDS;
    public static final ForgeConfigSpec.DoubleValue SOUND_CUE_VOLUME;
    public static final ForgeConfigSpec.IntValue FEEDBACK_LABEL_MS;
    public static final ForgeConfigSpec.BooleanValue STATION_NOTICES;
    public static final ForgeConfigSpec.IntValue NOTICE_SECONDS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("tooltips");
        SHOW_NUMERIC_SCORES = b.comment("Show Integrity and Workmanship as 0-100 numbers next to their grade.")
                .define("showNumericScores", true);
        b.pop();
        b.comment("Working at a station (1.3). Every mode works the same material with the same rules; none changes scoring.").push("focus");
        CAMERA_MODE = b.comment("CINEMATIC: the view leans in to each task. REDUCED_MOTION: the same framing without travel, sway or flourish.",
                        "STATIONARY: your normal view; the parts you work are larger targets and keyboard cycling selects them.")
                .defineEnum("cameraMode", CameraMode.CINEMATIC);
        TRANSITION_SECONDS = b.comment("How long the view takes to lean in or back (cinematic mode). Leaving always gives back control at once.")
                .defineInRange("transitionSeconds", 0.35, 0.1, 1.0);
        FOCUS_FOV = b.comment("Vertical field of view of the working shots; 0 uses each station's own framing (about 50-58).")
                .defineInRange("focusFov", 0, 0, 110);
        CAMERA_SWAY = b.comment("A barely visible breathing sway while idle at a station (never during a stroke; off in reduced motion).")
                .define("cameraSway", true);
        GESTURE_MODE = b.comment("HOLD: press and drag to scrape, cut and pull. TOGGLE: click once to put the tool down, again to lift it.")
                .defineEnum("gestureMode", GestureMode.HOLD);
        POINTER_SENSITIVITY = b.comment("How far the tool moves for a mouse movement while it is down (1 = follows the cursor).")
                .defineInRange("pointerSensitivity", 1.0, 0.25, 2.0);
        TOOL_SMOOTHING = b.comment("Visual smoothing of the tool (0 = none). The accepted path is the pointer's, not the smoothed one.")
                .defineInRange("toolSmoothing", 0.35, 0.0, 0.9);
        HAND_VISUALS = b.comment("HANDS_AND_TOOLS: your arms hold the tools and steady the work. TOOLS_ONLY: only the tools.")
                .defineEnum("handVisuals", HandVisuals.HANDS_AND_TOOLS);
        ARM_OPACITY = b.comment("Opacity of your vanilla-sized arms while working (including skin sleeves). Tools stay opaque.")
                .defineInRange("armOpacity", 0.35, 0.1, 1.0);
        SUPPORTING_HAND = b.comment("Show the hand that steadies the hide or holds the panel.")
                .define("supportingHand", true);
        EDGE_SWITCHING = b.comment("Moving the cursor to the edge of the view (and holding it there) switches to the neighbouring station.",
                        "Off by default: use the next/previous station keys. Never during a stroke.")
                .define("edgeSwitching", false);
        EDGE_SWITCH_DELAY_MS = b.defineInRange("edgeSwitchDelayMs", 700, 200, 3000);
        ASSISTED_PROFILE = b.comment("Ask the server for the assisted interaction profile: wider cut lines, holes and seating guides.",
                        "The server decides (work.allowAssistedProfile); the best possible quality is the same.")
                .define("assistedProfile", false);
        LARGE_TARGETS = b.comment("Larger hover areas, outlines and markers on the parts you work.")
                .define("largeTargets", false);
        SHOW_HINTS = b.comment("Short instructions next to the part you can work right now.")
                .define("showHints", true);
        TENSION_INDICATOR = b.comment("A small labelled gauge (Slack / Even / Puckered) while pulling thread, besides the thread itself.")
                .define("tensionIndicator", true);
        JADE_IN_FOCUS = b.comment("Keep Jade's tooltip while working at a station (off: the station explains itself).")
                .define("jadeInFocus", false);
        FAVORITE_PATTERNS = b.comment("Patterns starred in the bench's pattern book.")
                .defineListAllowEmpty("favoritePatterns", java.util.List.of(), o -> o instanceof String);
        b.pop();
        b.comment("Accessibility. These options only change presentation; they never widen scoring windows.").push("accessibility");
        SHOW_GRAIN_HINTS = b.comment("Draw faint grain lines over the flesh while fleshing. Scraping along the grain removes more per pass.")
                .define("showGrainHints", true);
        PARTICLE_AMOUNT = b.defineEnum("particleAmount", ParticleAmount.NORMAL);
        HIGH_CONTRAST_MINIGAMES = b.define("highContrastMinigames", false);
        COLORBLIND_SAFE_COLORS = b.comment("Use a blue/orange palette instead of green/red.")
                .define("colorblindSafeColors", false);
        LARGE_MINIGAME_MARKERS = b.comment("Kept from 1.2; focus.largeTargets is its successor.")
                .define("largeMinigameMarkers", false);
        TIMING_CUE_SOUNDS = b.comment("Small confirmation sounds for accepted stitches, seats and cuts.").define("timingCueSounds", true);
        SOUND_CUE_VOLUME = b.defineInRange("soundCueVolume", 0.8, 0.0, 1.0);
        FEEDBACK_LABEL_MS = b.comment("How long a short work result (\"Even seams; clean cut\") stays readable, in milliseconds.")
                .defineInRange("feedbackLabelMs", 2500, 500, 10000);
        STATION_NOTICES = b.comment("Show station messages (vat status, what a hide waits for, errors) above the hotbar, where they stay",
                        "for noticeSeconds and several can show at once. Off: the vanilla action bar, which the next message replaces.")
                .define("stationNotices", true);
        NOTICE_SECONDS = b.comment("How long a station message stays above the hotbar.")
                .defineInRange("noticeSeconds", 8, 2, 60);
        b.pop();
        SPEC = b.build();
    }

    public static boolean get(ForgeConfigSpec.BooleanValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static double get(ForgeConfigSpec.DoubleValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static int get(ForgeConfigSpec.IntValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static <E extends Enum<E>> E get(ForgeConfigSpec.EnumValue<E> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    @SuppressWarnings("unchecked")
    public static java.util.List<String> favorites() {
        return (java.util.List<String>) (SPEC.isLoaded() ? FAVORITE_PATTERNS.get() : FAVORITE_PATTERNS.getDefault());
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
