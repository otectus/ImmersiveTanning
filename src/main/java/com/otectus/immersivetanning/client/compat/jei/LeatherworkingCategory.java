package com.otectus.immersivetanning.client.compat.jei;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.client.screen.VanillaGui;
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

/**
 * A bench recipe in JEI's own vanilla style: leather of any finish (all of one finish) and the other components in
 * standard slots, the bench and JEI's recipe arrow, the piece in the output slot, and dark grey notes on the seam.
 * Everything is JEI's standard drawables on its own background; nothing here is themed.
 */
public class LeatherworkingCategory implements IRecipeCategory<LeatherworkingRecipe> {
    public static final RecipeType<LeatherworkingRecipe> TYPE = RecipeType.create(ImmersiveTanning.MOD_ID, "leatherworking", LeatherworkingRecipe.class);
    private static final int WIDTH = 150;
    private static final int HEIGHT = 66;
    private static final int GRID_Y = 5;
    private static final int PROCESS_X = 62;
    private static final int PROCESS_Y = 14;

    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawable slot;
    private final IDrawable outputSlot;
    private final IDrawable arrow;
    private final IDrawable bench;

    public LeatherworkingCategory(IGuiHelper helper) {
        this.background = helper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = helper.createDrawableItemStack(new ItemStack(ModItems.LEATHERWORKERS_BENCH.get()));
        this.slot = helper.getSlotDrawable();
        this.outputSlot = helper.getOutputSlot();
        this.arrow = helper.getRecipeArrow();
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
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, LeatherworkingRecipe recipe, IFocusGroup focuses) {
        List<ItemStack> leather = new ArrayList<>();
        for (Finish finish : Finish.values()) leather.add(new ItemStack(ModItems.leather(finish), recipe.leather()));
        builder.addSlot(RecipeIngredientRole.INPUT, 1, GRID_Y)
                .addItemStacks(leather)
                .setBackground(slot, -1, -1)
                .addTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.immersive_tanning.leather_note").withStyle(ChatFormatting.GRAY)));
        List<AuxiliaryIngredient> aux = recipe.auxiliary();
        for (int i = 0; i < Math.min(4, aux.size()); i++) {
            AuxiliaryIngredient a = aux.get(i);
            builder.addSlot(RecipeIngredientRole.INPUT, 1 + ((i + 1) % 3) * 18, GRID_Y + ((i + 1) / 3) * 18)
                    .addItemStacks(Arrays.stream(a.ingredient().getItems()).map(s -> s.copyWithCount(a.count())).toList())
                    .setBackground(slot, -1, -1);
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, outputX(), PROCESS_Y)
                .addItemStack(recipe.result())
                .setBackground(outputSlot, -5, -5)
                .addTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.immersive_tanning.quality_note").withStyle(ChatFormatting.GRAY)));
        builder.addInvisibleIngredients(RecipeIngredientRole.CATALYST).addItemStack(new ItemStack(ModItems.AWL.get()));
    }

    private int outputX() {
        return PROCESS_X + 20 + arrow.getWidth() + 9;
    }

    @Override
    public void draw(LeatherworkingRecipe recipe, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        bench.draw(g, PROCESS_X, PROCESS_Y);
        arrow.draw(g, PROCESS_X + 20, PROCESS_Y);
        Font font = Minecraft.getInstance().font;
        Component seam = Component.translatable("jei.immersive_tanning.seam",
                Component.translatableWithFallback("seam." + recipe.seam().getNamespace() + "." + recipe.seam().getPath(), recipe.seam().getPath()));
        if (recipe.isAuto()) seam = seam.copy().append(Component.translatable("jei.immersive_tanning.auto"));
        g.drawString(font, VanillaGui.clipped(font, seam, WIDTH), 0, 45, VanillaGui.LABEL, false);
        g.drawString(font, VanillaGui.clipped(font, Component.translatable("jei.immersive_tanning.stitch_note"), WIDTH), 0, 56, VanillaGui.LABEL_DIM, false);
    }

    @Override
    public List<Component> getTooltipStrings(LeatherworkingRecipe recipe, IRecipeSlotsView slots, double mouseX, double mouseY) {
        if (mouseX >= PROCESS_X && mouseX < PROCESS_X + 16 && mouseY >= PROCESS_Y && mouseY < PROCESS_Y + 16) {
            return List.of(new ItemStack(ModItems.LEATHERWORKERS_BENCH.get()).getHoverName());
        }
        return List.of();
    }
}
