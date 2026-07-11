package com.newbulaco.showdown.network.packets;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.newbulaco.showdown.compat.PartyLearnerCheck;
import com.newbulaco.showdown.network.ShowdownNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

// client asks the server "which of my party pokemon can learn this move?"
// answered by PartyLearnersResponsePacket. needed because Cobblemon's Learnset.decode
// only syncs level-up moves to the client, so tm/tutor/egg lookups on the client-side
// species Learnset always return empty in multiplayer.
public class PartyLearnersRequestPacket {

    private final String moveName;

    public PartyLearnersRequestPacket(String moveName) {
        this.moveName = moveName;
    }

    public static void encode(PartyLearnersRequestPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.moveName);
    }

    public static PartyLearnersRequestPacket decode(FriendlyByteBuf buf) {
        return new PartyLearnersRequestPacket(buf.readUtf());
    }

    public static void handle(PartyLearnersRequestPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) return;

            MoveTemplate move = Moves.INSTANCE.getByName(packet.moveName.toLowerCase());
            if (move == null) return;

            PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(sender);
            List<PartyLearnersResponsePacket.Entry> entries = new ArrayList<>();
            for (Pokemon pokemon : party) {
                if (pokemon == null) continue;
                boolean canLearn = PartyLearnerCheck.canLearnMove(pokemon, move);
                entries.add(new PartyLearnersResponsePacket.Entry(pokemon.getUuid(), canLearn));
            }

            ShowdownNetwork.sendToPlayer(new PartyLearnersResponsePacket(packet.moveName, entries), sender);
        });
        context.setPacketHandled(true);
    }

    public String getMoveName() { return moveName; }
}
