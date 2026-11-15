package com.otectus.immersivetanning.station;

import com.otectus.immersivetanning.advancement.ModAdvancements;
import com.otectus.immersivetanning.api.ImmersiveTanningAPI;
import com.otectus.immersivetanning.api.event.LeatherworkStitchedEvent;
import com.otectus.immersivetanning.blockentity.LeatherworkersBenchBlockEntity;
import com.otectus.immersivetanning.config.ServerConfig;
import com.otectus.immersivetanning.item.UnfinishedLeatherworkItem;
import com.otectus.immersivetanning.leather.FieldRepair;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.leather.LeatherData;
import com.otectus.immersivetanning.leather.LeatherworkData;
import com.otectus.immersivetanning.leather.TanningQuality;
import com.otectus.immersivetanning.network.packet.BenchOffersPacket;
import com.otectus.immersivetanning.network.packet.StationAckPacket;
import com.otectus.immersivetanning.network.packet.StationCommandPacket;
import com.otectus.immersivetanning.network.packet.StationStrokePacket;
import com.otectus.immersivetanning.network.packet.WorkResultPacket;
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
import com.otectus.immersivetanning.work.LeatherworkJob;
import com.otectus.immersivetanning.work.MaterialLedger;
import com.otectus.immersivetanning.work.StationLease;
import com.otectus.immersivetanning.work.WorkAction;
import com.otectus.immersivetanning.work.WorkCommandHandler.Reply;
import com.otectus.immersivetanning.work.WorkNet;
import com.otectus.immersivetanning.work.WorkPatterns;
import com.otectus.immersivetanning.work.WorkRules;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.HorseArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SaddleItem;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The Leatherworker's Bench's rules. Materials move between the player and the job's {@link MaterialLedger} only, and
 * leave it once: whole, before any cut, when the job is cancelled; inside an unfinished workpiece when it is carried
 * off; to the output pad (with scraps and crafting remainders) when the final knot commits it. Progression gates are
 * asked before anything is taken and again before the piece is committed; the stitched event, experience,
 * advancements, tool wear and scraps happen exactly once, at that commit, for the player who tied it off.
 */
public final class BenchStationLogic {
    /** Work-plane path a tool may travel per second (teleports are refused) and the most it may bank. */
    private static final float PLANE_PER_SECOND = 7F;
    private static final float PLANE_BURST = 3F;

    public static Reply handle(ServerPlayer player, LeatherworkersBenchBlockEntity bench, StationLease lease, StationCommandPacket p) {
        LeatherworkJob job = bench.job();
        if (job != null && p.job() != 0 && p.job() != job.id() && touchesJob(p.action())) return Reply.refused(StationAckPacket.STALE);
        return switch (p.action()) {
            case BENCH_OFFERS -> {
                WorkNet.to(player, offers(player, bench));
                yield Reply.OK;
            }
            case BENCH_PREPARE -> (p.arg(0, 1) < 0 ? prepareAny(player, bench, p.id()) : prepare(player, bench, p.id(), Finish.byOrdinal(p.arg(0, 1)), p.id2()))
                    ? Reply.OK : Reply.refused(StationAckPacket.NO_MATERIAL);
            case BENCH_AGAIN -> again(player, bench) ? Reply.OK : Reply.refused(StationAckPacket.NO_MATERIAL);
            case BENCH_CANCEL -> cancel(player, bench) ? Reply.OK : Reply.refused(StationAckPacket.REFUSED);
            case BENCH_CUT -> cut(player, bench, lease, p);
            case BENCH_PLACE -> place(player, bench, lease, p);
            case BENCH_STITCH -> stitch(player, bench, lease, p);
            case BENCH_LOOSEN -> loosen(player, bench, p.arg(0, -1));
            case BENCH_TIE -> tie(player, bench) ? Reply.OK : Reply.refused(StationAckPacket.WRONG_STAGE);
            case BENCH_COLLECT -> collect(player, bench) ? Reply.OK : Reply.refused(StationAckPacket.REFUSED);
            case BENCH_TAKE_WORKPIECE -> takeWorkpiece(player, bench) ? Reply.OK : Reply.refused(StationAckPacket.REFUSED);
            case BENCH_RECOVER -> recover(player, bench) ? Reply.OK : Reply.refused(StationAckPacket.REFUSED);
            case BENCH_REPAIR -> prepareRepair(player, bench, p.arg(0, -1)) ? Reply.OK : Reply.refused(StationAckPacket.NO_MATERIAL);
            case BENCH_MOUNT_WORKPIECE -> mountWorkpiece(player, bench, player.getMainHandItem()) ? Reply.OK : Reply.refused(StationAckPacket.NO_MATERIAL);
            case HANDOFF -> handoff(player, bench) ? Reply.OK : Reply.refused(StationAckPacket.NOT_OWNER);
            default -> Reply.refused(StationAckPacket.REFUSED);
        };
    }

    private static boolean touchesJob(WorkAction action) {
        return switch (action) {
            case BENCH_CUT, BENCH_PLACE, BENCH_STITCH, BENCH_LOOSEN, BENCH_TIE, BENCH_CANCEL, BENCH_TAKE_WORKPIECE, BENCH_RECOVER -> true;
            default -> false;
        };
    }

    // ------------------------------------------------------------------ the pattern book

    public static BenchOffersPacket offers(ServerPlayer player, LeatherworkersBenchBlockEntity bench) {
        List<LeatherStock.Group> groups = LeatherStock.groups(player);
        List<LeatherworkingRecipe> recipes = new ArrayList<>(TanningData.current().recipes());
        recipes.sort(Comparator.comparing((LeatherworkingRecipe r) -> r.result().getHoverName().getString()).thenComparing(r -> r.getId().toString()));
        List<BenchOffersPacket.Entry> entries = new ArrayList<>();
        for (LeatherworkingRecipe recipe : recipes) {
            if (entries.size() >= BenchOffersPacket.MAX_ENTRIES) break;
            Component denial = ImmersiveTanningAPI.recipeDenial(player, recipe).orElse(null);
            boolean auxMissing = !AuxInventory.hasAll(player, recipe.auxiliary(), -1);
            List<BenchOffersPacket.Group> usable = new ArrayList<>();
            for (LeatherStock.Group g : groups) {
                if (g.count() >= recipe.leather()) usable.add(new BenchOffersPacket.Group(g.finish(), g.trait(), g.count(), g.integrityFor(recipe.leather())));
            }
            entries.add(new BenchOffersPacket.Entry(recipe.getId(), recipe.result().copy(), recipe.leather(),
                    recipe.auxiliary().stream().map(AuxiliaryIngredient::displayStack).toList(), category(recipe.result()), denial, auxMissing,
                    usable, recipe.patternCuts(), recipe.patternPanels(), recipe.patternStitches()));
        }
        return new BenchOffersPacket(bench.getBlockPos(), entries, bench.lastRecipe(), bench.lastFinish(), bench.lastTrait());
    }

    private static int category(ItemStack result) {
        if (result.getItem() instanceof SaddleItem || result.getItem() instanceof HorseArmorItem) return BenchOffersPacket.CATEGORY_TACK;
        if (result.getItem() instanceof ArmorItem || LivingEntity.getEquipmentSlotForItem(result).isArmor()) return BenchOffersPacket.CATEGORY_ARMOR;
        String path = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(result.getItem()).getPath();
        if (path.contains("saddle") || path.contains("bridle") || path.contains("collar") || path.contains("harness")) return BenchOffersPacket.CATEGORY_TACK;
        return BenchOffersPacket.CATEGORY_OTHER;
    }

    // ------------------------------------------------------------------ preparing

    /**
     * Prepares a piece: the gate is asked, the chosen leather group (best Integrity first) and the auxiliaries are
     * taken atomically into a new job's ledger, and the pattern is copied into the job. Nothing is made yet.
     */
    public static boolean prepare(ServerPlayer player, LeatherworkersBenchBlockEntity bench, @Nullable ResourceLocation recipeId,
                                  Finish finish, @Nullable ResourceLocation traitId) {
        if (bench.job() != null) {
            Feedback.fail(player, "bench_busy");
            return false;
        }
        LeatherworkingRecipe recipe = recipeId == null ? null : TanningData.current().recipe(recipeId);
        if (recipe == null) {
            Feedback.fail(player, "recipe_unavailable");
            return false;
        }
        Optional<Component> denied = ImmersiveTanningAPI.recipeDenial(player, recipe);
        if (denied.isPresent()) {
            Feedback.fail(player, denied.get());
            return false;
        }
        if (!bench.outputRoom(3)) {
            Feedback.fail(player, "collect_first");
            return false;
        }
        LeatherStock.Group group = null;
        for (LeatherStock.Group g : LeatherStock.groups(player)) {
            if (g.finish() == finish && (g.trait() == null ? traitId == null : g.trait().id().equals(traitId))) group = g;
        }
        if (group == null || group.count() < recipe.leather()) {
            Feedback.fail(player, "need_more_leather");
            return false;
        }
        if (!AuxInventory.hasAll(player, recipe.auxiliary(), -1)) {
            Feedback.fail(player, "missing_auxiliary");
            return false;
        }
        List<ItemStack> leather = LeatherStock.take(player, finish, group.trait(), recipe.leather());
        if (leather == null) {
            Feedback.fail(player, "materials_changed");
            return false;
        }
        List<ItemStack> aux = AuxInventory.take(player, recipe.auxiliary(), -1);
        if (aux == null) {
            leather.forEach(s -> give(player, s));
            Feedback.fail(player, "materials_changed");
            return false;
        }
        MaterialLedger ledger = new MaterialLedger();
        int integritySum = 0;
        int units = 0;
        for (ItemStack s : leather) {
            ledger.add(MaterialLedger.Role.LEATHER, s);
            int integrity = LeatherData.get(s).map(LeatherData::integrity).orElse(LeatherData.DEFAULT_INTEGRITY);
            integritySum += integrity * s.getCount();
            units += s.getCount();
        }
        aux.forEach(s -> ledger.add(MaterialLedger.Role.AUXILIARY, s));
        int integrity = units == 0 ? 0 : Math.round(integritySum / (float) units);
        WorkPatterns.Resolution pattern = WorkPatterns.resolve(recipe.workPattern(), recipe.seam());
        LeatherworkJob job = LeatherworkJob.craft(player.getUUID(), player.getGameProfile().getName(), recipe.getId(), recipe.result(),
                finish, group.trait(), integrity, units, ledger, pattern.pattern());
        bench.setJob(job);
        bench.remember(recipe.getId(), finish, group.trait() == null ? null : group.trait().id());
        player.level().playSound(null, bench.getBlockPos(), ModSounds.BENCH_PAGE.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
        return true;
    }

    /**
     * Prepares a recipe without a chosen group (JEI's transfer): the bench's last group when it has enough leather,
     * else the first group that does. Never mixes groups.
     */
    public static boolean prepareAny(ServerPlayer player, LeatherworkersBenchBlockEntity bench, @Nullable ResourceLocation recipeId) {
        LeatherworkingRecipe recipe = recipeId == null ? null : TanningData.current().recipe(recipeId);
        if (recipe == null) {
            Feedback.fail(player, "recipe_unavailable");
            return false;
        }
        LeatherStock.Group chosen = null;
        for (LeatherStock.Group g : LeatherStock.groups(player)) {
            if (g.count() < recipe.leather()) continue;
            boolean last = g.finish() == bench.lastFinish() && (g.trait() == null ? bench.lastTrait() == null : g.trait().id().equals(bench.lastTrait()));
            if (chosen == null || last) chosen = g;
            if (last) break;
        }
        if (chosen == null) {
            Feedback.fail(player, "need_more_leather");
            return false;
        }
        return prepare(player, bench, recipeId, chosen.finish(), chosen.trait() == null ? null : chosen.trait().id());
    }

    /** "Make another": the bench's last pattern and leather group again, each piece with its own materials and result. */
    public static boolean again(ServerPlayer player, LeatherworkersBenchBlockEntity bench) {
        if (bench.lastRecipe() == null || bench.lastFinish() == null) return false;
        return prepare(player, bench, bench.lastRecipe(), bench.lastFinish(), bench.lastTrait());
    }

    /** Before any cut, a prepared piece is called off and its exact materials come back. */
    public static boolean cancel(ServerPlayer player, LeatherworkersBenchBlockEntity bench) {
        LeatherworkJob job = bench.job();
        if (job == null || !mayWork(player, job)) return false;
        if (job.started()) {
            Feedback.fail(player, "cancel_started");
            return false;
        }
        bench.takeJob();
        for (ItemStack s : job.ledger().drain()) give(player, s);
        return true;
    }

    // ------------------------------------------------------------------ the work

    private static Reply cut(ServerPlayer player, LeatherworkersBenchBlockEntity bench, StationLease lease, StationCommandPacket p) {
        LeatherworkJob job = bench.job();
        if (job == null) return Reply.refused(StationAckPacket.WRONG_STAGE);
        if (!mayWork(player, job)) return notOwner(player, job);
        int knife = WorkRules.findTool(player, WorkRules::isKnife, job.knifeSlot());
        if (knife < 0) {
            Feedback.fail(player, "need_knife_cut");
            return Reply.refused(StationAckPacket.NO_TOOL);
        }
        float[][] points = plane(lease, p);
        boolean wasStarted = job.started();
        int before = job.activeCut();
        LeatherworkJob.Outcome outcome = job.cut(p.has(WorkAction.BEGIN), p.has(WorkAction.END), points[0], points[1], scale(lease));
        if (outcome == LeatherworkJob.Outcome.WRONG_STAGE) return Reply.refused(StationAckPacket.WRONG_STAGE);
        job.claim(player.getUUID(), player.getGameProfile().getName());
        if (outcome == LeatherworkJob.Outcome.ACCEPTED) job.reserveKnife(knife);
        relay(player, bench, StationStrokePacket.CUT, p, points[0].length);
        if (job.activeCut() != before) {
            player.level().playSound(null, bench.getBlockPos(), ModSounds.BENCH_CUT.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
            StationEffects.send(player.serverLevel(), bench.getBlockPos(), StationEffects.CUT, 1F);
        }
        if (outcome == LeatherworkJob.Outcome.ACCEPTED || !wasStarted || p.has(WorkAction.END)) bench.changed();
        return Reply.OK;
    }

    private static Reply place(ServerPlayer player, LeatherworkersBenchBlockEntity bench, StationLease lease, StationCommandPacket p) {
        LeatherworkJob job = bench.job();
        if (job == null) return Reply.refused(StationAckPacket.WRONG_STAGE);
        if (!mayWork(player, job)) return notOwner(player, job);
        if (p.xs().length < 1) return Reply.refused(StationAckPacket.REFUSED);
        float x = StationCommandPacket.plane(p.xs()[0]);
        float y = StationCommandPacket.plane(p.ys()[0]);
        float rot = (p.arg(1, 3600) - 3600) / 10F;
        LeatherworkJob.Outcome outcome = job.place(p.arg(0, -1), x, y, rot, scale(lease));
        if (outcome == LeatherworkJob.Outcome.WRONG_STAGE) return Reply.refused(StationAckPacket.WRONG_STAGE);
        if (outcome != LeatherworkJob.Outcome.ACCEPTED) return Reply.refused(StationAckPacket.OUT_OF_PLACE);
        player.level().playSound(null, bench.getBlockPos(), ModSounds.BENCH_PANEL.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
        StationEffects.send(player.serverLevel(), bench.getBlockPos(), StationEffects.PANEL, 1F);
        bench.changed();
        return Reply.OK;
    }

    /**
     * One stitch: the press (the first point, with the action index) punctures the next hole pair, the points that
     * follow draw the thread, the release decides how cleanly the seam closed. A thread let go of without a release
     * (the player left) is simply not a stitch yet.
     */
    private static Reply stitch(ServerPlayer player, LeatherworkersBenchBlockEntity bench, StationLease lease, StationCommandPacket p) {
        LeatherworkJob job = bench.job();
        if (job == null) return Reply.refused(StationAckPacket.WRONG_STAGE);
        if (!mayWork(player, job)) return notOwner(player, job);
        int awl = WorkRules.findTool(player, WorkRules::isAwl, job.awlSlot());
        if (awl < 0) {
            Feedback.fail(player, "need_awl");
            return Reply.refused(StationAckPacket.NO_TOOL);
        }
        float[][] points = plane(lease, p);
        // A release may come without new points (the tool did not move since the last batch): it lets go where the
        // thread was. A press always needs its point.
        if (points[0].length == 0 && (p.has(WorkAction.BEGIN) || !p.has(WorkAction.END))) return Reply.refused(StationAckPacket.REFUSED);
        int first = 0;
        if (p.has(WorkAction.BEGIN)) {
            LeatherworkJob.Outcome punctured = job.puncture(p.arg(0, -1), points[0][0], points[1][0], scale(lease));
            if (punctured != LeatherworkJob.Outcome.ACCEPTED) {
                return Reply.refused(punctured == LeatherworkJob.Outcome.WRONG_STAGE ? StationAckPacket.WRONG_STAGE : StationAckPacket.OUT_OF_PLACE);
            }
            job.reserveAwl(awl);
            job.claim(player.getUUID(), player.getGameProfile().getName());
            player.level().playSound(null, bench.getBlockPos(), ModSounds.BENCH_NEEDLE.get(), SoundSource.BLOCKS, 0.7F, 1.0F);
            first = 1;
        }
        if (job.pending() < 0) return Reply.refused(StationAckPacket.WRONG_STAGE);
        int last = points[0].length - 1;
        for (int i = first; i <= last; i++) job.pull(points[0][i], points[1][i]);
        relay(player, bench, StationStrokePacket.PULL, p, points[0].length);
        if (p.has(WorkAction.END) && p.has(WorkAction.CANCEL)) {
            job.dropThread();
        } else if (p.has(WorkAction.END)) {
            int action = job.pending();
            job.releaseThread(last >= 0 ? points[0][last] : job.pullX(), last >= 0 ? points[1][last] : job.pullY(), lease.assisted());
            LeatherworkJob.Tension tension = job.tension(action);
            player.level().playSound(null, bench.getBlockPos(), ModSounds.BENCH_TIGHTEN.get(), SoundSource.BLOCKS,
                    tension == LeatherworkJob.Tension.CLEAN ? 0.8F : 0.6F, tension == LeatherworkJob.Tension.PUCKERED ? 1.3F
                            : tension == LeatherworkJob.Tension.SLACK ? 0.75F : 1.0F);
            StationEffects.send(player.serverLevel(), bench.getBlockPos(), StationEffects.STITCH, job.stitchQuality(action));
        }
        bench.changed();
        return Reply.OK;
    }

    private static Reply loosen(ServerPlayer player, LeatherworkersBenchBlockEntity bench, int action) {
        LeatherworkJob job = bench.job();
        if (job == null) return Reply.refused(StationAckPacket.WRONG_STAGE);
        if (!mayWork(player, job)) return notOwner(player, job);
        if (job.loosen(action) != LeatherworkJob.Outcome.ACCEPTED) {
            if (action >= 0 && action < job.stitchCount() && job.redoCount(action) >= LeatherworkJob.MAX_REDO) Feedback.info(player, "loosen_limit");
            return Reply.refused(StationAckPacket.REFUSED);
        }
        player.level().playSound(null, bench.getBlockPos(), ModSounds.BENCH_LOOSEN.get(), SoundSource.BLOCKS, 0.7F, 1.0F);
        bench.changed();
        return Reply.OK;
    }

    /** The final knot: the recipe and its gate are checked once more, then the piece is committed. */
    public static boolean tie(ServerPlayer player, LeatherworkersBenchBlockEntity bench) {
        LeatherworkJob job = bench.job();
        if (job == null || job.stage() != LeatherworkJob.Stage.TIE) return false;
        if (!mayWork(player, job)) {
            notOwner(player, job);
            return false;
        }
        if (WorkRules.findTool(player, WorkRules::isAwl, job.awlSlot()) < 0) {
            Feedback.fail(player, "need_awl");
            return false;
        }
        if (!bench.outputRoom(4)) {
            Feedback.fail(player, "collect_first");
            return false;
        }
        if (job.kind() == LeatherworkJob.Kind.CRAFT) {
            LeatherworkingRecipe recipe = TanningData.current().recipe(job.recipeId());
            if (recipe == null) {
                Feedback.fail(player, "recipe_removed_recover");
                return false;
            }
            Optional<Component> denied = ImmersiveTanningAPI.recipeDenial(player, recipe);
            if (denied.isPresent()) {
                Feedback.fail(player, denied.get());
                return false;
            }
        }
        if (job.tie() != LeatherworkJob.Outcome.DONE) return false;
        bench.takeJob();
        if (job.kind() == LeatherworkJob.Kind.REPAIR) commitRepair(player, bench, job);
        else commitCraft(player, bench, job);
        return true;
    }

    /**
     * The commit, exactly once per job: progression mods hear the piece and may adjust its workmanship, the leatherwork
     * identity is written, materials are consumed (remainders returned), and the piece, its scraps and remainders
     * wait on the output pad. Experience, advancements and tool wear follow the final values.
     */
    private static void commitCraft(ServerPlayer player, LeatherworkersBenchBlockEntity bench, LeatherworkJob job) {
        ItemStack result = job.result();
        int workmanship = weighted(job);
        BlockPos pos = bench.getBlockPos();
        LeatherworkStitchedEvent stitched = new LeatherworkStitchedEvent(player, result, job.recipeId(), job.finish(), job.integrity(),
                workmanship, job.trait(), job.leatherUsed(), pos, false);
        MinecraftForge.EVENT_BUS.post(stitched);
        workmanship = stitched.getWorkmanship();
        LeatherworkData data = new LeatherworkData(job.finish(), job.integrity(), workmanship, job.trait(), player.getGameProfile().getName());
        data.apply(result);
        LeatherworkData.stampCrafter(result, player.getUUID());
        if (result.getItem() instanceof DyeableLeatherItem dyeable) dyeable.setColor(result, job.finish().leatherColor());
        List<ItemStack> out = new ArrayList<>();
        out.add(result);
        int perOffcut = ServerConfig.get(ServerConfig.LEATHER_PER_OFFCUT);
        if (perOffcut > 0 && job.leatherUsed() / perOffcut > 0) out.add(new ItemStack(ModItems.LEATHER_SCRAPS.get(), job.leatherUsed() / perOffcut));
        out.addAll(job.ledger().consume());
        bench.addOutput(out, player.getUUID(), player.getGameProfile().getName());
        if (job.knifeUsed()) WorkRules.wear(player, job.knifeSlot(), WorkRules::isKnife);
        WorkRules.wear(player, job.awlSlot(), WorkRules::isAwl);
        ServerLevel level = player.serverLevel();
        level.playSound(null, pos, ModSounds.BENCH_TIE.get(), SoundSource.BLOCKS, 0.9F, 1.0F);
        level.playSound(null, pos, ModSounds.BENCH_FINISH.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
        StationEffects.send(level, pos, StationEffects.FINISHED, workmanship / 100F);
        int xp = benchExperience(data);
        if (xp > 0) ExperienceOrb.award(level, Vec3.atCenterOf(pos).add(0, 0.6, 0), xp);
        ModAdvancements.award(player, ModAdvancements.LEATHERWORK);
        if (data.quality() == TanningQuality.MASTERWORK) ModAdvancements.award(player, ModAdvancements.MASTERWORK);
        if (job.trait() != null) ModAdvancements.award(player, ModAdvancements.TROPHY);
        List<String> why = job.explanation();
        bench.recordResult(workmanship, why);
        WorkNet.to(player, new WorkResultPacket(pos, WorkResultPacket.LEATHERWORK, workmanship, job.finish().ordinal(), why));
    }

    /** A bench repair keeps the item itself - finish, quality, enchantments, dye, maker - and only mends wear. */
    private static void commitRepair(ServerPlayer player, LeatherworkersBenchBlockEntity bench, LeatherworkJob job) {
        ItemStack item = ItemStack.EMPTY;
        List<ItemStack> extra = new ArrayList<>();
        for (MaterialLedger.Entry e : job.ledger().entries()) {
            if (e.role() == MaterialLedger.Role.WORKPIECE && item.isEmpty()) item = e.stack().copy();
            else if (e.role() != MaterialLedger.Role.SCRAP) extra.add(e.stack().copy());
        }
        job.ledger().consume();
        if (item.isEmpty()) return;
        item.setDamageValue(Math.max(0, item.getDamageValue() - job.repairPoints()));
        List<ItemStack> out = new ArrayList<>();
        out.add(item);
        out.addAll(extra);
        bench.addOutput(out, player.getUUID(), player.getGameProfile().getName());
        WorkRules.wear(player, job.awlSlot(), WorkRules::isAwl);
        player.level().playSound(null, bench.getBlockPos(), ModSounds.AWL_REPAIR.get(), SoundSource.BLOCKS, 0.9F, 1.0F);
        ModAdvancements.award(player, ModAdvancements.PATCHED_UP);
        List<String> why = job.explanation();
        bench.recordResult(-1, why);
        WorkNet.to(player, new WorkResultPacket(bench.getBlockPos(), WorkResultPacket.REPAIR, job.workmanship(), -1, why));
    }

    /** Workmanship with the pattern's weights, or the configured ones when the server overrides them. */
    public static int weighted(LeatherworkJob job) {
        if (!ServerConfig.get(ServerConfig.OVERRIDE_QUALITY_WEIGHTS)) return job.workmanship();
        float wc = job.pattern().cuts().isEmpty() ? 0F : (float) (double) ServerConfig.get(ServerConfig.CUTTING_WEIGHT);
        float wa = (float) (double) ServerConfig.get(ServerConfig.ASSEMBLY_WEIGHT);
        float ws = (float) (double) ServerConfig.get(ServerConfig.STITCHING_WEIGHT);
        float sum = wc + wa + ws;
        if (sum <= 0F) return job.workmanship();
        float raw = (wc * job.cuttingScore() + wa * job.assemblyScore() + ws * job.stitchingScore()) / sum;
        return Math.max(0, Math.min(100, Math.round(raw * 100F)));
    }

    /** Experience for a finished piece: the configured amount at a score of 50, half at 0, one and a half at 100. */
    public static int benchExperience(LeatherworkData data) {
        int base = ServerConfig.get(ServerConfig.BENCH_EXPERIENCE);
        return base <= 0 ? 0 : Math.max(1, Math.round(base * (0.5F + data.overallScore() / 100F)));
    }

    // ------------------------------------------------------------------ the output, workpieces, recovery, repair

    public static boolean collect(ServerPlayer player, LeatherworkersBenchBlockEntity bench) {
        if (!bench.hasOutput()) return false;
        if (!bench.mayCollect(player)) {
            Feedback.fail(player, "output_belongs_to", bench.outputOwnerName());
            return false;
        }
        List<ItemStack> moved = bench.collect(player);
        if (moved.isEmpty()) {
            Feedback.fail(player, "inventory_full");
            return false;
        }
        player.level().playSound(null, bench.getBlockPos(), ModSounds.WET_LEATHER.get(), SoundSource.BLOCKS, 0.5F, 1.3F);
        if (bench.hasOutput()) Feedback.info(player, "output_left");
        return true;
    }

    /** A started piece is carried off whole, as an unfinished workpiece (only its owner takes it). */
    public static boolean takeWorkpiece(ServerPlayer player, LeatherworkersBenchBlockEntity bench) {
        LeatherworkJob job = bench.job();
        if (job == null || !job.started()) return false;
        if (!mayWork(player, job)) {
            notOwner(player, job);
            return false;
        }
        if (player.getInventory().getFreeSlot() < 0) {
            Feedback.fail(player, "inventory_full");
            return false;
        }
        bench.takeJob();
        job.settle();
        give(player, UnfinishedLeatherworkItem.of(job));
        return true;
    }

    /** Lays an unfinished workpiece on an empty bench: the job resumes there, now the player's. */
    public static boolean mountWorkpiece(ServerPlayer player, LeatherworkersBenchBlockEntity bench, ItemStack held) {
        if (bench.job() != null || !(held.getItem() instanceof UnfinishedLeatherworkItem)) return false;
        LeatherworkJob job = UnfinishedLeatherworkItem.job(held);
        if (job == null) {
            Feedback.fail(player, "workpiece_damaged");
            return false;
        }
        job.claim(player.getUUID(), player.getGameProfile().getName());
        if (!player.getAbilities().instabuild) held.shrink(1);
        bench.setJob(job);
        player.level().playSound(null, bench.getBlockPos(), ModSounds.BENCH_PANEL.get(), SoundSource.BLOCKS, 0.8F, 0.9F);
        if (job.kind() == LeatherworkJob.Kind.CRAFT && TanningData.current().recipe(job.recipeId()) == null) {
            Feedback.warn(player, Feedback.message("recipe_removed_recover"));
        }
        return true;
    }

    /**
     * A piece whose recipe is gone, or which its maker may no longer make, gives back its exact materials once: no
     * piece, scraps, quality or experience.
     */
    public static boolean recover(ServerPlayer player, LeatherworkersBenchBlockEntity bench) {
        LeatherworkJob job = bench.job();
        if (job == null || !mayWork(player, job) || !recoverable(player, job)) return false;
        bench.takeJob();
        for (ItemStack s : job.ledger().drain()) give(player, s);
        Feedback.info(player, "recovered");
        return true;
    }

    public static boolean recoverable(ServerPlayer player, LeatherworkJob job) {
        if (job.kind() == LeatherworkJob.Kind.REPAIR) return false;
        LeatherworkingRecipe recipe = TanningData.current().recipe(job.recipeId());
        return recipe == null || ImmersiveTanningAPI.recipeDenial(player, recipe).isPresent();
    }

    /** Lays a damaged leather piece on the mat with the scraps its wear needs (up to what the player has). */
    public static boolean prepareRepair(ServerPlayer player, LeatherworkersBenchBlockEntity bench, int slot) {
        if (bench.job() != null) {
            Feedback.fail(player, "bench_busy");
            return false;
        }
        Inventory inv = player.getInventory();
        if (slot < 0 || slot >= inv.getContainerSize()) return false;
        ItemStack item = inv.getItem(slot);
        if (!FieldRepair.isRepairable(item) || !item.isDamaged()) {
            Feedback.fail(player, "not_repairable");
            return false;
        }
        int per = FieldRepair.repairAmount(item);
        int needed = Math.max(1, Math.min(8, (item.getDamageValue() + per - 1) / per));
        int available = inv.countItem(ModItems.LEATHER_SCRAPS.get());
        if (available <= 0) {
            Feedback.fail(player, "need_scraps");
            return false;
        }
        int scraps = Math.min(needed, available);
        MaterialLedger ledger = new MaterialLedger();
        ItemStack taken = inv.removeItem(slot, item.getCount());
        ledger.add(MaterialLedger.Role.WORKPIECE, taken);
        int left = scraps;
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            if (!inv.getItem(i).is(ModItems.LEATHER_SCRAPS.get())) continue;
            ItemStack removed = inv.removeItem(i, Math.min(left, inv.getItem(i).getCount()));
            left -= removed.getCount();
            ledger.add(MaterialLedger.Role.SCRAP, removed);
        }
        inv.setChanged();
        LeatherworkJob job = LeatherworkJob.repair(player.getUUID(), player.getGameProfile().getName(), taken, ledger,
                scraps * per, WorkPatterns.patch());
        bench.setJob(job);
        player.level().playSound(null, bench.getBlockPos(), ModSounds.BENCH_PANEL.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
        return true;
    }

    private static boolean handoff(ServerPlayer player, LeatherworkersBenchBlockEntity bench) {
        LeatherworkJob job = bench.job();
        if (job == null || job.owner() == null || !job.owner().equals(player.getUUID())) return false;
        job.release();
        bench.changed();
        Feedback.info(player, "handed_off");
        return true;
    }

    /** A gesture left open (lost release): the thread is let go of, the blade lifted. */
    public static void lift(LeatherworkersBenchBlockEntity bench) {
        if (bench.job() != null) {
            bench.job().settle();
            bench.changed();
        }
    }

    // ------------------------------------------------------------------ helpers

    private static boolean mayWork(ServerPlayer player, LeatherworkJob job) {
        return job.ownedBy(player.getUUID()) || player.hasPermissions(2);
    }

    private static Reply notOwner(ServerPlayer player, LeatherworkJob job) {
        Feedback.fail(player, "piece_belongs_to", job.ownerName());
        return Reply.refused(StationAckPacket.NOT_OWNER);
    }

    private static float scale(StationLease lease) {
        return lease.assisted() && ServerConfig.get(ServerConfig.ALLOW_ASSISTED_PROFILE) ? 1.5F : 1F;
    }

    /** The command's points on the work plane, trimmed where they outrun the lease's path budget. */
    private static float[][] plane(StationLease lease, StationCommandPacket p) {
        long now = net.minecraft.Util.getMillis();
        float budget = lease.budget(now, PLANE_PER_SECOND, PLANE_BURST);
        int n = p.xs().length;
        float[] xs = new float[n];
        float[] ys = new float[n];
        int kept = 0;
        if (p.has(WorkAction.BEGIN)) lease.lastX = Float.NaN;
        for (int i = 0; i < n; i++) {
            float x = StationCommandPacket.plane(p.xs()[i]);
            float y = StationCommandPacket.plane(p.ys()[i]);
            if (!Float.isNaN(lease.lastX)) {
                float d = (float) Math.sqrt((x - lease.lastX) * (x - lease.lastX) + (y - lease.lastY) * (y - lease.lastY));
                if (d > budget) break;
                budget -= d;
            }
            xs[kept] = x;
            ys[kept] = y;
            kept++;
            lease.lastX = x;
            lease.lastY = y;
        }
        lease.spend(lease.budget(now, PLANE_PER_SECOND, PLANE_BURST) - budget);
        return new float[][]{java.util.Arrays.copyOf(xs, kept), java.util.Arrays.copyOf(ys, kept)};
    }

    private static void relay(ServerPlayer player, LeatherworkersBenchBlockEntity bench, int kind, StationCommandPacket p, int accepted) {
        if (accepted <= 0) return;
        WorkNet.toWatchers(player.serverLevel(), bench.getBlockPos(), player, new StationStrokePacket(bench.getBlockPos(), kind, player.getId(),
                p.has(WorkAction.BEGIN), p.has(WorkAction.END), java.util.Arrays.copyOf(p.xs(), accepted), java.util.Arrays.copyOf(p.ys(), accepted)));
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        player.getInventory().add(stack);
        if (!stack.isEmpty()) player.drop(stack, false);
    }

    /** Trait of a group, for tests. */
    @Nullable
    public static TraitRef traitOf(LeatherStock.Group group) {
        return group.trait();
    }

    private BenchStationLogic() {}
}
