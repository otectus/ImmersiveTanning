package com.otectus.immersivetanning.client.station;

import com.mojang.blaze3d.platform.InputConstants;
import com.otectus.immersivetanning.client.screen.VanillaGui;
import com.otectus.immersivetanning.config.ClientConfig;
import com.otectus.immersivetanning.minigame.PressGate;
import com.otectus.immersivetanning.network.ModNetwork;
import com.otectus.immersivetanning.network.packet.OperatorPosePacket;
import com.otectus.immersivetanning.work.WorkAction;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The input side of focus: an invisible, non-pausing screen over the world, so the cursor is free and nothing else
 * (attacking, mining, using an item, walking) acts while the player works. It draws no background and no window -
 * only the verb at the cursor, a line of guidance and, when asked for, the material drawer. Every press resolves to one
 * action: the station's behaviour gets primary and secondary presses (by the player's own attack and use bindings),
 * held or toggled, and the keyboard can do all of it (cycle targets, move the tool, press, rotate).
 */
public final class FocusScreen extends Screen {
    private static final float KEY_SPEED = 140F;
    private boolean quiet;
    private boolean pressing;
    private boolean keyboardPress;
    private final PressGate gate = new PressGate();
    private boolean drawer;
    private long lastTickMs = Util.getMillis();
    private long lastPoseMs;
    private double mouseX;
    private double mouseY;
    /** Whether the pointer has been placed yet; until then it is wherever the OS cursor is. */
    private boolean pointed;
    private int lastWidth;
    private int lastHeight;
    private long edgeSinceMs;
    private final List<int[]> drawerSlots = new ArrayList<>();

    public FocusScreen() {
        super(Component.translatable("screen.immersive_tanning.focus"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** The pointer starts at the OS cursor and keeps its place on the screen when the GUI scale changes. */
    @Override
    protected void init() {
        if (!pointed) {
            var window = minecraft.getWindow();
            mouseX = minecraft.mouseHandler.xpos() * width / Math.max(1, window.getScreenWidth());
            mouseY = minecraft.mouseHandler.ypos() * height / Math.max(1, window.getScreenHeight());
        } else if (lastWidth > 0 && lastHeight > 0) {
            mouseX = mouseX * width / lastWidth;
            mouseY = mouseY * height / lastHeight;
        }
        lastWidth = width;
        lastHeight = height;
    }

    @Override
    public void renderBackground(GuiGraphics g) {
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    /** Closed because focus already ended (no second release). */
    void closeQuietly() {
        quiet = true;
        if (minecraft != null && minecraft.screen == this) minecraft.setScreen(null);
    }

    @Override
    public void onClose() {
        if (!quiet) {
            quiet = true;
            StationFocus.leave(true);
        }
        if (minecraft != null && minecraft.screen == this) minecraft.setScreen(null);
    }

    @Override
    public void removed() {
        FocusCursor.reset();
        if (pressing) release();
    }

    @Nullable
    private StationBehavior behavior() {
        StationFocus.Session s = StationFocus.session();
        return s == null ? null : s.behavior();
    }

    // ------------------------------------------------------------------ pointer

    private void point(double x, double y) {
        mouseX = x;
        mouseY = y;
        pointed = true;
        StationBehavior b = behavior();
        if (b == null) return;
        if (b.hudCovers(x, y) && !pressing) {
            b.pointer(null, x, y);
            FocusCursor.set(FocusCursor.Shape.ARROW);
            return;
        }
        PointerRay.Local ray = PointerRay.local(b.pos, b.facing(), x, y);
        b.pointer(ray, x, y);
        StationBehavior.Target t = b.hovered();
        FocusCursor.set(pressing ? FocusCursor.Shape.CROSSHAIR : t == null ? FocusCursor.Shape.ARROW
                : t.enabled() ? FocusCursor.Shape.HAND : FocusCursor.Shape.ARROW);
    }

    @Override
    public void mouseMoved(double x, double y) {
        point(x, y);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        point(x, y);
        return true;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        StationBehavior b = behavior();
        if (b == null) return false;
        if (drawer && drawerClick(x, y)) return true;
        if (b.hudClick(x, y, button)) return true;
        if (b.hudCovers(x, y)) return true;
        InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(button);
        boolean primary = minecraft.options.keyAttack.isActiveAndMatches(key) || (button == 0 && !minecraft.options.keyUse.isActiveAndMatches(key));
        boolean secondary = !primary && (minecraft.options.keyUse.isActiveAndMatches(key) || button == 1);
        if (!gate.click(Util.getMillis())) return true;
        point(x, y);
        if (primary) {
            if (ClientConfig.get(ClientConfig.GESTURE_MODE) == ClientConfig.GestureMode.TOGGLE && pressing) release();
            else press();
        } else if (secondary) {
            if (pressing) {
                pressing = false;
                b.cancelGesture();
            } else {
                b.secondary();
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        point(x, y);
        if (ClientConfig.get(ClientConfig.GESTURE_MODE) == ClientConfig.GestureMode.HOLD && pressing && !keyboardPress) release();
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        StationBehavior b = behavior();
        if (b == null) return false;
        if (b.hudScroll(x, y, delta)) return true;
        return b.scrolled(delta);
    }

    private void press() {
        StationBehavior b = behavior();
        if (b == null) return;
        PointerRay.Local ray = PointerRay.local(b.pos, b.facing(), mouseX, mouseY);
        if (b.press(ray)) pressing = b.gestureActive();
    }

    private void release() {
        StationBehavior b = behavior();
        pressing = false;
        keyboardPress = false;
        if (b == null) return;
        b.release(PointerRay.local(b.pos, b.facing(), mouseX, mouseY));
    }

    // ------------------------------------------------------------------ keyboard

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        StationBehavior b = behavior();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (drawer) {
                drawer = false;
                return true;
            }
            return super.keyPressed(key, scan, modifiers);
        }
        if (b != null && b.hudKey(key, scan, modifiers)) return true;
        InputConstants.Key k = InputConstants.getKey(key, scan);
        if (minecraft.options.keyInventory.isActiveAndMatches(k)) {
            StationFocus.leave(true);
            if (minecraft.player != null) minecraft.setScreen(new InventoryScreen(minecraft.player));
            return true;
        }
        if (minecraft.options.keyChat.isActiveAndMatches(k)) {
            if (pressing) release();
            minecraft.setScreen(new ChatScreen(""));
            return true;
        }
        if (b == null) return false;
        if (FocusKeys.is(FocusKeys.CONFIRM, key, scan)) {
            if (!gate.keyDown(key, Util.getMillis())) return true;
            if (ClientConfig.get(ClientConfig.GESTURE_MODE) == ClientConfig.GestureMode.TOGGLE && pressing) {
                release();
            } else {
                press();
                keyboardPress = pressing;
            }
            return true;
        }
        if (FocusKeys.is(FocusKeys.CYCLE, key, scan)) {
            float[] at = b.cycle(Screen.hasShiftDown() ? -1 : 1);
            if (at != null) warp(at[0], at[1]);
            return true;
        }
        if (FocusKeys.is(FocusKeys.DRAWER, key, scan)) {
            drawer = !drawer;
            return true;
        }
        if (FocusKeys.is(FocusKeys.NEXT, key, scan)) {
            StationFocus.navigate(1);
            return true;
        }
        if (FocusKeys.is(FocusKeys.PREVIOUS, key, scan)) {
            StationFocus.navigate(-1);
            return true;
        }
        if (FocusKeys.is(FocusKeys.CAMERA_MODE, key, scan)) {
            StationFocus.cycleMode();
            return true;
        }
        if (b.key(key, scan, modifiers)) return true;
        return isArrow(key, scan);
    }

    @Override
    public boolean keyReleased(int key, int scan, int modifiers) {
        gate.keyUp(key);
        if (FocusKeys.is(FocusKeys.CONFIRM, key, scan) && keyboardPress
                && ClientConfig.get(ClientConfig.GESTURE_MODE) == ClientConfig.GestureMode.HOLD && pressing) {
            release();
            return true;
        }
        return super.keyReleased(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        StationBehavior b = behavior();
        return b != null && b.hudChar(c, modifiers);
    }

    private static boolean isArrow(int key, int scan) {
        return FocusKeys.is(FocusKeys.UP, key, scan) || FocusKeys.is(FocusKeys.DOWN, key, scan)
                || FocusKeys.is(FocusKeys.LEFT, key, scan) || FocusKeys.is(FocusKeys.RIGHT, key, scan);
    }

    /** Puts the OS cursor on a GUI point (the move arrives as an ordinary mouse move). */
    private void warp(float guiX, float guiY) {
        var window = minecraft.getWindow();
        double sx = window.getScreenWidth() / (double) window.getGuiScaledWidth();
        double sy = window.getScreenHeight() / (double) window.getGuiScaledHeight();
        GLFW.glfwSetCursorPos(window.getWindow(), guiX * sx, guiY * sy);
        point(guiX, guiY);
    }

    @Override
    public void tick() {
        long now = Util.getMillis();
        float dt = Math.min(0.1F, (now - lastTickMs) / 1000F);
        lastTickMs = now;
        StationBehavior b = behavior();
        if (b == null) return;
        long window = minecraft.getWindow().getWindow();
        float dx = (down(window, FocusKeys.RIGHT) ? 1 : 0) - (down(window, FocusKeys.LEFT) ? 1 : 0);
        float dy = (down(window, FocusKeys.DOWN) ? 1 : 0) - (down(window, FocusKeys.UP) ? 1 : 0);
        if (dx != 0F || dy != 0F) {
            float[] follow = pressing ? b.follow(dx, dy, dt) : null;
            if (follow != null) {
                warp(follow[0], follow[1]);
            } else {
                float speed = KEY_SPEED * (float) (double) ClientConfig.get(ClientConfig.POINTER_SENSITIVITY) * dt;
                warp((float) Math.max(0, Math.min(width - 1, mouseX + dx * speed)), (float) Math.max(0, Math.min(height - 1, mouseY + dy * speed)));
            }
        }
        edge(now, b);
        relayPose(now, b);
    }

    private static boolean down(long window, net.minecraft.client.KeyMapping mapping) {
        InputConstants.Key key = mapping.getKey();
        return key.getType() == InputConstants.Type.KEYSYM && key.getValue() > 0 && InputConstants.isKeyDown(window, key.getValue());
    }

    /** Optional: resting the cursor at a side edge (never mid-gesture) moves to the neighbouring station. */
    private void edge(long now, StationBehavior b) {
        if (!ClientConfig.get(ClientConfig.EDGE_SWITCHING) || pressing || b.gestureActive()) {
            edgeSinceMs = 0;
            return;
        }
        boolean atEdge = mouseX <= 2 || mouseX >= width - 3;
        if (!atEdge) {
            edgeSinceMs = 0;
            return;
        }
        if (edgeSinceMs == 0) edgeSinceMs = now;
        else if (now - edgeSinceMs >= ClientConfig.get(ClientConfig.EDGE_SWITCH_DELAY_MS)) {
            edgeSinceMs = 0;
            StationFocus.navigate(mouseX <= 2 ? -1 : 1);
        }
    }

    private void relayPose(long now, StationBehavior b) {
        StationFocus.Session s = StationFocus.session();
        if (s == null || !s.operator || now - lastPoseMs < 100L) return;
        float[] pose = b.toolPose();
        if (pose == null) return;
        lastPoseMs = now;
        ModNetwork.CHANNEL.sendToServer(new OperatorPosePacket(s.pos, s.lease, 0, (int) pose[0], pose[1] > 0.5F,
                OperatorPosePacket.pack(pose[2]), OperatorPosePacket.pack(pose[3]), OperatorPosePacket.pack(pose[4]),
                pose.length > 5 ? (int) pose[5] : -1, (short) (pose.length > 6 ? Math.round(pose[6] * 10F) : 0)));
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        StationBehavior b = behavior();
        if (b == null) return;
        // The pointer the work follows: the OS cursor's moves, the keyboard's and scripted input all place it.
        int px = (int) mouseX;
        int py = (int) mouseY;
        b.renderHud(g, font, width, height, px, py, partialTick);
        if (drawer) renderDrawer(g, px, py, b);
        if (StationBehavior.hints()) footer(g);
    }

    /**
     * The keys to leave, open the inventory and the drawer, and change the view, stacked beside the hotbar (never over
     * it or the work). Left out when the window is too narrow for them there; the controls screen lists them all.
     */
    private void footer(GuiGraphics g) {
        List<Component> lines = List.of(
                Component.translatable("hint.immersive_tanning.focus_leave"),
                Component.translatable("hint.immersive_tanning.focus_inventory", minecraft.options.keyInventory.getTranslatedKeyMessage()),
                Component.translatable("hint.immersive_tanning.focus_drawer", FocusKeys.DRAWER.getTranslatedKeyMessage()),
                Component.translatable("hint.immersive_tanning.focus_view", FocusKeys.CAMERA_MODE.getTranslatedKeyMessage()));
        int widest = 0;
        for (Component line : lines) widest = Math.max(widest, font.width(line));
        int room = (width - 182) / 2 - 10;
        if (widest > room) return;
        int y = height - 4 - lines.size() * 10;
        for (Component line : lines) {
            g.drawString(font, line, 6, y, 0xC0FFFFFF, true);
            y += 10;
        }
    }

    /** The material drawer: what the player carries that this station takes. A click brings it to hand. */
    private void renderDrawer(GuiGraphics g, int mx, int my, StationBehavior b) {
        drawerSlots.clear();
        Inventory inv = minecraft.player.getInventory();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inv.items.size() && slots.size() < 12; i++) {
            ItemStack s = inv.items.get(i);
            if (!s.isEmpty() && b.drawerTakes(s)) slots.add(i);
        }
        int w = Math.max(1, slots.size()) * 20 + 8;
        int x = (width - w) / 2;
        int y = height - 64;
        VanillaGui.panel(g, x, y, w, 30);
        if (slots.isEmpty()) {
            VanillaGui.centered(g, font, Component.translatable("hint.immersive_tanning.drawer_empty"), width / 2, y + 11, VanillaGui.LABEL);
            return;
        }
        for (int i = 0; i < slots.size(); i++) {
            int sx = x + 4 + i * 20;
            VanillaGui.slot(g, sx, y + 6);
            ItemStack stack = inv.items.get(slots.get(i));
            g.renderItem(stack, sx + 1, y + 7);
            g.renderItemDecorations(font, stack, sx + 1, y + 7);
            drawerSlots.add(new int[]{sx, y + 6, slots.get(i)});
            if (mx >= sx && mx < sx + 18 && my >= y + 6 && my < y + 24) g.renderTooltip(font, stack, mx, my);
        }
    }

    private boolean drawerClick(double x, double y) {
        for (int[] slot : drawerSlots) {
            if (x >= slot[0] && x < slot[0] + 18 && y >= slot[1] && y < slot[1] + 18) {
                StationFocus.send(WorkAction.SELECT_SLOT, slot[2]);
                drawer = false;
                return true;
            }
        }
        return false;
    }
}
