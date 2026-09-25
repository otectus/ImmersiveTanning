package com.otectus.immersivetanning.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.CraftingHelper;

/** A non-leather ingredient (string, iron, a chest...) consumed from the leatherworker's inventory when stitching starts. */
public record AuxiliaryIngredient(Ingredient ingredient, int count) {

    public static AuxiliaryIngredient fromJson(JsonObject json) {
        Ingredient ingredient = CraftingHelper.getIngredient(json.get("ingredient"), false);
        int count = GsonHelper.getAsInt(json, "count", 1);
        if (count <= 0 || count > 64) throw new JsonParseException("Auxiliary count must be 1-64");
        return new AuxiliaryIngredient(ingredient, count);
    }

    public void write(FriendlyByteBuf buf) {
        ingredient.toNetwork(buf);
        buf.writeVarInt(count);
    }

    public static AuxiliaryIngredient read(FriendlyByteBuf buf) {
        return new AuxiliaryIngredient(Ingredient.fromNetwork(buf), buf.readVarInt());
    }

    /** A representative stack for display, with this ingredient's count. */
    public ItemStack displayStack() {
        ItemStack[] items = ingredient.getItems();
        if (items.length == 0) return ItemStack.EMPTY;
        return items[0].copyWithCount(count);
    }
}
