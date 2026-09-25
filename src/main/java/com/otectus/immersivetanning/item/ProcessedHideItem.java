package com.otectus.immersivetanning.item;

import com.otectus.immersivetanning.hide.HideData;
import com.otectus.immersivetanning.hide.HideSize;
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

/**
 * A fleshed hide on its way through the tannery: {@link Stage#SCRAPED} goes into the vat, {@link Stage#WET} comes out
 * and is mounted on the rack to dry. Size, Integrity and trait live in the stack's {@code immersive_tanning} compound;
 * Integrity is kept in steps of ten so hides from similar work stack.
 */
public class ProcessedHideItem extends Item {
    public enum Stage { SCRAPED, WET }

    private final Stage stage;

    public ProcessedHideItem(Stage stage, Properties properties) {
        super(properties);
        this.stage = stage;
    }

    public Stage stage() {
        return stage;
    }

    public ItemStack create(HideSize size, int integrity, @Nullable TraitRef trait) {
        return create(new HideData(size, HideData.roundIntegrity(integrity), trait));
    }

    public ItemStack create(HideData data) {
        HideData fleshed = data.isFleshed() ? data : data.fleshed(0);
        return fleshed.applyTo(new ItemStack(this));
    }

    public static HideData data(ItemStack stack) {
        HideData data = HideData.of(stack, HideSize.MEDIUM);
        return data.isFleshed() ? data : data.fleshed(0);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId() + ".sized", data(stack).size().displayName());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        HideData data = data(stack);
        tooltip.add(Component.translatable("tooltip.immersive_tanning.integrity", Scores.label(data.integrity()))
                .withStyle(TanningQuality.fromScore(data.integrity()).color()));
        if (data.trait() != null) {
            tooltip.add(Component.translatable("tooltip.immersive_tanning.trait", data.trait().displayName()).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.immersive_tanning.yields", data.size().leather()).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(getDescriptionId() + ".hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
