package com.otectus.immersivetanning.work;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.minigame.SeamPatterns;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Loads {@code data/<namespace>/immersive_tanning/work_patterns/<name>.json} (see {@link WorkPattern}) and resolves the
 * pattern of a bench recipe. A reload replaces the whole set at once; a broken file is skipped with one message naming
 * it and why, and recipes that named it fall back (never a crash, never a broken station).
 *
 * <p>Resolution of a recipe: its {@code work_pattern}, else a work pattern with its seam's id, else its 1.2 seam
 * pattern adapted ({@link WorkPattern#fromLegacy}), else the generic wrap.</p>
 */
public class WorkPatterns extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    private static volatile Map<ResourceLocation, WorkPattern> patterns = Map.of();
    private static volatile Map<ResourceLocation, String> failures = Map.of();

    public enum Source { EXPLICIT, SEAM, LEGACY, GENERIC }

    public record Resolution(WorkPattern pattern, Source source, @Nullable String note) {}

    public WorkPatterns() {
        super(GSON, "immersive_tanning/work_patterns");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, WorkPattern> loaded = new LinkedHashMap<>();
        Map<ResourceLocation, String> failed = new LinkedHashMap<>();
        files.forEach((id, element) -> {
            try {
                loaded.put(id, WorkPattern.fromJson(id, GsonHelper.convertToJsonObject(element, "work pattern")));
            } catch (RuntimeException e) {
                failed.put(id, e.getMessage());
                ImmersiveTanning.LOGGER.error("Skipping work pattern {} (data/{}/immersive_tanning/work_patterns/{}.json): {}",
                        id, id.getNamespace(), id.getPath(), e.getMessage());
            }
        });
        patterns = Map.copyOf(loaded);
        failures = Map.copyOf(failed);
        ImmersiveTanning.LOGGER.info("Loaded {} work patterns{}", loaded.size(), failed.isEmpty() ? "" : " (" + failed.size() + " skipped)");
    }

    public static Set<ResourceLocation> ids() {
        return patterns.keySet();
    }

    public static Map<ResourceLocation, String> failures() {
        return failures;
    }

    @Nullable
    public static WorkPattern get(ResourceLocation id) {
        return patterns.get(id);
    }

    /** The pattern for a recipe's explicit {@code work_pattern} (may be null) and resolved seam. */
    public static Resolution resolve(@Nullable ResourceLocation explicit, ResourceLocation seam) {
        String note = null;
        if (explicit != null) {
            WorkPattern pattern = patterns.get(explicit);
            if (pattern != null) return new Resolution(pattern, Source.EXPLICIT, null);
            note = failures.containsKey(explicit) ? "work pattern " + explicit + " failed to load (" + failures.get(explicit) + ")"
                    : "work pattern " + explicit + " does not exist";
        }
        WorkPattern bySeam = patterns.get(seam);
        if (bySeam != null) return new Resolution(bySeam, Source.SEAM, note);
        if (SeamPatterns.has(seam)) return new Resolution(WorkPattern.fromLegacy(SeamPatterns.get(seam)), Source.LEGACY, note);
        return new Resolution(generic(), Source.GENERIC, note);
    }

    public static WorkPattern generic() {
        WorkPattern pattern = patterns.get(WorkPattern.GENERIC);
        return pattern != null ? pattern : WorkPattern.genericWrap();
    }

    public static WorkPattern patch() {
        WorkPattern pattern = patterns.get(WorkPattern.PATCH_ID);
        return pattern != null ? pattern : WorkPattern.patch();
    }
}
