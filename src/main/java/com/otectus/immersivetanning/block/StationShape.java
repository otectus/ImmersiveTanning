package com.otectus.immersivetanning.block;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Precomputed quarter turns of a north-facing model. No shape allocation during collision or picking. */
final class StationShape {
    private final VoxelShape[] shapes = new VoxelShape[4];

    StationShape(VoxelShape north) {
        VoxelShape current = north;
        for (Direction direction : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            shapes[direction.get2DDataValue()] = current.optimize();
            VoxelShape[] rotated = {Shapes.empty()};
            current.forAllBoxes((x0, y0, z0, x1, y1, z1) -> rotated[0] = Shapes.or(rotated[0],
                    Shapes.box(1 - z1, y0, x0, 1 - z0, y1, x1)));
            current = rotated[0];
        }
    }

    VoxelShape facing(Direction facing) { return shapes[facing.get2DDataValue()]; }
}
