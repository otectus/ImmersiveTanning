package com.otectus.immersivetanning.mixin;

import com.otectus.immersivetanning.leather.BindingLeather;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applied after the default priority, so it injects into the method bodies that performance mods (FastSuite, ModernFix)
 * put in place with {@code @Overwrite}; their {@code getItems} no longer writes the vanilla cache field itself.
 */
@Mixin(value = Ingredient.class, priority = 1500)
public abstract class IngredientMixin {
    @Shadow
    private ItemStack[] itemStacks;

    /**
     * When an ingredient's resolved stacks list vanilla leather, Binding Leather is returned instead. {@code test},
     * stacking ids, the recipe network sync and JEI all read these stacks, so every recipe that took leather takes
     * Binding Leather instead. The swap is written back into the vanilla cache, so a vanilla-cached ingredient swaps
     * once per tag reload; other caches cost one scan of the stacks per call.
     */
    @Inject(method = "getItems", at = @At("RETURN"), cancellable = true)
    private void immersive_tanning$bindingLeather(CallbackInfoReturnable<ItemStack[]> cir) {
        ItemStack[] items = cir.getReturnValue();
        ItemStack[] swapped = items == null ? null : BindingLeather.swap(items);
        if (swapped == null) return;
        if (items == itemStacks) itemStacks = swapped;
        cir.setReturnValue(swapped);
    }
}
