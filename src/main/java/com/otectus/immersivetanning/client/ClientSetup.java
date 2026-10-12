package com.otectus.immersivetanning.client;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.client.renderer.LeatherworkersBenchRenderer;
import com.otectus.immersivetanning.client.renderer.TannersRackRenderer;
import com.otectus.immersivetanning.client.renderer.TannersVatRenderer;
import com.otectus.immersivetanning.item.ProcessedHideItem;
import com.otectus.immersivetanning.registry.ModBlockEntities;
import com.otectus.immersivetanning.registry.ModItems;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client-only mod-bus registration. Reached only through DistExecutor, so it never loads on a server. */
public final class ClientSetup {

    public static void init(IEventBus modBus) {
        modBus.addListener(ClientSetup::onClientSetup);
        modBus.addListener(ClientSetup::onRegisterRenderers);
        modBus.addListener(StationNotices::register);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (ProcessedHideItem item : new ProcessedHideItem[]{ModItems.SCRAPED_HIDE.get(), ModItems.WET_TANNED_HIDE.get()}) {
                ItemProperties.register(item, ImmersiveTanning.id("size"),
                        (stack, level, entity, seed) -> ProcessedHideItem.data(stack).size().ordinal() / 2F);
            }
        });
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.TANNERS_RACK.get(), TannersRackRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.TANNERS_VAT.get(), TannersVatRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.LEATHERWORKERS_BENCH.get(), LeatherworkersBenchRenderer::new);
    }

    private ClientSetup() {}
}
