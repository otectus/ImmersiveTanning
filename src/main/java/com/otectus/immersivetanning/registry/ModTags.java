package com.otectus.immersivetanning.registry;

import com.otectus.immersivetanning.ImmersiveTanning;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class ModTags {
    public static final TagKey<Item> RAW_HIDES = item("raw_hides");
    public static final TagKey<Item> TANNIN_SOURCES = item("tannin_sources");
    public static final TagKey<Item> FINISHED_LEATHER = item("finished_leather");
    public static final TagKey<Item> LEATHER_INGREDIENTS = item("leather_ingredients");
    public static final TagKey<Item> LEATHERWORK_EQUIPMENT = item("leatherwork_equipment");
    public static final TagKey<Item> NON_LEATHERWORK_EQUIPMENT = item("non_leatherwork_equipment");
    public static final TagKey<Item> REPLACED_BY_HIDES = item("replaced_by_hides");
    public static final TagKey<Item> FIELD_REPAIRABLE = item("field_repairable");
    /** Weapons that butcher a carcass (another mod's skinning path) instead of letting a hide drop. */
    public static final TagKey<Item> CARCASS_TOOLS = item("carcass_tools");

    /** Animals whose carcass another mod skins, so a carcass kill drops no hide of ours. */
    public static final TagKey<EntityType<?>> CARCASS_ANIMALS = TagKey.create(Registries.ENTITY_TYPE, ImmersiveTanning.id("carcass_animals"));

    /** Damage the built-in hide traits reduce. */
    public static final TagKey<DamageType> RESISTED_BY_HEATPROOF = damage("resisted_by/heatproof");
    public static final TagKey<DamageType> RESISTED_BY_INSULATING = damage("resisted_by/insulating");
    public static final TagKey<DamageType> RESISTED_BY_WATER_RESISTANT = damage("resisted_by/water_resistant");

    public static final TagKey<Block> BARK_SOURCES = BlockTags.create(ImmersiveTanning.id("bark_sources"));

    private static TagKey<Item> item(String name) {
        return ItemTags.create(ImmersiveTanning.id(name));
    }

    private static TagKey<DamageType> damage(String name) {
        return TagKey.create(Registries.DAMAGE_TYPE, ImmersiveTanning.id(name));
    }

    private ModTags() {}
}
