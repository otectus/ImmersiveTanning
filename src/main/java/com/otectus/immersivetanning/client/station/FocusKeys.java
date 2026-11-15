package com.otectus.immersivetanning.client.station;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.IKeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * The rebindable controls of station work. They act only while working at a station (their own conflict context), so
 * they never clash with movement or the hotbar; the vanilla inventory key and Escape keep their meaning (leave).
 */
public final class FocusKeys {
    /** Active while focused at a station. */
    public static final IKeyConflictContext FOCUS = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            return StationFocus.active();
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return other == this;
        }
    };

    private static final String CATEGORY = "key.categories.immersive_tanning";

    public static final KeyMapping CONFIRM = key("confirm", GLFW.GLFW_KEY_SPACE);
    public static final KeyMapping CYCLE = key("cycle_target", GLFW.GLFW_KEY_TAB);
    public static final KeyMapping UP = key("pointer_up", GLFW.GLFW_KEY_UP);
    public static final KeyMapping DOWN = key("pointer_down", GLFW.GLFW_KEY_DOWN);
    public static final KeyMapping LEFT = key("pointer_left", GLFW.GLFW_KEY_LEFT);
    public static final KeyMapping RIGHT = key("pointer_right", GLFW.GLFW_KEY_RIGHT);
    public static final KeyMapping ROTATE_LEFT = key("rotate_left", GLFW.GLFW_KEY_Q);
    public static final KeyMapping ROTATE_RIGHT = key("rotate_right", GLFW.GLFW_KEY_R);
    public static final KeyMapping DRAWER = key("material_drawer", GLFW.GLFW_KEY_B);
    public static final KeyMapping NEXT = key("next_station", GLFW.GLFW_KEY_RIGHT_BRACKET);
    public static final KeyMapping PREVIOUS = key("previous_station", GLFW.GLFW_KEY_LEFT_BRACKET);
    public static final KeyMapping CAMERA_MODE = key("camera_mode", GLFW.GLFW_KEY_V);
    public static final KeyMapping FINISH = key("finish_roughly", GLFW.GLFW_KEY_F);

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.immersive_tanning." + name, FOCUS, InputConstants.Type.KEYSYM, code, CATEGORY);
    }

    public static void register(RegisterKeyMappingsEvent event) {
        for (KeyMapping key : new KeyMapping[]{CONFIRM, CYCLE, UP, DOWN, LEFT, RIGHT, ROTATE_LEFT, ROTATE_RIGHT, DRAWER, NEXT, PREVIOUS,
                CAMERA_MODE, FINISH}) {
            event.register(key);
        }
    }

    /** Whether a key event is this mapping (modifiers and remapping respected). */
    public static boolean is(KeyMapping mapping, int key, int scan) {
        return mapping.isActiveAndMatches(InputConstants.getKey(key, scan));
    }

    private FocusKeys() {}
}
