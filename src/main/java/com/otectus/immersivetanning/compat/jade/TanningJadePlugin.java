package com.otectus.immersivetanning.compat.jade;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.block.LeatherworkersBenchBlock;
import com.otectus.immersivetanning.block.TannersRackBlock;
import com.otectus.immersivetanning.block.TannersVatBlock;
import com.otectus.immersivetanning.blockentity.LeatherworkersBenchBlockEntity;
import com.otectus.immersivetanning.blockentity.TannersRackBlockEntity;
import com.otectus.immersivetanning.blockentity.TannersVatBlockEntity;
import com.otectus.immersivetanning.work.LeatherworkJob;
import com.otectus.immersivetanning.hide.HideData;
import com.otectus.immersivetanning.hide.HideSize;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.trait.TraitRef;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade overlays for the three stations. Discovered by Jade through the annotation, so nothing here loads without it.
 * The server providers write what the block entity knows (world-time progress included) into the tooltip's server
 * data; the client providers only format it.
 */
@WailaPlugin
public class TanningJadePlugin implements IWailaPlugin {
    static final ResourceLocation RACK = ImmersiveTanning.id("rack");
    static final ResourceLocation VAT = ImmersiveTanning.id("vat");
    static final ResourceLocation BENCH = ImmersiveTanning.id("bench");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(RackProvider.INSTANCE, TannersRackBlockEntity.class);
        registration.registerBlockDataProvider(VatProvider.INSTANCE, TannersVatBlockEntity.class);
        registration.registerBlockDataProvider(BenchProvider.INSTANCE, LeatherworkersBenchBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(RackProvider.INSTANCE, TannersRackBlock.class);
        registration.registerBlockComponent(VatProvider.INSTANCE, TannersVatBlock.class);
        registration.registerBlockComponent(BenchProvider.INSTANCE, LeatherworkersBenchBlock.class);
        // While working at a station the station explains itself; Jade steps aside unless the player keeps it.
        registration.addBeforeRenderCallback((tooltip, rect, graphics, accessor, color) ->
                com.otectus.immersivetanning.client.station.StationFocus.active()
                        && !com.otectus.immersivetanning.config.ClientConfig.get(com.otectus.immersivetanning.config.ClientConfig.JADE_IN_FOCUS));
    }

    private static MutableComponent line(String key, Object... args) {
        return Component.translatable("jade.immersive_tanning." + key, args);
    }

    /** "Large hide" or "Large Heatproof hide". */
    private static Component hideName(HideData hide) {
        TraitRef trait = hide.trait();
        return trait == null ? line("hide", hide.size().displayName()) : line("hide.traited", hide.size().displayName(), trait.displayName());
    }

    private static int percent(float progress) {
        return Math.round(progress * 100F);
    }

    enum RackProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof TannersRackBlockEntity rack)) return;
            data.putString("Mode", rack.mode().name());
            data.putBoolean("Locked", rack.isLocked());
            data.putString("Operator", rack.operatorName());
            if (rack.hide() != null) data.put("Hide", rack.hide().save());
            data.putString("Finish", rack.finish().getSerializedName());
            if (rack.mode() == TannersRackBlockEntity.Mode.DRYING) data.putInt("Dry", percent(rack.dryProgress(accessor.getLevel().getGameTime())));
            if (rack.mode() == TannersRackBlockEntity.Mode.FRAMING) data.putInt("Treated", percent(rack.treatment()));
            if (rack.work() != null) {
                data.putInt("Clean", percent(rack.work().surface().coverage()));
                data.putString("Owner", rack.work().started() ? rack.work().ownerName() : "");
            }
            if (rack.mode() == TannersRackBlockEntity.Mode.SCRAPED) data.putString("Owner", rack.scrapedByName());
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains("Mode")) return;
            HideData hide = data.contains("Hide", Tag.TAG_COMPOUND) ? HideData.load(data.getCompound("Hide"), HideSize.MEDIUM) : null;
            Finish finish = Finish.byName(data.getString("Finish"));
            String owner = data.getString("Owner");
            switch (data.getString("Mode")) {
                case "RAW" -> {
                    if (hide != null) tooltip.add(line("rack.raw", hideName(hide)).withStyle(ChatFormatting.GOLD));
                    tooltip.add(line("rack.clean", data.getInt("Clean")).withStyle(ChatFormatting.GRAY));
                    if (!owner.isEmpty()) tooltip.add(line("owner", owner).withStyle(ChatFormatting.DARK_GRAY));
                }
                case "SCRAPED" -> {
                    if (hide != null) tooltip.add(line("rack.scraped", hideName(hide)).withStyle(ChatFormatting.GREEN));
                    if (!owner.isEmpty()) tooltip.add(line("owner", owner).withStyle(ChatFormatting.DARK_GRAY));
                }
                case "FRAMING" -> {
                    if (hide != null && finish != null) tooltip.add(line("rack.framing", hideName(hide), finish.displayName()).withStyle(ChatFormatting.GOLD));
                    tooltip.add(line("rack.treated", data.getInt("Treated")).withStyle(ChatFormatting.GRAY));
                }
                case "DRYING" -> {
                    if (hide != null && finish != null) tooltip.add(line("rack.drying", finish.displayName(), hideName(hide)));
                    int dry = data.getInt("Dry");
                    tooltip.add(dry >= 100 ? line("rack.dry").withStyle(ChatFormatting.GREEN)
                            : line("rack.dry_progress", dry).withStyle(ChatFormatting.AQUA));
                }
                default -> tooltip.add(line("rack.empty").withStyle(ChatFormatting.GRAY));
            }
            if (data.getBoolean("Locked")) tooltip.add(line("in_use_by", data.getString("Operator")).withStyle(ChatFormatting.RED));
        }

        @Override
        public ResourceLocation getUid() {
            return RACK;
        }
    }

    enum VatProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof TannersVatBlockEntity vat)) return;
            data.putBoolean("Water", vat.hasWater());
            data.putInt("WaterAmount", vat.water());
            data.putInt("Tannin", vat.tannin());
            data.putInt("MaxTannin", vat.maxTannin());
            data.putInt("Capacity", vat.capacity());
            long time = accessor.getLevel().getGameTime();
            ListTag hides = new ListTag();
            for (TannersVatBlockEntity.Slot slot : vat.slots()) {
                CompoundTag entry = slot.hide().save();
                entry.putBoolean("Started", slot.started());
                entry.putBoolean("Paused", slot.paused());
                entry.putInt("Progress", percent(slot.progress(time)));
                entry.putInt("MissingBark", vat.missingBark(slot));
                entry.putBoolean("Tended", slot.tended());
                hides.add(entry);
            }
            data.put("Hides", hides);
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains("Capacity")) return;
            boolean water = data.getBoolean("Water");
            int amount = data.getInt("WaterAmount");
            tooltip.add(water ? line("vat.water").withStyle(ChatFormatting.AQUA)
                    : amount > 0 ? line("vat.water_partial", amount, TannersVatBlockEntity.WATER_CAPACITY).withStyle(ChatFormatting.DARK_AQUA)
                    : line("vat.no_water").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(line("vat.tannin", data.getInt("Tannin"), data.getInt("MaxTannin"))
                    .withStyle(data.getInt("Tannin") > 0 ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));
            ListTag hides = data.getList("Hides", Tag.TAG_COMPOUND);
            if (hides.isEmpty()) {
                tooltip.add(line("vat.empty", data.getInt("Capacity")).withStyle(ChatFormatting.GRAY));
                return;
            }
            for (int i = 0; i < hides.size(); i++) {
                CompoundTag entry = hides.getCompound(i);
                HideData hide = HideData.load(entry, HideSize.MEDIUM);
                int progress = entry.getInt("Progress");
                // A waiting hide says what it waits for: water first, then the bark still missing for its size.
                int missingBark = entry.contains("MissingBark") ? Math.max(1, entry.getInt("MissingBark"))
                        : Math.max(1, hide.size().tannin() - data.getInt("Tannin"));
                MutableComponent state = !entry.getBoolean("Started")
                        ? (water ? line("vat.waiting_bark", missingBark) : line("vat.waiting_water")).withStyle(ChatFormatting.GOLD)
                        : progress >= 100 ? line("vat.tanned").withStyle(ChatFormatting.GREEN)
                        : entry.getBoolean("Paused") ? line("vat.paused", progress).withStyle(ChatFormatting.GOLD)
                        : line(entry.getBoolean("Tended") ? "vat.progress_tended" : "vat.progress", progress).withStyle(ChatFormatting.AQUA);
                tooltip.add(line("vat.hide", hideName(hide), state));
            }
        }

        @Override
        public ResourceLocation getUid() {
            return VAT;
        }
    }

    enum BenchProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof LeatherworkersBenchBlockEntity bench)) return;
            data.putBoolean("Locked", bench.isLocked());
            data.putString("Operator", bench.operatorName());
            data.putInt("Output", bench.output().size());
            data.putString("OutputOwner", bench.outputOwnerName());
            LeatherworkJob job = bench.job();
            if (job != null) {
                data.putString("Stage", job.stage().name());
                data.put("Piece", LeatherworkJob.displayCopy(job.result()).save(new CompoundTag()));
                data.putString("Owner", job.ownerName());
                int step;
                int of;
                switch (job.stage()) {
                    case CUTTING, RESERVED -> {
                        of = job.pattern().cuts().size();
                        step = job.activeCut() < 0 ? of : job.activeCut();
                    }
                    case ASSEMBLY -> {
                        of = job.pattern().panels().size();
                        int seated = 0;
                        for (int p = 0; p < of; p++) if (job.seated(p)) seated++;
                        step = seated;
                    }
                    default -> {
                        of = job.stitchCount();
                        step = job.nextStitch() < 0 ? of : job.nextStitch();
                    }
                }
                data.putInt("Step", step);
                data.putInt("Of", of);
            }
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains("Locked")) return;
            if (data.contains("Stage")) {
                net.minecraft.world.item.ItemStack piece = net.minecraft.world.item.ItemStack.of(data.getCompound("Piece"));
                String stage = data.getString("Stage").toLowerCase(java.util.Locale.ROOT);
                tooltip.add(line("bench.piece", piece.getHoverName(), Component.translatable("stage.immersive_tanning.bench." + stage),
                        data.getInt("Step"), data.getInt("Of")).withStyle(ChatFormatting.GOLD));
                if (!data.getString("Owner").isEmpty()) tooltip.add(line("owner", data.getString("Owner")).withStyle(ChatFormatting.DARK_GRAY));
            } else if (data.getInt("Output") == 0) {
                tooltip.add(line("bench.idle").withStyle(ChatFormatting.GRAY));
            }
            if (data.getInt("Output") > 0) {
                tooltip.add(line("bench.output", data.getInt("Output")).withStyle(ChatFormatting.GREEN));
                if (!data.getString("OutputOwner").isEmpty()) tooltip.add(line("owner", data.getString("OutputOwner")).withStyle(ChatFormatting.DARK_GRAY));
            }
            if (data.getBoolean("Locked")) tooltip.add(line("in_use_by", data.getString("Operator")).withStyle(ChatFormatting.RED));
        }

        @Override
        public ResourceLocation getUid() {
            return BENCH;
        }
    }
}
