package com.otectus.immersivetanning.work;

/**
 * Every intent a client may send to a station. Intents only: the server resolves the player, tools, materials and
 * work itself and decides every outcome. Flags of a command: {@link #BEGIN}, {@link #END}, {@link #STACK}.
 */
public enum WorkAction {
    // lease
    RELEASE, KEEPALIVE, HANDOFF, SELECT_SLOT, FOCUS,
    // rack
    FLESH, FLESH_ROUGH, RACK_TAKE, RACK_MOUNT, NEXT_HIDE, DETENT, TREAT, SEAT,
    // vat
    VAT_POUR, VAT_ADD, VAT_LOWER, VAT_LIFT, VAT_TAKE_BACK, VAT_SWEEP, VAT_DRAIN,
    // bench
    BENCH_OFFERS, BENCH_PREPARE, BENCH_CANCEL, BENCH_CUT, BENCH_PLACE, BENCH_STITCH, BENCH_LOOSEN, BENCH_TIE,
    BENCH_COLLECT, BENCH_TAKE_WORKPIECE, BENCH_RECOVER, BENCH_REPAIR, BENCH_MOUNT_WORKPIECE, BENCH_AGAIN;

    public static final int BEGIN = 1;
    public static final int END = 2;
    public static final int STACK = 4;
    /** With {@link #END}: the gesture is abandoned, not completed (a thread let go of, a sweep broken off). */
    public static final int CANCEL = 8;

    public static WorkAction byId(int id) {
        WorkAction[] values = values();
        return id >= 0 && id < values.length ? values[id] : null;
    }

    /** Commands that continue a gesture: frequent, bounded, rate-limited (their release never is). */
    public boolean stroke() {
        return this == FLESH || this == TREAT || this == VAT_SWEEP || this == BENCH_CUT || this == BENCH_STITCH;
    }
}
