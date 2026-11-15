package com.otectus.immersivetanning.mixin.compat.eanimod;

import com.otectus.immersivetanning.compat.geneticanimals.GeneticAnimalsEquipment;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Genetic Animals 0.11.12: {@code saddleAnimal} puts a brand-new vanilla saddle on the animal in place of the one the
 * player used. The saddle used is remembered for the length of the call and copied in its place
 * ({@link GeneticAnimalsEquipment}). Its own saddles go through {@code getReplacementItemWithColour} instead.
 */
@Pseudo
@Mixin(targets = "mokiyoki.enhancedanimals.entity.EnhancedAnimalRideableAbstract", remap = false)
public abstract class EnhancedAnimalRideableAbstractMixin {
    private static final String SADDLE_ANIMAL = "saddleAnimal(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;"
            + "Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/entity/LivingEntity;)Z";

    @Unique
    private ItemStack immersive_tanning$saddleUsed;

    @Inject(method = SADDLE_ANIMAL, at = @At("HEAD"), require = 0)
    private void immersive_tanning$rememberSaddle(ItemStack saddle, Player player, InteractionHand hand, LivingEntity target,
                                                  CallbackInfoReturnable<Boolean> cir) {
        immersive_tanning$saddleUsed = saddle;
    }

    @Redirect(method = SADDLE_ANIMAL, at = @At(value = "NEW", target = "(Lnet/minecraft/world/level/ItemLike;I)Lnet/minecraft/world/item/ItemStack;"),
            require = 0)
    private ItemStack immersive_tanning$keepSaddle(ItemLike item, int count) {
        return GeneticAnimalsEquipment.keep(immersive_tanning$saddleUsed, new ItemStack(item, count));
    }

    @Inject(method = SADDLE_ANIMAL, at = @At("RETURN"), require = 0)
    private void immersive_tanning$forgetSaddle(ItemStack saddle, Player player, InteractionHand hand, LivingEntity target,
                                                CallbackInfoReturnable<Boolean> cir) {
        immersive_tanning$saddleUsed = null;
    }
}
