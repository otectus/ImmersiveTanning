package com.otectus.immersivetanning.event;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.block.LeatherworkersBenchBlock;
import com.otectus.immersivetanning.block.TannersRackBlock;
import com.otectus.immersivetanning.block.TannersVatBlock;
import com.otectus.immersivetanning.blockentity.StationBlockEntity;
import com.otectus.immersivetanning.command.TanningCommands;
import com.otectus.immersivetanning.hide.HideSourceLoader;
import com.otectus.immersivetanning.leather.BindingLeather;
import com.otectus.immersivetanning.minigame.SeamPatterns;
import com.otectus.immersivetanning.minigame.SessionManager;
import com.otectus.immersivetanning.recipe.TanningData;
import com.otectus.immersivetanning.trait.HideTraitLoader;
import com.otectus.immersivetanning.util.Feedback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.event.village.WandererTradesEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Gameplay events (Forge bus). */
@Mod.EventBusSubscriber(modid = ImmersiveTanning.MOD_ID)
public final class CommonEvents {

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        // Traits load before sources so a source can resolve its trait when hides drop.
        event.addListener(new HideTraitLoader());
        event.addListener(new HideSourceLoader());
        event.addListener(new SeamPatterns());
    }

    /** Trades that ask for or pay vanilla leather deal in Binding Leather (decided when an offer is generated). */
    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        event.getTrades().values().forEach(list -> list.replaceAll(BindingLeather::trade));
    }

    @SubscribeEvent
    public static void onWandererTrades(WandererTradesEvent event) {
        event.getGenericTrades().replaceAll(BindingLeather::trade);
        event.getRareTrades().replaceAll(BindingLeather::trade);
    }

    /** First build, once tags are bound and before any player can join. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        TanningData.rebuild(event.getServer());
    }

    /**
     * After /reload the new tags are bound and this fires before the recipe packet is sent, so rebuilding here means
     * clients receive the updated (filtered and generated) recipe list.
     */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null || TanningData.isDirty()) TanningData.rebuild(event.getPlayerList().getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        SessionManager.onServerStopping(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TanningData.reset();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) SessionManager.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SessionManager.onLogout(player);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SessionManager.onDeath(player);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        TanningCommands.register(event.getDispatcher());
    }

    /**
     * Vanilla skips a block's use() when the player sneaks with an item in hand. Sneak-mounting a wet hide with the last
     * finish needs it, so allow it for non-block items on the stations. Sneaking with a block item still places blocks.
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!player.isSecondaryUseActive()) return;
        Block block = event.getLevel().getBlockState(event.getPos()).getBlock();
        if (!(block instanceof TannersRackBlock || block instanceof TannersVatBlock || block instanceof LeatherworkersBenchBlock)) return;
        ItemStack held = event.getItemStack();
        if (!held.isEmpty() && !(held.getItem() instanceof BlockItem)) event.setUseBlock(Event.Result.ALLOW);
    }

    /** A station someone else is working at cannot be broken out from under them. */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.getAbilities().instabuild) return;
        if (event.getLevel().getBlockEntity(event.getPos()) instanceof StationBlockEntity station && station.isLockedFor(player.getUUID())) {
            event.setCanceled(true);
            Feedback.fail(player, "station_in_use");
        }
    }

    private CommonEvents() {}
}
