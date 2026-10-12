package com.otectus.immersivetanning.test;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.api.ILeatherworkRecipeGate;
import com.otectus.immersivetanning.api.ITanningAssistSource;
import com.otectus.immersivetanning.api.ImmersiveTanningAPI;
import com.otectus.immersivetanning.api.event.HideFleshedEvent;
import com.otectus.immersivetanning.api.event.LeatherworkStitchedEvent;
import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.hide.HideData;
import com.otectus.immersivetanning.hide.HideSize;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.recipe.LeatherworkingRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/** The 1.1.0 progression hooks: recipe gates, fleshing assistance and the two events' mutable scores. */
@GameTestHolder(ImmersiveTanning.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ApiGameTests {

    private static LeatherworkingRecipe recipe() {
        return new LeatherworkingRecipe(ImmersiveTanning.id("test/api_tunic"), 5, List.of(), new ItemStack(Items.LEATHER_CHESTPLATE),
                null, false, null);
    }

    @GameTest(template = "empty")
    public static void recipeGatesFirstDenialWinsAndThrowingGatesAreSkipped(GameTestHelper h) {
        FakePlayer player = TestSupport.player(h);
        LeatherworkingRecipe recipe = recipe();
        ILeatherworkRecipeGate broken = (p, r) -> { throw new IllegalStateException("broken gate"); };
        ILeatherworkRecipeGate allow = (p, r) -> Optional.empty();
        ILeatherworkRecipeGate first = (p, r) -> Optional.of(Component.literal("first"));
        ILeatherworkRecipeGate second = (p, r) -> Optional.of(Component.literal("second"));
        try {
            h.assertTrue(ImmersiveTanningAPI.recipeDenial(player, recipe).isEmpty(), "No gates: allowed");
            ImmersiveTanningAPI.registerRecipeGate(broken);
            ImmersiveTanningAPI.registerRecipeGate(allow);
            h.assertTrue(ImmersiveTanningAPI.recipeDenial(player, recipe).isEmpty(), "A throwing gate is skipped, an allowing one allows");
            ImmersiveTanningAPI.registerRecipeGate(first);
            ImmersiveTanningAPI.registerRecipeGate(second);
            Optional<Component> reason = ImmersiveTanningAPI.recipeDenial(player, recipe);
            h.assertTrue(reason.isPresent() && "first".equals(reason.get().getString()), "The first registered denial is the reason");
        } finally {
            for (ILeatherworkRecipeGate g : List.of(broken, allow, first, second)) ImmersiveTanningAPI.unregisterRecipeGate(g);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fleshingAssistanceOnlyLengthensAndStopsAtTheCeiling(GameTestHelper h) {
        FakePlayer player = TestSupport.player(h);
        int base = 25_000;
        double ceiling = ServerConfig.get(ServerConfig.MAX_ASSIST_TIME_MULTIPLIER);
        ITanningAssistSource shorter = p -> 0.5;
        ITanningAssistSource longer = p -> 1.2;
        ITanningAssistSource huge = p -> 10.0;
        ITanningAssistSource broken = p -> { throw new IllegalStateException("broken source"); };
        try {
            h.assertTrue(ImmersiveTanningAPI.assistedFleshingMs(player, base) == base, "No sources: the base time");
            ImmersiveTanningAPI.registerAssistSource(shorter);
            h.assertTrue(ImmersiveTanningAPI.assistedFleshingMs(player, base) == base, "Assistance never shortens a session");
            ImmersiveTanningAPI.unregisterAssistSource(shorter);
            ImmersiveTanningAPI.registerAssistSource(longer);
            h.assertTrue(ImmersiveTanningAPI.assistedFleshingMs(player, base) == 30_000, "1.2 lengthens by a fifth");
            ImmersiveTanningAPI.registerAssistSource(huge);
            ImmersiveTanningAPI.registerAssistSource(broken);
            h.assertTrue(ImmersiveTanningAPI.assistedFleshingMs(player, base) == Math.round(base * ceiling),
                    "The product stops at maxAssistTimeMultiplier and a throwing source counts as 1");
        } finally {
            for (ITanningAssistSource s : List.of(shorter, longer, huge, broken)) ImmersiveTanningAPI.unregisterAssistSource(s);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void eventScoresAreMutableAndClamped(GameTestHelper h) {
        FakePlayer player = TestSupport.player(h);
        HideFleshedEvent fleshed = new HideFleshedEvent(player, HideData.raw(HideSize.MEDIUM, null), 140, BlockPos.ZERO, false);
        h.assertTrue(fleshed.getScore() == 100, "A score above 100 is clamped on construction");
        fleshed.setScore(-5);
        h.assertTrue(fleshed.getScore() == 0, "A score below 0 is clamped");
        fleshed.setScore(63);
        h.assertTrue(fleshed.getScore() == 63, "A score in range is kept");
        ItemStack tunic = new ItemStack(Items.LEATHER_CHESTPLATE);
        LeatherworkStitchedEvent stitched = new LeatherworkStitchedEvent(player, tunic, ImmersiveTanning.id("test/api_tunic"),
                Finish.SUPPLE, 80, 71, null, 5, BlockPos.ZERO, false);
        stitched.setWorkmanship(71 + 8);
        h.assertTrue(stitched.getWorkmanship() == 79, "Workmanship follows the setter");
        stitched.setWorkmanship(500);
        h.assertTrue(stitched.getWorkmanship() == 100, "Workmanship is clamped to 100");
        h.assertTrue(stitched.getItem() == tunic && stitched.getLeatherUsed() == 5 && stitched.getFinish() == Finish.SUPPLE,
                "The event carries the live stack and the craft's facts");
        h.succeed();
    }
}
