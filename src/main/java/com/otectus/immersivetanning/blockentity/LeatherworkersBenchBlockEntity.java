package com.otectus.immersivetanning.blockentity;

import com.otectus.immersivetanning.minigame.SessionManager;
import com.otectus.immersivetanning.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The Leatherworker's Bench. While a piece is being stitched, the leather and other ingredients taken for it sit in the
 * bench's persisted escrow: consumed when the piece is finished, refunded exactly once when the session is cancelled.
 * Escrow left behind by an interrupted session (chunk unload, crash) is dropped at the bench when it next loads.
 */
public class LeatherworkersBenchBlockEntity extends StationBlockEntity {
    private final List<ItemStack> escrow = new ArrayList<>();
    private ItemStack working = ItemStack.EMPTY;
    private int leatherColor = 0x9C5A30;
    private boolean stale;

    public LeatherworkersBenchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LEATHERWORKERS_BENCH.get(), pos, state);
    }

    /** The piece currently on the bench (for rendering), or empty. */
    public ItemStack working() { return working; }
    public int leatherColor() { return leatherColor; }
    public boolean hasEscrow() { return !escrow.isEmpty(); }

    public void beginSession(java.util.UUID player, List<ItemStack> taken, ItemStack result, int color) {
        lock(player);
        escrow.clear();
        taken.forEach(s -> escrow.add(s.copy()));
        working = result.copyWithCount(1);
        leatherColor = color;
        sync();
    }

    /** The piece is finished: the escrow is consumed. */
    public void completeSession() {
        escrow.clear();
        working = ItemStack.EMPTY;
        unlock();
        sync();
    }

    /** Removes and returns the escrow for a refund. */
    public List<ItemStack> takeEscrow() {
        List<ItemStack> out = new ArrayList<>(escrow);
        escrow.clear();
        working = ItemStack.EMPTY;
        unlock();
        sync();
        return out;
    }

    public void dropEscrow() {
        if (level == null) return;
        for (ItemStack stack : takeEscrow()) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, stack);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // Escrow in a freshly loaded bench belongs to a session that no longer exists.
        if (level != null && !level.isClientSide && !escrow.isEmpty()) stale = true;
    }

    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, LeatherworkersBenchBlockEntity bench) {
        if (bench.stale) {
            bench.stale = false;
            if (SessionManager.atStation(level, pos) == null) bench.dropEscrow();
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && !level.isClientSide) SessionManager.onStationUnloaded(level, worldPosition);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag list = new ListTag();
        for (ItemStack stack : escrow) list.add(stack.save(new CompoundTag()));
        tag.put("Escrow", list);
        if (!working.isEmpty()) tag.put("Working", working.save(new CompoundTag()));
        tag.putInt("LeatherColor", leatherColor);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        escrow.clear();
        for (Tag t : tag.getList("Escrow", Tag.TAG_COMPOUND)) {
            ItemStack stack = ItemStack.of((CompoundTag) t);
            if (!stack.isEmpty()) escrow.add(stack);
        }
        working = tag.contains("Working", Tag.TAG_COMPOUND) ? ItemStack.of(tag.getCompound("Working")) : ItemStack.EMPTY;
        leatherColor = tag.contains("LeatherColor") ? tag.getInt("LeatherColor") : 0x9C5A30;
    }
}
