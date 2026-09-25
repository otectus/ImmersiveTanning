package com.otectus.immersivetanning.hide;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.trait.HideTraitLoader;
import com.otectus.immersivetanning.trait.TraitRef;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Loads {@code data/<namespace>/immersive_tanning/hide_sources/<name>.json}:
 * <pre>
 * {
 *   "entities": ["minecraft:cow", "#forge:some_entity_tag"],
 *   "size": "large",              // small, medium, large, or "none" to stop an entity dropping hides
 *   "count": 1,
 *   "trait": "immersive_tanning:tough",   // optional; only then does the hide keep an identity
 *   "priority": 0                 // the highest priority source matching an entity wins
 * }
 * </pre>
 * Entity tags are resolved at lookup time, after tags are bound.
 */
public class HideSourceLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    private static volatile List<HideSource> sources = List.of();

    /** One parsed file. {@code size == null} means "drops no hide". */
    public record HideSource(ResourceLocation id, List<ResourceLocation> entities, List<TagKey<EntityType<?>>> tags,
                             @Nullable HideSize size, int count, @Nullable ResourceLocation trait, int priority) {
        boolean matches(EntityType<?> type) {
            if (entities.contains(ForgeRegistries.ENTITY_TYPES.getKey(type))) return true;
            for (TagKey<EntityType<?>> tag : tags) {
                if (type.is(tag)) return true;
            }
            return false;
        }

        /** The trait as it will be written onto the hide, or null if none or not defined by any datapack. */
        @Nullable
        public TraitRef traitRef() {
            return trait == null ? null : HideTraitLoader.get(trait);
        }
    }

    public HideSourceLoader() {
        super(GSON, "immersive_tanning/hide_sources");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        List<HideSource> loaded = new ArrayList<>();
        files.forEach((id, element) -> {
            try {
                loaded.add(parse(id, GsonHelper.convertToJsonObject(element, "hide source")));
            } catch (RuntimeException e) {
                ImmersiveTanning.LOGGER.error("Skipping hide source {}: {}", id, e.getMessage());
            }
        });
        // Highest priority first; ties resolved by id so the result never depends on load order.
        loaded.sort(Comparator.comparingInt(HideSource::priority).reversed().thenComparing(s -> s.id().toString(), Comparator.reverseOrder()));
        sources = List.copyOf(loaded);
        ImmersiveTanning.LOGGER.info("Loaded {} hide sources", loaded.size());
    }

    static HideSource parse(ResourceLocation id, JsonObject json) {
        List<ResourceLocation> entities = new ArrayList<>();
        List<TagKey<EntityType<?>>> tags = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(json, "entities")) {
            String value = GsonHelper.convertToString(e, "entity");
            if (value.startsWith("#")) tags.add(TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(value.substring(1))));
            else entities.add(new ResourceLocation(value));
        }
        String sizeName = GsonHelper.getAsString(json, "size");
        HideSize size = sizeName.equals("none") ? null : HideSize.byName(sizeName);
        if (size == null && !sizeName.equals("none")) throw new JsonParseException("size must be small, medium, large or none");
        int count = GsonHelper.getAsInt(json, "count", 1);
        if (count < 0 || count > 64) throw new JsonParseException("count must be between 0 and 64");
        ResourceLocation trait = json.has("trait") ? new ResourceLocation(GsonHelper.getAsString(json, "trait")) : null;
        return new HideSource(id, List.copyOf(entities), List.copyOf(tags), size, count, trait, GsonHelper.getAsInt(json, "priority", 0));
    }

    /** The hide source for an entity type, if any matches and it drops a hide. */
    @Nullable
    public static HideSource forEntity(EntityType<?> type) {
        for (HideSource source : sources) {
            if (source.matches(type)) return source.size() == null || source.count() == 0 ? null : source;
        }
        return null;
    }

    public static List<HideSource> all() {
        return sources;
    }

    /** Entity ids named by hide sources that no registered entity has (usually a mod that is not installed). */
    public static List<String> unknownEntities() {
        return sources.stream().flatMap(s -> s.entities().stream())
                .filter(id -> !ForgeRegistries.ENTITY_TYPES.containsKey(id)).map(ResourceLocation::toString).distinct().sorted().toList();
    }

    /** How many registered entity types drop a hide. Tags must be bound. */
    public static long coveredEntityTypes() {
        return ForgeRegistries.ENTITY_TYPES.getValues().stream().filter(type -> forEntity(type) != null).count();
    }
}
