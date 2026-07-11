package com.newbulaco.showdown.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class PartyLearnersResponsePacket {

    public record Entry(UUID pokemonId, boolean canLearn) {}

    private final String moveName;
    private final List<Entry> entries;

    public PartyLearnersResponsePacket(String moveName, List<Entry> entries) {
        this.moveName = moveName;
        this.entries = new ArrayList<>(entries);
    }

    public static void encode(PartyLearnersResponsePacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.moveName);
        buf.writeVarInt(packet.entries.size());
        for (Entry entry : packet.entries) {
            buf.writeUUID(entry.pokemonId());
            buf.writeBoolean(entry.canLearn());
        }
    }

    public static PartyLearnersResponsePacket decode(FriendlyByteBuf buf) {
        String moveName = buf.readUtf();
        int count = buf.readVarInt();
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID id = buf.readUUID();
            boolean canLearn = buf.readBoolean();
            entries.add(new Entry(id, canLearn));
        }
        return new PartyLearnersResponsePacket(moveName, entries);
    }

    public static void handle(PartyLearnersResponsePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> com.newbulaco.showdown.client.PartyLearnersCache.getInstance().receive(packet.moveName, packet.entries)
        ));
        context.setPacketHandled(true);
    }

    public String getMoveName() { return moveName; }
    public List<Entry> getEntries() { return entries; }
}
