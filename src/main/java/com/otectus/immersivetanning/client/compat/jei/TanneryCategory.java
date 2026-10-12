package com.otectus.immersivetanning.client.compat.jei;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.client.screen.VanillaGui;
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

/**
 * The tannery for one hide size in JEI's own vanilla style: hide, rack (flesh, knife below), scraped hide, vat (water
 * and bark below), wet tanned hide, rack (finish and dry), then JEI's recipe arrow to the leather in the output slot.
 * Everything is JEI's standard drawables on its own background; nothing here is themed.
 */
public class TanneryCategory implements IRecipeCategory<HideSize> {
    public static final RecipeType<HideSize> TYPE = RecipeType.create(ImmersiveTanning.MOD_ID, "tannery", HideSize.class);
    private static final int ROW_Y = 5;
    private static final int TOOLS_Y = 27;
    private static final int RACK_X = 22;
    private static final int VAT_X = 64;
    private static final int DRY_RACK_X = 106;
    private static final int ARROW_X = 125;
    private static final int HEIGHT = 80;

    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawable slot;
    private final IDrawable outputSlot;
    private final IDrawable arrow;
    private final IDrawable rack;
    private final IDrawable vat;
    private final int width;

    public TanneryCategory(IGuiHelper helper) {
        this.icon = helper.createDrawableItemStack(new ItemStack(ModItems.TANNERS_RACK.get()));
        this.slot = helper.getSlotDrawable();
        this.outputSlot = helper.getOutputSlot();
        this.arrow = helper.getRecipeArrow();
        this.width = outputX() + 21;
        this.background = helper.createBlankDrawable(width, HEIGHT);
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
        return width;
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
    public void setRecipe(IRecipeLayoutBuilder builder, HideSize size, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 1, ROW_Y).addItemStacks(hides(size)).setBackground(slot, -1, -1);
        builder.addSlot(RecipeIngredientRole.CATALYST, RACK_X, TOOLS_Y).addItemStack(new ItemStack(ModItems.TANNERS_KNIFE.get())).setBackground(slot, -1, -1);
        builder.addSlot(RecipeIngredientRole.INPUT, VAT_X - 9, TOOLS_Y).addItemStack(new ItemStack(Items.WATER_BUCKET)).setBackground(slot, -1, -1);
        List<ItemStack> tannin = new ArrayList<>();
        BuiltInRegistries.ITEM.getTagOrEmpty(ModTags.TANNIN_SOURCES).forEach(h -> tannin.add(new ItemStack(h.value(), size.tannin())));
        if (tannin.isEmpty()) tannin.add(new ItemStack(ModItems.BARK.get(), size.tannin()));
        builder.addSlot(RecipeIngredientRole.INPUT, VAT_X + 9, TOOLS_Y).addItemStacks(tannin).setBackground(slot, -1, -1);
        builder.addSlot(RecipeIngredientRole.OUTPUT, 43, ROW_Y).addItemStack(ModItems.SCRAPED_HIDE.get().create(size, 80, null)).setBackground(slot, -1, -1);
        builder.addSlot(RecipeIngredientRole.OUTPUT, 85, ROW_Y).addItemStack(ModItems.WET_TANNED_HIDE.get().create(size, 80, null)).setBackground(slot, -1, -1);
        List<ItemStack> leather = new ArrayList<>();
        for (Finish finish : Finish.values()) leather.add(new ItemStack(ModItems.leather(finish), size.leather()));
        builder.addSlot(RecipeIngredientRole.OUTPUT, outputX(), ROW_Y).addItemStacks(leather).setBackground(outputSlot, -5, -5)
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

    private int outputX() {
        return ARROW_X + arrow.getWidth() + 9;
    }

    @Override
    public void draw(HideSize size, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        rack.draw(g, RACK_X, ROW_Y);
        vat.draw(g, VAT_X, ROW_Y);
        rack.draw(g, DRY_RACK_X, ROW_Y);
        arrow.draw(g, ARROW_X, ROW_Y);
        Font font = Minecraft.getInstance().font;
        g.drawString(font, VanillaGui.clipped(font, Component.translatable("jei.immersive_tanning.tannery_steps"), width), 0, 49, VanillaGui.LABEL, false);
        VanillaGui.wrapped(g, font, Component.translatable("jei.immersive_tanning.tannery_yield", size.leather()), 0, 60, width, VanillaGui.LABEL_DIM, 2);
    }

    @Override
    public List<Component> getTooltipStrings(HideSize size, IRecipeSlotsView slots, double mouseX, double mouseY) {
        if (mouseY < ROW_Y || mouseY >= ROW_Y + 16) return List.of();
        if ((mouseX >= RACK_X && mouseX < RACK_X + 16) || (mouseX >= DRY_RACK_X && mouseX < DRY_RACK_X + 16)) {
            return List.of(new ItemStack(ModItems.TANNERS_RACK.get()).getHoverName());
        }
        if (mouseX >= VAT_X && mouseX < VAT_X + 16) return List.of(new ItemStack(ModItems.TANNERS_VAT.get()).getHoverName());
        return List.of();
    }
}
