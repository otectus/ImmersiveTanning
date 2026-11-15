package com.otectus.immersivetanning.client.station;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.blockentity.LeatherworkersBenchBlockEntity;
import com.otectus.immersivetanning.blockentity.StationBlockEntity;
import com.otectus.immersivetanning.blockentity.TannersRackBlockEntity;
import com.otectus.immersivetanning.blockentity.TannersVatBlockEntity;
import com.otectus.immersivetanning.client.StationNotices;
import com.otectus.immersivetanning.config.ClientConfig;
import com.otectus.immersivetanning.network.ModNetwork;
import com.otectus.immersivetanning.network.packet.StationCommandPacket;
import com.otectus.immersivetanning.network.packet.StationFocusPacket;
import com.otectus.immersivetanning.station.StationFrame;
import com.otectus.immersivetanning.work.WorkAction;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The native focus backend: the one owner of the camera and of input while the player works at a station.
 *
 * <p>The server opens focus (a lease after a real use of the block); the client frames the task with an authored shot
 * attached to the block ({@link StationShots}), or keeps the player's own view (stationary mode, or when no shot fits).
 * The player's body stays where it stands - the camera never grants reach. Input is captured by an invisible screen
 * ({@link FocusScreen}) and routed to the station's {@link StationBehavior}.</p>
 *
 * <p>Focus ends at once on Escape, the inventory key, damage, death, a dimension change, being moved away, the station
 * changing or disappearing, a lost lease, or another screen taking over; the pause menu and chat only suspend it (any
 * gesture is let go). Every exit gives back the cursor, the field of view, the hands, the HUD and the camera; the
 * camera then glides back (or snaps, in reduced motion) - input is never held for it.</p>
 *
 * <p>Camera mods: only one camera update runs. Focus overrides the camera last (after other mods' updates) while it is
 * active; if the camera is found somewhere else on the next frame anyway, another mod is driving it and focus falls back
 * to stationary mode for the rest of the session, with a notice.</p>
 */
public final class StationFocus {
    private static final long KEEPALIVE_MS = 5000L;
    private static final double MAX_DISPLACEMENT = 1.25;

    /** A camera pose: world position, yaw, pitch (Minecraft degrees), vertical field of view. */
    public record Pose(Vec3 position, float yaw, float pitch, float fov) {}

    public static final class Session {
        public final BlockPos pos;
        public final long instance;
        public final int lease;
        public final boolean operator;
        final ResourceKey<Level> dimension;
        final Vec3 bodyAt;
        final float health;
        StationBehavior behavior;
        int sequence;
        long lastKeepAlive;
        boolean suspended;
        boolean stationary;
        boolean fallbackNoticed;
        StationShots.Task task;
        @Nullable StationShots.Shot shot;
        Pose from;
        long transitionStart;
        float transitionSeconds;
        @Nullable Pose last;
        int disagreement;

        Session(StationFocusPacket p, LocalPlayer player) {
            this.pos = p.pos().immutable();
            this.instance = p.instance();
            this.lease = p.lease();
            this.operator = p.operator();
            this.dimension = player.level().dimension();
            this.bodyAt = player.position();
            this.health = player.getHealth();
        }

        public StationBehavior behavior() {
            return behavior;
        }
    }

    @Nullable
    private static Session session;
    /** The glide back after leaving: from this pose to the player's own eyes. */
    @Nullable
    private static Pose exitFrom;
    private static long exitStart;
    private static float exitSeconds;
    private static boolean warnedCameraMod;

    // ------------------------------------------------------------------ opening and closing

    public static void open(StationFocusPacket p) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        if (!p.open()) {
            if (session != null && session.pos.equals(p.pos())) {
                leave(false);
                if (!p.reason().getString().isEmpty()) StationNotices.show(p.reason(), true);
            }
            return;
        }
        BlockEntity be = mc.level.getBlockEntity(p.pos());
        if (!(be instanceof StationBlockEntity)) return;
        Pose current = session != null && session.last != null ? session.last : eyes(player, 1F, currentFov(mc));
        if (session != null && !session.pos.equals(p.pos())) {
            session.behavior.cancelGesture();
            send(WorkAction.RELEASE, 0, new int[0], new short[0], new short[0], null, null);
        }
        Session s = new Session(p, player);
        s.behavior = be instanceof TannersRackBlockEntity ? new RackBehavior(s)
                : be instanceof TannersVatBlockEntity ? new VatBehavior(s) : new BenchBehavior(s);
        s.stationary = ClientConfig.get(ClientConfig.CAMERA_MODE) == ClientConfig.CameraMode.STATIONARY;
        s.task = s.behavior.task();
        s.from = current;
        s.transitionStart = Util.getMillis();
        s.transitionSeconds = transition();
        frame(s, mc);
        session = s;
        exitFrom = null;
        PointerRay.invalidate();
        if (!(mc.screen instanceof FocusScreen)) mc.setScreen(new FocusScreen());
        keepAlive(true);
        if (!s.operator) StationNotices.show(Component.translatable("message.immersive_tanning.watching_hint"), false);
    }

    /** Leaves focus now: the gesture is let go, the lease returned, input and camera given back. */
    public static void leave(boolean tellServer) {
        Session s = session;
        if (s == null) return;
        s.behavior.cancelGesture();
        if (tellServer) send(WorkAction.RELEASE, 0, new int[0], new short[0], new short[0], null, null);
        session = null;
        Minecraft mc = Minecraft.getInstance();
        if (s.last != null && !s.stationary && ClientConfig.get(ClientConfig.CAMERA_MODE) == ClientConfig.CameraMode.CINEMATIC) {
            exitFrom = s.last;
            exitStart = Util.getMillis();
            exitSeconds = Math.min(0.3F, transition());
        }
        if (mc.screen instanceof FocusScreen screen) screen.closeQuietly();
        FocusCursor.reset();
        WorkSounds.fade(s.pos);
    }

    public static boolean active() {
        return session != null;
    }

    @Nullable
    public static Session session() {
        return session;
    }

    /** The behaviour of the station at a position, when this client works or watches there. */
    @Nullable
    public static StationBehavior behaviorAt(BlockPos pos) {
        Session s = session;
        return s != null && s.pos.equals(pos) ? s.behavior : null;
    }

    public static boolean stationary() {
        Session s = session;
        return s != null && (s.stationary || s.shot == null);
    }

    // ------------------------------------------------------------------ the client tick

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        Session s = session;
        if (s == null) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            session = null;
            return;
        }
        Component why = interruption(s, mc, player);
        if (why != null) {
            leave(true);
            if (!why.getString().isEmpty()) StationNotices.show(why, true);
            return;
        }
        Screen screen = mc.screen;
        if (screen != null && !(screen instanceof FocusScreen)) {
            // The pause menu, chat and JEI's recipe view (opened from the pattern book) suspend focus; anything else ends it.
            if (screen instanceof PauseScreen || screen instanceof ChatScreen || screen.getClass().getName().startsWith("mezz.jei.")) {
                if (!s.suspended) {
                    s.suspended = true;
                    s.behavior.cancelGesture();
                }
            } else {
                leave(true);
                return;
            }
        } else if (screen == null) {
            s.suspended = false;
            mc.setScreen(new FocusScreen());
        }
        s.behavior.tick();
        StationShots.Task task = s.behavior.task();
        if (task != s.task && !s.behavior.gestureActive()) {
            s.from = s.last != null ? s.last : eyes(player, 1F, currentFov(mc));
            s.task = task;
            s.transitionStart = Util.getMillis();
            s.transitionSeconds = transition();
            frame(s, mc);
        }
        keepAlive(false);
    }

    @Nullable
    private static Component interruption(Session s, Minecraft mc, LocalPlayer player) {
        if (!player.isAlive()) return Component.empty();
        if (player.level().dimension() != s.dimension) return Component.empty();
        if (player.getHealth() < s.health - 0.01F || player.hurtTime > 0) {
            return Component.translatable("message.immersive_tanning.focus_hurt");
        }
        if (player.position().distanceTo(s.bodyAt) > MAX_DISPLACEMENT) return Component.translatable("message.immersive_tanning.session_interrupted");
        BlockEntity be = mc.level.getBlockEntity(s.pos);
        if (!(be instanceof StationBlockEntity station) || station.instanceId() != s.instance) {
            return Component.translatable("message.immersive_tanning.station_missing");
        }
        return null;
    }

    private static void keepAlive(boolean force) {
        Session s = session;
        if (s == null) return;
        long now = Util.getMillis();
        if (!force && now - s.lastKeepAlive < KEEPALIVE_MS) return;
        s.lastKeepAlive = now;
        send(WorkAction.KEEPALIVE, 0, new int[]{ClientConfig.get(ClientConfig.ASSISTED_PROFILE) ? 1 : 0}, new short[0], new short[0], null, null);
    }

    // ------------------------------------------------------------------ camera

    /** Picks the shot of the current task, or the player's own view when none fits or stationary mode is chosen. */
    private static void frame(Session s, Minecraft mc) {
        if (s.stationary || mc.level == null || mc.player == null) {
            s.shot = null;
            return;
        }
        Optional<StationShots.Shot> shot = StationShots.choose(mc.level, s.pos, s.behavior.facing(), s.task, mc.player);
        s.shot = shot.orElse(null);
        if (s.shot == null && !s.fallbackNoticed) {
            s.fallbackNoticed = true;
            StationNotices.show(Component.translatable("message.immersive_tanning.focus_no_room"), false);
        }
    }

    /**
     * The camera this frame, or null to leave it alone. Called at the end of the camera's own setup, after other mods'
     * updates, so focus is the one owner while it is active.
     */
    @Nullable
    public static Pose camera(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return null;
        Session s = session;
        if (s != null) {
            if (s.shot == null || s.stationary) {
                s.last = null;
                return null;
            }
            Pose target = shotPose(s, s.shot);
            if (!s.behavior.gestureActive() && ClientConfig.get(ClientConfig.CAMERA_SWAY)
                    && ClientConfig.get(ClientConfig.CAMERA_MODE) == ClientConfig.CameraMode.CINEMATIC) {
                double t = Util.getMillis() / 1000.0;
                target = new Pose(target.position().add(0, Math.sin(t * 0.9) * 0.0035, Math.cos(t * 0.7) * 0.0025),
                        target.yaw() + (float) Math.sin(t * 0.55) * 0.12F, target.pitch() + (float) Math.sin(t * 0.8) * 0.1F, target.fov());
            }
            float e = ease(progress(s.transitionStart, s.transitionSeconds));
            Pose pose = lerp(s.from, target, e);
            s.last = pose;
            return pose;
        }
        if (exitFrom != null) {
            float t = progress(exitStart, exitSeconds);
            if (t >= 1F) {
                exitFrom = null;
                return null;
            }
            return lerp(exitFrom, eyes(player, partialTick, currentFov(mc)), ease(t));
        }
        return null;
    }

    /** The field of view to use this frame (vanilla's when focus does not hold the camera). */
    public static double fov(double vanilla) {
        Session s = session;
        if (s != null && s.last != null && s.shot != null && !s.stationary) return s.last.fov();
        if (s == null && exitFrom != null) {
            float t = progress(exitStart, exitSeconds);
            return Mth.lerp(ease(t), exitFrom.fov(), vanilla);
        }
        return vanilla;
    }

    /** The camera really stood where focus put it last frame; if another mod moved it anyway, fall back to stationary. */
    public static void verifyCamera(Vec3 actual) {
        Session s = session;
        if (s == null || s.last == null || s.shot == null || s.stationary) return;
        if (actual.distanceTo(s.last.position()) > 0.05) {
            if (++s.disagreement > 10) {
                s.stationary = true;
                s.shot = null;
                if (!warnedCameraMod) {
                    warnedCameraMod = true;
                    ImmersiveTanning.LOGGER.warn("Immersive Tanning: another mod keeps moving the camera; station focus uses the stationary view");
                }
                StationNotices.show(Component.translatable("message.immersive_tanning.focus_camera_mod"), false);
            }
        } else {
            s.disagreement = 0;
        }
    }

    private static Pose shotPose(Session s, StationShots.Shot shot) {
        Vec3 position = StationFrame.pxToWorld(s.pos, s.behavior.facing(), shot.position().x, shot.position().y, shot.position().z);
        Vec3 target = StationFrame.pxToWorld(s.pos, s.behavior.facing(), shot.target().x, shot.target().y, shot.target().z);
        Vec3 d = target.subtract(position);
        float yaw = (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
        float pitch = (float) (Mth.atan2(-d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        int configured = ClientConfig.get(ClientConfig.FOCUS_FOV);
        return new Pose(position, yaw, pitch, configured > 0 ? configured : shot.fov());
    }

    private static Pose eyes(LocalPlayer player, float partialTick, float fov) {
        return new Pose(player.getEyePosition(partialTick), player.getViewYRot(partialTick), player.getViewXRot(partialTick), fov);
    }

    private static float currentFov(Minecraft mc) {
        return mc.options.fov().get().floatValue();
    }

    private static Pose lerp(Pose a, Pose b, float t) {
        if (t >= 1F) return b;
        float yaw = a.yaw() + Mth.wrapDegrees(b.yaw() - a.yaw()) * t;
        return new Pose(a.position().lerp(b.position(), t), yaw, Mth.lerp(t, a.pitch(), b.pitch()), Mth.lerp(t, a.fov(), b.fov()));
    }

    private static float progress(long start, float seconds) {
        if (seconds <= 0F) return 1F;
        return Mth.clamp((Util.getMillis() - start) / (seconds * 1000F), 0F, 1F);
    }

    private static float ease(float t) {
        return t * t * (3F - 2F * t);
    }

    private static float transition() {
        return ClientConfig.get(ClientConfig.CAMERA_MODE) == ClientConfig.CameraMode.CINEMATIC
                ? (float) (double) ClientConfig.get(ClientConfig.TRANSITION_SECONDS) : 0F;
    }

    /** Whether focus currently drives the camera (vanilla view bobbing and the block outline are then off). */
    public static boolean ownsCamera() {
        Session s = session;
        return (s != null && s.shot != null && !s.stationary) || exitFrom != null;
    }

    // ------------------------------------------------------------------ navigation and modes

    /** Moves straight to the next or previous station within reach, asked of the server like a use of the block. */
    public static void navigate(int direction) {
        Session s = session;
        Minecraft mc = Minecraft.getInstance();
        if (s == null || mc.level == null || mc.player == null || s.behavior.gestureActive()) return;
        List<BlockPos> stations = new ArrayList<>();
        BlockPos center = mc.player.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-5, -2, -5), center.offset(5, 2, 5))) {
            if (mc.level.getBlockEntity(p) instanceof StationBlockEntity && mc.player.distanceToSqr(Vec3.atCenterOf(p)) < 36) stations.add(p.immutable());
        }
        if (stations.size() < 2) return;
        Vec3 eye = mc.player.getEyePosition();
        stations.sort(Comparator.comparingDouble(p -> Mth.atan2(p.getZ() + 0.5 - eye.z, p.getX() + 0.5 - eye.x)));
        int index = stations.indexOf(s.pos);
        BlockPos next = stations.get(Math.floorMod(index + direction, stations.size()));
        if (next.equals(s.pos)) return;
        BlockEntity be = mc.level.getBlockEntity(next);
        if (!(be instanceof StationBlockEntity station)) return;
        ModNetwork.CHANNEL.sendToServer(new StationCommandPacket(next, station.instanceId(), 0, 0, 0, WorkAction.FOCUS, 0, new int[0],
                new short[0], new short[0], null, null));
    }

    /** Cycles cinematic, reduced motion and stationary presentation (the same work in every one). */
    public static void cycleMode() {
        ClientConfig.CameraMode next = ClientConfig.CameraMode.values()[(ClientConfig.get(ClientConfig.CAMERA_MODE).ordinal() + 1)
                % ClientConfig.CameraMode.values().length];
        ClientConfig.CAMERA_MODE.set(next);
        ClientConfig.SPEC.save();
        Session s = session;
        if (s != null) {
            s.stationary = next == ClientConfig.CameraMode.STATIONARY;
            s.from = s.last != null ? s.last : s.from;
            s.transitionStart = Util.getMillis();
            s.transitionSeconds = transition();
            frame(s, Minecraft.getInstance());
        }
        StationNotices.show(Component.translatable("message.immersive_tanning.camera_mode." + next.name().toLowerCase(java.util.Locale.ROOT)), false);
    }

    // ------------------------------------------------------------------ commands

    /** Sends an intent to the focused station (stamped with the station instance, lease, job and a new sequence). */
    public static void send(WorkAction action, int flags, int[] ints, short[] xs, short[] ys, @Nullable ResourceLocation id,
                            @Nullable ResourceLocation id2) {
        Session s = session;
        if (s == null) return;
        int job = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(s.pos) instanceof LeatherworkersBenchBlockEntity bench && bench.job() != null) {
            job = bench.job().id();
        }
        SENT[(s.sequence + 1) & (SENT.length - 1)] = action;
        ModNetwork.CHANNEL.sendToServer(new StationCommandPacket(s.pos, s.instance, s.lease, job, ++s.sequence, action, flags,
                ints, xs, ys, id, id2));
    }

    /** The actions of the last commands sent, by sequence, to name what a refusal answered. */
    private static final WorkAction[] SENT = new WorkAction[64];
    @Nullable
    private static String lastRefusal;

    /** Notes the server's refusal of a command (for checks and diagnostics: what was refused and why). */
    public static void noteAck(com.otectus.immersivetanning.network.packet.StationAckPacket packet) {
        if (packet.accepted()) return;
        WorkAction action = SENT[packet.sequence() & (SENT.length - 1)];
        lastRefusal = (action == null ? "?" : action.name()) + " #" + packet.sequence() + " refused with code " + packet.code();
    }

    /** The last refusal noted, or null. */
    @Nullable
    public static String lastRefusal() {
        return lastRefusal;
    }

    public static void send(WorkAction action, int... ints) {
        send(action, 0, ints, new short[0], new short[0], null, null);
    }

    /** The level unloads or the player disconnects: nothing of focus survives. */
    public static void reset() {
        session = null;
        exitFrom = null;
        FocusCursor.reset();
        PointerRay.invalidate();
    }

    private StationFocus() {}
}
