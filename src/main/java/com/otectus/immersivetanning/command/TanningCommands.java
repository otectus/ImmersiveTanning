package com.otectus.immersivetanning.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.otectus.immersivetanning.blockentity.TannersRackBlockEntity;
import com.otectus.immersivetanning.blockentity.TannersVatBlockEntity;
import com.otectus.immersivetanning.hide.HideSourceLoader;
import com.otectus.immersivetanning.recipe.TanningData;
import com.otectus.immersivetanning.registry.ModRecipeTypes;
import com.otectus.immersivetanning.trait.HideTraitLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

/**
 * {@code /immersive_tanning report} - recipe, hide and trait summary with detection notes.
 * {@code /immersive_tanning advance <pos> <ticks>} - moves a vat's or rack's clock forward (operators, for testing).
 */
public final class TanningCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
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
                    int shown = 0;
                    for (String note : data.notes()) {
                        if (shown++ >= 20) break;
                        source.sendSuccess(() -> Component.literal(" - " + note), false);
                    }
                    return data.recipes().size();
                }))
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

    private TanningCommands() {}
}
