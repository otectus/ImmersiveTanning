package com.otectus.immersivetanning.client.station;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.station.StationFrame;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The authored framing of every task, in station-local pixels (so it turns with the block), with a compact fallback
 * for tight workshops. A shot is used only when its camera stands in open air and nothing solid lies between it and
 * the work - checked for the near plane's corners as well as its centre. Walls are never hidden to make a shot work:
 * when no shot fits, the player works from their own view (stationary mode). Resource packs may reframe the shots in
 * {@code assets/immersive_tanning/station_shots.json}; framing never changes what the work accepts.
 */
public final class StationShots implements ResourceManagerReloadListener {

    public enum Task { FLESHING, FINISHING, VAT, BENCH, STITCHING, RESULT }

    /** A camera position and the point it looks at (local pixels), and its vertical field of view. */
    public record Shot(Vec3 position, Vec3 target, float fov) {}

    private static final Map<Task, List<Shot>> DEFAULTS = new EnumMap<>(Task.class);
    private static volatile Map<Task, List<Shot>> shots;

    static {
        DEFAULTS.put(Task.FLESHING, List.of(new Shot(new Vec3(8, 11.6, -7.2), new Vec3(8, 4.3, 1.6), 52F),
                new Shot(new Vec3(8, 10.2, -4.6), new Vec3(8, 4.2, 1.6), 56F)));
        DEFAULTS.put(Task.FINISHING, List.of(new Shot(new Vec3(5.6, 10.8, -13.0), new Vec3(8, 8.8, 7.5), 56F),
                new Shot(new Vec3(8, 10.0, -8.0), new Vec3(8, 8.6, 7.5), 64F)));
        DEFAULTS.put(Task.VAT, List.of(new Shot(new Vec3(12.0, 25.0, -6.0), new Vec3(8, 10.5, 7.5), 55F),
                new Shot(new Vec3(8, 23.0, -1.5), new Vec3(8, 10.5, 8.0), 62F)));
        DEFAULTS.put(Task.BENCH, List.of(new Shot(new Vec3(8, 30.0, -6.0), new Vec3(8, 15.6, 7.6), 52F),
                new Shot(new Vec3(8, 27.0, -1.0), new Vec3(8, 15.6, 8.0), 62F)));
        DEFAULTS.put(Task.STITCHING, List.of(new Shot(new Vec3(8, 26.5, -2.4), new Vec3(8, 15.6, 7.8), 50F),
                new Shot(new Vec3(8, 25.0, 0.5), new Vec3(8, 15.6, 8.0), 58F)));
        DEFAULTS.put(Task.RESULT, List.of(new Shot(new Vec3(8, 31.0, -9.0), new Vec3(8, 16.0, 6.5), 55F),
                new Shot(new Vec3(8, 27.0, -2.0), new Vec3(8, 16.0, 7.0), 62F)));
        shots = DEFAULTS;
    }

    /** The first shot of a task that fits where the station stands, or empty (work from the player's own view). */
    public static Optional<Shot> choose(Level level, BlockPos pos, Direction facing, Task task, @Nullable Entity viewer) {
        for (Shot shot : shots.getOrDefault(task, DEFAULTS.get(task))) {
            if (clear(level, pos, facing, shot, viewer)) return Optional.of(shot);
        }
        return Optional.empty();
    }

    /** Whether the camera stands in open air with a clear line to the work from its centre and near-plane corners. */
    public static boolean clear(Level level, BlockPos pos, Direction facing, Shot shot, @Nullable Entity viewer) {
        Vec3 camera = StationFrame.pxToWorld(pos, facing, shot.position().x, shot.position().y, shot.position().z);
        Vec3 target = StationFrame.pxToWorld(pos, facing, shot.target().x, shot.target().y, shot.target().z);
        if (solidNear(level, camera, 0.12)) return false;
        Vec3 forward = target.subtract(camera).normalize();
        Vec3 right = forward.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1.0E-6) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = right.cross(forward).normalize();
        double h = 0.09;
        Vec3[] offsets = {Vec3.ZERO, right.scale(h).add(up.scale(h)), right.scale(-h).add(up.scale(h)),
                right.scale(h).add(up.scale(-h)), right.scale(-h).add(up.scale(-h))};
        for (Vec3 o : offsets) {
            Vec3 from = camera.add(o);
            Vec3 to = target.add(o.scale(0.3));
            BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, viewer));
            if (hit.getType() == HitResult.Type.BLOCK && !hit.getBlockPos().equals(pos)) return false;
        }
        return true;
    }

    private static boolean solidNear(Level level, Vec3 point, double r) {
        AABB box = new AABB(point, point).inflate(r);
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        for (BlockPos p : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(p);
            if (state.isAir()) continue;
            VoxelShape shape = state.getVisualShape(level, p, CollisionContext.empty());
            if (shape.isEmpty()) continue;
            for (AABB part : shape.toAabbs()) {
                if (part.move(p).intersects(box)) return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ resource override

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        Map<Task, List<Shot>> loaded = new EnumMap<>(DEFAULTS);
        Optional<Resource> resource = manager.getResource(ImmersiveTanning.id("station_shots.json"));
        if (resource.isPresent()) {
            try (Reader reader = resource.get().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                for (Task task : Task.values()) {
                    String key = task.name().toLowerCase(java.util.Locale.ROOT);
                    if (!json.has(key)) continue;
                    JsonArray list = GsonHelper.getAsJsonArray(json, key);
                    java.util.ArrayList<Shot> parsed = new java.util.ArrayList<>();
                    for (JsonElement e : list) {
                        JsonObject o = e.getAsJsonObject();
                        parsed.add(new Shot(vec(GsonHelper.getAsJsonArray(o, "position")), vec(GsonHelper.getAsJsonArray(o, "target")),
                                Math.max(30F, Math.min(90F, GsonHelper.getAsFloat(o, "fov", 55F)))));
                    }
                    if (!parsed.isEmpty()) loaded.put(task, List.copyOf(parsed));
                }
            } catch (Exception e) {
                ImmersiveTanning.LOGGER.warn("Immersive Tanning: station_shots.json is broken, the built-in framing is used: {}", e.getMessage());
                loaded = new EnumMap<>(DEFAULTS);
            }
        }
        shots = loaded;
    }

    private static Vec3 vec(JsonArray a) {
        double x = a.get(0).getAsDouble();
        double y = a.get(1).getAsDouble();
        double z = a.get(2).getAsDouble();
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) throw new IllegalArgumentException("not finite");
        return new Vec3(x, y, z);
    }
}
