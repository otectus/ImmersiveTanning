package com.otectus.immersivetanning.mixin.compat.eanimod;

import com.otectus.immersivetanning.compat.geneticanimals.GeneticAnimalsEquipment;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Genetic Animals 0.11.12: bridles and its own saddles put on by hand are rebuilt from item and colour. The animal gets
 * a one-item copy of the stack the player used instead ({@link GeneticAnimalsEquipment}). Applied only when Genetic
 * Animals is installed; {@code TanningMixinPlugin} checks that it took.
 */
@Pseudo
@Mixin(targets = "mokiyoki.enhancedanimals.entity.EnhancedAnimalAbstract", remap = false)
public abstract class EnhancedAnimalAbstractMixin {

    @Inject(method = "getReplacementItemWithColour", at = @At("RETURN"), cancellable = true, require = 0)
    private void immersive_tanning$keepEquipment(ItemStack used, CallbackInfoReturnable<ItemStack> cir) {
        cir.setReturnValue(GeneticAnimalsEquipment.keep(used, cir.getReturnValue()));
    }
}
