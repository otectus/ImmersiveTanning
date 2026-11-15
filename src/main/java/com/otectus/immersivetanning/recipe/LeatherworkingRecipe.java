package com.otectus.immersivetanning.recipe;

import com.otectus.immersivetanning.minigame.StitchPattern;
import com.otectus.immersivetanning.work.WorkPattern;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.registry.ModRecipeSerializers;
import com.otectus.immersivetanning.registry.ModRecipeTypes;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One piece the Leatherworker's Bench can make: how many units of finished leather (all of one finish and trait),
 * which other ingredients, and which seam is stitched. Explicit recipes come from datapacks; automatic ones are
 * generated from crafting recipes at reload and inserted into the recipe manager, so clients and JEI see them too.
 * An explicit recipe may name the crafting recipes it {@link #replaces()}: they are disabled while it is active and can
 * be made (see TanningData). That list stays on the server; the network format is unchanged.
 */
public class LeatherworkingRecipe implements Recipe<Container> {
    private final ResourceLocation id;
    private final int leather;
    private final List<AuxiliaryIngredient> auxiliary;
    private final ItemStack result;
    @Nullable
    private final ResourceLocation seam;
    private final boolean auto;
    @Nullable
    private final ResourceLocation sourceRecipe;
    private final List<ResourceLocation> replaces;
    /** 1.3: an explicit work pattern (cuts, panels, seams); null resolves through the seam. */
    @Nullable
    private ResourceLocation workPattern;
    /** Filled in on the server during rebuild and carried to clients. */
    private ResourceLocation resolvedSeam = StitchPattern.GENERIC;
    private ResourceLocation resolvedPattern = WorkPattern.GENERIC;
    private int patternCuts = 2;
    private int patternPanels = 2;
    private int patternStitches = 5;

    public LeatherworkingRecipe(ResourceLocation id, int leather, List<AuxiliaryIngredient> auxiliary, ItemStack result,
                                @Nullable ResourceLocation seam, boolean auto, @Nullable ResourceLocation sourceRecipe) {
        this(id, leather, auxiliary, result, seam, auto, sourceRecipe, List.of());
    }

    public LeatherworkingRecipe(ResourceLocation id, int leather, List<AuxiliaryIngredient> auxiliary, ItemStack result,
                                @Nullable ResourceLocation seam, boolean auto, @Nullable ResourceLocation sourceRecipe,
                                List<ResourceLocation> replaces) {
        this.id = id;
        this.leather = leather;
        this.auxiliary = List.copyOf(auxiliary);
        this.result = result;
        this.seam = seam;
        this.auto = auto;
        this.sourceRecipe = sourceRecipe;
        this.replaces = List.copyOf(replaces);
        if (seam != null) resolvedSeam = seam;
    }

    /** The explicit {@code work_pattern} of the recipe, if it names one. */
    @Nullable public ResourceLocation workPattern() { return workPattern; }
    public void setWorkPattern(@Nullable ResourceLocation id) { this.workPattern = id; }
    /** The work pattern this recipe resolved to at the last rebuild, and its size (for JEI and the pattern book). */
    public ResourceLocation resolvedPattern() { return resolvedPattern; }
    public int patternCuts() { return patternCuts; }
    public int patternPanels() { return patternPanels; }
    public int patternStitches() { return patternStitches; }

    public void resolvePattern(WorkPattern pattern) {
        this.resolvedPattern = pattern.id();
        this.patternCuts = pattern.cuts().size();
        this.patternPanels = pattern.panels().size();
        this.patternStitches = pattern.totalActions();
    }

    public void resolvePattern(ResourceLocation id, int cuts, int panels, int stitches) {
        this.resolvedPattern = id;
        this.patternCuts = cuts;
        this.patternPanels = panels;
        this.patternStitches = stitches;
    }

    public int leather() { return leather; }
    public List<AuxiliaryIngredient> auxiliary() { return auxiliary; }
    public ItemStack result() { return result; }
    @Nullable public ResourceLocation explicitSeam() { return seam; }
    public boolean isAuto() { return auto; }
    @Nullable public ResourceLocation sourceRecipe() { return sourceRecipe; }
    /** Crafting recipe ids this recipe replaces (server side; empty on clients). */
    public List<ResourceLocation> replaces() { return replaces; }
    public ResourceLocation seam() { return resolvedSeam; }
    public Item resultItem() { return result.getItem(); }

    public void resolveSeam(ResourceLocation seam) {
        this.resolvedSeam = seam;
    }

    @Override
    public boolean matches(Container container, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess access) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess access) {
        return result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        auxiliary.forEach(a -> list.add(a.ingredient()));
        return list;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public boolean isIncomplete() {
        return false;
    }

    @Override
    public ItemStack getToastSymbol() {
        return new ItemStack(ModItems.LEATHERWORKERS_BENCH.get());
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.LEATHERWORKING.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipeTypes.LEATHERWORKING.get();
    }
}
