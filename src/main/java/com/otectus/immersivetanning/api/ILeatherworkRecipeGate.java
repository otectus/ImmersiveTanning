package com.otectus.immersivetanning.api;

import com.otectus.immersivetanning.recipe.LeatherworkingRecipe;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Decides whether a player may stitch a recipe at all. Asked when the bench lists its offers (a denied recipe is not
 * offered; the first denial is the reason shown when nothing is left) and again, authoritatively, when the recipe is
 * selected, before any leather or auxiliary ingredient is taken. Server thread only; a gate that throws is skipped.
 */
@FunctionalInterface
public interface ILeatherworkRecipeGate {
    /** A player-facing reason to refuse {@code recipe} to {@code player}, or empty to allow it. */
    Optional<Component> deny(ServerPlayer player, LeatherworkingRecipe recipe);
}
