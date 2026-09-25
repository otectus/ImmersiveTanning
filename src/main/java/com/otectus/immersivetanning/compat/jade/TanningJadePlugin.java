package com.otectus.immersivetanning.compat.jade;

import com.otectus.immersivetanning.ImmersiveTanning;
import com.otectus.immersivetanning.block.LeatherworkersBenchBlock;
import com.otectus.immersivetanning.block.TannersRackBlock;
import com.otectus.immersivetanning.block.TannersVatBlock;
import com.otectus.immersivetanning.blockentity.LeatherworkersBenchBlockEntity;
import com.otectus.immersivetanning.blockentity.TannersRackBlockEntity;
import com.otectus.immersivetanning.blockentity.TannersVatBlockEntity;
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
            if (rack.hide() != null) data.put("Hide", rack.hide().save());
            if (rack.mode() == TannersRackBlockEntity.Mode.DRYING) {
                data.putString("Finish", rack.finish().getSerializedName());
                data.putInt("Dry", percent(rack.dryProgress(accessor.getLevel().getGameTime())));
            }
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains("Mode")) return;
            HideData hide = data.contains("Hide", Tag.TAG_COMPOUND) ? HideData.load(data.getCompound("Hide"), HideSize.MEDIUM) : null;
            switch (data.getString("Mode")) {
                case "RAW" -> {
                    if (hide != null) tooltip.add(line("rack.raw", hideName(hide)).withStyle(ChatFormatting.GOLD));
                    tooltip.add(line("rack.flesh").withStyle(ChatFormatting.GRAY));
                }
                case "DRYING" -> {
                    Finish finish = Finish.byName(data.getString("Finish"));
                    if (hide != null && finish != null) tooltip.add(line("rack.drying", finish.displayName(), hideName(hide)));
                    int dry = data.getInt("Dry");
                    tooltip.add(dry >= 100 ? line("rack.dry").withStyle(ChatFormatting.GREEN)
                            : line("rack.dry_progress", dry).withStyle(ChatFormatting.AQUA));
                }
                default -> tooltip.add(line("rack.empty").withStyle(ChatFormatting.GRAY));
            }
            if (data.getBoolean("Locked")) tooltip.add(line("in_use").withStyle(ChatFormatting.RED));
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
            data.putInt("Tannin", vat.tannin());
            data.putInt("MaxTannin", vat.maxTannin());
            data.putInt("Capacity", vat.capacity());
            long time = accessor.getLevel().getGameTime();
            ListTag hides = new ListTag();
            for (TannersVatBlockEntity.Slot slot : vat.slots()) {
                CompoundTag entry = slot.hide().save();
                entry.putBoolean("Started", slot.started());
                entry.putInt("Progress", percent(slot.progress(time)));
                hides.add(entry);
            }
            data.put("Hides", hides);
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains("Capacity")) return;
            tooltip.add(data.getBoolean("Water") ? line("vat.water").withStyle(ChatFormatting.AQUA)
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
                MutableComponent state = !entry.getBoolean("Started") ? line("vat.waiting").withStyle(ChatFormatting.DARK_GRAY)
                        : progress >= 100 ? line("vat.tanned").withStyle(ChatFormatting.GREEN)
                        : line("vat.progress", progress).withStyle(ChatFormatting.AQUA);
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
            if (accessor.getBlockEntity() instanceof LeatherworkersBenchBlockEntity bench) data.putBoolean("Locked", bench.isLocked());
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains("Locked")) return;
            tooltip.add(data.getBoolean("Locked") ? line("in_use").withStyle(ChatFormatting.RED) : line("bench.idle").withStyle(ChatFormatting.GRAY));
        }

        @Override
        public ResourceLocation getUid() {
            return BENCH;
        }
    }
}
