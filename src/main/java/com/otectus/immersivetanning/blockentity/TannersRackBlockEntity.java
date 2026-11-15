package com.otectus.immersivetanning.blockentity;

import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.hide.HideData;
import com.otectus.immersivetanning.hide.HideSize;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.registry.ModBlockEntities;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.registry.ModSounds;
import com.otectus.immersivetanning.work.FleshingWork;
import com.otectus.immersivetanning.work.StationLease;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.Random;
import java.util.UUID;

/**
 * The Tanner's Rack and the work on it. One hide at a time:
 * <ul>
 *     <li>{@link Mode#RAW} - a raw hide over the fleshing beam, with its {@link FleshingWork} (seed, every scraped
 *     cell, whose work it is). Leaving and coming back resumes it; taking the hide off carries the work with it.</li>
 *     <li>{@link Mode#SCRAPED} - the clean hide stays on the beam until it is picked up; scoring already happened.</li>
 *     <li>{@link Mode#FRAMING} - a wet tanned hide in the upper frame. The tension lever previews a finish (free to
 *     change), the finish's short treatment is worked, and seating the lever commits it.</li>
 *     <li>{@link Mode#DRYING} - the committed hide dries on world time while nobody is near. Taken off unfinished, it
 *     keeps its finish and its drying so far, paused; remounted, it resumes.</li>
 * </ul>
 * Save format 2 (since 1.3); format 1 racks load as they were, a raw hide gaining untouched work.
 */
public class TannersRackBlockEntity extends StationBlockEntity {
    public static final int SAVE_VERSION = 2;
    /** Item data a wet hide carries when it was taken off a rack unfinished. */
    public static final String DRY_FINISH = "DryFinish";
    public static final String DRY_ELAPSED = "DryElapsed";
    public static final String DRY_TICKS = "DryTicks";
    /** Item data a raw hide carries when it was taken off a rack partly scraped. */
    public static final String FLESHING = "Fleshing";

    public enum Mode { EMPTY, RAW, SCRAPED, FRAMING, DRYING }

    private Mode mode = Mode.EMPTY;
    @Nullable
    private HideData hide;
    @Nullable
    private FleshingWork work;
    /** How the scraped hide looks: the worked surface, kept for drawing only while it waits on the beam. */
    @Nullable
    private com.otectus.immersivetanning.minigame.FleshingSurface scrapedLook;
    @Nullable
    private UUID scrapedBy;
    private String scrapedByName = "";
    private Finish finish = Finish.HARNESS;
    private float treatment;
    private long dryStart;
    private int dryTicks;
    private boolean settled;

    public TannersRackBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TANNERS_RACK.get(), pos, state);
    }

    @Override
    public boolean exclusive() {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(0.4, 0, 0.4).expandTowards(0, 0.5, 0);
    }

    public Mode mode() { return mode; }
    @Nullable public HideData hide() { return hide; }
    @Nullable public FleshingWork work() { return work; }
    /** The surface a scraped hide was worked to (for drawing only); null in any other mode. */
    @Nullable public com.otectus.immersivetanning.minigame.FleshingSurface scrapedLook() { return mode == Mode.SCRAPED ? scrapedLook : null; }
    public Finish finish() { return finish; }
    public boolean isEmpty() { return mode == Mode.EMPTY; }
    /** How far the previewed finish's treatment has been worked (0..1). */
    public float treatment() { return treatment; }
    public boolean treated() { return treatment >= 1F; }
    @Nullable public UUID scrapedBy() { return scrapedBy; }
    public String scrapedByName() { return scrapedByName; }

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

    // ------------------------------------------------------------------ fleshing

    /** Lays a raw hide over the beam: untouched work with a fresh seed, or the work the hide carries. */
    public void mountRaw(HideData data, @Nullable CompoundTag carried) {
        mode = Mode.RAW;
        hide = HideData.raw(data.size(), data.trait());
        FleshingWork resumed = FleshingWork.load(carried, data.size());
        work = resumed != null ? resumed : FleshingWork.start(data.size(), new Random().nextLong());
        settled = false;
        scrapedBy = null;
        sync();
    }

    public void mountRaw(HideData data) {
        mountRaw(data, null);
    }

    /** The raw hide as the item it would come off as (work done on it travels with it), leaving it on the rack. */
    public ItemStack rawStack() {
        if (mode != Mode.RAW || hide == null) return ItemStack.EMPTY;
        ItemStack stack = ModItems.rawHide(hide.size()).create(hide.trait());
        if (work != null && work.started()) {
            work.surface().end();
            stack.getOrCreateTagElement(HideData.ROOT).put(FLESHING, work.save());
        }
        return stack;
    }

    /** The raw hide as an item, removing it from the rack. Work done on it travels with it. */
    public ItemStack takeRaw() {
        ItemStack stack = rawStack();
        if (!stack.isEmpty()) clear();
        return stack;
    }

    /** Fleshing is done: the clean hide stays on the beam until picked up. */
    public void completeFleshing(int score, @Nullable UUID by, String byName) {
        if (mode != Mode.RAW || hide == null) return;
        hide = hide.fleshed(score);
        mode = Mode.SCRAPED;
        scrapedLook = work == null ? null : work.surface().copy();
        work = null;
        scrapedBy = by;
        scrapedByName = byName;
        sync();
    }

    public ItemStack takeScraped() {
        if (mode != Mode.SCRAPED || hide == null) return ItemStack.EMPTY;
        ItemStack stack = ModItems.SCRAPED_HIDE.get().create(hide);
        clear();
        return stack;
    }

    // ------------------------------------------------------------------ finishing and drying

    /**
     * Laces a wet hide into the frame. A hide taken off unfinished resumes drying with its finish; any other waits in
     * the frame with {@code preview} on the lever until a finish is worked and seated.
     */
    public void mountWet(ItemStack wetHide, HideData data, Finish preview) {
        CompoundTag root = wetHide.getTagElement(HideData.ROOT);
        Finish carried = root != null ? Finish.byName(root.getString(DRY_FINISH)) : null;
        hide = data;
        settled = false;
        if (carried != null && root.getInt(DRY_TICKS) > 0) {
            mode = Mode.DRYING;
            finish = carried;
            dryTicks = root.getInt(DRY_TICKS);
            dryStart = gameTime() - Math.max(0L, Math.min(root.getLong(DRY_ELAPSED), dryTicks - 1L));
        } else {
            mode = Mode.FRAMING;
            finish = preview;
            treatment = 0F;
        }
        sync();
    }

    /** The lever moved to another detent: the previous treatment no longer applies. */
    public void setDetent(Finish detent) {
        if (mode != Mode.FRAMING || detent == finish) return;
        finish = detent;
        treatment = 0F;
        sync();
    }

    public void treat(float amount) {
        if (mode != Mode.FRAMING || amount <= 0F) return;
        float before = treatment;
        treatment = Math.min(1F, treatment + amount);
        if (treatment != before) sync();
    }

    /** Seats the lever: the previewed finish is committed and drying starts. */
    public boolean seat() {
        if (mode != Mode.FRAMING || !treated()) return false;
        startDrying(finish);
        return true;
    }

    /** The last-finish shortcut: the short treatment of that finish is done at once and drying starts. */
    public void startDrying(Finish chosen) {
        if (hide == null) return;
        mode = Mode.DRYING;
        finish = chosen;
        treatment = 1F;
        dryStart = gameTime();
        dryTicks = ServerConfig.get(ServerConfig.DRYING_TICKS);
        settled = false;
        sync();
    }

    /** Test, command and pre-1.3 hook: mounts a wet hide straight into drying. */
    public void mountWet(HideData data, Finish chosen) {
        hide = data;
        startDrying(chosen);
    }

    /** A hide in the frame comes back as the wet hide it was; one still drying keeps its finish and drying so far. */
    public ItemStack takeWet() {
        if (hide == null || (mode != Mode.FRAMING && mode != Mode.DRYING) || isDry()) return ItemStack.EMPTY;
        ItemStack stack = ModItems.WET_TANNED_HIDE.get().create(hide);
        if (mode == Mode.DRYING) {
            CompoundTag root = stack.getOrCreateTagElement(HideData.ROOT);
            root.putString(DRY_FINISH, finish.getSerializedName());
            root.putLong(DRY_ELAPSED, Math.max(0L, gameTime() - dryStart));
            root.putInt(DRY_TICKS, dryTicks);
        }
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

    public void clear() {
        mode = Mode.EMPTY;
        hide = null;
        work = null;
        scrapedLook = null;
        settled = false;
        treatment = 0F;
        scrapedBy = null;
        scrapedByName = "";
        sync();
    }

    /** Test and command hook: moves the drying clock forward. */
    public void advance(int ticks) {
        dryStart -= ticks;
        setChanged();
    }

    @Override
    protected void onLeaseReleased(StationLease lease) {
        if (work != null && lease.operating()) work.surface().end();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TannersRackBlockEntity rack) {
        if (rack.mode != Mode.DRYING || rack.settled || level.getGameTime() % 10 != 0) return;
        if (rack.dryProgress(level.getGameTime()) >= 1F) {
            rack.settled = true;
            level.playSound(null, pos, ModSounds.RACK_DRIED.get(), SoundSource.BLOCKS, 0.6F, 0.9F);
            rack.sync();
        }
    }

    /** What the rack drops when broken: exactly the one thing on it, in its current state. */
    public void dropContents() {
        if (level == null) return;
        ItemStack stack = switch (mode) {
            case EMPTY -> ItemStack.EMPTY;
            case RAW -> takeRaw();
            case SCRAPED -> takeScraped();
            case FRAMING -> takeWet();
            case DRYING -> isDry() ? collectLeather() : takeWet();
        };
        if (!stack.isEmpty()) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.8, worldPosition.getZ() + 0.5, stack);
        }
    }

    // ------------------------------------------------------------------ persistence

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("RackVersion", SAVE_VERSION);
        writeState(tag, false);
    }

    @Override
    protected void saveClient(CompoundTag tag) {
        super.saveClient(tag);
        writeState(tag, true);
    }

    private void writeState(CompoundTag tag, boolean client) {
        tag.putString("Mode", mode.name());
        if (hide != null) tag.put("Hide", hide.save());
        if (work != null) tag.put("Work", work.save());
        if (mode == Mode.SCRAPED && scrapedLook != null) tag.put("Look", scrapedLook.save());
        if (scrapedBy != null && !client) tag.putUUID("ScrapedBy", scrapedBy);
        tag.putString("ScrapedByName", scrapedByName);
        tag.putString("Finish", finish.getSerializedName());
        tag.putFloat("Treatment", treatment);
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
        treatment = Mth.clamp(tag.getFloat("Treatment"), 0F, 1F);
        dryStart = tag.getLong("DryStart");
        dryTicks = tag.getInt("DryTicks");
        settled = tag.getBoolean("Settled");
        scrapedBy = tag.hasUUID("ScrapedBy") ? tag.getUUID("ScrapedBy") : null;
        scrapedByName = tag.getString("ScrapedByName");
        work = null;
        if (mode == Mode.RAW && hide != null) {
            work = FleshingWork.load(tag.getCompound("Work"), hide.size());
            // Format 1 racks never held fleshing work: the hide starts untouched, nothing is lost or granted.
            if (work == null) work = FleshingWork.start(hide.size(), new Random().nextLong());
            if (hide.isFleshed()) hide = HideData.raw(hide.size(), hide.trait());
        }
        if (mode == Mode.SCRAPED && hide != null && !hide.isFleshed()) hide = hide.fleshed(0);
        scrapedLook = mode == Mode.SCRAPED && hide != null
                ? com.otectus.immersivetanning.minigame.FleshingSurface.tryLoad(tag.getCompound("Look"), hide.size()) : null;
        if (mode == Mode.DRYING && dryTicks <= 0) dryTicks = ServerConfig.get(ServerConfig.DRYING_TICKS);
    }
}
