package com.otectus.immersivetanning.registry;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.loot.SetHideTraitFunction;
import com.otectus.immersivetanning.loot.SetLeatherworkFunction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModLootFunctions {
    public static final DeferredRegister<LootItemFunctionType> LOOT_FUNCTIONS = DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, ImmersiveTanning.MOD_ID);

    public static final RegistryObject<LootItemFunctionType> SET_HIDE_TRAIT = LOOT_FUNCTIONS.register("set_hide_trait",
            () -> new LootItemFunctionType(new SetHideTraitFunction.Serializer()));

    public static final RegistryObject<LootItemFunctionType> SET_LEATHERWORK = LOOT_FUNCTIONS.register("set_leatherwork",
            () -> new LootItemFunctionType(new SetLeatherworkFunction.Serializer()));

    private ModLootFunctions() {}
}
