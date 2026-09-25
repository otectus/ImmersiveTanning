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
 */
public class TannersVatBlockEntity extends StationBlockEntity {
    public static final int WATER_CAPACITY = 1000;

    /** One hide in the vat. It waits (not started) until water and tannin let it begin. */
    public static final class Slot {
        final HideData hide;
        boolean started;
        long start;
        int duration;
        boolean announced;

        Slot(HideData hide) {
            this.hide = hide;
        }

        public HideData hide() { return hide; }
        public boolean started() { return started; }

        public float progress(long gameTime) {
            if (!started || duration <= 0) return 0F;
            return Math.min(1F, Math.max(0F, (gameTime - start) / (float) duration));
        }
    }

    private final List<Slot> slots = new ArrayList<>();
    private int water;
    private int tannin;

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
    public int maxTannin() { return ServerConfig.get(ServerConfig.VAT_MAX_TANNIN); }
    public boolean isFull() { return slots.size() >= capacity(); }

    public int finishedCount() {
        long now = gameTime();
        int n = 0;
        for (Slot s : slots) if (s.started() && s.progress(now) >= 1F) n++;
        return n;
    }

    public int waitingCount() {
        int n = 0;
        for (Slot s : slots) if (!s.started()) n++;
        return n;
    }

    // ------------------------------------------------------------------ interactions

    public boolean fillWater() {
        if (hasWater()) return false;
        water = WATER_CAPACITY;
        startWaiting();
        sync();
        return true;
    }

    /** Empties the vat's liquor (water and tannin). Hides stay and wait for a refill. */
    public boolean drainWater() {
        if (!hasWater()) return false;
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
            if (s.started() && s.progress(now) >= 1F) {
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
            if (s.started() && s.progress(now) >= 1F) continue;
            slots.remove(i);
            sync();
            return ModItems.SCRAPED_HIDE.get().create(s.hide);
        }
        return ItemStack.EMPTY;
    }

    /** Starts every waiting hide that the water and tannin allow, in insertion order. */
    private void startWaiting() {
        if (!hasWater()) return;
        long now = gameTime();
        boolean started = false;
        for (Slot s : slots) {
            if (s.started()) continue;
            int need = s.hide.size().tannin();
            if (tannin < need) break;
            tannin -= need;
            s.started = true;
            s.start = now;
            s.duration = ServerConfig.get(ServerConfig.TANNING_TICKS);
            started = true;
        }
        if (started) setChanged();
    }

    /** Test and command hook: moves every started hide's clock forward. */
    public void advance(int ticks) {
        for (Slot s : slots) if (s.started()) s.start -= ticks;
        setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TannersVatBlockEntity vat) {
        if (vat.slots.isEmpty() || level.getGameTime() % 10 != 0) return;
        long now = level.getGameTime();
        boolean changed = false;
        for (Slot s : vat.slots) {
            if (s.started() && !s.announced && s.progress(now) >= 1F) {
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
        tag.putInt("Water", water);
        tag.putInt("Tannin", tannin);
        ListTag list = new ListTag();
        for (Slot s : slots) {
            CompoundTag entry = s.hide.save();
            entry.putBoolean("Started", s.started);
            entry.putLong("Start", s.start);
            entry.putInt("Duration", s.duration);
            entry.putBoolean("Announced", s.announced);
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
            slots.add(s);
        }
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
                if (s.started() && s.progress(now) >= 1F) return ModItems.WET_TANNED_HIDE.get().create(s.hide);
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
                if (!s.started() || s.progress(now) < 1F) continue;
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
