package com.otectus.immersivetanning.recipe;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.compat.geneticanimals.GeneticAnimals;
import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.leather.BindingLeather;
import com.otectus.immersivetanning.minigame.SeamPatterns;
import com.otectus.immersivetanning.work.WorkPatterns;
import com.otectus.immersivetanning.registry.ModTags;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable server-side snapshot of the active leatherworking recipes and the crafting recipes they replaced. Rebuilt
 * after every datapack reload once tags are bound (never during gameplay); gameplay code only reads {@link #current()}.
 *
 * <p>Automatic detection is conservative: a crafting recipe is routed through the bench only when its unstackable
 * result is leather armor or in {@code #immersive_tanning:leatherwork_equipment}, at least one ingredient is wholly
 * leather, and no ingredient is itself equipment. Everything else is left alone.</p>
 *
 * <p>Explicit replacement is narrower still: a bench recipe that names crafting recipes in {@code replaces} disables
 * each one only while replacement is enabled, the bench recipe is active and can be made (every auxiliary matches an
 * item), the named recipe is a loaded crafting recipe making the same item, and neither blacklist protects it. Every
 * refusal is a note in {@code /immersive_tanning report}.</p>
 *
 * <p>Each rebuild starts from the recipes as the datapacks loaded them, so a rebuild without a reload in between
 * recomputes the same result, and a reload (which brings fresh recipes) leaves nothing stale behind.</p>
 *
 * <p>Genetic Animals tack goes to the bench only while {@code compat.geneticAnimals} allows it; otherwise its bench
 * recipes are dropped and nothing of it is detected, so its crafting recipes stay.</p>
 */
public final class TanningData {
    private static final TanningData EMPTY = new TanningData(List.of(), Map.of(), List.of(), 0);
    private static volatile TanningData current = EMPTY;
    private static volatile boolean dirty = true;
    /** Counts datapack loads (see {@link #reloadMarker()}); the loaded recipes are kept once per load. */
    private static volatile int reloadGeneration;
    private static int pristineGeneration = -1;
    @Nullable
    private static List<Recipe<?>> pristine;

    private final Map<ResourceLocation, LeatherworkingRecipe> byId;
    private final Map<Item, List<LeatherworkingRecipe>> byResult;
    private final Map<ResourceLocation, Item> suppressed;
    private final List<String> notes;
    private final int bindingResults;

    private TanningData(List<LeatherworkingRecipe> recipes, Map<ResourceLocation, Item> suppressed, List<String> notes, int bindingResults) {
        Map<ResourceLocation, LeatherworkingRecipe> ids = new LinkedHashMap<>();
        Map<Item, List<LeatherworkingRecipe>> results = new HashMap<>();
        for (LeatherworkingRecipe r : recipes) {
            ids.put(r.getId(), r);
            results.computeIfAbsent(r.resultItem(), k -> new ArrayList<>()).add(r);
        }
        results.replaceAll((k, v) -> List.copyOf(v));
        this.byId = Map.copyOf(ids);
        this.byResult = Map.copyOf(results);
        this.suppressed = Map.copyOf(suppressed);
        this.notes = List.copyOf(notes);
        this.bindingResults = bindingResults;
    }

    public static TanningData current() {
        return current;
    }

    public static boolean isDirty() {
        return dirty;
    }

    @Nullable
    public LeatherworkingRecipe recipe(ResourceLocation id) { return byId.get(id); }
    public Collection<LeatherworkingRecipe> recipes() { return byId.values(); }
    public List<LeatherworkingRecipe> recipesProducing(Item item) { return byResult.getOrDefault(item, List.of()); }
    /** Disabled crafting recipe ids and the item each one produced. */
    public Map<ResourceLocation, Item> suppressedRecipes() { return suppressed; }
    public List<String> notes() { return notes; }
    /** Recipes that made vanilla leather and now make Binding Leather. */
    public int bindingResults() { return bindingResults; }

    public String summary() {
        long auto = byId.values().stream().filter(LeatherworkingRecipe::isAuto).count();
        return (byId.size() - auto) + " explicit and " + auto + " automatic leatherworking recipes, "
                + suppressed.size() + " crafting recipes routed through the bench, "
                + bindingResults + " leather recipes making Binding Leather";
    }

    /**
     * A reload listener that only counts datapack loads, so the next rebuild takes its recipes fresh from the reloaded
     * recipe manager rather than from the copy kept since the last load.
     */
    public static PreparableReloadListener reloadMarker() {
        return (ResourceManagerReloadListener) resources -> reloadGeneration++;
    }

    /** Rebuilds from the recipes the datapacks loaded and the current tags and config, then rewrites the recipe manager. */
    public static synchronized void rebuild(MinecraftServer server) {
        long start = System.nanoTime();
        RecipeManager manager = server.getRecipeManager();
        RegistryAccess access = server.registryAccess();
        List<String> notes = new ArrayList<>();
        Set<String> recipeBlacklist = new HashSet<>(ServerConfig.list(ServerConfig.RECIPE_BLACKLIST));
        Set<String> modBlacklist = new HashSet<>(ServerConfig.list(ServerConfig.MOD_BLACKLIST));
        if (pristine == null || pristineGeneration != reloadGeneration) {
            pristine = List.copyOf(manager.getRecipes());
            pristineGeneration = reloadGeneration;
        }

        // 0. Recipes that make vanilla leather make Binding Leather instead (ingredients are swapped by IngredientMixin).
        List<Recipe<?>> all = new ArrayList<>(pristine);
        int bindingResults = 0;
        for (int i = 0; i < all.size(); i++) {
            Recipe<?> r = all.get(i);
            ItemStack result;
            try {
                result = r.getResultItem(access);
            } catch (RuntimeException e) {
                continue;
            }
            if (result == null || !result.is(Items.LEATHER)) continue;
            Recipe<?> rebuilt = withBindingResult(r, BindingLeather.replace(result));
            if (rebuilt != null) {
                all.set(i, rebuilt);
                bindingResults++;
            } else {
                notes.add("Recipe " + r.getId() + " (" + r.getClass().getSimpleName() + ") makes vanilla leather and could not be converted");
            }
        }

        // 1. Explicit recipes; stale automatic recipes from an earlier pass are dropped.
        Set<ResourceLocation> removed = new HashSet<>();
        List<LeatherworkingRecipe> active = new ArrayList<>();
        Set<Item> explicitResults = new HashSet<>();
        for (Recipe<?> r : all) {
            if (!(r instanceof LeatherworkingRecipe lr)) continue;
            if (lr.isAuto()) {
                removed.add(lr.getId());
            } else if (lr.result().is(ModTags.NON_LEATHERWORK_EQUIPMENT) || modBlacklist.contains(namespace(lr.resultItem()))) {
                removed.add(lr.getId());
                notes.add("Disabled " + lr.getId() + ": result is excluded from leatherworking");
            } else if (GeneticAnimals.owns(lr.resultItem()) && !GeneticAnimals.tackAtBench()) {
                removed.add(lr.getId());
                notes.add("Disabled " + lr.getId() + ": Genetic Animals tack keeps its own recipes (compat.geneticAnimals)");
            } else {
                active.add(lr);
                explicitResults.add(lr.resultItem());
            }
        }

        // 2. Automatic recipes from crafting recipes of leather equipment.
        Map<ResourceLocation, Recipe<?>> loaded = new HashMap<>();
        List<CraftingRecipe> crafting = new ArrayList<>();
        for (Recipe<?> r : all) {
            loaded.put(r.getId(), r);
            if (r instanceof CraftingRecipe c && r.getType() == RecipeType.CRAFTING) crafting.add(c);
        }
        crafting.sort(Comparator.comparing(r -> r.getId().toString()));
        Map<ResourceLocation, Integer> leatherSlotsByRecipe = new HashMap<>();
        List<LeatherworkingRecipe> auto = new ArrayList<>();
        Set<Item> autoResults = new HashSet<>();
        for (CraftingRecipe recipe : crafting) {
            try {
                if (recipe.isSpecial()) continue;
                ItemStack result = recipe.getResultItem(access);
                if (result.isEmpty()) continue;
                int leatherSlots = countLeather(recipe.getIngredients());
                if (leatherSlots > 0) leatherSlotsByRecipe.put(recipe.getId(), leatherSlots);
                if (!ServerConfig.get(ServerConfig.AUTO_DETECT_LEATHER_EQUIPMENT) || leatherSlots == 0) continue;
                if (explicitResults.contains(result.getItem()) || autoResults.contains(result.getItem())) continue;
                if (recipeBlacklist.contains(recipe.getId().toString()) || modBlacklist.contains(namespace(result.getItem()))) continue;
                if (GeneticAnimals.owns(result.getItem()) && !GeneticAnimals.tackAtBench()) continue;
                LeatherworkingRecipe detected = detect(recipe, result, notes);
                if (detected == null) continue;
                auto.add(detected);
                autoResults.add(result.getItem());
            } catch (RuntimeException e) {
                ImmersiveTanning.LOGGER.debug("Could not analyse recipe {}: {}", recipe.getId(), e.toString());
            }
        }
        active.addAll(auto);

        // 3. Seams and work patterns travel with each recipe to the client. A pattern that cannot be used is one
        // warning per recipe and reload, naming what it falls back to; the recipe itself always stays usable.
        for (LeatherworkingRecipe r : active) {
            ResourceLocation seam = r.explicitSeam() != null ? r.explicitSeam() : EquipmentSeams.classify(r.result());
            r.resolveSeam(seam);
            WorkPatterns.Resolution resolution = WorkPatterns.resolve(r.workPattern(), seam);
            if (resolution.note() != null) {
                ImmersiveTanning.LOGGER.warn("Immersive Tanning: bench recipe {}: {}; it is worked with {} instead", r.getId(),
                        resolution.note(), resolution.pattern().id());
            }
            if (resolution.source() == WorkPatterns.Source.GENERIC && !SeamPatterns.has(seam)) {
                notes.add("No work pattern or seam " + seam + " for " + r.getId() + "; the generic wrap is used");
            }
            r.resolvePattern(resolution.pattern());
        }

        // 4. Disable crafting recipes of leather results the bench now makes.
        Map<ResourceLocation, Item> suppressed = new HashMap<>();
        if (ServerConfig.get(ServerConfig.REPLACE_LEATHER_EQUIPMENT_RECIPES)) {
            Set<Item> benchResults = new HashSet<>();
            active.forEach(r -> benchResults.add(r.resultItem()));
            for (CraftingRecipe recipe : crafting) {
                if (recipeBlacklist.contains(recipe.getId().toString()) || !leatherSlotsByRecipe.containsKey(recipe.getId())) continue;
                ItemStack result;
                try {
                    result = recipe.getResultItem(access);
                } catch (RuntimeException e) {
                    continue;
                }
                if (result.isEmpty() || !benchResults.contains(result.getItem())) continue;
                if (modBlacklist.contains(namespace(result.getItem()))) continue;
                suppressed.put(recipe.getId(), result.getItem());
            }
            // 4b. Crafting recipes an active bench recipe names in "replaces".
            for (LeatherworkingRecipe r : active) {
                if (r.replaces().isEmpty()) continue;
                String unmakeable = unmakeable(r);
                if (unmakeable != null) {
                    notes.add("Kept the crafting recipes " + r.getId() + " replaces: " + unmakeable);
                    continue;
                }
                for (ResourceLocation id : r.replaces()) {
                    String refusal = replacementRefusal(r, id, loaded.get(id), recipeBlacklist, modBlacklist, access);
                    if (refusal != null) notes.add("Kept " + id + " (named by " + r.getId() + "): " + refusal);
                    else suppressed.put(id, r.resultItem());
                }
            }
        }

        // 5. Rewrite the recipe manager: remove disabled and stale entries, add the automatic recipes.
        List<Recipe<?>> next = new ArrayList<>(all.size() + auto.size());
        for (Recipe<?> r : all) {
            if (!removed.contains(r.getId()) && !suppressed.containsKey(r.getId())) next.add(r);
        }
        next.addAll(auto);
        manager.replaceRecipes(next);

        TanningData data = new TanningData(active, suppressed, notes, bindingResults);
        current = data;
        dirty = false;
        notes.forEach(n -> ImmersiveTanning.LOGGER.debug("Immersive Tanning: {}", n));
        ImmersiveTanning.LOGGER.info("Immersive Tanning: {} ({} ms)", data.summary(), (System.nanoTime() - start) / 1_000_000);
    }

    /** Why the bench recipe can never be made (an auxiliary that matches no item), or null when it can. */
    @Nullable
    static String unmakeable(LeatherworkingRecipe recipe) {
        for (int i = 0; i < recipe.auxiliary().size(); i++) {
            boolean any = false;
            // Forge stands an empty tag in as a barrier named "Empty Tag", which no recipe means to ask for.
            for (ItemStack s : recipe.auxiliary().get(i).ingredient().getItems()) any |= !s.isEmpty() && !s.is(Items.BARRIER);
            if (!any) return "auxiliary " + (i + 1) + " matches no item";
        }
        return null;
    }

    /** Why the named recipe stays, or null when the bench recipe may replace it. */
    @Nullable
    static String replacementRefusal(LeatherworkingRecipe bench, ResourceLocation id, @Nullable Recipe<?> named,
                                     Set<String> recipeBlacklist, Set<String> modBlacklist, RegistryAccess access) {
        if (recipeBlacklist.contains(id.toString())) return "it is in recipeBlacklist";
        if (!(named instanceof CraftingRecipe) || named.getType() != RecipeType.CRAFTING) {
            return named == null ? "no such recipe is loaded" : "it is not a crafting recipe";
        }
        ItemStack made;
        try {
            made = named.getResultItem(access);
        } catch (RuntimeException e) {
            return "its result cannot be read (" + e + ")";
        }
        if (made == null || made.isEmpty() || made.getItem() != bench.resultItem()) {
            return "it makes " + (made == null || made.isEmpty() ? "nothing" : BuiltInRegistries.ITEM.getKey(made.getItem()))
                    + ", not " + BuiltInRegistries.ITEM.getKey(bench.resultItem());
        }
        if (modBlacklist.contains(namespace(made.getItem()))) return "its mod is in modBlacklist";
        return null;
    }

    /**
     * The automatic bench recipe for one crafting recipe, or null when it is not leather equipment: the unstackable
     * result must be leather armor or tagged leatherwork equipment, at least one ingredient must be wholly leather, and
     * no ingredient may itself be equipment. Leather ingredients become the leather count, the rest auxiliaries.
     */
    @Nullable
    public static LeatherworkingRecipe detect(CraftingRecipe recipe, ItemStack result, List<String> notes) {
        if (!isCandidate(result)) return null;
        int leatherSlots = countLeather(recipe.getIngredients());
        if (leatherSlots == 0) return null;
        List<AuxiliaryIngredient> aux = auxiliaries(recipe.getIngredients());
        if (aux == null) {
            notes.add("Skipped " + recipe.getId() + ": uses equipment as an ingredient");
            return null;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(result.getItem());
        ResourceLocation id = ImmersiveTanning.id("auto/" + itemId.getNamespace() + "/" + itemId.getPath());
        return new LeatherworkingRecipe(id, leatherSlots, aux, result.copyWithCount(1), null, true, recipe.getId());
    }

    /**
     * The same recipe with another result, for the vanilla recipe classes whose fields are all readable. Subclasses and
     * other recipe types are left alone (null), since they may carry behaviour a copy would lose.
     */
    @Nullable
    static Recipe<?> withBindingResult(Recipe<?> r, ItemStack result) {
        Class<?> type = r.getClass();
        if (type == ShapedRecipe.class) {
            ShapedRecipe s = (ShapedRecipe) r;
            return new ShapedRecipe(s.getId(), s.getGroup(), s.category(), s.getWidth(), s.getHeight(), s.getIngredients(), result, s.showNotification());
        }
        if (type == ShapelessRecipe.class) {
            ShapelessRecipe s = (ShapelessRecipe) r;
            return new ShapelessRecipe(s.getId(), s.getGroup(), s.category(), result, s.getIngredients());
        }
        if (r instanceof AbstractCookingRecipe c && !c.getIngredients().isEmpty()) {
            Ingredient in = c.getIngredients().get(0);
            if (type == SmeltingRecipe.class) return new SmeltingRecipe(c.getId(), c.getGroup(), c.category(), in, result, c.getExperience(), c.getCookingTime());
            if (type == BlastingRecipe.class) return new BlastingRecipe(c.getId(), c.getGroup(), c.category(), in, result, c.getExperience(), c.getCookingTime());
            if (type == SmokingRecipe.class) return new SmokingRecipe(c.getId(), c.getGroup(), c.category(), in, result, c.getExperience(), c.getCookingTime());
            if (type == CampfireCookingRecipe.class) return new CampfireCookingRecipe(c.getId(), c.getGroup(), c.category(), in, result, c.getExperience(), c.getCookingTime());
        }
        if (type == StonecutterRecipe.class && !r.getIngredients().isEmpty()) {
            return new StonecutterRecipe(r.getId(), r.getGroup(), r.getIngredients().get(0), result);
        }
        return null;
    }

    static boolean isCandidate(ItemStack result) {
        if (result.getMaxStackSize() != 1 || result.is(ModTags.NON_LEATHERWORK_EQUIPMENT)) return false;
        return result.is(ModTags.LEATHERWORK_EQUIPMENT) || EquipmentSeams.isLeatherArmor(result);
    }

    /** Number of ingredients that accept only leather. */
    static int countLeather(List<Ingredient> ingredients) {
        int count = 0;
        for (Ingredient ingredient : ingredients) {
            if (isLeather(ingredient)) count++;
        }
        return count;
    }

    static boolean isLeather(Ingredient ingredient) {
        if (ingredient.isEmpty()) return false;
        ItemStack[] items = ingredient.getItems();
        if (items.length == 0) return false;
        for (ItemStack s : items) {
            if (!s.is(ModTags.LEATHER_INGREDIENTS)) return false;
        }
        return true;
    }

    /**
     * The non-leather ingredients grouped by identity, or null if one of them is equipment (every item it accepts is
     * damageable: an upgrade of an existing piece). A tag that merely contains a damageable item among others, such as
     * a modpack's {@code #minecraft:arrows}, is not equipment.
     */
    @Nullable
    static List<AuxiliaryIngredient> auxiliaries(List<Ingredient> ingredients) {
        Map<String, Ingredient> byKey = new LinkedHashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty() || isLeather(ingredient)) continue;
            ItemStack[] items = ingredient.getItems();
            if (items.length == 0) return null;
            boolean equipment = true;
            StringBuilder key = new StringBuilder();
            for (ItemStack s : items) {
                equipment &= s.isDamageableItem();
                key.append(BuiltInRegistries.ITEM.getKey(s.getItem())).append(',');
            }
            if (equipment) return null;
            byKey.putIfAbsent(key.toString(), ingredient);
            counts.merge(key.toString(), 1, Integer::sum);
        }
        List<AuxiliaryIngredient> out = new ArrayList<>();
        byKey.forEach((k, ingredient) -> out.add(new AuxiliaryIngredient(ingredient, counts.get(k))));
        return out;
    }

    private static String namespace(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).getNamespace();
    }

    /** Clears state when the server stops, so a later world starts clean. */
    public static synchronized void reset() {
        current = EMPTY;
        dirty = true;
        pristine = null;
        pristineGeneration = -1;
    }
}
