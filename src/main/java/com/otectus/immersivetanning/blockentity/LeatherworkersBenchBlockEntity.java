package com.otectus.immersivetanning.blockentity;

import com.otectus.immersivetanning.item.UnfinishedLeatherworkItem;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.registry.ModBlockEntities;
import com.otectus.immersivetanning.work.LeatherworkJob;
import com.otectus.immersivetanning.work.MaterialLedger;
import com.otectus.immersivetanning.work.StationLease;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Leatherworker's Bench and the piece on it. A {@link LeatherworkJob} holds its materials and every accepted cut,
 * seat and stitch, saved with the bench, so the work waits for its maker through leaving, logging out and restarts.
 * Finished pieces (and their scraps and crafting remainders) wait on the output pad until collected: a full inventory
 * never destroys a result, and nothing is ever produced twice.
 *
 * <p>Save format 2 (since 1.3). A format 1 bench held only an escrow of taken materials and no record of the work:
 * those exact stacks move onto the output pad once, for anyone to collect, rather than inventing a half-made piece.</p>
 */
public class LeatherworkersBenchBlockEntity extends StationBlockEntity {
    public static final int SAVE_VERSION = 2;
    public static final int MAX_OUTPUT = 12;

    @Nullable
    private LeatherworkJob job;
    private final List<ItemStack> output = new ArrayList<>();
    @Nullable
    private UUID outputOwner;
    private String outputOwnerName = "";
    @Nullable
    private ResourceLocation lastRecipe;
    @Nullable
    private Finish lastFinish;
    @Nullable
    private ResourceLocation lastTrait;
    private int lastWorkmanship = -1;
    private final List<String> lastExplanation = new ArrayList<>();
    private int completions;

    public LeatherworkersBenchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LEATHERWORKERS_BENCH.get(), pos, state);
    }

    @Override
    public boolean exclusive() {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).expandTowards(0, 0.6, 0);
    }

    @Nullable
    public LeatherworkJob job() {
        return job;
    }

    public void setJob(@Nullable LeatherworkJob job) {
        this.job = job;
        sync();
    }

    /** Removes the job (committed, carried away or recovered). */
    @Nullable
    public LeatherworkJob takeJob() {
        LeatherworkJob taken = job;
        job = null;
        sync();
        return taken;
    }

    public List<ItemStack> output() {
        List<ItemStack> copy = new ArrayList<>();
        for (ItemStack s : output) copy.add(s.copy());
        return copy;
    }

    public boolean hasOutput() {
        return !output.isEmpty();
    }

    @Nullable
    public UUID outputOwner() {
        return outputOwner;
    }

    public String outputOwnerName() {
        return outputOwnerName;
    }

    public boolean outputRoom(int stacks) {
        return output.size() + stacks <= MAX_OUTPUT;
    }

    /** Puts finished items on the output pad for their owner (null: anyone may collect). */
    public void addOutput(List<ItemStack> stacks, @Nullable UUID owner, String ownerName) {
        for (ItemStack s : stacks) if (!s.isEmpty()) output.add(s.copy());
        if (owner != null || output.isEmpty()) {
            outputOwner = owner;
            outputOwnerName = ownerName;
        }
        sync();
    }

    /** Whether a player may take what lies on the output pad. */
    public boolean mayCollect(Player player) {
        return outputOwner == null || outputOwner.equals(player.getUUID()) || player.hasPermissions(2);
    }

    /** Moves the output into the player's inventory as far as it fits; the rest stays. Returns what was moved. */
    public List<ItemStack> collect(ServerPlayer player) {
        List<ItemStack> moved = new ArrayList<>();
        for (int i = 0; i < output.size(); i++) {
            ItemStack stack = output.get(i);
            int before = stack.getCount();
            ItemStack copy = stack.copy();
            player.getInventory().add(stack);
            int taken = before - stack.getCount();
            if (taken > 0) moved.add(copy.copyWithCount(taken));
        }
        output.removeIf(ItemStack::isEmpty);
        if (output.isEmpty()) {
            outputOwner = null;
            outputOwnerName = "";
        }
        if (!moved.isEmpty()) {
            player.getInventory().setChanged();
            sync();
        }
        return moved;
    }

    // ------------------------------------------------------------------ preferences and results

    public void remember(ResourceLocation recipe, Finish finish, @Nullable ResourceLocation trait) {
        lastRecipe = recipe;
        lastFinish = finish;
        lastTrait = trait;
        setChanged();
    }

    @Nullable public ResourceLocation lastRecipe() { return lastRecipe; }
    @Nullable public Finish lastFinish() { return lastFinish; }
    @Nullable public ResourceLocation lastTrait() { return lastTrait; }
    public int lastWorkmanship() { return lastWorkmanship; }
    public List<String> lastExplanation() { return List.copyOf(lastExplanation); }
    public int completions() { return completions; }

    public void recordResult(int workmanship, List<String> explanation) {
        lastWorkmanship = workmanship;
        lastExplanation.clear();
        lastExplanation.addAll(explanation);
        completions++;
        sync();
    }

    @Override
    protected void onLeaseReleased(StationLease lease) {
        if (job != null && lease.operating()) {
            job.settle();
            sync();
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LeatherworkersBenchBlockEntity bench) {
    }

    /**
     * Breaking the bench drops exactly one route for the job: a started piece as an unfinished workpiece (its
     * materials inside), an unstarted one as the materials themselves. The output pad drops as it is.
     */
    public void dropAll() {
        if (level == null) return;
        List<ItemStack> drops = new ArrayList<>();
        if (job != null) {
            LeatherworkJob taken = job;
            job = null;
            taken.settle();
            if (taken.started()) drops.add(UnfinishedLeatherworkItem.of(taken));
            else drops.addAll(taken.ledger().drain());
        }
        drops.addAll(output);
        output.clear();
        for (ItemStack stack : drops) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, stack);
        }
        setChanged();
    }

    // ------------------------------------------------------------------ persistence

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("BenchVersion", SAVE_VERSION);
        if (job != null) tag.put("Job", job.save());
        ListTag list = new ListTag();
        for (ItemStack stack : output) list.add(stack.save(new CompoundTag()));
        tag.put("Output", list);
        if (outputOwner != null) tag.putUUID("OutputOwner", outputOwner);
        writeShared(tag);
    }

    @Override
    protected void saveClient(CompoundTag tag) {
        super.saveClient(tag);
        if (job != null) tag.put("Job", job.saveClient());
        ListTag list = new ListTag();
        for (ItemStack stack : output) list.add(LeatherworkJob.displayCopy(stack).save(new CompoundTag()));
        tag.put("Output", list);
        if (outputOwner != null) tag.putUUID("OutputOwner", outputOwner);
        writeShared(tag);
    }

    private void writeShared(CompoundTag tag) {
        tag.putString("OutputOwnerName", outputOwnerName);
        if (lastRecipe != null) tag.putString("LastRecipe", lastRecipe.toString());
        if (lastFinish != null) tag.putString("LastFinish", lastFinish.getSerializedName());
        if (lastTrait != null) tag.putString("LastTrait", lastTrait.toString());
        tag.putInt("LastWorkmanship", lastWorkmanship);
        ListTag why = new ListTag();
        for (String key : lastExplanation) why.add(StringTag.valueOf(key));
        tag.put("LastExplanation", why);
        tag.putInt("Completions", completions);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        job = tag.contains("Job", Tag.TAG_COMPOUND) ? LeatherworkJob.load(tag.getCompound("Job")) : null;
        output.clear();
        for (Tag t : tag.getList("Output", Tag.TAG_COMPOUND)) {
            ItemStack stack = ItemStack.of((CompoundTag) t);
            if (!stack.isEmpty()) output.add(stack);
        }
        if (tag.contains("Job", Tag.TAG_COMPOUND) && job == null) {
            // An unreadable job never swallows its materials: whatever stacks it names go onto the output pad.
            for (ItemStack s : LeatherworkJob.salvage(tag.getCompound("Job"))) output.add(s);
        }
        // Format 1: an escrow without any record of the work. The exact stacks move onto the output pad, once.
        if (tag.getInt("BenchVersion") < 2) {
            for (Tag t : tag.getList("Escrow", Tag.TAG_COMPOUND)) {
                ItemStack stack = ItemStack.of((CompoundTag) t);
                if (!stack.isEmpty()) output.add(stack);
            }
        }
        outputOwner = tag.hasUUID("OutputOwner") ? tag.getUUID("OutputOwner") : null;
        outputOwnerName = tag.getString("OutputOwnerName");
        lastRecipe = tag.contains("LastRecipe") ? ResourceLocation.tryParse(tag.getString("LastRecipe")) : null;
        lastFinish = tag.contains("LastFinish") ? Finish.byName(tag.getString("LastFinish")) : null;
        lastTrait = tag.contains("LastTrait") ? ResourceLocation.tryParse(tag.getString("LastTrait")) : null;
        lastWorkmanship = tag.contains("LastWorkmanship") ? tag.getInt("LastWorkmanship") : -1;
        lastExplanation.clear();
        for (Tag t : tag.getList("LastExplanation", Tag.TAG_STRING)) lastExplanation.add(t.getAsString());
        completions = tag.getInt("Completions");
    }

    /** Test hook: the ledger of the current job, if any. */
    @Nullable
    public MaterialLedger ledger() {
        return job == null ? null : job.ledger();
    }

    /** Whether materials are held for a job (cancellable, refundable or in work). */
    public boolean hasEscrow() {
        return job != null && !job.ledger().isEmpty();
    }
}
