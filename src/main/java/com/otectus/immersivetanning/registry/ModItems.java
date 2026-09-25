package com.otectus.immersivetanning.registry;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.hide.HideSize;
import com.otectus.immersivetanning.item.AwlItem;
import com.otectus.immersivetanning.item.FinishedLeatherItem;
import com.otectus.immersivetanning.item.HintItem;
import com.otectus.immersivetanning.item.LeatherScrapsItem;
import com.otectus.immersivetanning.item.ProcessedHideItem;
import com.otectus.immersivetanning.item.RawHideItem;
import com.otectus.immersivetanning.item.StationBlockItem;
import com.otectus.immersivetanning.item.TannersKnifeItem;
import com.otectus.immersivetanning.leather.Finish;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ImmersiveTanning.MOD_ID);

    public static final RegistryObject<Item> TANNERS_RACK = ITEMS.register("tanners_rack",
            () -> new StationBlockItem(ModBlocks.TANNERS_RACK.get(), new Item.Properties()));
    public static final RegistryObject<Item> TANNERS_VAT = ITEMS.register("tanners_vat",
            () -> new StationBlockItem(ModBlocks.TANNERS_VAT.get(), new Item.Properties()));
    public static final RegistryObject<Item> LEATHERWORKERS_BENCH = ITEMS.register("leatherworkers_bench",
            () -> new StationBlockItem(ModBlocks.LEATHERWORKERS_BENCH.get(), new Item.Properties()));

    public static final Map<HideSize, RegistryObject<RawHideItem>> RAW_HIDES = new EnumMap<>(HideSize.class);

    static {
        for (HideSize size : HideSize.values()) {
            RAW_HIDES.put(size, ITEMS.register(size.getSerializedName() + "_hide",
                    () -> new RawHideItem(size, new Item.Properties().stacksTo(16))));
        }
    }

    public static final RegistryObject<ProcessedHideItem> SCRAPED_HIDE = ITEMS.register("scraped_hide",
            () -> new ProcessedHideItem(ProcessedHideItem.Stage.SCRAPED, new Item.Properties().stacksTo(16)));
    public static final RegistryObject<ProcessedHideItem> WET_TANNED_HIDE = ITEMS.register("wet_tanned_hide",
            () -> new ProcessedHideItem(ProcessedHideItem.Stage.WET, new Item.Properties().stacksTo(16)));

    public static final Map<Finish, RegistryObject<FinishedLeatherItem>> LEATHERS = new EnumMap<>(Finish.class);

    static {
        for (Finish finish : Finish.values()) {
            LEATHERS.put(finish, ITEMS.register(finish.getSerializedName() + "_leather",
                    () -> new FinishedLeatherItem(finish, new Item.Properties())));
        }
    }

    /** Replaces vanilla leather in every recipe, loot table and trade. See {@link com.otectus.immersivetanning.leather.BindingLeather}. */
    public static final RegistryObject<Item> BINDING_LEATHER = ITEMS.register("binding_leather", () -> new HintItem(new Item.Properties()));
    public static final RegistryObject<LeatherScrapsItem> LEATHER_SCRAPS = ITEMS.register("leather_scraps",
            () -> new LeatherScrapsItem(new Item.Properties()));
    public static final RegistryObject<Item> BARK = ITEMS.register("bark", () -> new Item(new Item.Properties()));
    public static final RegistryObject<TannersKnifeItem> TANNERS_KNIFE = ITEMS.register("tanners_knife",
            () -> new TannersKnifeItem(new Item.Properties().durability(250)));
    public static final RegistryObject<AwlItem> AWL = ITEMS.register("awl",
            () -> new AwlItem(new Item.Properties().durability(200)));

    public static RawHideItem rawHide(HideSize size) {
        return RAW_HIDES.get(size).get();
    }

    public static FinishedLeatherItem leather(Finish finish) {
        return LEATHERS.get(finish).get();
    }

    private ModItems() {}
}
