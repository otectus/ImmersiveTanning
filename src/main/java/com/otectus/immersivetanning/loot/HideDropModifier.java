package com.otectus.immersivetanning.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.otectus.immersivetanning.hide.HideSourceLoader;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

/**
 * Adds the hide an entity's hide source names to its death loot and, by default, removes the leather it would
 * otherwise drop (see {@link HideDrops}). Applies only to the entity's own loot table, never to chests or fishing
 * opened by an entity.
 */
public class HideDropModifier extends LootModifier {
    public static final Codec<HideDropModifier> CODEC = RecordCodecBuilder.create(inst -> codecStart(inst).apply(inst, HideDropModifier::new));

    public HideDropModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        Entity entity = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        if (!(entity instanceof LivingEntity living) || !context.hasParam(LootContextParams.DAMAGE_SOURCE)) return loot;
        if (!living.getLootTable().equals(context.getQueriedLootTableId())) return loot;
        HideSourceLoader.HideSource source = HideSourceLoader.forEntity(living.getType());
        if (source == null) return loot;

        HideDrops.apply(loot, living, source, context.getParamOrNull(LootContextParams.KILLER_ENTITY), context.getLootingModifier(),
                context.getRandom());
        return loot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
