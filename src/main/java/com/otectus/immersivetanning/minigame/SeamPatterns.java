package com.otectus.immersivetanning.minigame;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.otectus.immersivetanning.ImmersiveTanning;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Loads {@code data/<namespace>/immersive_tanning/seam_patterns/<name>.json}; see {@link StitchPattern}. */
public class SeamPatterns extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    private static volatile Map<ResourceLocation, StitchPattern> patterns = Map.of();

    public SeamPatterns() {
        super(GSON, "immersive_tanning/seam_patterns");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, StitchPattern> loaded = new LinkedHashMap<>();
        files.forEach((id, element) -> {
            try {
                loaded.put(id, StitchPattern.fromJson(id, GsonHelper.convertToJsonObject(element, "seam pattern")));
            } catch (RuntimeException e) {
                ImmersiveTanning.LOGGER.error("Skipping seam pattern {}: {}", id, e.getMessage());
            }
        });
        patterns = Map.copyOf(loaded);
        ImmersiveTanning.LOGGER.info("Loaded {} seam patterns", loaded.size());
    }

    public static Set<ResourceLocation> ids() {
        return patterns.keySet();
    }

    public static boolean has(ResourceLocation id) {
        return patterns.containsKey(id);
    }

    /** The named pattern, else the generic one, else the built-in default. */
    public static StitchPattern get(ResourceLocation id) {
        StitchPattern pattern = patterns.get(id);
        if (pattern == null) pattern = patterns.get(StitchPattern.GENERIC);
        return pattern != null ? pattern : StitchPattern.DEFAULT;
    }
}
