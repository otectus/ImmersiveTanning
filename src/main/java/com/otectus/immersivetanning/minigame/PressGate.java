package com.otectus.immersivetanning.minigame;

import java.util.HashSet;
import java.util.Set;

/**
 * Turns raw awl inputs into presses: one physical action is one press. A held key's auto-repeat is not a press, and a
 * second control firing within {@link #DUPLICATE_MS} of a press (a click and Space together) is the same press.
 * Deliberately timed presses are never merged: stitches are at least 375 ms apart. Presentation only - the server
 * still scores whatever presses arrive, so this can never widen a timing window.
 */
public final class PressGate {
    public static final int DUPLICATE_MS = 60;

    private final Set<Integer> held = new HashSet<>();
    private long lastPressMs = Long.MIN_VALUE / 2;

    /** A key event arrived (first press or repeat). True when it is a new press. */
    public boolean keyDown(int key, long nowMs) {
        if (!held.add(key)) return false;
        return accept(nowMs);
    }

    public void keyUp(int key) {
        held.remove(key);
    }

    /** A mouse button went down. True when it is a new press. */
    public boolean click(long nowMs) {
        return accept(nowMs);
    }

    public boolean isHeld(int key) {
        return held.contains(key);
    }

    private boolean accept(long nowMs) {
        if (nowMs - lastPressMs < DUPLICATE_MS) return false;
        lastPressMs = nowMs;
        return true;
    }
}
