package com.otectus.immersivetanning.blockentity;

import com.otectus.immersivetanning.work.StationLease;
import com.otectus.immersivetanning.work.StationLeases;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Shared plumbing of the three stations. Three kinds of state are kept apart:
 * <ul>
 *     <li><b>Work state</b> (subclasses) is saved and survives every player leaving;</li>
 *     <li><b>operator leases</b> ({@link StationLease}) are temporary permission for a player to work here - never
 *     saved, released by leaving, logging out, dying, walking away or idling, and never deleting work;</li>
 *     <li><b>presentation</b> lives only on clients.</li>
 * </ul>
 * Each station also has an instance id, saved, so a command meant for a broken and replaced station at the same place
 * is recognised as stale. Clients receive a sanitized copy of the work ({@link #saveClient}).
 */
public abstract class StationBlockEntity extends BlockEntity {
    private long instanceId = new Random().nextLong();
    private final Map<UUID, StationLease> leases = new LinkedHashMap<>();
    @Nullable
    private UUID operator;
    private String operatorName = "";

    protected StationBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Whether one player at a time works here (rack, bench); others watch. The vat takes everyone's transactions. */
    public abstract boolean exclusive();

    public long instanceId() {
        return instanceId;
    }

    // ------------------------------------------------------------------ leases

    /** Grants (or renews) a lease. On an exclusive station a second player becomes an observer. */
    public StationLease grant(ServerPlayer player, boolean assisted, long gameTime) {
        StationLease existing = leases.get(player.getUUID());
        if (existing != null) {
            existing.touch(gameTime);
            return existing;
        }
        boolean operating = !exclusive() || operator == null || operator.equals(player.getUUID());
        StationLease lease = new StationLease(player.getUUID(), operating ? StationLease.Role.OPERATOR : StationLease.Role.OBSERVER,
                assisted, gameTime);
        leases.put(player.getUUID(), lease);
        if (operating && exclusive()) {
            operator = player.getUUID();
            operatorName = player.getGameProfile().getName();
            sync();
        }
        StationLeases.index(player.getUUID(), this);
        return lease;
    }

    @Nullable
    public StationLease lease(UUID player) {
        return leases.get(player);
    }

    public List<StationLease> leases() {
        return new ArrayList<>(leases.values());
    }

    /** Ends a player's lease; their work stays. Returns whether they held one. */
    public boolean release(UUID player) {
        StationLease lease = leases.remove(player);
        if (lease == null) return false;
        StationLeases.forget(player, this);
        onLeaseReleased(lease);
        if (player.equals(operator)) {
            operator = null;
            operatorName = "";
            sync();
        }
        return true;
    }

    /** Hook: a lease ended (drop a thread in hand, lift a blade). */
    protected void onLeaseReleased(StationLease lease) {
    }

    /** Every lease ends (the station unloads or is removed); nothing is saved about them. */
    public void releaseAll() {
        for (UUID player : new ArrayList<>(leases.keySet())) release(player);
    }

    @Nullable
    public UUID operator() {
        return operator;
    }

    public String operatorName() {
        return operatorName;
    }

    /** Whether someone other than {@code player} is working this exclusive station. */
    public boolean isLockedFor(UUID player) {
        return operator != null && !operator.equals(player);
    }

    public boolean isLocked() {
        return operator != null;
    }

    protected long gameTime() {
        return level != null ? level.getGameTime() : 0L;
    }

    // ------------------------------------------------------------------ persistence and sync

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("Instance", instanceId);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Instance")) instanceId = tag.getLong("Instance");
        if (tag.contains("ClientOperator")) operatorName = tag.getString("ClientOperator");
        if (tag.hasUUID("ClientOperatorId")) operator = tag.getUUID("ClientOperatorId");
        else if (tag.contains("ClientOperator")) operator = null;
    }

    /** The state clients see. Subclasses add their work, sanitized. */
    protected void saveClient(CompoundTag tag) {
        tag.putLong("Instance", instanceId);
        tag.putString("ClientOperator", operatorName);
        if (operator != null) tag.putUUID("ClientOperatorId", operator);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveClient(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet) {
        CompoundTag tag = packet.getTag();
        if (tag != null) load(tag);
    }

    protected void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Public sync for the work layer. */
    public void changed() {
        sync();
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide) releaseAll();
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        if (level != null && !level.isClientSide) StationLeases.stationUnloaded(this);
        super.onChunkUnloaded();
    }
}
