package com.otectus.immersivetanning.client.compat.jei;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.hide.HideSize;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.recipe.HideMountingRecipe;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.registry.ModRecipeTypes;
import com.otectus.immersivetanning.registry.ModTags;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** The tannery for one hide size: rack (flesh), vat (water and bark), rack (finish and dry), leather. */
public class TanneryCategory implements IRecipeCategory<HideSize> {
    public static final RecipeType<HideSize> TYPE = RecipeType.create(ImmersiveTanning.MOD_ID, "tannery", HideSize.class);
    private static final int WIDTH = 160;
    private static final int HEIGHT = 64;

    private final IDrawable icon;
    private final IDrawable slot;
    private final IDrawable arrow;
    private final IDrawable rack;
    private final IDrawable vat;

    public TanneryCategory(IGuiHelper helper) {
        this.icon = helper.createDrawableItemStack(new ItemStack(ModItems.TANNERS_RACK.get()));
        this.slot = helper.getSlotDrawable();
        this.arrow = helper.getRecipeArrow();
        this.rack = helper.createDrawableItemStack(new ItemStack(ModItems.TANNERS_RACK.get()));
        this.vat = helper.createDrawableItemStack(new ItemStack(ModItems.TANNERS_VAT.get()));
    }

    @Override
    public RecipeType<HideSize> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.immersive_tanning.tannery");
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, HideSize size, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 1, 1).addItemStacks(hides(size)).setBackground(slot, -1, -1);
        builder.addSlot(RecipeIngredientRole.CATALYST, 1, 23).addItemStack(new ItemStack(ModItems.TANNERS_KNIFE.get())).setBackground(slot, -1, -1);
        builder.addSlot(RecipeIngredientRole.INPUT, 43, 23).addItemStack(new ItemStack(Items.WATER_BUCKET)).setBackground(slot, -1, -1);
        List<ItemStack> tannin = new ArrayList<>();
        BuiltInRegistries.ITEM.getTagOrEmpty(ModTags.TANNIN_SOURCES).forEach(h -> tannin.add(new ItemStack(h.value(), size.tannin())));
        if (tannin.isEmpty()) tannin.add(new ItemStack(ModItems.BARK.get(), size.tannin()));
        builder.addSlot(RecipeIngredientRole.INPUT, 61, 23).addItemStacks(tannin).setBackground(slot, -1, -1);
        builder.addSlot(RecipeIngredientRole.OUTPUT, 43, 1).addItemStack(ModItems.SCRAPED_HIDE.get().create(size, 80, null)).setBackground(slot, -1, -1);
        builder.addSlot(RecipeIngredientRole.OUTPUT, 90, 1).addItemStack(ModItems.WET_TANNED_HIDE.get().create(size, 80, null)).setBackground(slot, -1, -1);
        List<ItemStack> leather = new ArrayList<>();
        for (Finish finish : Finish.values()) leather.add(new ItemStack(ModItems.leather(finish), size.leather()));
        builder.addSlot(RecipeIngredientRole.OUTPUT, WIDTH - 19, 1).addItemStacks(leather).setOutputSlotBackground()
                .addTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.immersive_tanning.finish_note").withStyle(ChatFormatting.GRAY)));
    }

    /** The raw hide of this size, then every foreign skin or pelt a hide mounting recipe turns into one. */
    private static List<ItemStack> hides(HideSize size) {
        List<ItemStack> hides = new ArrayList<>();
        hides.add(new ItemStack(ModItems.rawHide(size)));
        if (Minecraft.getInstance().level != null) {
            for (HideMountingRecipe recipe : Minecraft.getInstance().level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.HIDE_MOUNTING.get())) {
                if (recipe.size() == size) hides.addAll(List.of(recipe.ingredient().getItems()));
            }
        }
        return hides;
    }

    @Override
    public void draw(HideSize size, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        rack.draw(g, 20, 1);
        vat.draw(g, 68, 1);
        rack.draw(g, 110, 1);
        arrow.draw(g, 116, 22);
        Font font = Minecraft.getInstance().font;
        g.drawString(font, Component.translatable("jei.immersive_tanning.tannery_steps"), 0, 44, 0xFF404040, false);
        g.drawString(font, Component.translatable("jei.immersive_tanning.tannery_yield", size.leather()), 0, 54, 0xFF606060, false);
    }
}
