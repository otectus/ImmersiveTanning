package com.otectus.immersivetanning.item;

import com.otectus.immersivetanning.hide.HideData;
import com.otectus.immersivetanning.hide.HideSize;
import com.otectus.immersivetanning.trait.TraitRef;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A raw hide of one size. Ordinary hides carry no NBT so they always stack; only a hide whose source grants an innate
 * trait keeps that trait in its {@code immersive_tanning} compound.
 */
public class RawHideItem extends Item {
    private final HideSize size;

    public RawHideItem(HideSize size, Properties properties) {
        super(properties);
        this.size = size;
    }

    public HideSize size() {
        return size;
    }

    public ItemStack create(@Nullable TraitRef trait) {
        ItemStack stack = new ItemStack(this);
        if (trait != null) {
            CompoundTag root = new CompoundTag();
            trait.save(root);
            stack.getOrCreateTag().put(HideData.ROOT, root);
        }
        return stack;
    }

    public HideData data(ItemStack stack) {
        return HideData.raw(size, HideData.of(stack, size).trait());
    }

    @Override
    public Component getName(ItemStack stack) {
        TraitRef trait = HideData.of(stack, size).trait();
        if (trait == null) return super.getName(stack);
        return Component.translatable("item.immersive_tanning.raw_hide.traited", TraitRef.displayName(trait.id()), super.getName(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        TraitRef trait = HideData.of(stack, size).trait();
        if (trait != null) {
            tooltip.add(Component.translatable("tooltip.immersive_tanning.trait", trait.displayName()).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.immersive_tanning.yields", size.leather()).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.immersive_tanning.raw_hide.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
