package com.otectus.immersivetanning.registry;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.blockentity.LeatherworkersBenchBlockEntity;
import com.otectus.immersivetanning.blockentity.TannersRackBlockEntity;
import com.otectus.immersivetanning.blockentity.TannersVatBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ImmersiveTanning.MOD_ID);

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<TannersRackBlockEntity>> TANNERS_RACK = BLOCK_ENTITIES.register("tanners_rack",
            () -> BlockEntityType.Builder.of(TannersRackBlockEntity::new, ModBlocks.TANNERS_RACK.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<TannersVatBlockEntity>> TANNERS_VAT = BLOCK_ENTITIES.register("tanners_vat",
            () -> BlockEntityType.Builder.of(TannersVatBlockEntity::new, ModBlocks.TANNERS_VAT.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<LeatherworkersBenchBlockEntity>> LEATHERWORKERS_BENCH = BLOCK_ENTITIES.register("leatherworkers_bench",
            () -> BlockEntityType.Builder.of(LeatherworkersBenchBlockEntity::new, ModBlocks.LEATHERWORKERS_BENCH.get()).build(null));

    private ModBlockEntities() {}
}
