package com.otectus.immersivetanning.client.station;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * A cursor that means something: a hand over a part that can be picked up or used, a crosshair over a work surface,
 * the plain arrow elsewhere. Standard system cursors only, created once and released when the game closes; leaving
 * focus always restores the default cursor.
 */
public final class FocusCursor {
    public enum Shape { ARROW, HAND, CROSSHAIR }

    private static long hand;
    private static long crosshair;
    private static Shape current = Shape.ARROW;

    public static void set(Shape shape) {
        if (shape == current) return;
        long window = Minecraft.getInstance().getWindow().getWindow();
        long cursor = switch (shape) {
            case HAND -> hand != 0 ? hand : (hand = GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR));
            case CROSSHAIR -> crosshair != 0 ? crosshair : (crosshair = GLFW.glfwCreateStandardCursor(GLFW.GLFW_CROSSHAIR_CURSOR));
            case ARROW -> 0L;
        };
        GLFW.glfwSetCursor(window, cursor);
        current = shape;
    }

    public static void reset() {
        if (current != Shape.ARROW) {
            GLFW.glfwSetCursor(Minecraft.getInstance().getWindow().getWindow(), 0L);
            current = Shape.ARROW;
        }
    }

    private FocusCursor() {}
}
