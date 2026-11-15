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

    // 1.3: hands-on work. Every one has its own synthesized sound and subtitle (see art/tanning_sounds.py).
    public static final RegistryObject<SoundEvent> RACK_SCRAPE_ROUGH = register("block.tanners_rack.scrape_rough");
    public static final RegistryObject<SoundEvent> RACK_LACE = register("block.tanners_rack.lace");
    public static final RegistryObject<SoundEvent> RACK_SEAT = register("block.tanners_rack.seat");
    public static final RegistryObject<SoundEvent> RACK_BURNISH = register("block.tanners_rack.burnish");
    public static final RegistryObject<SoundEvent> RACK_SMOOTH = register("block.tanners_rack.smooth");
    public static final RegistryObject<SoundEvent> WET_LEATHER = register("block.tanners_rack.wet_leather");
    public static final RegistryObject<SoundEvent> VAT_POUR = register("block.tanners_vat.pour");
    public static final RegistryObject<SoundEvent> VAT_SWEEP = register("block.tanners_vat.sweep");
    public static final RegistryObject<SoundEvent> VAT_LIFT = register("block.tanners_vat.lift");
    public static final RegistryObject<SoundEvent> BENCH_PANEL = register("block.leatherworkers_bench.panel");
    public static final RegistryObject<SoundEvent> BENCH_NEEDLE = register("block.leatherworkers_bench.needle");
    public static final RegistryObject<SoundEvent> BENCH_TIGHTEN = register("block.leatherworkers_bench.tighten");
    public static final RegistryObject<SoundEvent> BENCH_TIE = register("block.leatherworkers_bench.tie");
    public static final RegistryObject<SoundEvent> BENCH_PAGE = register("block.leatherworkers_bench.page");
    public static final RegistryObject<SoundEvent> BENCH_LOOSEN = register("block.leatherworkers_bench.loosen");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ImmersiveTanning.id(name)));
    }

    private ModSounds() {}
}
