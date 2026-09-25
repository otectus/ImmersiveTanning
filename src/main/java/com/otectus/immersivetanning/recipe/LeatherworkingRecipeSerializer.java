package com.otectus.immersivetanning.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.common.crafting.CraftingHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON format:
 * <pre>
 * {
 *   "type": "immersive_tanning:leatherworking",
 *   "leather": 5,
 *   "auxiliary": [{"ingredient": {"item": "minecraft:string"}, "count": 2}],
 *   "result": {"item": "minecraft:saddle"},
 *   "seam": "immersive_tanning:saddle"
 * }
 * </pre>
 * {@code seam} is optional; without it the seam follows the result's equipment slot.
 */
public class LeatherworkingRecipeSerializer implements RecipeSerializer<LeatherworkingRecipe> {

    @Override
    public LeatherworkingRecipe fromJson(ResourceLocation id, JsonObject json) {
        int leather = GsonHelper.getAsInt(json, "leather");
        if (leather <= 0 || leather > 64) throw new JsonParseException("leather must be 1-64");
        List<AuxiliaryIngredient> aux = new ArrayList<>();
        if (json.has("auxiliary")) {
            for (JsonElement e : GsonHelper.getAsJsonArray(json, "auxiliary")) {
                aux.add(AuxiliaryIngredient.fromJson(GsonHelper.convertToJsonObject(e, "auxiliary")));
            }
        }
        ItemStack result = CraftingHelper.getItemStack(GsonHelper.getAsJsonObject(json, "result"), true);
        if (result.isEmpty()) throw new JsonParseException("Leatherworking recipe result is empty");
        ResourceLocation seam = json.has("seam") ? new ResourceLocation(GsonHelper.getAsString(json, "seam")) : null;
        return new LeatherworkingRecipe(id, leather, aux, result, seam, false, null);
    }

    @Override
    public @Nullable LeatherworkingRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
        int leather = buf.readVarInt();
        int auxCount = buf.readVarInt();
        List<AuxiliaryIngredient> aux = new ArrayList<>(auxCount);
        for (int i = 0; i < auxCount; i++) aux.add(AuxiliaryIngredient.read(buf));
        ItemStack result = buf.readItem();
        ResourceLocation seam = buf.readBoolean() ? buf.readResourceLocation() : null;
        boolean auto = buf.readBoolean();
        ResourceLocation source = buf.readBoolean() ? buf.readResourceLocation() : null;
        LeatherworkingRecipe recipe = new LeatherworkingRecipe(id, leather, aux, result, seam, auto, source);
        recipe.resolveSeam(buf.readResourceLocation());
        return recipe;
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf, LeatherworkingRecipe recipe) {
        buf.writeVarInt(recipe.leather());
        buf.writeVarInt(recipe.auxiliary().size());
        recipe.auxiliary().forEach(a -> a.write(buf));
        buf.writeItem(recipe.result());
        buf.writeBoolean(recipe.explicitSeam() != null);
        if (recipe.explicitSeam() != null) buf.writeResourceLocation(recipe.explicitSeam());
        buf.writeBoolean(recipe.isAuto());
        buf.writeBoolean(recipe.sourceRecipe() != null);
        if (recipe.sourceRecipe() != null) buf.writeResourceLocation(recipe.sourceRecipe());
        buf.writeResourceLocation(recipe.seam());
    }
}
