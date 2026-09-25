package com.otectus.immersivetanning;

import com.mojang.logging.LogUtils;
import com.otectus.immersivetanning.client.ClientSetup;
import com.otectus.immersivetanning.config.ClientConfig;
import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.network.ModNetwork;
import com.otectus.immersivetanning.registry.ModBlockEntities;
import com.otectus.immersivetanning.registry.ModBlocks;
import com.otectus.immersivetanning.registry.ModCreativeTabs;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.registry.ModLootFunctions;
import com.otectus.immersivetanning.registry.ModLootModifiers;
import com.otectus.immersivetanning.registry.ModRecipeSerializers;
import com.otectus.immersivetanning.registry.ModRecipeTypes;
import com.otectus.immersivetanning.registry.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(ImmersiveTanning.MOD_ID)
public class ImmersiveTanning {
    public static final String MOD_ID = "immersive_tanning";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ImmersiveTanning() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModRecipeTypes.RECIPE_TYPES.register(modBus);
        ModRecipeSerializers.RECIPE_SERIALIZERS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModLootModifiers.LOOT_MODIFIERS.register(modBus);
        ModLootFunctions.LOOT_FUNCTIONS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);

        modBus.addListener(this::commonSetup);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSetup.init(modBus));
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
