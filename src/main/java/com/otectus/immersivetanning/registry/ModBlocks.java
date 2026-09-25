package com.otectus.immersivetanning.registry;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.block.LeatherworkersBenchBlock;
import com.otectus.immersivetanning.block.TannersRackBlock;
import com.otectus.immersivetanning.block.TannersVatBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ImmersiveTanning.MOD_ID);

    public static final RegistryObject<TannersRackBlock> TANNERS_RACK = BLOCKS.register("tanners_rack",
            () -> new TannersRackBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.0F, 3.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)));

    public static final RegistryObject<TannersVatBlock> TANNERS_VAT = BLOCKS.register("tanners_vat",
            () -> new TannersVatBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F, 3.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)));

    public static final RegistryObject<LeatherworkersBenchBlock> LEATHERWORKERS_BENCH = BLOCKS.register("leatherworkers_bench",
            () -> new LeatherworkersBenchBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BROWN)
                    .strength(2.5F, 3.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)));

    private ModBlocks() {}
}
