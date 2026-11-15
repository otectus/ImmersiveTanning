package com.otectus.immersivetanning.network.packet;

import com.otectus.immersivetanning.client.ClientPacketHandler;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.trait.TraitRef;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The bench's pattern book for one player, computed by the server: every pattern, with what the player could cut it
 * from (one entry per finish and trait group they carry enough of), what it still lacks, and a progression denial if a
 * gate refuses it. Only the server's book is authoritative: preparing a pattern checks everything again.
 */
public record BenchOffersPacket(BlockPos pos, List<Entry> entries, @Nullable ResourceLocation lastRecipe, @Nullable Finish lastFinish,
                                @Nullable ResourceLocation lastTrait) {
    public static final int MAX_ENTRIES = 400;
    public static final int MAX_GROUPS = 12;

    public static final int CATEGORY_ARMOR = 0;
    public static final int CATEGORY_TACK = 1;
    public static final int CATEGORY_OTHER = 2;

    /** One leather group the player carries enough of: its finish and trait, units, and the Integrity cut from its best. */
    public record Group(Finish finish, @Nullable TraitRef trait, int units, int integrity) {}

    public record Entry(ResourceLocation recipe, ItemStack result, int leather, List<ItemStack> auxiliary, int category,
                        @Nullable Component denial, boolean auxMissing, List<Group> groups, int cuts, int panels, int stitches) {
        public boolean craftable() {
            return denial == null && !auxMissing && !groups.isEmpty();
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        int n = Math.min(entries.size(), MAX_ENTRIES);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Entry e = entries.get(i);
            buf.writeResourceLocation(e.recipe());
            buf.writeItem(e.result());
            buf.writeVarInt(e.leather());
            buf.writeVarInt(Math.min(e.auxiliary().size(), 16));
            for (int j = 0; j < Math.min(e.auxiliary().size(), 16); j++) buf.writeItem(e.auxiliary().get(j));
            buf.writeVarInt(e.category());
            buf.writeBoolean(e.denial() != null);
            if (e.denial() != null) buf.writeComponent(e.denial());
            buf.writeBoolean(e.auxMissing());
            buf.writeVarInt(Math.min(e.groups().size(), MAX_GROUPS));
            for (int j = 0; j < Math.min(e.groups().size(), MAX_GROUPS); j++) {
                Group g = e.groups().get(j);
                buf.writeEnum(g.finish());
                TraitRef.writeNullable(buf, g.trait());
                buf.writeVarInt(g.units());
                buf.writeVarInt(g.integrity());
            }
            buf.writeVarInt(e.cuts());
            buf.writeVarInt(e.panels());
            buf.writeVarInt(e.stitches());
        }
        buf.writeBoolean(lastRecipe != null);
        if (lastRecipe != null) buf.writeResourceLocation(lastRecipe);
        buf.writeBoolean(lastFinish != null);
        if (lastFinish != null) buf.writeEnum(lastFinish);
        buf.writeBoolean(lastTrait != null);
        if (lastTrait != null) buf.writeResourceLocation(lastTrait);
    }

    public static BenchOffersPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int n = Math.min(buf.readVarInt(), MAX_ENTRIES);
        List<Entry> entries = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ResourceLocation recipe = buf.readResourceLocation();
            ItemStack result = buf.readItem();
            int leather = buf.readVarInt();
            int auxCount = Math.min(buf.readVarInt(), 16);
            List<ItemStack> aux = new ArrayList<>(auxCount);
            for (int j = 0; j < auxCount; j++) aux.add(buf.readItem());
            int category = buf.readVarInt();
            Component denial = buf.readBoolean() ? buf.readComponent() : null;
            boolean auxMissing = buf.readBoolean();
            int groupCount = Math.min(buf.readVarInt(), MAX_GROUPS);
            List<Group> groups = new ArrayList<>(groupCount);
            for (int j = 0; j < groupCount; j++) {
                groups.add(new Group(buf.readEnum(Finish.class), TraitRef.readNullable(buf), buf.readVarInt(), buf.readVarInt()));
            }
            entries.add(new Entry(recipe, result, leather, List.copyOf(aux), category, denial, auxMissing, List.copyOf(groups),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        }
        ResourceLocation last = buf.readBoolean() ? buf.readResourceLocation() : null;
        Finish lastFinish = buf.readBoolean() ? buf.readEnum(Finish.class) : null;
        ResourceLocation lastTrait = buf.readBoolean() ? buf.readResourceLocation() : null;
        return new BenchOffersPacket(pos, List.copyOf(entries), last, lastFinish, lastTrait);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.offers(this)));
        ctx.get().setPacketHandled(true);
    }
}
