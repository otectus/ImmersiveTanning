package com.otectus.immersivetanning.registry;

import com.otectus.immersivetanning.ImmersiveTanning;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ImmersiveTanning.MOD_ID);

    public static final RegistryObject<SoundEvent> RACK_MOUNT = register("block.tanners_rack.mount");
    public static final RegistryObject<SoundEvent> RACK_SCRAPE = register("block.tanners_rack.scrape");
    public static final RegistryObject<SoundEvent> RACK_DRIED = register("block.tanners_rack.dried");
    public static final RegistryObject<SoundEvent> VAT_FILL = register("block.tanners_vat.fill");
    public static final RegistryObject<SoundEvent> VAT_BARK = register("block.tanners_vat.bark");
    public static final RegistryObject<SoundEvent> VAT_HIDE = register("block.tanners_vat.hide");
    public static final RegistryObject<SoundEvent> VAT_BUBBLE = register("block.tanners_vat.bubble");
    public static final RegistryObject<SoundEvent> BENCH_CUT = register("block.leatherworkers_bench.cut");
    public static final RegistryObject<SoundEvent> BENCH_STITCH = register("block.leatherworkers_bench.stitch");
    public static final RegistryObject<SoundEvent> BENCH_FINISH = register("block.leatherworkers_bench.finish");
    public static final RegistryObject<SoundEvent> KNIFE_STRIP = register("item.tanners_knife.strip");
    public static final RegistryObject<SoundEvent> AWL_REPAIR = register("item.awl.repair");
    public static final RegistryObject<SoundEvent> ACTION_FAIL = register("ui.tanning.fail");
    public static final RegistryObject<SoundEvent> TIMING_CUE = register("ui.tanning.timing_cue");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ImmersiveTanning.id(name)));
    }

    private ModSounds() {}
}
