package com.newbulaco.showdown.network;

import com.newbulaco.showdown.CobblemonShowdown;
import com.newbulaco.showdown.network.packets.BattleClearPacket;
import com.newbulaco.showdown.network.packets.BattleStatePacket;
import com.newbulaco.showdown.network.packets.CommandingSyncPacket;
import com.newbulaco.showdown.network.packets.SeriesStatePacket;
import com.newbulaco.showdown.network.packets.FieldStatusPacket;
import com.newbulaco.showdown.network.packets.PartyLearnersRequestPacket;
import com.newbulaco.showdown.network.packets.PartyLearnersResponsePacket;
import com.newbulaco.showdown.network.packets.PartyStatusPacket;
import com.newbulaco.showdown.network.packets.SideConditionPacket;
import com.newbulaco.showdown.network.packets.SpectatorStatePacket;
import com.newbulaco.showdown.network.packets.StatChangePacket;
import com.newbulaco.showdown.network.packets.VolatileEffectPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class ShowdownNetwork {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShowdownNetwork.class);

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(CobblemonShowdown.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void register() {
        CHANNEL.registerMessage(
                packetId++,
                BattleStatePacket.class,
                BattleStatePacket::encode,
                BattleStatePacket::decode,
                BattleStatePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(
                packetId++,
                SeriesStatePacket.class,
                SeriesStatePacket::encode,
                SeriesStatePacket::decode,
                SeriesStatePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(
                packetId++,
                SpectatorStatePacket.class,
                SpectatorStatePacket::encode,
                SpectatorStatePacket::decode,
                SpectatorStatePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(
                packetId++,
                VolatileEffectPacket.class,
                VolatileEffectPacket::encode,
                VolatileEffectPacket::decode,
                VolatileEffectPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(
                packetId++,
                StatChangePacket.class,
                StatChangePacket::encode,
                StatChangePacket::decode,
                StatChangePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(
                packetId++,
                SideConditionPacket.class,
                SideConditionPacket::encode,
                SideConditionPacket::decode,
                SideConditionPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(
                packetId++,
                FieldStatusPacket.class,
                FieldStatusPacket::encode,
                FieldStatusPacket::decode,
                FieldStatusPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(
                packetId++,
                BattleClearPacket.class,
                BattleClearPacket::encode,
                BattleClearPacket::new,
                BattleClearPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(
                packetId++,
                PartyStatusPacket.class,
                PartyStatusPacket::encode,
                PartyStatusPacket::decode,
                PartyStatusPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(
                packetId++,
                PartyLearnersRequestPacket.class,
                PartyLearnersRequestPacket::encode,
                PartyLearnersRequestPacket::decode,
                PartyLearnersRequestPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        CHANNEL.registerMessage(
                packetId++,
                PartyLearnersResponsePacket.class,
                PartyLearnersResponsePacket::encode,
                PartyLearnersResponsePacket::decode,
                PartyLearnersResponsePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(
                packetId++,
                CommandingSyncPacket.class,
                CommandingSyncPacket::encode,
                CommandingSyncPacket::decode,
                CommandingSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        LOGGER.info("Registered {} network packets", packetId);
    }

    public static void sendToPlayer(Object packet, ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToAll(Object packet) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }

    public static void sendToTracking(Object packet, ServerPlayer trackedPlayer) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> trackedPlayer), packet);
    }
}
