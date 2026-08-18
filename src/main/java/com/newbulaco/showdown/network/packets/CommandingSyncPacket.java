package com.newbulaco.showdown.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public class CommandingSyncPacket {
    private final Set<UUID> commanding;

    public CommandingSyncPacket(Set<UUID> commanding) {
        this.commanding = commanding;
    }

    public static void encode(CommandingSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.commanding.size());
        for (UUID id : packet.commanding) buf.writeUUID(id);
    }

    public static CommandingSyncPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        Set<UUID> set = new HashSet<>(n);
        for (int i = 0; i < n; i++) set.add(buf.readUUID());
        return new CommandingSyncPacket(set);
    }

    public static void handle(CommandingSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.newbulaco.showdown.client.battle.ClientCommandingTracker.getInstance().set(packet.commanding)
        ));
        ctx.get().setPacketHandled(true);
    }
}
