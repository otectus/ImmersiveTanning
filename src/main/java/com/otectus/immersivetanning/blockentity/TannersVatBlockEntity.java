package com.otectus.immersivetanning.blockentity;

import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.hide.HideData;
import com.otectus.immersivetanning.hide.HideSize;
import com.otectus.immersivetanning.item.ProcessedHideItem;
import com.otectus.immersivetanning.registry.ModBlockEntities;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.registry.ModSounds;
import com.otectus.immersivetanning.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Tanner's Vat: a batch of scraped hides steeping in water charged with bark. No GUI and no chemistry - one bucket
 * of water, one bark per tannin charge, and each hide spends the charges its size needs when it starts. Hides progress
 * independently along world time, so a batch can be topped up at any moment and keeps working while nobody is near.
 * Automation may supply water, bark and scraped hides and take tanned hides out; it never finishes or flesh hides.
 *
 * <p>Every change to water, tannin or hides runs one transition ({@link #startWaiting}): waiting hides start in
 * insertion order, each paying its own cost once, and a hide the tannin cannot pay for yet does not hold back a
 * cheaper one behind it. Draining the liquor pauses started hides where they are; a refill resumes them for their
 * remaining time without charging them again.</p>
 */
public class TannersVatBlockEntity extends StationBlockEntity {
    public static final int WATER_CAPACITY = 1000;

    /** Save format: 2 added paused hides (a drained vat used to keep its clock running). */
    private static final int SAVE_VERSION = 2;

    /** One hide in the vat. It waits (not started) until water and tannin let it begin. */
    public static final class Slot {
        final HideData hide;
        boolean started;
        long start;
        int duration;
        boolean announced;
        /** Ticks it had steeped when the liquor was drained, or -1 while it steeps (or has not started). */
        long pausedElapsed = -1;

        Slot(HideData hide) {
            this.hide = hide;
        }

        public HideData hide() { return hide; }
        public boolean started() { return started; }
        public boolean paused() { return started && pausedElapsed >= 0; }
        public int duration() { return duration; }

        public float progress(long gameTime) {
            if (!started || duration <= 0) return 0F;
            long elapsed = pausedElapsed >= 0 ? pausedElapsed : gameTime - start;
            return Math.min(1F, Math.max(0F, elapsed / (float) duration));
        }

        public boolean finished(long gameTime) {
            return started && progress(gameTime) >= 1F;
        }

        /** Ticks until it is tanned while it keeps steeping. */
        public long remainingTicks(long gameTime) {
            if (!started) return duration;
            long elapsed = pausedElapsed >= 0 ? pausedElapsed : gameTime - start;
            return Math.max(0L, duration - elapsed);
        }
    }

    private final List<Slot> slots = new ArrayList<>();
    private int water;
    private int tannin;
    /** Set by {@link #load}: the first server tick re-runs the start transition (and migrates older dry vats). */
    private boolean restoreCheck;
    private boolean migrateDryVat;

    private final LazyOptional<IItemHandler> items = LazyOptional.of(AutomationItems::new);
    private final LazyOptional<IFluidHandler> fluids = LazyOptional.of(AutomationWater::new);

    public TannersVatBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TANNERS_VAT.get(), pos, state);
    }

    public List<Slot> slots() { return slots; }
    public int water() { return water; }
    public int tannin() { return tannin; }
    public boolean hasWater() { return water >= WATER_CAPACITY; }
    public int capacity() { return ServerConfig.get(ServerConfig.VAT_CAPACITY); }
    public int maxTannin() { return effectiveMaxTannin(); }
    public boolean isFull() { return slots.size() >= capacity(); }

    /**
     * The configured tannin limit, raised to the largest hide's cost: a limit below it (older configs allowed
     * {@code vatMaxTannin = 1}) would leave large hides waiting forever.
     */
    public static int effectiveMaxTannin() {
        return Math.max(ServerConfig.get(ServerConfig.VAT_MAX_TANNIN), HideSize.maxTannin());
    }

    public int finishedCount() {
        long now = gameTime();
        int n = 0;
        for (Slot s : slots) if (s.finished(now)) n++;
        return n;
    }

    public int waitingCount() {
        int n = 0;
        for (Slot s : slots) if (!s.started()) n++;
        return n;
    }

    /** Started hides whose clock is running (water in the vat, not yet tanned). */
    public int steepingCount() {
        long now = gameTime();
        int n = 0;
        for (Slot s : slots) if (s.started() && !s.paused() && !s.finished(now)) n++;
        return n;
    }

    public int pausedCount() {
        int n = 0;
        for (Slot s : slots) if (s.paused()) n++;
        return n;
    }

    /** Tannin charges still missing to start every waiting hide (0 when none waits or water is what is missing). */
    public int missingTannin() {
        int need = 0;
        for (Slot s : slots) if (!s.started()) need += s.hide.size().tannin();
        return Math.max(0, need - tannin);
    }

    /** Ticks until the next steeping hide is tanned, or -1 when none is steeping. */
    public long nextReadyTicks() {
        long now = gameTime();
        long best = -1;
        for (Slot s : slots) {
            if (!s.started() || s.paused() || s.finished(now)) continue;
            long left = s.remainingTicks(now);
            if (best < 0 || left < best) best = left;
        }
        return best;
    }

    // ------------------------------------------------------------------ interactions

    /** Fills the vat with a bucket of water; paused hides resume and waiting hides start. */
    public boolean fillWater() {
        if (hasWater()) return false;
        water = WATER_CAPACITY;
        startWaiting();
        sync();
        return true;
    }

    /**
     * Empties the vat's liquor (water and unspent tannin). Hides stay: waiting ones keep waiting, started ones pause
     * where they are and resume for their remaining time after a refill. Tannin they already spent stays spent.
     */
    public boolean drainWater() {
        if (!hasWater()) return false;
        pauseStarted();
        water = 0;
        tannin = 0;
        sync();
        return true;
    }

    /** Adds up to {@code count} tannin charges and returns how many were taken. */
    public int addTannin(int count) {
        int taken = Math.max(0, Math.min(count, maxTannin() - tannin));
        if (taken > 0) {
            tannin += taken;
            startWaiting();
            sync();
        }
        return taken;
    }

    public boolean insertHide(HideData hide) {
        if (isFull() || !hide.isFleshed()) return false;
        slots.add(new Slot(hide));
        startWaiting();
        sync();
        return true;
    }

    /** Removes and returns every tanned hide, as wet tanned hide items. */
    public List<ItemStack> takeFinished() {
        long now = gameTime();
        List<ItemStack> out = new ArrayList<>();
        slots.removeIf(s -> {
            if (s.finished(now)) {
                out.add(ModItems.WET_TANNED_HIDE.get().create(s.hide));
                return true;
            }
            return false;
        });
        if (!out.isEmpty()) sync();
        return out;
    }

    /**
     * Takes back the most recently added hide that is not finished, as the scraped hide it was. Tannin a started hide
     * already spent stays spent.
     */
    public ItemStack takeBackUnfinished() {
        long now = gameTime();
        for (int i = slots.size() - 1; i >= 0; i--) {
            Slot s = slots.get(i);
            if (s.finished(now)) continue;
            slots.remove(i);
            sync();
            return ModItems.SCRAPED_HIDE.get().create(s.hide);
        }
        return ItemStack.EMPTY;
    }

    /**
     * The vat's one start transition, run after every change to water, tannin or hides. With water in the vat,
     * paused hides resume for their remaining time, then waiting hides start in insertion order, each spending its
     * size's tannin exactly once. A hide the tannin cannot pay for yet is skipped, not a barrier: a cheaper hide
     * behind it may still start. Returns how many hides started or resumed.
     */
    private int startWaiting() {
        if (!hasWater()) return 0;
        long now = gameTime();
        int changed = 0;
        for (Slot s : slots) {
            if (s.paused()) {
                s.start = now - s.pausedElapsed;
                s.pausedElapsed = -1;
                changed++;
            }
        }
        for (Slot s : slots) {
            if (s.started()) continue;
            int need = s.hide.size().tannin();
            if (tannin < need) continue;
            tannin -= need;
            s.started = true;
            s.start = now;
            s.duration = ServerConfig.get(ServerConfig.TANNING_TICKS);
            changed++;
        }
        if (changed > 0) setChanged();
        return changed;
    }

    /** Stops the clock of every started, unfinished hide at its current progress. */
    private void pauseStarted() {
        long now = gameTime();
        for (Slot s : slots) {
            if (!s.started() || s.paused() || s.finished(now)) continue;
            s.pausedElapsed = Math.max(0L, Math.min(s.duration, now - s.start));
        }
        setChanged();
    }

    /** Test and command hook: moves the clock of every steeping hide forward. Paused hides have no liquor to steep in. */
    public void advance(int ticks) {
        for (Slot s : slots) if (s.started() && !s.paused()) s.start -= ticks;
        setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TannersVatBlockEntity vat) {
        if (vat.restoreCheck) {
            vat.restoreCheck = false;
            // Vats saved before paused hides existed kept tanning while dry; from now on they wait for water.
            if (vat.migrateDryVat && !vat.hasWater()) vat.pauseStarted();
            vat.migrateDryVat = false;
            if (vat.startWaiting() > 0) vat.sync();
        }
        if (vat.slots.isEmpty() || level.getGameTime() % 10 != 0) return;
        long now = level.getGameTime();
        boolean changed = false;
        for (Slot s : vat.slots) {
            if (s.finished(now) && !s.announced) {
                s.announced = true;
                changed = true;
            }
        }
        if (changed) {
            level.playSound(null, pos, ModSounds.VAT_HIDE.get(), SoundSource.BLOCKS, 0.5F, 0.8F);
            vat.sync();
        }
    }

    public void dropContents() {
        if (level == null) return;
        List<ItemStack> drops = new ArrayList<>(takeFinished());
        for (Slot s : slots) drops.add(ModItems.SCRAPED_HIDE.get().create(s.hide));
        slots.clear();
        for (ItemStack stack : drops) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.9, worldPosition.getZ() + 0.5, stack);
        }
    }

    /** Comparators read ready hides, so every change also updates the output signal. */
    @Override
    protected void sync() {
        super.sync();
        if (level != null && !level.isClientSide) level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
    }

    // ------------------------------------------------------------------ persistence

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Version", SAVE_VERSION);
        tag.putInt("Water", water);
        tag.putInt("Tannin", tannin);
        ListTag list = new ListTag();
        for (Slot s : slots) {
            CompoundTag entry = s.hide.save();
            entry.putBoolean("Started", s.started);
            entry.putLong("Start", s.start);
            entry.putInt("Duration", s.duration);
            entry.putBoolean("Announced", s.announced);
            if (s.pausedElapsed >= 0) entry.putLong("PausedElapsed", s.pausedElapsed);
            list.add(entry);
        }
        tag.put("Hides", list);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        water = tag.getInt("Water");
        tannin = tag.getInt("Tannin");
        slots.clear();
        for (Tag t : tag.getList("Hides", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            HideData hide = HideData.load(entry, HideSize.MEDIUM);
            Slot s = new Slot(hide.isFleshed() ? hide : hide.fleshed(0));
            s.started = entry.getBoolean("Started");
            s.start = entry.getLong("Start");
            s.duration = entry.getInt("Duration");
            s.announced = entry.getBoolean("Announced");
            s.pausedElapsed = s.started && entry.contains("PausedElapsed", Tag.TAG_ANY_NUMERIC) ? entry.getLong("PausedElapsed") : -1L;
            slots.add(s);
        }
        restoreCheck = true;
        migrateDryVat = tag.getInt("Version") < SAVE_VERSION;
    }

    // ------------------------------------------------------------------ automation

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (ServerConfig.get(ServerConfig.ENABLE_AUTOMATION)) {
            if (cap == ForgeCapabilities.ITEM_HANDLER) return items.cast();
            if (cap == ForgeCapabilities.FLUID_HANDLER) return fluids.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        items.invalidate();
        fluids.invalidate();
    }

    /** Slot 0 takes scraped hides and tannin sources; slot 1 offers tanned hides. */
    private final class AutomationItems implements IItemHandler {
        @Override
        public int getSlots() {
            return 2;
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            if (slot != 1) return ItemStack.EMPTY;
            long now = gameTime();
            for (Slot s : slots) {
                if (s.finished(now)) return ModItems.WET_TANNED_HIDE.get().create(s.hide);
            }
            return ItemStack.EMPTY;
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (slot != 0 || stack.isEmpty()) return stack;
            if (stack.is(ModItems.SCRAPED_HIDE.get())) {
                int room = Math.min(stack.getCount(), capacity() - slots.size());
                if (room <= 0) return stack;
                if (!simulate) {
                    HideData data = ProcessedHideItem.data(stack);
                    for (int i = 0; i < room; i++) insertHide(data);
                }
                return stack.copyWithCount(stack.getCount() - room);
            }
            if (stack.is(ModTags.TANNIN_SOURCES)) {
                int room = Math.min(stack.getCount(), maxTannin() - tannin);
                if (room <= 0) return stack;
                if (!simulate) addTannin(room);
                return stack.copyWithCount(stack.getCount() - room);
            }
            return stack;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 1 || amount <= 0) return ItemStack.EMPTY;
            long now = gameTime();
            for (int i = 0; i < slots.size(); i++) {
                Slot s = slots.get(i);
                if (!s.finished(now)) continue;
                ItemStack out = ModItems.WET_TANNED_HIDE.get().create(s.hide);
                if (!simulate) {
                    slots.remove(i);
                    sync();
                }
                return out;
            }
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return slot == 0 && (stack.is(ModItems.SCRAPED_HIDE.get()) || stack.is(ModTags.TANNIN_SOURCES));
        }
    }

    /** Accepts water only, up to one bucket. Nothing can be drained. */
    private final class AutomationWater implements IFluidHandler {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            return water > 0 ? new FluidStack(Fluids.WATER, water) : FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return WATER_CAPACITY;
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return stack.getFluid() == Fluids.WATER;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || resource.getFluid() != Fluids.WATER) return 0;
            int filled = Math.min(resource.getAmount(), WATER_CAPACITY - water);
            if (filled > 0 && action.execute()) {
                water += filled;
                startWaiting();
                sync();
            }
            return filled;
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    }
}
