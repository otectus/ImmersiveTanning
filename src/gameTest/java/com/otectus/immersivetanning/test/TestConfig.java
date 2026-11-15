package com.otectus.immersivetanning.test;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.recipe.TanningData;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;

/**
 * Every batch runs on the server config's defaults, held in memory. The world's config file autosaves on each change
 * and Forge's watcher thread reloads it while the server thread keeps changing values, so tests that change a value
 * (and put it back) could leave a half-written or stale file behind for later tests and runs.
 */
@GameTestHolder(ImmersiveTanning.MOD_ID)
public final class TestConfig {

    @BeforeBatch(batch = "defaultBatch")
    public static void defaultBatch(ServerLevel level) {
        defaults(level);
    }

    @BeforeBatch(batch = "datapack_reload")
    public static void datapackReloadBatch(ServerLevel level) {
        defaults(level);
    }

    /** Default config in memory (no file, no watcher) and the bench recipes rebuilt from it. */
    private static void defaults(ServerLevel level) {
        ServerConfig.SPEC.setConfig(CommentedConfig.inMemory());
        TanningData.rebuild(level.getServer());
    }

    private TestConfig() {}
}
