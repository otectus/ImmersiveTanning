package com.otectus.immersivetanning.client.compat.jei;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.client.screen.StonebornGui;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.recipe.AuxiliaryIngredient;
import com.otectus.immersivetanning.recipe.LeatherworkingRecipe;
import com.otectus.immersivetanning.registry.ModItems;
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
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** A bench recipe: leather of any finish (all of one finish), the other components, the seam, the piece. */
public class LeatherworkingCategory implements IRecipeCategory<LeatherworkingRecipe> {
    public static final RecipeType<LeatherworkingRecipe> TYPE = RecipeType.create(ImmersiveTanning.MOD_ID, "leatherworking", LeatherworkingRecipe.class);
    private static final int WIDTH = 150;
    private static final int HEIGHT = 70;

    private final IDrawable icon;
    private final IDrawable slot;
    private final IDrawable arrow;
    private final IDrawable bench;

    public LeatherworkingCategory(IGuiHelper helper) {
        this.icon = helper.createDrawableItemStack(new ItemStack(ModItems.LEATHERWORKERS_BENCH.get()));
        this.slot = StonebornJei.slot();
        this.arrow = StonebornJei.arrow();
        this.bench = helper.createDrawableItemStack(new ItemStack(ModItems.LEATHERWORKERS_BENCH.get()));
    }

    @Override
    public RecipeType<LeatherworkingRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.immersive_tanning.leatherworking");
    }

    @Override
    public int getWidth() {
        return WIDTH + 12;
    }

    @Override
    public int getHeight() {
        return HEIGHT + 12;
    }

    @Override
    public IDrawable getBackground() { return StonebornJei.background(getWidth(), getHeight()); }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, LeatherworkingRecipe recipe, IFocusGroup focuses) {
        List<ItemStack> leather = new ArrayList<>();
        for (Finish finish : Finish.values()) leather.add(new ItemStack(ModItems.leather(finish), recipe.leather()));
        builder.addSlot(RecipeIngredientRole.INPUT, 6 + (1), 6 + (1))
                .addItemStacks(leather)
                .setBackground(slot, -1, -1)
                .addTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.immersive_tanning.leather_note").withStyle(ChatFormatting.GRAY)));
        List<AuxiliaryIngredient> aux = recipe.auxiliary();
        for (int i = 0; i < Math.min(4, aux.size()); i++) {
            AuxiliaryIngredient a = aux.get(i);
            builder.addSlot(RecipeIngredientRole.INPUT, 6 + (1 + ((i + 1) % 3) * 18), 6 + (1 + ((i + 1) / 3) * 18))
                    .addItemStacks(Arrays.stream(a.ingredient().getItems()).map(s -> s.copyWithCount(a.count())).toList())
                    .setBackground(slot, -1, -1);
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, 6 + (WIDTH - 19), 6 + (10))
                .addItemStack(recipe.result())
                .setBackground(slot, -1, -1)
                .addTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.immersive_tanning.quality_note").withStyle(ChatFormatting.GRAY)));
        builder.addInvisibleIngredients(RecipeIngredientRole.CATALYST).addItemStack(new ItemStack(ModItems.AWL.get()));
    }

    @Override
    public void draw(LeatherworkingRecipe recipe, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        g.pose().pushPose();
        g.pose().translate(6, 6, 0);
        bench.draw(g, 64, 10);
        arrow.draw(g, 84, 10);
        Font font = Minecraft.getInstance().font;
        Component seam = Component.translatable("jei.immersive_tanning.seam",
                Component.translatableWithFallback("seam." + recipe.seam().getNamespace() + "." + recipe.seam().getPath(), recipe.seam().getPath()));
        if (recipe.isAuto()) seam = seam.copy().append(Component.translatable("jei.immersive_tanning.auto"));
        g.drawString(font, trim(font, seam, WIDTH), 0, 42, StonebornGui.TEXT, false);
        g.drawString(font, trim(font, Component.translatable("jei.immersive_tanning.stitch_note"), WIDTH), 0, 54, StonebornGui.TEXT_DIM, false);
            g.pose().popPose();
    }

    private static String trim(Font font, Component text, int width) {
        if (font.width(text) <= width) return text.getString();
        return font.plainSubstrByWidth(text.getString(), width - font.width("...")) + "...";
    }
}
