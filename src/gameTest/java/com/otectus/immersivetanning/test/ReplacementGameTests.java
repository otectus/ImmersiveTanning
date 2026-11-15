package com.otectus.immersivetanning.test;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.recipe.LeatherworkingRecipe;
import com.otectus.immersivetanning.recipe.TanningData;
import com.otectus.immersivetanning.registry.ModRecipeTypes;
import com.otectus.immersivetanning.registry.ModTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Bench recipes that name the crafting recipes they replace ({@code replaces}): only what they name, only when they can
 * be made and the named recipe makes the same item, and never past the switches and blacklists. The test datapack
 * stands vanilla diamond armor and prismarine in for Ice and Fire's armor and scales.
 */
@GameTestHolder(ImmersiveTanning.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ReplacementGameTests {
    private static final ResourceLocation STAND_IN = ImmersiveTanning.id("test/scale_chestplate");
    private static final ResourceLocation CHESTPLATE = new ResourceLocation("minecraft", "diamond_chestplate");

    @GameTest(template = "empty")
    public static void explicitReplacementTakesOnlyWhatItNames(GameTestHelper h) {
        TanningData data = TanningData.current();
        RecipeManager manager = h.getLevel().getServer().getRecipeManager();
        LeatherworkingRecipe stitched = data.recipe(STAND_IN);
        h.assertTrue(stitched != null && stitched.leather() == 2 && stitched.auxiliary().size() == 1
                && stitched.auxiliary().get(0).count() == 8 && stitched.auxiliary().get(0).ingredient().test(new ItemStack(Items.PRISMARINE_SHARD))
                && stitched.seam().equals(ImmersiveTanning.id("chestplate")) && stitched.replaces().equals(List.of(CHESTPLATE)),
                "The stand-in bench recipe loads with its leather, scales, seam and replaced recipe");
        h.assertTrue(manager.byKey(CHESTPLATE).isEmpty() && data.suppressedRecipes().get(CHESTPLATE) == Items.DIAMOND_CHESTPLATE,
                "The crafting recipe it names is disabled");
        h.assertTrue(manager.byKey(new ResourceLocation("minecraft", "diamond_helmet")).isPresent(), "Recipes nobody names stay");
        h.assertTrue(manager.byKey(new ResourceLocation("minecraft", "diamond_boots")).isPresent()
                && note(data, "Kept minecraft:diamond_boots (named by immersive_tanning:test/scale_mismatch): it makes minecraft:diamond_boots, not minecraft:diamond_helmet"),
                "A named recipe that makes something else stays, got " + data.notes());
        h.assertTrue(manager.byKey(new ResourceLocation("minecraft", "diamond_leggings")).isPresent()
                && note(data, "Kept the crafting recipes immersive_tanning:test/scale_unmakeable replaces: auxiliary 1 matches no item"),
                "A bench recipe that can never be made replaces nothing, got " + data.notes());
        h.assertTrue(data.recipe(ImmersiveTanning.id("leatherworking/saddle")).replaces().isEmpty()
                && data.recipe(ImmersiveTanning.id("leatherworking/leather_chestplate")) != null,
                "Bench recipes without replaces load as before");

        h.assertTrue(!ModList.get().isLoaded("iceandfire"), "Ice and Fire is not part of the test server");
        h.assertTrue(data.recipes().stream().noneMatch(r -> r.getId().getPath().startsWith("leatherworking/compat/iceandfire/"))
                && data.notes().stream().noneMatch(n -> n.contains("iceandfire")), "Without Ice and Fire its bench recipes do not load and leave no notes");
        Set<String> tagged = BuiltInRegistries.ITEM.getTag(ModTags.SCALE_BACKED)
                .map(set -> set.stream().map(holder -> holder.unwrapKey().orElseThrow().location().toString()).collect(Collectors.toSet())).orElse(Set.of());
        h.assertTrue(tagged.equals(Set.of("minecraft:diamond_helmet", "minecraft:diamond_chestplate", "minecraft:diamond_leggings", "minecraft:diamond_boots")),
                "The test datapack's scale_backed tag shadows the main one and its optional Ice and Fire entries resolve to nothing, got " + tagged);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void replacementFollowsTheSwitchesAndRebuildsCleanly(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        RecipeManager manager = server.getRecipeManager();
        boolean replace = ServerConfig.REPLACE_LEATHER_EQUIPMENT_RECIPES.get();
        List<? extends String> recipes = ServerConfig.RECIPE_BLACKLIST.get();
        List<? extends String> mods = ServerConfig.MOD_BLACKLIST.get();
        int benchRecipes = manager.getAllRecipesFor(ModRecipeTypes.LEATHERWORKING.get()).size();
        try {
            ServerConfig.REPLACE_LEATHER_EQUIPMENT_RECIPES.set(false);
            TanningData.rebuild(server);
            h.assertTrue(manager.byKey(CHESTPLATE).isPresent() && TanningData.current().recipe(STAND_IN) != null
                    && manager.byKey(new ResourceLocation("minecraft", "leather_chestplate")).isPresent(),
                    "With replacement off every crafting recipe returns and the bench recipes stay");

            ServerConfig.REPLACE_LEATHER_EQUIPMENT_RECIPES.set(true);
            ServerConfig.RECIPE_BLACKLIST.set(List.of(CHESTPLATE.toString()));
            TanningData.rebuild(server);
            h.assertTrue(manager.byKey(CHESTPLATE).isPresent() && TanningData.current().recipe(STAND_IN) != null
                    && note(TanningData.current(), "Kept minecraft:diamond_chestplate (named by immersive_tanning:test/scale_chestplate): it is in recipeBlacklist"),
                    "recipeBlacklist keeps a named recipe next to the bench recipe");

            ServerConfig.RECIPE_BLACKLIST.set(List.of());
            ServerConfig.MOD_BLACKLIST.set(List.of("minecraft"));
            TanningData.rebuild(server);
            h.assertTrue(manager.byKey(CHESTPLATE).isPresent() && TanningData.current().recipe(STAND_IN) == null,
                    "modBlacklist disables the bench recipes of the mod's items and keeps their crafting recipes");

            ServerConfig.MOD_BLACKLIST.set(List.of());
            TanningData.rebuild(server);
            TanningData.rebuild(server);
            h.assertTrue(manager.byKey(CHESTPLATE).isEmpty() && TanningData.current().suppressedRecipes().containsKey(CHESTPLATE),
                    "Back on, the named recipe is disabled again");
            h.assertTrue(TanningData.current().recipesProducing(Items.DIAMOND_CHESTPLATE).size() == 1
                    && manager.getAllRecipesFor(ModRecipeTypes.LEATHERWORKING.get()).size() == benchRecipes,
                    "Repeated rebuilds keep one bench recipe per piece and add nothing");
        } finally {
            ServerConfig.REPLACE_LEATHER_EQUIPMENT_RECIPES.set(replace);
            ServerConfig.RECIPE_BLACKLIST.set(recipes);
            ServerConfig.MOD_BLACKLIST.set(mods);
            TanningData.rebuild(server);
        }
        h.succeed();
    }

    /** Real datapack reloads (what /reload does), alone in their batch because a reload replaces every recipe. */
    @GameTest(template = "empty", batch = "datapack_reload")
    public static void datapackReloadsRestoreAndReapplyReplacement(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        boolean replace = ServerConfig.REPLACE_LEATHER_EQUIPMENT_RECIPES.get();
        try {
            ServerConfig.REPLACE_LEATHER_EQUIPMENT_RECIPES.set(false);
            reload(server);
            h.assertTrue(server.getRecipeManager().byKey(CHESTPLATE).isPresent() && TanningData.current().recipe(STAND_IN) != null,
                    "A reload with replacement off brings the crafting recipe back");

            ServerConfig.REPLACE_LEATHER_EQUIPMENT_RECIPES.set(true);
            reload(server);
            int benchRecipes = server.getRecipeManager().getAllRecipesFor(ModRecipeTypes.LEATHERWORKING.get()).size();
            h.assertTrue(server.getRecipeManager().byKey(CHESTPLATE).isEmpty() && TanningData.current().recipesProducing(Items.DIAMOND_CHESTPLATE).size() == 1,
                    "A reload with replacement on disables it again");

            reload(server);
            h.assertTrue(server.getRecipeManager().byKey(CHESTPLATE).isEmpty()
                    && server.getRecipeManager().getAllRecipesFor(ModRecipeTypes.LEATHERWORKING.get()).size() == benchRecipes
                    && TanningData.current().recipesProducing(Items.DIAMOND_CHESTPLATE).size() == 1,
                    "Another reload keeps the same recipes, without duplicates or stale entries");
        } finally {
            if (ServerConfig.REPLACE_LEATHER_EQUIPMENT_RECIPES.get() != replace) {
                ServerConfig.REPLACE_LEATHER_EQUIPMENT_RECIPES.set(replace);
                reload(server);
            }
        }
        h.succeed();
    }

    /** Reloads the selected datapacks and waits on the server thread, running its queued tasks, until it is applied. */
    private static void reload(MinecraftServer server) {
        CompletableFuture<Void> done = server.reloadResources(server.getPackRepository().getSelectedIds());
        server.managedBlock(done::isDone);
        done.join();
    }

    private static boolean note(TanningData data, String text) {
        return data.notes().contains(text);
    }

    private ReplacementGameTests() {}
}
