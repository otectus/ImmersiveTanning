package com.otectus.immersivetanning.network.packet;

import com.otectus.immersivetanning.client.ClientPacketHandler;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.trait.TraitRef;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Every piece the player can cut and stitch right now, one entry per recipe and leather group. */
public record OpenBenchPacket(int sessionId, BlockPos pos, List<Entry> entries) {
    public static final int MAX_ENTRIES = 512;

    public record Entry(ResourceLocation recipe, ItemStack result, int leather, Finish finish, @Nullable TraitRef trait,
                        int integrity, List<ItemStack> auxiliary) {}

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(sessionId);
        buf.writeBlockPos(pos);
        int n = Math.min(entries.size(), MAX_ENTRIES);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Entry e = entries.get(i);
            buf.writeResourceLocation(e.recipe());
            buf.writeItem(e.result());
            buf.writeVarInt(e.leather());
            buf.writeEnum(e.finish());
            TraitRef.writeNullable(buf, e.trait());
            buf.writeVarInt(e.integrity());
            buf.writeVarInt(e.auxiliary().size());
            e.auxiliary().forEach(buf::writeItem);
        }
    }

    public static OpenBenchPacket decode(FriendlyByteBuf buf) {
        int session = buf.readVarInt();
        BlockPos pos = buf.readBlockPos();
        int n = Math.min(buf.readVarInt(), MAX_ENTRIES);
        List<Entry> entries = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ResourceLocation recipe = buf.readResourceLocation();
            ItemStack result = buf.readItem();
            int leather = buf.readVarInt();
            Finish finish = buf.readEnum(Finish.class);
            TraitRef trait = TraitRef.readNullable(buf);
            int integrity = buf.readVarInt();
            int auxCount = Math.min(buf.readVarInt(), 16);
            List<ItemStack> aux = new ArrayList<>(auxCount);
            for (int j = 0; j < auxCount; j++) aux.add(buf.readItem());
            entries.add(new Entry(recipe, result, leather, finish, trait, integrity, List.copyOf(aux)));
        }
        return new OpenBenchPacket(session, pos, List.copyOf(entries));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.openBench(this));
    }
}
