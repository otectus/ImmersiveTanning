package com.otectus.immersivetanning.loot;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.leather.VillageLeatherwork;
import com.otectus.immersivetanning.registry.ModLootFunctions;
import com.otectus.immersivetanning.trait.HideTraitLoader;
import com.otectus.immersivetanning.trait.TraitRef;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * Loot function {@code immersive_tanning:set_hide_trait}: gives a raw or processed hide, finished leather or a finished
 * piece an innate trait, so chests, treasure bags and quest rewards can hand out trophy hides.
 * <pre>{ "function": "immersive_tanning:set_hide_trait", "trait": "immersive_tanning:insulating" }</pre>
 */
public class SetHideTraitFunction extends LootItemConditionalFunction {
    private final ResourceLocation trait;

    SetHideTraitFunction(LootItemCondition[] conditions, ResourceLocation trait) {
        super(conditions);
        this.trait = trait;
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        TraitRef ref = HideTraitLoader.get(trait);
        if (ref == null) {
            ImmersiveTanning.LOGGER.warn("set_hide_trait names unknown hide trait {}", trait);
            return stack;
        }
        return VillageLeatherwork.withTrait(stack, ref);
    }

    @Override
    public LootItemFunctionType getType() {
        return ModLootFunctions.SET_HIDE_TRAIT.get();
    }

    public static class Serializer extends LootItemConditionalFunction.Serializer<SetHideTraitFunction> {
        @Override
        public void serialize(JsonObject json, SetHideTraitFunction function, JsonSerializationContext context) {
            super.serialize(json, function, context);
            json.addProperty("trait", function.trait.toString());
        }

        @Override
        public SetHideTraitFunction deserialize(JsonObject json, JsonDeserializationContext context, LootItemCondition[] conditions) {
            return new SetHideTraitFunction(conditions, new ResourceLocation(GsonHelper.getAsString(json, "trait")));
        }
    }
}
