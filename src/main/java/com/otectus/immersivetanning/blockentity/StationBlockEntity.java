package com.otectus.immersivetanning.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Shared plumbing of the three stations: full-state client sync and a transient player lock for minigames. */
public abstract class StationBlockEntity extends BlockEntity {
    @Nullable
    private UUID lockedBy; // transient: locks never survive a restart

    protected StationBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public boolean isLocked() {
        return lockedBy != null;
    }

    public boolean isLockedFor(UUID player) {
        return lockedBy != null && !lockedBy.equals(player);
    }

    public void lock(UUID player) {
        lockedBy = player;
    }

    public void unlock() {
        lockedBy = null;
    }

    protected long gameTime() {
        return level != null ? level.getGameTime() : 0L;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    protected void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
