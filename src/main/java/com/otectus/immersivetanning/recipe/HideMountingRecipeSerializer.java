package com.otectus.immersivetanning.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.otectus.immersivetanning.hide.HideSize;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.jetbrains.annotations.Nullable;

/**
 * JSON format:
 * <pre>
 * {
 *   "type": "immersive_tanning:hide_mounting",
 *   "conditions": [{"type": "forge:mod_loaded", "modid": "butchery"}],
 *   "ingredient": [{"item": "butchery:cow_skin"}, {"item": "butchery:donkey_skin"}],
 *   "size": "large",
 *   "trait": "immersive_tanning:insulating"
 * }
 * </pre>
 * {@code trait} is optional and names a hide trait (immersive_tanning/hide_traits).
 */
public class HideMountingRecipeSerializer implements RecipeSerializer<HideMountingRecipe> {

    @Override
    public HideMountingRecipe fromJson(ResourceLocation id, JsonObject json) {
        Ingredient ingredient = Ingredient.fromJson(json.get("ingredient"), false);
        if (ingredient.isEmpty()) throw new JsonParseException("Hide mounting ingredient is empty");
        HideSize size = HideSize.byName(GsonHelper.getAsString(json, "size"));
        if (size == null) throw new JsonParseException("size must be small, medium or large");
        ResourceLocation trait = json.has("trait") ? new ResourceLocation(GsonHelper.getAsString(json, "trait")) : null;
        return new HideMountingRecipe(id, ingredient, size, trait);
    }

    @Override
    public @Nullable HideMountingRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
        Ingredient ingredient = Ingredient.fromNetwork(buf);
        HideSize size = buf.readEnum(HideSize.class);
        ResourceLocation trait = buf.readBoolean() ? buf.readResourceLocation() : null;
        return new HideMountingRecipe(id, ingredient, size, trait);
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf, HideMountingRecipe recipe) {
        recipe.ingredient().toNetwork(buf);
        buf.writeEnum(recipe.size());
        buf.writeBoolean(recipe.trait() != null);
        if (recipe.trait() != null) buf.writeResourceLocation(recipe.trait());
    }
}
