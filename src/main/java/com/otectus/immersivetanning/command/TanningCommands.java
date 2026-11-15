package com.otectus.immersivetanning.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.otectus.immersivetanning.blockentity.TannersRackBlockEntity;
import com.otectus.immersivetanning.blockentity.TannersVatBlockEntity;
import com.otectus.immersivetanning.compat.geneticanimals.GeneticAnimals;
import com.otectus.immersivetanning.compat.geneticanimals.HideMode;
import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.hide.HideSourceLoader;
import com.otectus.immersivetanning.recipe.TanningData;
import com.otectus.immersivetanning.registry.ModRecipeTypes;
import com.otectus.immersivetanning.registry.ModTags;
import com.otectus.immersivetanning.trait.HideTraitLoader;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /immersive_tanning report} - recipe, hide and trait summary with detection notes and other mods' integration.
 * {@code /immersive_tanning inspect <entity>} / {@code inspect type <entity type>} - how one animal's hides are decided.
 * {@code /immersive_tanning advance <pos> <ticks>} - moves a vat's or rack's clock forward (operators, for testing).
 */
public final class TanningCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal("immersive_tanning")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("report").executes(ctx -> {
                    TanningData data = TanningData.current();
                    CommandSourceStack source = ctx.getSource();
                    source.sendSuccess(() -> Component.literal(data.summary()), false);
                    int mounting = source.getServer().getRecipeManager().getAllRecipesFor(ModRecipeTypes.HIDE_MOUNTING.get()).size();
                    source.sendSuccess(() -> Component.literal(HideSourceLoader.all().size() + " hide sources covering "
                            + HideSourceLoader.coveredEntityTypes() + " entity types, " + HideTraitLoader.all().size() + " hide traits, "
                            + mounting + " hide mounting recipes"), false);
                    List<String> unknown = HideSourceLoader.unknownEntities();
                    if (!unknown.isEmpty()) {
                        source.sendSuccess(() -> Component.literal(" - " + unknown.size() + " hide source entities are not installed: "
                                + String.join(", ", unknown.subList(0, Math.min(12, unknown.size()))) + (unknown.size() > 12 ? ", ..." : "")), false);
                    }
                    for (String invalid : HideSourceLoader.invalid()) {
                        source.sendSuccess(() -> Component.literal(" - Skipped invalid hide source " + invalid), false);
                    }
                    source.sendSuccess(() -> Component.literal(com.otectus.immersivetanning.work.WorkPatterns.ids().size() + " work patterns"), false);
                    com.otectus.immersivetanning.work.WorkPatterns.failures().forEach((id, why) ->
                            source.sendSuccess(() -> Component.literal(" - Skipped work pattern " + id + ": " + why), false));
                    source.sendSuccess(() -> Component.literal(GeneticAnimals.summary()), false);
                    int shown = 0;
                    for (String note : data.notes()) {
                        if (shown++ >= 20) break;
                        source.sendSuccess(() -> Component.literal(" - " + note), false);
                    }
                    return data.recipes().size();
                }))
                .then(Commands.literal("inspect")
                        .then(Commands.literal("type")
                                .then(Commands.argument("type", ResourceArgument.resource(context, Registries.ENTITY_TYPE)).executes(ctx -> {
                                    EntityType<?> type = ResourceArgument.getSummonableEntityType(ctx, "type").value();
                                    inspect(ctx.getSource(), type, null).forEach(line -> ctx.getSource().sendSuccess(() -> Component.literal(line), false));
                                    return 1;
                                })))
                        .then(Commands.argument("entity", EntityArgument.entity()).executes(ctx -> {
                            Entity entity = EntityArgument.getEntity(ctx, "entity");
                            inspect(ctx.getSource(), entity.getType(), entity).forEach(line -> ctx.getSource().sendSuccess(() -> Component.literal(line), false));
                            return 1;
                        })))
                .then(Commands.literal("advance")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 1_000_000)).executes(ctx -> {
                                    BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
                                    int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
                                    BlockEntity be = ctx.getSource().getLevel().getBlockEntity(pos);
                                    if (be instanceof TannersVatBlockEntity vat) vat.advance(ticks);
                                    else if (be instanceof TannersRackBlockEntity rack) rack.advance(ticks);
                                    else {
                                        ctx.getSource().sendFailure(Component.translatable("message.immersive_tanning.not_a_tannery_station"));
                                        return 0;
                                    }
                                    ctx.getSource().sendSuccess(() -> Component.translatable("message.immersive_tanning.advanced", ticks), true);
                                    return 1;
                                })))));
    }

    /**
     * How an entity's hides are decided: the winning source and what it says, the sources it shadows, which stage
     * drops the hide, the carcass route, and for Genetic Animals the mode and hooks. Says what would happen, never rolls
     * a drop.
     */
    static List<String> inspect(CommandSourceStack source, EntityType<?> type, @Nullable Entity entity) {
        List<String> out = new ArrayList<>();
        String age = entity instanceof LivingEntity living ? (living.isBaby() ? " (juvenile)" : " (adult)") : "";
        out.add(EntityType.getKey(type) + age);
        List<HideSourceLoader.HideSource> matching = HideSourceLoader.matching(type);
        HideSourceLoader.Decision decision = HideSourceLoader.resolve(type);
        HideSourceLoader.HideSource winner = decision.source();
        switch (decision.kind()) {
            case NO_SOURCE -> out.add("Hide source: none matches, so it drops no hide");
            case SUPPRESSED -> out.add("Hide source: " + describe(winner, type) + " stops its hides (" + (winner.size() == null ? "size none" : "count 0") + ")");
            case HIDE -> out.add("Hide source: " + describe(winner, type) + ": " + winner.size().getSerializedName() + " x" + winner.count()
                    + (winner.trait() != null ? ", trait " + winner.trait() : ""));
        }
        for (HideSourceLoader.HideSource shadowed : matching) {
            if (shadowed != winner) out.add(" - also matches, not used: " + describe(shadowed, type));
        }
        boolean carcassAnimal = type.is(ModTags.CARCASS_ANIMALS);
        boolean butchery = ModList.get().isLoaded("butchery");
        if (!GeneticAnimals.owns(type)) {
            out.add("Stage: its loot table (hide_drops loot modifier), or its death drops when it has no loot table");
            out.add("Carcass: " + (carcassAnimal ? "a carcass tool (#immersive_tanning:carcass_tools) or carcassEnchantments on an adult drops no hide"
                    : "not in #immersive_tanning:carcass_animals, never a carcass kill"));
            return out;
        }
        out.add(GeneticAnimals.summary());
        if (!GeneticAnimals.enabled()) {
            out.add("Stage: none; with the integration off Immersive Tanning adds no hide (leather still becomes Binding Leather"
                    + (ServerConfig.get(ServerConfig.REPLACE_LEATHER_LOOT) ? ")" : " only with replaceLeatherLoot on)"));
            return out;
        }
        out.add("Stage: its death drops, after Genetic Animals' own death loot (the loot modifier skips it); carried items are never used");
        if (decision.dropsHide()) {
            if (GeneticAnimals.mode() == HideMode.NATIVE) {
                out.add("NATIVE: the leather and rabbit hide it actually drops become hides, at most " + winner.size().getSerializedName()
                        + " each (one leather is one leather's worth); genes, coat, age and fire decide that drop, so it is not predicted here."
                        + " No skin, no hide; Looting is only what Genetic Animals itself gives");
            } else {
                out.add("TANNING: an adult drops " + winner.count() + " " + winner.size().getSerializedName() + " hide(s) whatever it drops itself"
                        + (ServerConfig.get(ServerConfig.LOOTING_ADDS_HIDES) ? ", Looting adds up to one per level" : "") + "; juveniles none");
            }
            out.add("Its own leather and rabbit hide " + (ServerConfig.get(ServerConfig.REPLACE_LEATHER_DROPS)
                    ? "are replaced by the hides" : "stay next to the hides (replaceLeatherDrops off)"));
        }
        out.add("Carcass: " + (butchery && carcassAnimal
                ? "only when Butchery actually turns the kill into a carcass (its carcass item appears at the death); then no hide"
                : "no carcass route (" + (butchery ? "not in #immersive_tanning:carcass_animals" : "Butchery is not installed") + "); a cleaver changes nothing"));
        return out;
    }

    private static String describe(HideSourceLoader.HideSource source, EntityType<?> type) {
        return source.id() + " (priority " + source.priority() + ", by " + String.join(", ", source.matchedBy(type)) + ")";
    }

    private TanningCommands() {}
}
