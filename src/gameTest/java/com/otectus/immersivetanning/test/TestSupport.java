package com.otectus.immersivetanning.test;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;

import java.util.UUID;

final class TestSupport {
    static final BlockPos STATION = new BlockPos(2, 1, 2);

    static FakePlayer player(GameTestHelper h) {
        FakePlayer player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "TannerTest")) {
            @Override
            public boolean hasDisconnected() {
                return false;
            }
        };
        BlockPos pos = h.absolutePos(STATION);
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 1.5);
        return player;
    }

    /** Right-clicks the station with the item in the main hand. */
    static InteractionResult use(GameTestHelper h, FakePlayer player) {
        BlockPos pos = h.absolutePos(STATION);
        Vec3 hit = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.99);
        return h.getLevel().getBlockState(pos).use(h.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, Direction.SOUTH, pos, false));
    }

    private TestSupport() {}
}
