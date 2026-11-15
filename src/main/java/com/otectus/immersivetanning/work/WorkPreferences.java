package com.otectus.immersivetanning.work;

import com.otectus.immersivetanning.leather.Finish;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/** Per-player choices that outlive a station: the last finish worked on a rack (the sneak-use shortcut repeats it). */
public final class WorkPreferences {
    /** Same key as 1.2, so the last finish carries over. */
    private static final String LAST_FINISH = "immersive_tanning_last_finish";

    public static Finish lastFinish(Player player) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        Finish finish = Finish.byName(persisted.getString(LAST_FINISH));
        return finish != null ? finish : Finish.HARNESS;
    }

    public static void setLastFinish(Player player, Finish finish) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        persisted.putString(LAST_FINISH, finish.getSerializedName());
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    private WorkPreferences() {}
}
