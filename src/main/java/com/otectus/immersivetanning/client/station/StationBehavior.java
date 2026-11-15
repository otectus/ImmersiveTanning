package com.otectus.immersivetanning.client.station;

import com.otectus.immersivetanning.blockentity.StationBlockEntity;
import com.otectus.immersivetanning.config.ClientConfig;
import com.otectus.immersivetanning.network.packet.BenchOffersPacket;
import com.otectus.immersivetanning.network.packet.StationAckPacket;
import com.otectus.immersivetanning.network.packet.WorkResultPacket;
import com.otectus.immersivetanning.station.StationFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * How one kind of station is worked from the client: which parts can be pointed at and what they do (a short verb at
 * the cursor), how a press, drag and release become commands, what the tool and hands look like while working, and the
 * few words of guidance shown beside the work. Behaviours predict locally for immediacy; the server's synchronised
 * state is always the truth they settle on.
 */
public abstract class StationBehavior {
    /** Something that can be pointed at: a box in local pixels and what it does now. */
    public record Target(String id, AABB box, Component verb, boolean enabled) {
        public Vec3 center() {
            return box.getCenter();
        }
    }

    protected final StationFocus.Session session;
    protected final BlockPos pos;
    protected final Direction facing;
    @Nullable
    protected Target hovered;
    protected int keyboardTarget = -1;
    @Nullable
    protected PointerRay.Local lastRay;

    protected StationBehavior(StationFocus.Session session) {
        this.session = session;
        this.pos = session.pos;
        Minecraft mc = Minecraft.getInstance();
        this.facing = mc.level != null ? StationFrame.facing(mc.level.getBlockState(pos)) : Direction.NORTH;
    }

    public Direction facing() {
        return facing;
    }

    /** The task the view frames right now. */
    public abstract StationShots.Task task();

    /** Whether a tool is down or a gesture is in progress: the camera holds still and stations are not switched. */
    public abstract boolean gestureActive();

    /** The parts that may be pointed at now, in a sensible keyboard order. */
    public abstract List<Target> targets();

    public void tick() {
    }

    /** The pointer moved (pressed or not). */
    public void pointer(@Nullable PointerRay.Local ray, double guiX, double guiY) {
        lastRay = ray;
        hovered = ray == null ? null : pick(ray);
    }

    /** Primary press. Returns whether it was used. */
    public abstract boolean press(@Nullable PointerRay.Local ray);

    /** Primary release. */
    public abstract void release(@Nullable PointerRay.Local ray);

    /** Secondary click: put the active tool down, or cancel what is not committed yet. Never takes materials. */
    public abstract void secondary();

    /** Lets go of any gesture without committing it (pause menu, chat, leaving). */
    public abstract void cancelGesture();

    public boolean scrolled(double delta) {
        return false;
    }

    /** A key not handled by the focus itself. Returns whether it was used. */
    public boolean key(int key, int scan, int modifiers) {
        return false;
    }

    /** Keyboard assistance: moves the tool along the task's own path; returns a GUI point or null. */
    @Nullable
    public float[] follow(float dx, float dy, float seconds) {
        return null;
    }

    public void ack(StationAckPacket packet) {
    }

    public void offers(BenchOffersPacket packet) {
    }

    public void result(WorkResultPacket packet) {
    }

    /** HUD beside the work: the verb at the cursor and short guidance. */
    public abstract void renderHud(GuiGraphics g, Font font, int width, int height, int mouseX, int mouseY, float partialTick);

    /** The HUD buttons drawn last frame (for checks and accessibility tooling); empty when there are none. */
    public List<Hud.Button> hudButtons() {
        return List.of();
    }

    /** Extra mouse handling in the HUD (buttons). Returns whether the click was used. */
    public boolean hudClick(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean hudScroll(double mouseX, double mouseY, double delta) {
        return false;
    }

    public boolean hudKey(int key, int scan, int modifiers) {
        return false;
    }

    public boolean hudChar(char c, int modifiers) {
        return false;
    }

    /** Whether the HUD covers this point (the pointer is then not on the work). */
    public boolean hudCovers(double mouseX, double mouseY) {
        return false;
    }

    /**
     * The local operator's tool for watchers: {tool, pressed (0/1), x, y, z (local pixels)[, held panel, rotation]},
     * or null when no tool is out.
     */
    @Nullable
    public float[] toolPose() {
        return null;
    }

    /** Whether the material drawer offers this stack here. */
    public boolean drawerTakes(net.minecraft.world.item.ItemStack stack) {
        return false;
    }

    // ------------------------------------------------------------------ shared helpers

    @Nullable
    public Target hovered() {
        return hovered;
    }

    /** The nearest enabled target the ray passes through (larger targets with the accessibility option). */
    @Nullable
    protected Target pick(PointerRay.Local ray) {
        double grow = ClientConfig.get(ClientConfig.LARGE_TARGETS) || StationFocus.stationary() ? 0.9 : 0.0;
        Target best = null;
        double bestT = Double.MAX_VALUE;
        for (Target t : targets()) {
            AABB box = t.box().inflate(grow);
            var hit = box.clip(ray.origin(), ray.at(96));
            if (box.contains(ray.origin())) hit = java.util.Optional.of(ray.origin());
            if (hit.isEmpty()) continue;
            double d = hit.get().distanceTo(ray.origin());
            if (d < bestT) {
                bestT = d;
                best = t;
            }
        }
        return best;
    }

    /** Moves the keyboard selection to the next target and returns its point on the GUI. */
    @Nullable
    public float[] cycle(int direction) {
        List<Target> list = new ArrayList<>(targets());
        list.removeIf(t -> !t.enabled());
        if (list.isEmpty()) return null;
        keyboardTarget = Math.floorMod(keyboardTarget + direction, list.size());
        return PointerRay.projectLocal(pos, facing, list.get(keyboardTarget).center());
    }

    @Nullable
    protected StationBlockEntity station() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getBlockEntity(pos) instanceof StationBlockEntity be ? be : null;
    }

    protected static boolean hints() {
        return ClientConfig.get(ClientConfig.SHOW_HINTS);
    }
}
