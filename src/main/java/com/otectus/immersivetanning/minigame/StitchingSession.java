package com.otectus.immersivetanning.minigame;

import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.recipe.LeatherworkingRecipe;
import com.otectus.immersivetanning.trait.TraitRef;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** A piece being stitched at the Leatherworker's Bench: offered choices first, then the stitching run. */
public final class StitchingSession extends TanningSession {
    /** One choice the player was offered: a recipe cut from one group of leather. */
    public record Offer(ResourceLocation recipe, Finish finish, @Nullable ResourceLocation trait) {}

    List<Offer> offered = List.of();
    LeatherworkingRecipe recipe;
    Finish finish;
    @Nullable
    TraitRef trait;
    int integrity;
    int leatherUsed;
    StitchRun run;

    public StitchingSession(int id, UUID playerId, ResourceKey<Level> dimension, BlockPos pos, InteractionHand hand, long seed, long now) {
        super(id, playerId, dimension, pos, hand, seed, now);
    }

    @Override
    public Kind kind() {
        return Kind.STITCHING;
    }

    public StitchRun run() {
        return run;
    }
}
