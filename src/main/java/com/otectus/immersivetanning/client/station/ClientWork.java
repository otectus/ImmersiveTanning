package com.otectus.immersivetanning.client.station;

import com.otectus.immersivetanning.blockentity.TannersRackBlockEntity;
import com.otectus.immersivetanning.client.render.HideSurfaceTexture;
import com.otectus.immersivetanning.minigame.FleshingSurface;
import com.otectus.immersivetanning.network.packet.OperatorPosePacket;
import com.otectus.immersivetanning.network.packet.StationStrokePacket;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Client presentation state per station: what other players' tools are doing (relayed poses and strokes), short
 * animations (a pour, a splash, a seated panel) and the hide textures. Read-only projections of synchronised state:
 * nothing here advances tanning, changes material or completes anything. Entries expire on their own and everything is
 * cleared when the level changes, so nothing grows over a long session.
 */
public final class ClientWork {
    /** A watcher's view of another player's tool. */
    public static final class RemotePose {
        public int entity;
        public int tool;
        public boolean pressed;
        public float x;
        public float y;
        public float z;
        public float px;
        public float py;
        public float pz;
        public int held = -1;
        public float rot;
        public long updatedMs;
    }

    /** A short animation at a station (local effect clock, never game logic). */
    public record Animation(int type, float strength, long startMs) {
        public float age() {
            return (Util.getMillis() - startMs) / 1000F;
        }
    }

    private static final Map<BlockPos, RemotePose> POSES = new HashMap<>();
    private static final Map<BlockPos, List<Animation>> ANIMATIONS = new HashMap<>();
    private static final Map<BlockPos, HideSurfaceTexture> TEXTURES = new HashMap<>();
    private static final Map<BlockPos, float[]> TRAILS = new HashMap<>();
    private static final Map<BlockPos, Long> FLECK_TIMES = new HashMap<>();
    private static long lastSweep;

    // ------------------------------------------------------------------ remote operators

    public static void pose(OperatorPosePacket p) {
        RemotePose pose = POSES.computeIfAbsent(p.pos().immutable(), k -> new RemotePose());
        pose.px = pose.updatedMs == 0 ? OperatorPosePacket.unpack(p.x()) : pose.x;
        pose.py = pose.updatedMs == 0 ? OperatorPosePacket.unpack(p.y()) : pose.y;
        pose.pz = pose.updatedMs == 0 ? OperatorPosePacket.unpack(p.z()) : pose.z;
        pose.entity = p.entity();
        pose.tool = p.tool();
        pose.pressed = p.pressed();
        pose.x = OperatorPosePacket.unpack(p.x());
        pose.y = OperatorPosePacket.unpack(p.y());
        pose.z = OperatorPosePacket.unpack(p.z());
        pose.held = p.held();
        pose.rot = p.rot() / 10F;
        pose.updatedMs = Util.getMillis();
    }

    /** The remote operator's pose at a station, if one was heard from recently. */
    @Nullable
    public static RemotePose remote(BlockPos pos) {
        RemotePose pose = POSES.get(pos);
        if (pose == null) return null;
        if (Util.getMillis() - pose.updatedMs > 1500L) {
            POSES.remove(pos);
            return null;
        }
        return pose;
    }

    /** Whether an entity (another player) was seen working a station in the last moment. */
    public static boolean working(int entity) {
        long now = Util.getMillis();
        for (RemotePose pose : POSES.values()) {
            if (pose.entity == entity && pose.tool != OperatorPosePacket.TOOL_NONE && now - pose.updatedMs < 1500L) return true;
        }
        return false;
    }

    /** Another player's accepted contact: scraping is replayed on this client's copy of the hide. */
    public static void stroke(StationStrokePacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (p.kind() == StationStrokePacket.FLESH && mc.level.getBlockEntity(p.pos()) instanceof TannersRackBlockEntity rack && rack.work() != null) {
            FleshingSurface surface = rack.work().surface();
            for (int i = 0; i < p.xs().length; i++) {
                if (i == 0 && p.begins()) surface.begin(p.xs()[i], p.ys()[i]);
                else surface.drag(p.xs()[i], p.ys()[i]);
            }
            if (p.ends()) surface.end();
            WorkSounds.remoteScrape(p.pos(), !p.ends() && surface.lastRemovedCells() > 0, p.ends());
        }
        if (p.xs().length > 0) {
            int last = p.xs().length - 1;
            TRAILS.put(p.pos().immutable(), new float[]{p.kind(), p.xs()[last], p.ys()[last], p.ends() ? 0F : 1F, Util.getMillis()});
        }
    }

    /** The last relayed contact point of a kind at a station: {kind, qx, qy, down, ms}, or null. */
    @Nullable
    public static float[] trail(BlockPos pos) {
        float[] t = TRAILS.get(pos);
        if (t != null && Util.getMillis() - (long) t[4] > 1200L) {
            TRAILS.remove(pos);
            return null;
        }
        return t;
    }

    // ------------------------------------------------------------------ animations

    /** Cap contact flecks independently of mouse sampling and render rate. */
    public static boolean allowFlecks(BlockPos pos) {
        long now = Util.getMillis();
        Long previous = FLECK_TIMES.get(pos);
        if (previous != null && now - previous < 90L) return false;
        FLECK_TIMES.put(pos.immutable(), now);
        return true;
    }

    public static void animate(BlockPos pos, int type, float strength) {
        List<Animation> list = ANIMATIONS.computeIfAbsent(pos.immutable(), k -> new ArrayList<>());
        if (list.size() > 8) list.remove(0);
        list.add(new Animation(type, strength, Util.getMillis()));
    }

    /** Running animations at a station, oldest first; finished ones (older than {@code maxAge}) are dropped. */
    public static List<Animation> animations(BlockPos pos, float maxAge) {
        List<Animation> list = ANIMATIONS.get(pos);
        if (list == null) return List.of();
        list.removeIf(a -> a.age() > maxAge);
        if (list.isEmpty()) {
            ANIMATIONS.remove(pos);
            return List.of();
        }
        return list;
    }

    // ------------------------------------------------------------------ hide textures

    public static HideSurfaceTexture texture(BlockPos pos) {
        return TEXTURES.computeIfAbsent(pos.immutable(), HideSurfaceTexture::new);
    }

    /** Frees textures of racks that are gone or far away (called every second). */
    public static void sweep() {
        long now = Util.getMillis();
        if (now - lastSweep < 1000L) return;
        lastSweep = now;
        Minecraft mc = Minecraft.getInstance();
        Iterator<Map.Entry<BlockPos, HideSurfaceTexture>> it = TEXTURES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, HideSurfaceTexture> e = it.next();
            boolean keep = mc.level != null && e.getValue().usedWithin(now, 5000L)
                    && mc.level.getBlockEntity(e.getKey()) instanceof TannersRackBlockEntity;
            if (!keep) {
                e.getValue().close();
                it.remove();
            }
        }
        POSES.entrySet().removeIf(e -> now - e.getValue().updatedMs > 3000L);
        TRAILS.entrySet().removeIf(e -> now - (long) e.getValue()[4] > 3000L);
        ANIMATIONS.entrySet().removeIf(e -> e.getValue().isEmpty() || e.getValue().get(e.getValue().size() - 1).age() > 10F);
        FLECK_TIMES.entrySet().removeIf(e -> now - e.getValue() > 3000L);
    }

    /** The level changed or the player left: every client-side station presentation goes. */
    public static void clear() {
        TEXTURES.values().forEach(HideSurfaceTexture::close);
        TEXTURES.clear();
        POSES.clear();
        ANIMATIONS.clear();
        TRAILS.clear();
        FLECK_TIMES.clear();
        WorkSounds.stopAll();
    }

    private ClientWork() {}
}
