package com.newbulaco.showdown.battle;

import com.newbulaco.showdown.network.ShowdownNetwork;
import com.newbulaco.showdown.network.packets.SpectatorStatePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SpectatorManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpectatorManager.class);

    private final ShowdownBattle battle;
    private final Set<UUID> spectators = new HashSet<>();
    private boolean spectatingEnabled = true;
    private boolean notifyBattlersOfSpectators = true;

    public SpectatorManager(ShowdownBattle battle) {
        this.battle = battle;
    }

    public boolean addSpectator(ServerPlayer player) {
        if (!spectatingEnabled) {
            player.sendSystemMessage(Component.translatable("cobblemon_showdown.battle_spectate.disabled")
                .withStyle(ChatFormatting.RED));
            return false;
        }

        if (battle.isParticipant(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("cobblemon_showdown.battle_spectate.self")
                .withStyle(ChatFormatting.RED));
            return false;
        }

        if (battle.isCompleted()) {
            player.sendSystemMessage(Component.translatable("cobblemon_showdown.battle_spectate.ended_battle")
                .withStyle(ChatFormatting.RED));
            return false;
        }

        if (spectators.add(player.getUUID())) {
            LOGGER.info("Player {} is now spectating battle {}",
                    player.getName().getString(), battle.getBattleId());

            player.sendSystemMessage(Component.translatable(
                "cobblemon_showdown.battle_spectate.start",
                    battle.getPlayer1().getName(),
                    battle.getPlayer2().getName()
            ).withStyle(ChatFormatting.GREEN));

            if (notifyBattlersOfSpectators) {
                notifyBattlers(Component.translatable(
                    "cobblemon_showdown.battle_spectate.spectator_join",
                    player.getName()
                ).withStyle(ChatFormatting.GRAY));
            }

            sendSpectatorJoinedPacket(player);

            return true;
        }

        return false;
    }

    public boolean removeSpectator(ServerPlayer player) {
        if (spectators.remove(player.getUUID())) {
            LOGGER.info("Player {} stopped spectating battle {}",
                    player.getName().getString(), battle.getBattleId());

            player.sendSystemMessage(Component.translatable("cobblemon_showdown.battle_spectate.stop")
                .withStyle(ChatFormatting.GRAY));

            if (notifyBattlersOfSpectators) {
                notifyBattlers(Component.translatable(
                    "cobblemon_showdown.battle_spectate.spectator_leave",
                    player.getName().getString()
                ).withStyle(ChatFormatting.GRAY));
            }

            SpectatorStatePacket leftPacket = SpectatorStatePacket.left(battle.getBattleId());
            ShowdownNetwork.sendToPlayer(leftPacket, player);

            return true;
        }

        return false;
    }

    public boolean removeSpectator(UUID playerUuid) {
        boolean removed = spectators.remove(playerUuid);
        if (removed) {
            LOGGER.info("Removed spectator {} from battle {} (disconnected)",
                    playerUuid, battle.getBattleId());
        }
        return removed;
    }

    public void clearSpectators() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            spectators.clear();
            return;
        }

        for (UUID spectatorUuid : new HashSet<>(spectators)) {
            ServerPlayer spectator = server.getPlayerList().getPlayer(spectatorUuid);
            if (spectator != null) {
                spectator.sendSystemMessage(Component.translatable("cobblemon_showdown.battle_spectate.end")
                    .withStyle(ChatFormatting.GRAY));

                SpectatorStatePacket leftPacket = SpectatorStatePacket.left(battle.getBattleId());
                ShowdownNetwork.sendToPlayer(leftPacket, spectator);
            }
        }

        spectators.clear();
        LOGGER.info("Cleared all spectators from battle {}", battle.getBattleId());
    }

    public boolean isSpectating(UUID playerUuid) {
        return spectators.contains(playerUuid);
    }

    public int getSpectatorCount() {
        return spectators.size();
    }

    public Set<UUID> getSpectators() {
        return new HashSet<>(spectators);
    }

    public void setSpectatingEnabled(boolean enabled) {
        this.spectatingEnabled = enabled;

        if (!enabled) {
            clearSpectators();
        }
    }

    public boolean isSpectatingEnabled() {
        return spectatingEnabled;
    }

    public void setNotifyBattlersOfSpectators(boolean notify) {
        this.notifyBattlersOfSpectators = notify;
    }

    private void notifyBattlers(Component message) {
        if (battle.getPlayer1() != null) {
            battle.getPlayer1().sendSystemMessage(message);
        }
        if (battle.getPlayer2() != null) {
            battle.getPlayer2().sendSystemMessage(message);
        }
    }

    public void broadcastToSpectators(String message) {
        if (spectators.isEmpty()) {
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        SpectatorStatePacket messagePacket = SpectatorStatePacket.battleMessage(
                battle.getBattleId(), message);

        for (UUID spectatorUuid : spectators) {
            ServerPlayer spectator = server.getPlayerList().getPlayer(spectatorUuid);
            if (spectator != null) {
                spectator.sendSystemMessage(Component.translatable(
                    "cobblemon_showdown.battle_spectate.message.prefix",
                    Component.literal(message).withStyle(ChatFormatting.WHITE)
                ).withStyle(ChatFormatting.GRAY));
                ShowdownNetwork.sendToPlayer(messagePacket, spectator);
            }
        }

        LOGGER.debug("Broadcast to {} spectators: {}", spectators.size(), message);
    }

    public void notifyPokemonFainted(String pokemonName, String ownerName) {
        if (spectators.isEmpty()) {
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        SpectatorStatePacket faintPacket = SpectatorStatePacket.pokemonFainted(
                battle.getBattleId(), pokemonName, ownerName);

        for (UUID spectatorUuid : spectators) {
            ServerPlayer spectator = server.getPlayerList().getPlayer(spectatorUuid);
            if (spectator != null) {
                spectator.sendSystemMessage(Component.translatable(
                    "cobblemon_showdown.battle_spectate.message.prefix",
                    Component.translatable(
                        "cobblemon_showdown.battle_spectate.message.fainted",
                        ownerName,
                        pokemonName
                    ).withStyle(ChatFormatting.WHITE)
                ).withStyle(ChatFormatting.GRAY));
                ShowdownNetwork.sendToPlayer(faintPacket, spectator);
            }
        }
    }

    private void sendSpectatorJoinedPacket(ServerPlayer spectator) {
        int turnNumber = 0;
        if (battle.getCobblemonBattle() != null) {
            turnNumber = battle.getCobblemonBattle().getTurn();
        }

        SpectatorStatePacket joinedPacket = SpectatorStatePacket.joined(
                battle.getBattleId(),
                battle.getPlayer1().getName().getString(),
                battle.getPlayer2().getName().getString(),
                battle.getFormat().getName(),
                turnNumber,
                getSpectatorCount()
        );

        ShowdownNetwork.sendToPlayer(joinedPacket, spectator);
    }

    public void updateSpectators() {
        if (spectators.isEmpty() || battle.getCobblemonBattle() == null) {
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        int turnNumber = battle.getCobblemonBattle().getTurn();

        SpectatorStatePacket updatePacket = SpectatorStatePacket.battleUpdate(
                battle.getBattleId(),
                battle.getPlayer1().getName().getString(),
                battle.getPlayer2().getName().getString(),
                turnNumber,
                getSpectatorCount()
        );

        for (UUID spectatorUuid : spectators) {
            ServerPlayer spectator = server.getPlayerList().getPlayer(spectatorUuid);
            if (spectator != null) {
                ShowdownNetwork.sendToPlayer(updatePacket, spectator);
            }
        }

        LOGGER.debug("Updated {} spectators with battle state", spectators.size());
    }
}
