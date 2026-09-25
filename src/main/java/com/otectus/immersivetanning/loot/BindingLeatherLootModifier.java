package com.otectus.immersivetanning.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.leather.BindingLeather;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

/** Turns vanilla leather in any loot into Binding Leather. Runs after {@link HideDropModifier}. */
public class BindingLeatherLootModifier extends LootModifier {
    public static final Codec<BindingLeatherLootModifier> CODEC = RecordCodecBuilder.create(inst -> codecStart(inst).apply(inst, BindingLeatherLootModifier::new));

    public BindingLeatherLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        if (ServerConfig.get(ServerConfig.REPLACE_LEATHER_LOOT)) convert(loot);
        return loot;
    }

    public static void convert(ObjectArrayList<ItemStack> loot) {
        loot.replaceAll(BindingLeather::replace);
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
