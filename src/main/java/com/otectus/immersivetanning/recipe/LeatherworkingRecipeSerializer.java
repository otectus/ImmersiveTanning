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
 *   "seam": "immersive_tanning:saddle",
 *   "work_pattern": "immersive_tanning:saddle",
 *   "replaces": ["minecraft:saddle"]
 * }
 * </pre>
 * {@code work_pattern} (1.3, optional) names the work pattern: panels, cuts and seams worked at the bench. Without it
 * the seam's work pattern, the seam pattern adapted, or the generic wrap is used. It changes how the piece is worked,
 * never what it costs.
 * {@code seam} is optional; without it the seam follows the result's equipment slot. {@code replaces} is optional: the
 * crafting recipe ids (a list or one id) this recipe takes over, disabled while it is active. It never travels to
 * clients. Protocol 3 adds the work pattern and its size to the recipe's network form.
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
        List<ResourceLocation> replaces = new ArrayList<>();
        if (json.has("replaces")) {
            JsonElement element = json.get("replaces");
            if (element.isJsonArray()) {
                for (JsonElement e : element.getAsJsonArray()) replaces.add(new ResourceLocation(GsonHelper.convertToString(e, "replaces")));
            } else {
                replaces.add(new ResourceLocation(GsonHelper.convertToString(element, "replaces")));
            }
        }
        LeatherworkingRecipe recipe = new LeatherworkingRecipe(id, leather, aux, result, seam, false, null, replaces);
        if (json.has("work_pattern")) recipe.setWorkPattern(new ResourceLocation(GsonHelper.getAsString(json, "work_pattern")));
        return recipe;
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
        if (buf.readBoolean()) recipe.setWorkPattern(buf.readResourceLocation());
        recipe.resolvePattern(buf.readResourceLocation(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
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
        buf.writeBoolean(recipe.workPattern() != null);
        if (recipe.workPattern() != null) buf.writeResourceLocation(recipe.workPattern());
        buf.writeResourceLocation(recipe.resolvedPattern());
        buf.writeVarInt(recipe.patternCuts());
        buf.writeVarInt(recipe.patternPanels());
        buf.writeVarInt(recipe.patternStitches());
    }
}
