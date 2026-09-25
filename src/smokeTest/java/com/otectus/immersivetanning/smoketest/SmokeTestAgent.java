package com.otectus.immersivetanning.smoketest;

import net.minecraftforge.fml.common.Mod;

/**
 * Test-only companion mod used by {@code runClient -PsmokeTest}. It does nothing unless the system property
 * {@code immersive_tanning.smoketest.output} names a directory for its screenshots and result file.
 */
@Mod(SmokeTestAgent.MOD_ID)
public class SmokeTestAgent {
    public static final String MOD_ID = "immersive_tanning_smoketest";
    public static final String OUTPUT_PROPERTY = "immersive_tanning.smoketest.output";

    public SmokeTestAgent() {
    }
}
