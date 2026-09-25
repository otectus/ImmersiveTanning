package com.otectus.immersivetanning.smoketest;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

/** Captures the JEI runtime so the smoke test can open the tannery categories. */
@JeiPlugin
public class SmokeJeiPlugin implements IModPlugin {
    static volatile IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(SmokeTestAgent.MOD_ID, "jei");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }
}
