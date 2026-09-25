package com.otectus.immersivetanning.loot;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.leather.VillageLeatherwork;
import com.otectus.immersivetanning.registry.ModLootFunctions;
import com.otectus.immersivetanning.trait.HideTraitLoader;
import com.otectus.immersivetanning.trait.TraitRef;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import org.jetbrains.annotations.Nullable;

/**
 * Loot function {@code immersive_tanning:set_leatherwork}: makes an item a finished piece from someone else's bench.
 * <pre>
 * { "function": "immersive_tanning:set_leatherwork",
 *   "finish": "harness",                                  // optional; a random finish without it
 *   "integrity": {"type": "minecraft:uniform", "min": 60, "max": 90},   // optional number providers;
 *   "workmanship": 75,                                                  // village quality without them
 *   "trait": "immersive_tanning:tough" }                  // optional
 * </pre>
 */
public class SetLeatherworkFunction extends LootItemConditionalFunction {
    @Nullable private final Finish finish;
    @Nullable private final NumberProvider integrity;
    @Nullable private final NumberProvider workmanship;
    @Nullable private final ResourceLocation trait;

    SetLeatherworkFunction(LootItemCondition[] conditions, @Nullable Finish finish, @Nullable NumberProvider integrity,
                           @Nullable NumberProvider workmanship, @Nullable ResourceLocation trait) {
        super(conditions);
        this.finish = finish;
        this.integrity = integrity;
        this.workmanship = workmanship;
        this.trait = trait;
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        Finish chosen = finish != null ? finish : VillageLeatherwork.finish(context.getRandom());
        int i = integrity != null ? Mth.clamp(integrity.getInt(context), 0, 100) : VillageLeatherwork.score(context.getRandom());
        int w = workmanship != null ? Mth.clamp(workmanship.getInt(context), 0, 100) : VillageLeatherwork.score(context.getRandom());
        TraitRef ref = trait == null ? null : HideTraitLoader.get(trait);
        return VillageLeatherwork.make(stack, chosen, i, w, ref, null);
    }

    @Override
    public LootItemFunctionType getType() {
        return ModLootFunctions.SET_LEATHERWORK.get();
    }

    public static class Serializer extends LootItemConditionalFunction.Serializer<SetLeatherworkFunction> {
        @Override
        public void serialize(JsonObject json, SetLeatherworkFunction function, JsonSerializationContext context) {
            super.serialize(json, function, context);
            if (function.finish != null) json.addProperty("finish", function.finish.getSerializedName());
            if (function.integrity != null) json.add("integrity", context.serialize(function.integrity));
            if (function.workmanship != null) json.add("workmanship", context.serialize(function.workmanship));
            if (function.trait != null) json.addProperty("trait", function.trait.toString());
        }

        @Override
        public SetLeatherworkFunction deserialize(JsonObject json, JsonDeserializationContext context, LootItemCondition[] conditions) {
            Finish finish = null;
            if (json.has("finish")) {
                finish = Finish.byName(GsonHelper.getAsString(json, "finish"));
                if (finish == null) throw new JsonParseException("finish must be supple, harness or hardened");
            }
            NumberProvider integrity = GsonHelper.getAsObject(json, "integrity", null, context, NumberProvider.class);
            NumberProvider workmanship = GsonHelper.getAsObject(json, "workmanship", null, context, NumberProvider.class);
            ResourceLocation trait = json.has("trait") ? new ResourceLocation(GsonHelper.getAsString(json, "trait")) : null;
            return new SetLeatherworkFunction(conditions, finish, integrity, workmanship, trait);
        }
    }
}
