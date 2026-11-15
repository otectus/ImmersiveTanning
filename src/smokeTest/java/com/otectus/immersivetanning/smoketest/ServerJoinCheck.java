package com.otectus.immersivetanning.smoketest;

import com.otectus.immersivetanning.blockentity.TannersRackBlockEntity;
import com.otectus.immersivetanning.client.station.StationFocus;
import com.otectus.immersivetanning.minigame.FleshingSurface;
import com.otectus.immersivetanning.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The client half of the dedicated-server check ({@code -PjoinServer=host:port}): joins a real server running the
 * production jar, and once the console has made it an operator places a rack by command, leans in with a raw hide,
 * scrapes one stroke through the focus screen and leaves, so the station packets cross a real connection both ways.
 */
final class ServerJoinCheck {
    private static int phase;
    private static int ticks;
    private static BlockPos rack;
    private static float coverageBefore;

    static void connect(Minecraft mc, String address) {
        ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(address), new ServerData("Immersive Tanning check", address, false), false);
    }

    /** One client tick in the joined world; true once the check is over (passed or not). */
    static boolean tick(Minecraft mc, Map<String, Object> checks) {
        ticks++;
        FocusDriver.tick();
        switch (phase) {
            case 0 -> {
                if (ticks < 60) return false;
                checks.put("join_connected_to_dedicated_server", mc.getConnection() != null && mc.getCurrentServer() != null);
                next();
            }
            case 1 -> {
                if (mc.player.hasPermissions(2)) {
                    rack = mc.player.blockPosition().relative(Direction.NORTH, 2);
                    mc.player.connection.sendCommand("setblock " + rack.getX() + " " + rack.getY() + " " + rack.getZ()
                            + " immersive_tanning:tanners_rack[facing=south]");
                    mc.player.connection.sendCommand("give @s immersive_tanning:tanners_knife");
                    mc.player.connection.sendCommand("give @s immersive_tanning:large_hide");
                    next();
                } else if (ticks > 1200) {
                    checks.put("join_made_operator", false);
                    return true;
                }
            }
            case 2 -> {
                if (ticks == 30) {
                    Inventory inv = mc.player.getInventory();
                    for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
                        if (inv.getItem(slot).is(ModItems.rawHide(com.otectus.immersivetanning.hide.HideSize.LARGE))) inv.selected = slot;
                    }
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(rack).add(0, 0.5, 0), Direction.UP, rack, false));
                }
                TannersRackBlockEntity be = rack();
                if (ticks > 30 && be != null && be.mode() == TannersRackBlockEntity.Mode.RAW && FocusDriver.focusedAt(rack) && be.work() != null) {
                    checks.put("join_focus_opens_on_dedicated_server", true);
                    coverageBefore = be.work().surface().coverage();
                    List<Vec3> stroke = new ArrayList<>();
                    for (int i = 0; i <= 24; i++) stroke.add(FocusDriver.hide(0.15F + 0.7F * i / 24F, 0.5F));
                    FocusDriver.wait(12);
                    FocusDriver.drag(rack, stroke, 8);
                    next();
                } else if (ticks > 300) {
                    checks.put("join_focus_opens_on_dedicated_server", false);
                    return true;
                }
            }
            case 3 -> {
                TannersRackBlockEntity be = rack();
                FleshingSurface surface = be == null || be.work() == null ? null : be.work().surface();
                if (FocusDriver.idle() && surface != null && surface.coverage() > coverageBefore) {
                    checks.put("join_stroke_scrapes_on_dedicated_server", true);
                    checks.put("join_stroke_detail", "clean " + coverageBefore + " -> " + surface.coverage() + ", last refusal " + StationFocus.lastRefusal());
                    FocusDriver.key(GLFW.GLFW_KEY_ESCAPE);
                    next();
                } else if (ticks > 400) {
                    checks.put("join_stroke_scrapes_on_dedicated_server", false);
                    checks.put("join_stroke_detail", "clean " + coverageBefore + " -> " + (surface == null ? "?" : surface.coverage())
                            + ", last refusal " + StationFocus.lastRefusal());
                    return true;
                }
            }
            default -> {
                if (!StationFocus.active() && FocusDriver.idle() && ticks > 10) {
                    checks.put("join_left_the_station", true);
                    return true;
                }
                if (ticks > 200) {
                    checks.put("join_left_the_station", false);
                    return true;
                }
            }
        }
        return false;
    }

    private static void next() {
        phase++;
        ticks = 0;
    }

    private static TannersRackBlockEntity rack() {
        return Minecraft.getInstance().level.getBlockEntity(rack) instanceof TannersRackBlockEntity be ? be : null;
    }

    private ServerJoinCheck() {}
}
