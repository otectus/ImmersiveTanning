package com.otectus.immersivetanning.item;

import com.otectus.immersivetanning.hide.HideData;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.work.LeatherworkJob;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A piece taken off a Leatherworker's Bench before it was finished: the job (its materials, pattern and every accepted
 * cut, seat and stitch) travels inside this one item, under the mod's own NBT root. Laid on any bench it resumes where
 * it stopped. It is never an ingredient and never made any other way; it does not stack.
 */
public class UnfinishedLeatherworkItem extends Item {
    public static final String KEY = "Workpiece";

    public UnfinishedLeatherworkItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static ItemStack of(LeatherworkJob job) {
        ItemStack stack = new ItemStack(ModItems.UNFINISHED_LEATHERWORK.get());
        stack.getOrCreateTagElement(HideData.ROOT).put(KEY, job.save());
        return stack;
    }

    @Nullable
    public static CompoundTag jobTag(ItemStack stack) {
        CompoundTag root = stack.getTagElement(HideData.ROOT);
        return root != null && root.contains(KEY, Tag.TAG_COMPOUND) ? root.getCompound(KEY) : null;
    }

    @Nullable
    public static LeatherworkJob job(ItemStack stack) {
        CompoundTag tag = jobTag(stack);
        return tag == null ? null : LeatherworkJob.load(tag);
    }

    @Override
    public Component getName(ItemStack stack) {
        CompoundTag tag = jobTag(stack);
        if (tag != null && tag.contains("Result", Tag.TAG_COMPOUND)) {
            ItemStack result = ItemStack.of(tag.getCompound("Result"));
            if (!result.isEmpty()) return Component.translatable("item.immersive_tanning.unfinished_leatherwork.of", result.getHoverName());
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = jobTag(stack);
        if (tag == null) {
            tooltip.add(Component.translatable("tooltip.immersive_tanning.workpiece.empty").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        String stage = tag.getString("Stage").toLowerCase(java.util.Locale.ROOT);
        tooltip.add(Component.translatable("tooltip.immersive_tanning.workpiece.stage",
                Component.translatable("stage.immersive_tanning.bench." + stage)).withStyle(ChatFormatting.GRAY));
        String owner = tag.getString("OwnerName");
        if (!owner.isEmpty()) tooltip.add(Component.translatable("tooltip.immersive_tanning.workpiece.owner", owner).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.immersive_tanning.workpiece.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
