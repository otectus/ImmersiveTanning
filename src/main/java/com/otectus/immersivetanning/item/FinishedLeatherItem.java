package com.otectus.immersivetanning.item;

import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.leather.LeatherData;
import com.otectus.immersivetanning.leather.TanningQuality;
import com.otectus.immersivetanning.trait.TraitRef;
import com.otectus.immersivetanning.util.Scores;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Supple, Harness or Hardened Leather. The finish is the item; Integrity and trait ride on the stack. */
public class FinishedLeatherItem extends Item {
    private final Finish finish;

    public FinishedLeatherItem(Finish finish, Properties properties) {
        super(properties);
        this.finish = finish;
    }

    public Finish finish() {
        return finish;
    }

    public ItemStack create(int integrity, @Nullable TraitRef trait, int count) {
        return new LeatherData(finish, integrity, trait).applyTo(new ItemStack(this, count));
    }

    @Override
    public Component getName(ItemStack stack) {
        TraitRef trait = LeatherData.get(stack).map(LeatherData::trait).orElse(null);
        if (trait == null) return super.getName(stack);
        return Component.translatable("item.immersive_tanning.leather.traited", TraitRef.displayName(trait.id()), super.getName(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        LeatherData data = LeatherData.get(stack).orElse(new LeatherData(finish, LeatherData.DEFAULT_INTEGRITY, null));
        tooltip.add(Component.translatable("tooltip.immersive_tanning.finish_line", finish.displayName(), finish.tagline())
                .withStyle(s -> s.withColor(finish.textColor())));
        tooltip.add(Component.translatable("tooltip.immersive_tanning.integrity", Scores.label(data.integrity()))
                .withStyle(TanningQuality.fromScore(data.integrity()).color()));
        if (data.trait() != null) {
            tooltip.add(Component.translatable("tooltip.immersive_tanning.trait", data.trait().displayName()).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.immersive_tanning.leather.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
