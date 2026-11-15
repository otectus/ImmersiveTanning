package com.otectus.immersivetanning.client.station;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Tells apart pattern results whose items share a display name (Ice and Fire's twelve dragon-scale chestplates are all
 * "Dragon Scale Chestplate"; vanilla's music discs are all "Music Disc"): each such item gets a short label from the
 * words of its registry id that the others lack ("Red", "Cat"), prefixed by its mod's name when the items come from
 * different mods. An item with a name of its own gets none.
 */
public final class VariantLabels {

    public static Map<Item, Component> of(List<ItemStack> results) {
        Map<String, List<Item>> byName = new LinkedHashMap<>();
        for (ItemStack stack : results) {
            if (stack.isEmpty()) continue;
            List<Item> items = byName.computeIfAbsent(stack.getHoverName().getString(), k -> new ArrayList<>());
            if (!items.contains(stack.getItem())) items.add(stack.getItem());
        }
        Map<Item, Component> out = new HashMap<>();
        for (List<Item> items : byName.values()) {
            if (items.size() < 2) continue;
            List<ResourceLocation> ids = new ArrayList<>();
            for (Item item : items) ids.add(ForgeRegistries.ITEMS.getKey(item));
            Set<String> common = null;
            Set<String> namespaces = new HashSet<>();
            for (ResourceLocation id : ids) {
                if (id == null) continue;
                namespaces.add(id.getNamespace());
                Set<String> words = new HashSet<>(Arrays.asList(id.getPath().split("[_/]")));
                if (common == null) common = words;
                else common.retainAll(words);
            }
            Map<Item, String> labels = new LinkedHashMap<>();
            for (int i = 0; i < items.size(); i++) {
                ResourceLocation id = ids.get(i);
                if (id == null) continue;
                StringBuilder label = new StringBuilder();
                if (namespaces.size() > 1) label.append(modName(id.getNamespace()));
                for (String word : id.getPath().split("[_/]")) {
                    if (word.isEmpty() || common != null && common.contains(word)) continue;
                    if (!label.isEmpty()) label.append(' ');
                    label.append(capitalise(word));
                }
                labels.put(items.get(i), label.isEmpty() ? id.toString() : label.toString());
            }
            // Ids that differ only in word order (or not at all) still need telling apart: use the whole id.
            Set<String> seen = new HashSet<>();
            Set<String> repeated = new HashSet<>();
            for (String label : labels.values()) if (!seen.add(label)) repeated.add(label);
            for (int i = 0; i < items.size(); i++) {
                String label = labels.get(items.get(i));
                if (label == null) continue;
                out.put(items.get(i), Component.literal(repeated.contains(label) ? ids.get(i).toString() : label));
            }
        }
        return out;
    }

    private static String modName(String namespace) {
        return ModList.get().getModContainerById(namespace).map(c -> c.getModInfo().getDisplayName()).orElse(namespace);
    }

    private static String capitalise(String word) {
        return word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1);
    }

    private VariantLabels() {}
}
