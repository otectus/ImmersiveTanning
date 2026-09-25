package com.otectus.immersivetanning.recipe;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.minigame.SeamPatterns;
import com.otectus.immersivetanning.minigame.StitchPattern;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.HorseArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SaddleItem;

/**
 * Picks a seam pattern for a result that does not name one: an item tag {@code <namespace>:seams/<path>} naming the
 * seam {@code <namespace>:<path>} (so modded saddles can take the saddle seam), then equipment slot, horse gear, or the
 * generic seam.
 */
public final class EquipmentSeams {

    public static ResourceLocation classify(ItemStack stack) {
        for (ResourceLocation seam : SeamPatterns.ids()) {
            if (stack.is(ItemTags.create(new ResourceLocation(seam.getNamespace(), "seams/" + seam.getPath())))) return seam;
        }
        if (stack.getItem() instanceof SaddleItem) return ImmersiveTanning.id("saddle");
        if (stack.getItem() instanceof HorseArmorItem) return ImmersiveTanning.id("horse_armor");
        if (stack.getItem() instanceof ArmorItem || stack.getItem() instanceof net.minecraft.world.item.Equipable) {
            return switch (LivingEntity.getEquipmentSlotForItem(stack)) {
                case HEAD -> ImmersiveTanning.id("helmet");
                case CHEST -> ImmersiveTanning.id("chestplate");
                case LEGS -> ImmersiveTanning.id("leggings");
                case FEET -> ImmersiveTanning.id("boots");
                default -> StitchPattern.GENERIC;
            };
        }
        return StitchPattern.GENERIC;
    }

    /** Vanilla-style leather armor and dyeable leather gear, which are leatherwork even without a tag. */
    public static boolean isLeatherArmor(ItemStack stack) {
        if (stack.getItem() instanceof ArmorItem armor && armor.getMaterial() == ArmorMaterials.LEATHER) return true;
        return stack.getItem() instanceof DyeableLeatherItem;
    }

    private EquipmentSeams() {}
}
