package com.otectus.immersivetanning.registry;

import com.mojang.serialization.Codec;
import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.loot.BindingLeatherLootModifier;
import com.otectus.immersivetanning.loot.HideDropModifier;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModLootModifiers {
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, ImmersiveTanning.MOD_ID);

    public static final RegistryObject<Codec<HideDropModifier>> HIDE_DROPS = LOOT_MODIFIERS.register("hide_drops", () -> HideDropModifier.CODEC);

    public static final RegistryObject<Codec<BindingLeatherLootModifier>> BINDING_LEATHER = LOOT_MODIFIERS.register("binding_leather",
            () -> BindingLeatherLootModifier.CODEC);

    private ModLootModifiers() {}
}
