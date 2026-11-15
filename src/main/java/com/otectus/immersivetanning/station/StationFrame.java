package com.otectus.immersivetanning.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The one transform between a station's local space and the world, shared by renderers, hit testing, camera shots and
 * server validation. Local space is the north-facing model in block units (0..1, the front on the -Z side, as the
 * block models and Blockbench show it); a station facing another way is that model turned about the block's centre,
 * exactly as the renderers turn it ({@link #modelYRot}).
 *
 * <p>Seen from the front, local +X runs to the viewer's left and local -Z towards the viewer.</p>
 */
public final class StationFrame {
    public static final float PX = 1F / 16F;

    /** Clockwise quarter turns (seen from above) from the north-facing model: north 0, east 1, south 2, west 3. */
    public static int quarterTurns(Direction facing) {
        return (facing.get2DDataValue() + 2) & 3;
    }

    /** The renderers' rotation about +Y, in degrees, applied around the block centre. */
    public static float modelYRot(Direction facing) {
        return -quarterTurns(facing) * 90F;
    }

    public static Direction facing(BlockState state) {
        return state.hasProperty(HorizontalDirectionalBlock.FACING) ? state.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
    }

    /** A local point (block units) as a world position. */
    public static Vec3 toWorld(BlockPos pos, Direction facing, double lx, double ly, double lz) {
        double x = lx - 0.5;
        double z = lz - 0.5;
        double rx;
        double rz;
        switch (quarterTurns(facing)) {
            case 0 -> { rx = x; rz = z; }
            case 1 -> { rx = -z; rz = x; }
            case 2 -> { rx = -x; rz = -z; }
            default -> { rx = z; rz = -x; }
        }
        return new Vec3(pos.getX() + 0.5 + rx, pos.getY() + ly, pos.getZ() + 0.5 + rz);
    }

    /** A local point in pixels as a world position. */
    public static Vec3 pxToWorld(BlockPos pos, Direction facing, double px, double py, double pz) {
        return toWorld(pos, facing, px * PX, py * PX, pz * PX);
    }

    /** A world position in local block units. */
    public static Vec3 toLocal(BlockPos pos, Direction facing, Vec3 world) {
        double x = world.x - pos.getX() - 0.5;
        double z = world.z - pos.getZ() - 0.5;
        double lx;
        double lz;
        switch (quarterTurns(facing)) {
            case 0 -> { lx = x; lz = z; }
            case 1 -> { lx = z; lz = -x; }
            case 2 -> { lx = -x; lz = -z; }
            default -> { lx = -z; lz = x; }
        }
        return new Vec3(lx + 0.5, world.y - pos.getY(), lz + 0.5);
    }

    /** A world position in local pixels. */
    public static Vec3 toLocalPx(BlockPos pos, Direction facing, Vec3 world) {
        return toLocal(pos, facing, world).scale(16.0);
    }

    /** A local direction as a world direction. */
    public static Vec3 dirToWorld(Direction facing, Vec3 local) {
        return switch (quarterTurns(facing)) {
            case 0 -> local;
            case 1 -> new Vec3(-local.z, local.y, local.x);
            case 2 -> new Vec3(-local.x, local.y, -local.z);
            default -> new Vec3(local.z, local.y, -local.x);
        };
    }

    /** A world direction as a local direction. */
    public static Vec3 dirToLocal(Direction facing, Vec3 world) {
        return switch (quarterTurns(facing)) {
            case 0 -> world;
            case 1 -> new Vec3(world.z, world.y, -world.x);
            case 2 -> new Vec3(-world.x, world.y, -world.z);
            default -> new Vec3(-world.z, world.y, world.x);
        };
    }

    private StationFrame() {}
}
