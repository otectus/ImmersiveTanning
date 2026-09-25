package com.otectus.immersivetanning.registry;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.hide.HideSize;
import com.otectus.immersivetanning.leather.Finish;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ImmersiveTanning.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.immersive_tanning"))
            .icon(() -> new ItemStack(ModItems.leather(Finish.HARNESS)))
            .displayItems((params, output) -> {
                output.accept(ModItems.TANNERS_RACK.get());
                output.accept(ModItems.TANNERS_VAT.get());
                output.accept(ModItems.LEATHERWORKERS_BENCH.get());
                output.accept(ModItems.TANNERS_KNIFE.get());
                output.accept(ModItems.AWL.get());
                output.accept(ModItems.BARK.get());
                for (HideSize size : HideSize.values()) output.accept(ModItems.rawHide(size));
                for (HideSize size : HideSize.values()) output.accept(ModItems.SCRAPED_HIDE.get().create(size, 50, null));
                for (HideSize size : HideSize.values()) output.accept(ModItems.WET_TANNED_HIDE.get().create(size, 50, null));
                for (Finish finish : Finish.values()) output.accept(ModItems.leather(finish));
                output.accept(ModItems.BINDING_LEATHER.get());
                output.accept(ModItems.LEATHER_SCRAPS.get());
            })
            .build());

    private ModCreativeTabs() {}
}
