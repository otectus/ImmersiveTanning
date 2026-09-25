package com.otectus.immersivetanning.blockentity;

import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.hide.HideData;
import com.otectus.immersivetanning.hide.HideSize;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.minigame.SessionManager;
import com.otectus.immersivetanning.registry.ModBlockEntities;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * The Tanner's Rack has a lifecycle. A raw hide mounts low and forward for fleshing; after the vat, a wet tanned hide
 * mounts upright in the frame, loose, tensioned or taut depending on the chosen finish, and dries on its own. Drying
 * follows world time, so it continues while nobody is nearby.
 */
public class TannersRackBlockEntity extends StationBlockEntity {
    public enum Mode { EMPTY, RAW, DRYING }

    private Mode mode = Mode.EMPTY;
    @Nullable
    private HideData hide;
    private Finish finish = Finish.HARNESS;
    private long dryStart;
    private int dryTicks;
    private boolean settled;

    public TannersRackBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TANNERS_RACK.get(), pos, state);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(0.25, 0, 0.25).expandTowards(0, 0.5, 0);
    }

    public Mode mode() { return mode; }
    @Nullable public HideData hide() { return hide; }
    public Finish finish() { return finish; }
    public boolean isEmpty() { return mode == Mode.EMPTY; }

    /** 0..1 drying progress at the given world time. */
    public float dryProgress(long gameTime) {
        if (mode != Mode.DRYING || dryTicks <= 0) return 0F;
        return Math.min(1F, Math.max(0F, (gameTime - dryStart) / (float) dryTicks));
    }

    public boolean isDry() {
        return mode == Mode.DRYING && dryProgress(gameTime()) >= 1F;
    }

    /** Whether the dried hide has settled (the ready cue has played). Client-visible. */
    public boolean isSettled() {
        return settled;
    }

    public void mountRaw(HideData data) {
        mode = Mode.RAW;
        hide = HideData.raw(data.size(), data.trait());
        settled = false;
        sync();
    }

    public void mountWet(HideData data, Finish finish) {
        mode = Mode.DRYING;
        hide = data;
        this.finish = finish;
        dryStart = gameTime();
        dryTicks = ServerConfig.get(ServerConfig.DRYING_TICKS);
        settled = false;
        sync();
    }

    public void clear() {
        mode = Mode.EMPTY;
        hide = null;
        settled = false;
        sync();
    }

    /** The raw hide as an item, removing it from the rack. */
    public ItemStack takeRaw() {
        if (mode != Mode.RAW || hide == null) return ItemStack.EMPTY;
        ItemStack stack = ModItems.rawHide(hide.size()).create(hide.trait());
        clear();
        return stack;
    }

    /** A hide still drying comes back off the rack as the wet hide it was. */
    public ItemStack takeWet() {
        if (mode != Mode.DRYING || hide == null || isDry()) return ItemStack.EMPTY;
        ItemStack stack = ModItems.WET_TANNED_HIDE.get().create(hide);
        clear();
        return stack;
    }

    /** The dried hide as finished leather of the chosen finish. */
    public ItemStack collectLeather() {
        if (!isDry() || hide == null) return ItemStack.EMPTY;
        HideSize size = hide.size();
        ItemStack leather = ModItems.leather(finish).create(hide.integrity(), hide.trait(), size.leather());
        clear();
        return leather;
    }

    /** Test and command hook: moves the drying clock forward. */
    public void advance(int ticks) {
        dryStart -= ticks;
        setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TannersRackBlockEntity rack) {
        if (rack.mode != Mode.DRYING || rack.settled || level.getGameTime() % 10 != 0) return;
        if (rack.dryProgress(level.getGameTime()) >= 1F) {
            rack.settled = true;
            level.playSound(null, pos, ModSounds.RACK_DRIED.get(), SoundSource.BLOCKS, 0.6F, 0.9F);
            rack.sync();
        }
    }

    public void dropContents() {
        if (level == null) return;
        ItemStack stack = switch (mode) {
            case EMPTY -> ItemStack.EMPTY;
            case RAW -> takeRaw();
            case DRYING -> isDry() ? collectLeather() : takeWet();
        };
        if (!stack.isEmpty()) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.8, worldPosition.getZ() + 0.5, stack);
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
        tag.putString("Mode", mode.name());
        if (hide != null) tag.put("Hide", hide.save());
        tag.putString("Finish", finish.getSerializedName());
        tag.putLong("DryStart", dryStart);
        tag.putInt("DryTicks", dryTicks);
        tag.putBoolean("Settled", settled);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        try {
            mode = Mode.valueOf(tag.getString("Mode"));
        } catch (IllegalArgumentException e) {
            mode = Mode.EMPTY;
        }
        hide = tag.contains("Hide", Tag.TAG_COMPOUND) ? HideData.load(tag.getCompound("Hide"), HideSize.MEDIUM) : null;
        if (hide == null) mode = Mode.EMPTY;
        Finish f = Finish.byName(tag.getString("Finish"));
        finish = f != null ? f : Finish.HARNESS;
        dryStart = tag.getLong("DryStart");
        dryTicks = tag.getInt("DryTicks");
        settled = tag.getBoolean("Settled");
    }
}
