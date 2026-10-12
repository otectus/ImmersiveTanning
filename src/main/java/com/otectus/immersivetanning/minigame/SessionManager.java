package com.otectus.immersivetanning.minigame;

import com.otectus.immersivetanning.advancement.ModAdvancements;
import com.otectus.immersivetanning.api.ImmersiveTanningAPI;
import com.otectus.immersivetanning.api.event.HideFleshedEvent;
import com.otectus.immersivetanning.api.event.LeatherworkStitchedEvent;
import com.otectus.immersivetanning.blockentity.LeatherworkersBenchBlockEntity;
import com.otectus.immersivetanning.blockentity.TannersRackBlockEntity;
import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.hide.HideData;
import com.otectus.immersivetanning.hide.HideItems;
import com.otectus.immersivetanning.item.AwlItem;
import com.otectus.immersivetanning.item.ProcessedHideItem;
import com.otectus.immersivetanning.item.TannersKnifeItem;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.leather.LeatherData;
import com.otectus.immersivetanning.leather.LeatherworkData;
import com.otectus.immersivetanning.leather.TanningQuality;
import com.otectus.immersivetanning.network.ModNetwork;
import com.otectus.immersivetanning.network.packet.CloseSessionPacket;
import com.otectus.immersivetanning.network.packet.FleshStrokePacket;
import com.otectus.immersivetanning.network.packet.OpenBenchPacket;
import com.otectus.immersivetanning.network.packet.OpenFinishPacket;
import com.otectus.immersivetanning.network.packet.OpenFleshingPacket;
import com.otectus.immersivetanning.network.packet.SessionResultPacket;
import com.otectus.immersivetanning.network.packet.StitchResultPacket;
import com.otectus.immersivetanning.network.packet.StitchStartPacket;
import com.otectus.immersivetanning.recipe.AuxInventory;
import com.otectus.immersivetanning.recipe.AuxiliaryIngredient;
import com.otectus.immersivetanning.recipe.LeatherStock;
import com.otectus.immersivetanning.recipe.LeatherworkingRecipe;
import com.otectus.immersivetanning.recipe.TanningData;
import com.otectus.immersivetanning.registry.ModItems;
import com.otectus.immersivetanning.registry.ModSounds;
import com.otectus.immersivetanning.trait.TraitRef;
import com.otectus.immersivetanning.util.Feedback;
import com.otectus.immersivetanning.util.StationEffects;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Owns every active fleshing and stitching session. The server generates seeds, keeps its own copy of each minigame,
 * validates every input (session, player, order, timing, distance, tool) and computes scores itself. Clients only
 * report input events; they never submit a score or an item.
 *
 * <p>Material safety: fleshing never removes the hide from the rack until it is done, so a cancelled session loses
 * nothing. Leather and ingredients for a bench piece leave the player's inventory only into the bench's persisted
 * escrow, and leave the escrow exactly once - consumed on completion, or refunded on cancellation. On chunk unload the
 * escrow stays in the saved bench and is dropped there when it next loads. Failure never destroys material: a poor or
 * timed-out minigame still yields its hide or piece, only at a lower score.</p>
 */
public final class SessionManager {
    private static final Map<UUID, TanningSession> BY_PLAYER = new HashMap<>();
    private static final Map<GlobalPos, TanningSession> BY_STATION = new HashMap<>();
    private static final Random RANDOM = new Random();
    private static final long SELECTION_TIMEOUT_MS = 120_000L;
    private static final long EARLY_INPUT_MS = 250L;
    /** Knife speed cap in grid cells per millisecond (about 30 hide-widths a second): only teleports are refused. */
    private static final float MAX_CELLS_PER_MS = 2.0F;
    private static final float MAX_PATH_BUDGET = 256F;
    private static final String LAST_FINISH = "immersive_tanning_last_finish";
    private static int nextId = new Random().nextInt(1 << 24);
    private static int tickCounter;

    public static Optional<TanningSession> byPlayer(UUID player) {
        return Optional.ofNullable(BY_PLAYER.get(player));
    }

    @Nullable
    public static TanningSession atStation(Level level, BlockPos pos) {
        return BY_STATION.get(GlobalPos.of(level.dimension(), pos));
    }

    public static Collection<TanningSession> all() {
        return List.copyOf(BY_PLAYER.values());
    }

    // ------------------------------------------------------------------ fleshing

    public static void openFleshing(ServerPlayer player, TannersRackBlockEntity rack, InteractionHand hand) {
        HideData hide = rack.hide();
        if (rack.mode() != TannersRackBlockEntity.Mode.RAW || hide == null) return;
        if (rack.isLockedFor(player.getUUID())) {
            Feedback.fail(player, "station_in_use");
            return;
        }
        cancelExisting(player);
        FleshingSession session = new FleshingSession(nextId(), player.getUUID(), player.level().dimension(), rack.getBlockPos(),
                hand, RANDOM.nextLong(), Util.getMillis(), hide.size());
        session.allowedMs = ImmersiveTanningAPI.assistedFleshingMs(player, ServerConfig.get(ServerConfig.FLESHING_TIME_SECONDS) * 1000);
        int ready = ServerConfig.get(ServerConfig.READY_DELAY_MS);
        session.startMs = Util.getMillis() + ready;
        register(session);
        rack.lock(player.getUUID());
        send(player, new OpenFleshingPacket(session.id, rack.getBlockPos(), hide.size(), session.seed, session.allowedMs, ready, hide.trait()));
    }

    public static void fleshStroke(ServerPlayer player, FleshStrokePacket packet, long arrivalMs) {
        FleshingSession session = session(player, packet.sessionId(), FleshingSession.class);
        if (session == null || !session.isPlaying()) return;
        if (packet.sequence() <= session.lastSequence) return; // duplicate or replayed packet
        session.lastSequence = packet.sequence();
        long elapsed = arrivalMs - session.startMs;
        if (elapsed < -EARLY_INPUT_MS) return;
        int time = SessionTiming.validate(packet.clientMs(), elapsed, player.latency, session.lastValidMs);
        if (time > session.allowedMs) {
            finishFleshing(player, session, true);
            return;
        }
        session.pathBudget = Math.min(MAX_PATH_BUDGET, session.pathBudget + (time - session.lastPathMs) * MAX_CELLS_PER_MS);
        session.lastPathMs = time;
        session.lastValidMs = time;

        FleshingSurface surface = session.surface;
        float removedBefore = surface.coverage();
        int n = Math.min(packet.xs().length, packet.ys().length);
        for (int i = 0; i < n; i++) {
            int x = Math.max(0, Math.min(FleshingSurface.COORD_MAX, packet.xs()[i]));
            int y = Math.max(0, Math.min(FleshingSurface.COORD_MAX, packet.ys()[i]));
            if (i == 0 && packet.begins()) {
                surface.begin(x, y);
            } else {
                if (!surface.isStroking()) break;
                float dx = (x - session.lastQX) / (float) FleshingSurface.COORD_MAX * FleshingSurface.W;
                float dy = (y - session.lastQY) / (float) FleshingSurface.COORD_MAX * FleshingSurface.H;
                float length = (float) Math.sqrt(dx * dx + dy * dy);
                if (length > session.pathBudget) break; // faster than any hand: the rest of the batch is refused
                session.pathBudget -= length;
                surface.drag(x, y);
            }
            session.lastQX = x;
            session.lastQY = y;
        }
        if (packet.ends()) surface.end();

        if (surface.coverage() > removedBefore) {
            ServerLevel level = player.serverLevel();
            level.playSound(player, session.pos, ModSounds.RACK_SCRAPE.get(), SoundSource.BLOCKS, 0.5F, 0.9F + player.getRandom().nextFloat() * 0.2F);
            StationEffects.send(level, session.pos, StationEffects.SCRAPE, surface.coverage() - removedBefore);
        }
        if (surface.isComplete()) finishFleshing(player, session, false);
    }

    /** Mounts the next raw hide from the player's inventory on an empty rack and starts fleshing it. */
    public static void fleshNext(ServerPlayer player, BlockPos pos) {
        if (!player.level().isLoaded(pos) || !near(player, pos) || BY_PLAYER.containsKey(player.getUUID())) return;
        if (!(player.level().getBlockEntity(pos) instanceof TannersRackBlockEntity rack) || !rack.isEmpty() || rack.isLocked()) return;
        InteractionHand hand = player.getMainHandItem().getItem() instanceof TannersKnifeItem ? InteractionHand.MAIN_HAND
                : player.getOffhandItem().getItem() instanceof TannersKnifeItem ? InteractionHand.OFF_HAND : null;
        if (hand == null) return;
        net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        for (int slot = 0; slot < inv.items.size(); slot++) {
            ItemStack stack = inv.items.get(slot);
            Optional<HideData> raw = HideItems.resolve(player.level(), stack);
            if (raw.isPresent()) {
                rack.mountRaw(raw.get());
                if (!player.getAbilities().instabuild) stack.shrink(1);
                player.level().playSound(null, pos, ModSounds.RACK_MOUNT.get(), SoundSource.BLOCKS, 0.9F, 1.0F);
                openFleshing(player, rack, hand);
                return;
            }
        }
        Feedback.info(player, "no_more_hides");
    }

    public static void finishFleshingEarly(ServerPlayer player, int sessionId) {
        FleshingSession session = session(player, sessionId, FleshingSession.class);
        if (session != null && session.isPlaying()) finishFleshing(player, session, false);
    }

    private static void finishFleshing(ServerPlayer player, FleshingSession session, boolean timedOut) {
        TannersRackBlockEntity rack = rackAt(player.serverLevel(), session);
        if (rack == null || rack.mode() != TannersRackBlockEntity.Mode.RAW || rack.hide() == null) {
            cancel(session, player, message("station_missing"));
            return;
        }
        unregister(session);
        FleshingSurface surface = session.surface;
        // Progression mods hear the score before Integrity is rounded from it and may adjust it.
        HideFleshedEvent fleshed = new HideFleshedEvent(player, rack.hide(), surface.score(), session.pos, timedOut);
        MinecraftForge.EVENT_BUS.post(fleshed);
        HideData scraped = rack.hide().fleshed(fleshed.getScore());
        rack.unlock();
        rack.clear();
        ItemHandlerHelper.giveItemToPlayer(player, ModItems.SCRAPED_HIDE.get().create(scraped));

        ItemStack knife = player.getItemInHand(session.hand);
        if (knife.getItem() instanceof TannersKnifeItem && !player.getAbilities().instabuild) {
            knife.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(session.hand));
        }
        player.level().playSound(null, session.pos, ModSounds.RACK_MOUNT.get(), SoundSource.BLOCKS, 0.8F, 1.2F);
        ModAdvancements.award(player, ModAdvancements.FLESHED);
        send(player, new SessionResultPacket(session.id, TanningSession.Kind.FLESHING, scraped.integrity(),
                surface.coverage(), surface.control(), surface.overwork(), timedOut));
    }

    // ------------------------------------------------------------------ finishing (no minigame)

    public static void openFinish(ServerPlayer player, TannersRackBlockEntity rack, ItemStack wetHide) {
        send(player, new OpenFinishPacket(rack.getBlockPos(), ProcessedHideItem.data(wetHide), lastFinish(player)));
    }

    /** Mounts one wet hide from the player's hand with the chosen finish. */
    public static void selectFinish(ServerPlayer player, BlockPos pos, Finish finish) {
        if (!player.level().isLoaded(pos) || !near(player, pos)) return;
        if (!(player.level().getBlockEntity(pos) instanceof TannersRackBlockEntity rack)) return;
        if (!rack.isEmpty()) {
            Feedback.fail(player, "rack_occupied");
            return;
        }
        InteractionHand hand = null;
        for (InteractionHand h : InteractionHand.values()) {
            if (player.getItemInHand(h).is(ModItems.WET_TANNED_HIDE.get())) {
                hand = h;
                break;
            }
        }
        if (hand == null) {
            Feedback.fail(player, "need_wet_hide");
            return;
        }
        mountWet(player, rack, player.getItemInHand(hand), finish);
    }

    public static void mountWet(ServerPlayer player, TannersRackBlockEntity rack, ItemStack wetHide, Finish finish) {
        HideData data = ProcessedHideItem.data(wetHide);
        if (!player.getAbilities().instabuild) wetHide.shrink(1);
        rack.mountWet(data, finish);
        setLastFinish(player, finish);
        player.level().playSound(null, rack.getBlockPos(), ModSounds.RACK_MOUNT.get(), SoundSource.BLOCKS, 0.9F, 0.8F);
        Feedback.info(player, "hide_drying", finish.displayName());
    }

    public static Finish lastFinish(Player player) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        Finish finish = Finish.byName(persisted.getString(LAST_FINISH));
        return finish != null ? finish : Finish.HARNESS;
    }

    private static void setLastFinish(Player player, Finish finish) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        persisted.putString(LAST_FINISH, finish.getSerializedName());
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    // ------------------------------------------------------------------ stitching

    public static void openBench(ServerPlayer player, LeatherworkersBenchBlockEntity bench, InteractionHand hand) {
        if (bench.isLockedFor(player.getUUID())) {
            Feedback.fail(player, "station_in_use");
            return;
        }
        cancelExisting(player);
        int protectedSlot = AuxInventory.protectedSlot(player, hand);
        List<LeatherStock.Group> groups = LeatherStock.groups(player);
        if (groups.isEmpty()) {
            Feedback.fail(player, "need_leather");
            return;
        }
        List<LeatherworkingRecipe> recipes = new ArrayList<>(TanningData.current().recipes());
        recipes.sort(Comparator.comparing((LeatherworkingRecipe r) -> r.result().getHoverName().getString())
                .thenComparing(r -> r.getId().toString()));
        List<OpenBenchPacket.Entry> entries = new ArrayList<>();
        List<StitchingSession.Offer> offers = new ArrayList<>();
        boolean missingAux = false;
        Component denied = null;
        for (LeatherworkingRecipe recipe : recipes) {
            // A gated recipe is left off the list; the first reason is what the player hears if nothing is left.
            Optional<Component> denial = ImmersiveTanningAPI.recipeDenial(player, recipe);
            if (denial.isPresent()) {
                if (denied == null) denied = denial.get();
                continue;
            }
            for (LeatherStock.Group group : groups) {
                if (group.count() < recipe.leather()) continue;
                if (!AuxInventory.hasAll(player, recipe.auxiliary(), protectedSlot)) {
                    missingAux = true;
                    continue;
                }
                ResourceLocation trait = group.trait() == null ? null : group.trait().id();
                offers.add(new StitchingSession.Offer(recipe.getId(), group.finish(), trait));
                entries.add(new OpenBenchPacket.Entry(recipe.getId(), recipe.result().copy(), recipe.leather(), group.finish(),
                        group.trait(), group.integrityFor(recipe.leather()),
                        recipe.auxiliary().stream().map(AuxiliaryIngredient::displayStack).toList()));
                if (entries.size() >= OpenBenchPacket.MAX_ENTRIES) break;
            }
        }
        if (entries.isEmpty()) {
            if (denied != null && !missingAux) Feedback.fail(player, denied);
            else Feedback.fail(player, missingAux ? "missing_auxiliary" : "need_more_leather");
            return;
        }
        StitchingSession session = new StitchingSession(nextId(), player.getUUID(), player.level().dimension(), bench.getBlockPos(),
                hand, RANDOM.nextLong(), Util.getMillis());
        session.offered = List.copyOf(offers);
        register(session);
        bench.lock(player.getUUID());
        send(player, new OpenBenchPacket(session.id, bench.getBlockPos(), entries));
    }

    public static void selectBenchRecipe(ServerPlayer player, int sessionId, ResourceLocation recipeId, Finish finish,
                                         @Nullable ResourceLocation traitId) {
        StitchingSession session = session(player, sessionId, StitchingSession.class);
        if (session == null || session.isPlaying() || session.recipe != null) return;
        LeatherworkersBenchBlockEntity bench = benchAt(player.serverLevel(), session);
        if (bench == null) {
            cancel(session, player, message("station_missing"));
            return;
        }
        LeatherworkingRecipe recipe = TanningData.current().recipe(recipeId);
        if (recipe == null || !session.offered.contains(new StitchingSession.Offer(recipeId, finish, traitId))) {
            cancel(session, player, message("recipe_unavailable"));
            return;
        }
        if (!toolValid(player, session)) {
            cancel(session, player, message("tool_changed"));
            return;
        }
        // The authoritative gate check: the offer list was filtered, but levels and rules may have moved since.
        Optional<Component> denied = ImmersiveTanningAPI.recipeDenial(player, recipe);
        if (denied.isPresent()) {
            cancel(session, player, denied.get());
            return;
        }
        LeatherStock.Group group = null;
        for (LeatherStock.Group g : LeatherStock.groups(player)) {
            if (g.finish() == finish && (g.trait() == null ? traitId == null : g.trait().id().equals(traitId))) group = g;
        }
        int protectedSlot = AuxInventory.protectedSlot(player, session.hand);
        if (group == null || group.count() < recipe.leather() || !AuxInventory.hasAll(player, recipe.auxiliary(), protectedSlot)) {
            cancel(session, player, message("materials_changed"));
            return;
        }
        List<ItemStack> leather = LeatherStock.take(player, finish, group.trait(), recipe.leather());
        if (leather == null) {
            cancel(session, player, message("materials_changed"));
            return;
        }
        List<ItemStack> aux = AuxInventory.take(player, recipe.auxiliary(), protectedSlot);
        if (aux == null) {
            leather.forEach(s -> ItemHandlerHelper.giveItemToPlayer(player, s));
            cancel(session, player, message("materials_changed"));
            return;
        }
        int integritySum = 0;
        int units = 0;
        for (ItemStack s : leather) {
            int integrity = LeatherData.get(s).map(LeatherData::integrity).orElse(LeatherData.DEFAULT_INTEGRITY);
            integritySum += integrity * s.getCount();
            units += s.getCount();
        }
        List<ItemStack> escrow = new ArrayList<>(leather);
        escrow.addAll(aux);
        bench.beginSession(player.getUUID(), escrow, recipe.result(), finish.leatherColor());

        session.recipe = recipe;
        session.finish = finish;
        session.trait = group.trait();
        session.integrity = units == 0 ? 0 : Math.round(integritySum / (float) units);
        session.leatherUsed = units;
        StitchPattern pattern = SeamPatterns.get(recipe.seam());
        session.run = new StitchRun(pattern);
        session.allowedMs = session.run.endMs();
        int ready = ServerConfig.get(ServerConfig.READY_DELAY_MS);
        session.startMs = Util.getMillis() + ready;
        player.level().playSound(null, session.pos, ModSounds.BENCH_CUT.get(), SoundSource.BLOCKS, 0.9F, 1.0F);
        send(player, new StitchStartPacket(session.id, pattern, ready, recipe.result().copy(), finish, session.trait, session.integrity));
    }

    public static void stitchPress(ServerPlayer player, int sessionId, int sequence, int clientMs, long arrivalMs) {
        StitchingSession session = session(player, sessionId, StitchingSession.class);
        if (session == null || !session.isPlaying() || session.run == null || session.run.isComplete()) return;
        if (sequence <= session.lastSequence) return;
        session.lastSequence = sequence;
        long elapsed = arrivalMs - session.startMs;
        if (elapsed < -EARLY_INPUT_MS) return;
        int time = SessionTiming.validate(clientMs, elapsed, player.latency, session.lastValidMs);
        session.lastValidMs = time;
        StitchRun.Press press = session.run.press(time);
        ServerLevel level = player.serverLevel();
        if (press.outcome() != StitchRun.Outcome.IGNORED) {
            player.swing(session.hand, true);
            boolean hit = press.outcome() == StitchRun.Outcome.HIT;
            level.playSound(player, session.pos, ModSounds.BENCH_STITCH.get(), SoundSource.BLOCKS, hit ? 0.8F : 0.5F,
                    hit ? 0.8F + press.accuracy() * 0.4F : 0.6F);
            StationEffects.send(level, session.pos, StationEffects.STITCH, press.accuracy());
        }
        send(player, new StitchResultPacket(session.id, sequence, press.index(), press.accuracy(), session.run.next()));
        if (session.run.isComplete()) finishStitching(player, session, false);
    }

    private static void finishStitching(ServerPlayer player, StitchingSession session, boolean timedOut) {
        LeatherworkersBenchBlockEntity bench = benchAt(player.serverLevel(), session);
        if (bench == null) {
            cancel(session, player, message("station_missing"));
            return;
        }
        unregister(session);
        session.run.advanceTo(Integer.MAX_VALUE);
        int workmanship = session.run.score();
        bench.completeSession();

        ItemStack result = session.recipe.result().copy();
        // Progression mods hear about the piece before its leatherwork is written and may change the stack or the
        // workmanship; the experience and the Masterwork advancement below follow the final value.
        LeatherworkStitchedEvent stitched = new LeatherworkStitchedEvent(player, result, session.recipe.getId(), session.finish,
                session.integrity, workmanship, session.trait, session.leatherUsed, session.pos, timedOut);
        MinecraftForge.EVENT_BUS.post(stitched);
        workmanship = stitched.getWorkmanship();
        LeatherworkData data = new LeatherworkData(session.finish, session.integrity, workmanship, session.trait, player.getGameProfile().getName());
        data.apply(result);
        LeatherworkData.stampCrafter(result, player.getUUID());
        if (result.getItem() instanceof DyeableLeatherItem dyeable) dyeable.setColor(result, session.finish.leatherColor());
        ItemHandlerHelper.giveItemToPlayer(player, result);
        int perOffcut = ServerConfig.get(ServerConfig.LEATHER_PER_OFFCUT);
        if (perOffcut > 0 && session.leatherUsed / perOffcut > 0) {
            ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(ModItems.LEATHER_SCRAPS.get(), session.leatherUsed / perOffcut));
        }

        ItemStack awl = player.getItemInHand(session.hand);
        if (awl.getItem() instanceof AwlItem && !player.getAbilities().instabuild) {
            awl.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(session.hand));
        }
        player.level().playSound(null, session.pos, ModSounds.BENCH_FINISH.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        StationEffects.send(player.serverLevel(), session.pos, StationEffects.FINISHED, workmanship / 100F);
        int xp = benchExperience(data);
        if (xp > 0) ExperienceOrb.award(player.serverLevel(), Vec3.atCenterOf(session.pos).add(0, 0.6, 0), xp);
        ModAdvancements.award(player, ModAdvancements.LEATHERWORK);
        if (data.quality() == TanningQuality.MASTERWORK) ModAdvancements.award(player, ModAdvancements.MASTERWORK);
        if (session.trait != null) ModAdvancements.award(player, ModAdvancements.TROPHY);
        send(player, new SessionResultPacket(session.id, TanningSession.Kind.STITCHING, workmanship, 0F, 0F, 0F, timedOut));
    }

    /** Experience for a finished piece: the configured amount at a score of 50, half at 0, one and a half at 100. */
    public static int benchExperience(LeatherworkData data) {
        int base = ServerConfig.get(ServerConfig.BENCH_EXPERIENCE);
        return base <= 0 ? 0 : Math.max(1, Math.round(base * (0.5F + data.overallScore() / 100F)));
    }

    // ------------------------------------------------------------------ cancellation and upkeep

    public static void cancelFromClient(ServerPlayer player, int sessionId) {
        TanningSession session = BY_PLAYER.get(player.getUUID());
        if (session != null && session.id == sessionId) cancel(session, player, null);
    }

    public static void onLogout(ServerPlayer player) {
        TanningSession session = BY_PLAYER.get(player.getUUID());
        if (session != null) cancel(session, player, null);
    }

    public static void onDeath(ServerPlayer player) {
        TanningSession session = BY_PLAYER.get(player.getUUID());
        if (session != null) cancel(session, player, message("session_interrupted"));
    }

    /** The station block was broken or replaced: refund immediately, before its contents are dropped. */
    public static void onStationRemoved(Level level, BlockPos pos) {
        TanningSession session = atStation(level, pos);
        if (session == null || level.getServer() == null) return;
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(session.playerId);
        cancel(session, player, message("station_missing"));
    }

    /** The station's chunk is unloading and has already been saved; escrow stays in the bench for its next load. */
    public static void onStationUnloaded(Level level, BlockPos pos) {
        TanningSession session = atStation(level, pos);
        if (session == null) return;
        unregister(session);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TannersRackBlockEntity rack) rack.unlock();
        if (be instanceof LeatherworkersBenchBlockEntity bench) bench.unlock();
        if (level.getServer() != null) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(session.playerId);
            if (player != null) send(player, new CloseSessionPacket(session.id, message("session_interrupted")));
        }
    }

    public static void onServerStopping(MinecraftServer server) {
        for (TanningSession session : all()) {
            cancel(session, server.getPlayerList().getPlayer(session.playerId), null);
        }
        BY_PLAYER.clear();
        BY_STATION.clear();
    }

    public static void tick(MinecraftServer server) {
        if (BY_PLAYER.isEmpty()) return;
        tickCounter++;
        long now = Util.getMillis();
        for (TanningSession session : all()) {
            ServerPlayer player = server.getPlayerList().getPlayer(session.playerId);
            if (player == null) {
                cancel(session, null, null);
                continue;
            }
            boolean choosing = session instanceof StitchingSession s && s.recipe == null;
            if (choosing) {
                if (now - session.createdMs > SELECTION_TIMEOUT_MS) cancel(session, player, message("session_expired"));
                else if (tickCounter % 5 == 0 && !stillValid(player, session)) cancel(session, player, message("session_interrupted"));
                continue;
            }
            long elapsed = now - session.startMs;
            if (elapsed - SessionTiming.graceMs(player.latency) > session.allowedMs) {
                if (session instanceof FleshingSession f) finishFleshing(player, f, true);
                else if (session instanceof StitchingSession s) finishStitching(player, s, true);
                continue;
            }
            if (tickCounter % 5 == 0 && !stillValid(player, session)) cancel(session, player, message("session_interrupted"));
        }
    }

    private static void cancel(TanningSession session, @Nullable ServerPlayer player, @Nullable Component reason) {
        unregister(session);
        MinecraftServer server = player != null ? player.getServer() : ServerLifecycleHooks.getCurrentServer();
        ServerLevel level = server != null ? server.getLevel(session.dimension) : null;
        if (level != null && level.isLoaded(session.pos)) {
            BlockEntity be = level.getBlockEntity(session.pos);
            if (be instanceof TannersRackBlockEntity rack) {
                rack.unlock(); // the hide simply stays on the rack
            } else if (be instanceof LeatherworkersBenchBlockEntity bench) {
                for (ItemStack stack : bench.takeEscrow()) {
                    if (player != null && player.isAlive()) player.getInventory().add(stack);
                    if (!stack.isEmpty()) {
                        Containers.dropItemStack(level, session.pos.getX() + 0.5, session.pos.getY() + 1.0, session.pos.getZ() + 0.5, stack);
                    }
                }
            }
        }
        if (player != null && reason != null) send(player, new CloseSessionPacket(session.id, reason));
    }

    private static boolean stillValid(ServerPlayer player, TanningSession session) {
        if (!player.isAlive() || player.level().dimension() != session.dimension) return false;
        if (!near(player, session.pos)) return false;
        if (!toolValid(player, session)) return false;
        return session instanceof FleshingSession ? rackAt(player.serverLevel(), session) != null : benchAt(player.serverLevel(), session) != null;
    }

    private static boolean toolValid(ServerPlayer player, TanningSession session) {
        ItemStack held = player.getItemInHand(session.hand);
        return session instanceof FleshingSession ? held.getItem() instanceof TannersKnifeItem : held.getItem() instanceof AwlItem;
    }

    private static boolean near(Player player, BlockPos pos) {
        double max = ServerConfig.get(ServerConfig.MAX_STATION_DISTANCE);
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= max * max;
    }

    private static void cancelExisting(ServerPlayer player) {
        TanningSession existing = BY_PLAYER.get(player.getUUID());
        if (existing != null) cancel(existing, player, null);
    }

    @Nullable
    private static <T extends TanningSession> T session(ServerPlayer player, int sessionId, Class<T> type) {
        TanningSession session = BY_PLAYER.get(player.getUUID());
        if (session == null || session.id != sessionId || !type.isInstance(session)) return null;
        if (!player.level().dimension().equals(session.dimension) || !near(player, session.pos)) return null;
        return type.cast(session);
    }

    @Nullable
    private static TannersRackBlockEntity rackAt(ServerLevel level, TanningSession session) {
        if (!level.isLoaded(session.pos)) return null;
        return level.getBlockEntity(session.pos) instanceof TannersRackBlockEntity rack ? rack : null;
    }

    @Nullable
    private static LeatherworkersBenchBlockEntity benchAt(ServerLevel level, TanningSession session) {
        if (!level.isLoaded(session.pos)) return null;
        return level.getBlockEntity(session.pos) instanceof LeatherworkersBenchBlockEntity bench ? bench : null;
    }

    private static void register(TanningSession session) {
        BY_PLAYER.put(session.playerId, session);
        BY_STATION.put(session.station(), session);
    }

    private static void unregister(TanningSession session) {
        BY_PLAYER.remove(session.playerId, session);
        BY_STATION.remove(session.station(), session);
    }

    private static int nextId() {
        return ++nextId;
    }

    private static Component message(String key) {
        return Component.translatable("message.immersive_tanning." + key);
    }

    private static void send(ServerPlayer player, Object packet) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    private SessionManager() {}
}
