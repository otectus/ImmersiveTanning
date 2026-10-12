package com.otectus.immersivetanning.api.event;

import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.trait.TraitRef;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Posted on the Forge bus when a player finishes stitching a piece at the Leatherworker's Bench, before its
 * leatherwork data is written and before it reaches the inventory. {@link #getItem()} is the live result stack:
 * changes stay on the item (the leatherwork compound is written afterwards and keeps other tags).
 * {@link #getWorkmanship()} is the stitching score (0..100) the piece will carry; listeners may change it with
 * {@link #setWorkmanship(int)}, and the bench experience and the Masterwork advancement follow the final value.
 * Server side only, not cancellable. Progression mods reward leatherwork like any other craft here.
 */
public class LeatherworkStitchedEvent extends PlayerEvent {
    private final ItemStack result;
    private final ResourceLocation recipeId;
    private final Finish finish;
    private final int integrity;
    @Nullable
    private final TraitRef trait;
    private final int leatherUsed;
    private final BlockPos bench;
    private final boolean timedOut;
    private int workmanship;

    public LeatherworkStitchedEvent(ServerPlayer player, ItemStack result, ResourceLocation recipeId, Finish finish, int integrity,
                                    int workmanship, @Nullable TraitRef trait, int leatherUsed, BlockPos bench, boolean timedOut) {
        super(player);
        this.result = result;
        this.recipeId = recipeId;
        this.finish = finish;
        this.integrity = integrity;
        this.workmanship = Mth.clamp(workmanship, 0, 100);
        this.trait = trait;
        this.leatherUsed = leatherUsed;
        this.bench = bench;
        this.timedOut = timedOut;
    }

    @Override
    public ServerPlayer getEntity() {
        return (ServerPlayer) super.getEntity();
    }

    /** The finished piece, mutable. */
    public ItemStack getItem() { return result; }
    /** The leatherworking recipe that was stitched, explicit or automatic. */
    public ResourceLocation getRecipeId() { return recipeId; }
    public Finish getFinish() { return finish; }
    /** The averaged Integrity of the leather used (steps of 10). */
    public int getIntegrity() { return integrity; }
    /** The innate hide trait of the leather used, if any. */
    @Nullable public TraitRef getTrait() { return trait; }
    /** Units of finished leather consumed. */
    public int getLeatherUsed() { return leatherUsed; }
    public BlockPos getBench() { return bench; }
    /** Whether the stitch run ran out of time. */
    public boolean isTimedOut() { return timedOut; }
    /** The stitching score, 0..100, the piece will carry as Workmanship. */
    public int getWorkmanship() { return workmanship; }
    /** Replaces the workmanship; clamped to 0..100. */
    public void setWorkmanship(int value) { this.workmanship = Mth.clamp(value, 0, 100); }
}
