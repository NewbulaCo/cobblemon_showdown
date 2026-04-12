package com.newbulaco.showdown.battle;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.newbulaco.showdown.util.MessageUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

public class TeamPreview {
    private static final Logger LOGGER = LoggerFactory.getLogger(TeamPreview.class);
    private static final int SELECTION_TIMEOUT_SECONDS = 60;

    private static final Map<UUID, TeamPreviewSession> activeSessions = new HashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public static void startPreview(ShowdownBattle battle, BiConsumer<UUID, UUID> onBothSelected) {
        ServerPlayer player1 = battle.getPlayer1();
        ServerPlayer player2 = battle.getPlayer2();

        TeamPreviewSession session1 = new TeamPreviewSession(battle.getBattleId(), player1.getUUID(), player2.getUUID());
        TeamPreviewSession session2 = new TeamPreviewSession(battle.getBattleId(), player2.getUUID(), player1.getUUID());

        session1.setLinkedSession(session2);
        session2.setLinkedSession(session1);

        session1.setOnBothSelected(onBothSelected);
        session2.setOnBothSelected(onBothSelected);

        activeSessions.put(player1.getUUID(), session1);
        activeSessions.put(player2.getUUID(), session2);

        sendTeamPreviewUI(player1);
        sendTeamPreviewUI(player2);

        ScheduledFuture<?> timeout = scheduler.schedule(() -> {
            handleTimeout(battle.getBattleId(), player1.getUUID(), player2.getUUID());
        }, SELECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        session1.setTimeoutTask(timeout);
        session2.setTimeoutTask(timeout);

        LOGGER.info("Started team preview for battle: {}", battle.getBattleId());
    }

    private static void sendTeamPreviewUI(ServerPlayer player) {
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);

        player.sendSystemMessage(MessageUtil.header(Component.translatable("cobblemon_showdown.battle_preview")));
        player.sendSystemMessage(Component.translatable(
            "cobblemon_showdown.battle_preview.select.leader",
                Component.translatable("cobblemon_showdown.time.second", SELECTION_TIMEOUT_SECONDS)
                    .withStyle(ChatFormatting.YELLOW)
            ).withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.empty());

        for (int i = 0; i < 6; i++) {
            Pokemon pokemon = party.get(i);
            if (pokemon != null) {
                MutableComponent pokemonLine = createPokemonButton(player.getUUID(), i, pokemon);
                player.sendSystemMessage(pokemonLine);
            }
        }

        player.sendSystemMessage(Component.empty());
        player.sendSystemMessage(Component.translatable("cobblemon_showdown.battle_preview.select.hidden")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        player.sendSystemMessage(MessageUtil.separator());
    }

    private static MutableComponent createPokemonButton(UUID playerUuid, int slot, Pokemon pokemon) {
        Component species = pokemon.getSpecies().getTranslatedName();
        int level = pokemon.getLevel();
        Component nickname = pokemon.getNickname() != null ?
                pokemon.getNickname() : species;

        float healthPercent = (float) pokemon.getCurrentHealth() / pokemon.getHp();
        ChatFormatting healthColor;
        if (healthPercent > 0.5f) {
            healthColor = ChatFormatting.GREEN;
        } else if (healthPercent > 0.25f) {
            healthColor = ChatFormatting.YELLOW;
        } else {
            healthColor = ChatFormatting.RED;
        }

        MutableComponent hoverText = species.copy()
                .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)
                .append(Component.literal("\n")
                    .append(Component.translatable(
                        "cobblemon_showdown.battle_preview.pokemon.level",
                        level
                    ).withStyle(ChatFormatting.GRAY)))
                .append(Component.literal("\n")
                    .append(Component.translatable(
                        "tooltip.cobblemon_showdown.pokemon_info.hp",
                        pokemon.getCurrentHealth() + "/" + pokemon.getHp()
                    ).withStyle(healthColor)));

        if (pokemon.getAbility() != null) {
            String abilityName = pokemon.getAbility().getDisplayName();
            if (!abilityName.isEmpty()) {
                hoverText.append(Component.literal("\n")
                    .append(Component.translatable(
                        "tooltip.cobblemon_showdown.pokemon_info.ability",
                        abilityName
                    ).withStyle(ChatFormatting.AQUA)));
            }
        }

        if (!pokemon.heldItem().isEmpty()) {
            hoverText.append(Component.literal("\n")
                .append(Component.translatable(
                    "tooltip.cobblemon_showdown.pokemon_info.item",
                    pokemon.heldItem().getHoverName()
                ).withStyle(ChatFormatting.GOLD)));
        }

        return Component.translatable("cobblemon_showdown.battle_preview.pokemon",
                slot + 1,
                nickname.copy().withStyle(Style.EMPTY
                    .withColor(ChatFormatting.WHITE)
                    .withBold(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                        "/showdown preview_select " + slot))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText))),
                level,
                Component.empty().withStyle(ChatFormatting.DARK_GRAY)
                    .append("[")
                    .append(Component.literal(Math.round(healthPercent * 100) + "%").withStyle(healthColor))
                    .append("]")
            ).withStyle(ChatFormatting.GRAY);
    }

    public static boolean selectLead(ServerPlayer player, int slot) {
        UUID playerUuid = player.getUUID();
        TeamPreviewSession session = activeSessions.get(playerUuid);

        if (session == null) {
            MessageUtil.error(player, Component.translatable("cobblemon_showdown.battle_preview.select.invalid"));
            return false;
        }

        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        Pokemon pokemon = party.get(slot);

        if (pokemon == null) {
            MessageUtil.error(player, Component.translatable("cobblemon_showdown.battle_preview.select.empty_slot"));
            return false;
        }

        if (pokemon.getCurrentHealth() <= 0) {
            MessageUtil.error(player, Component.translatable("cobblemon_showdown.battle_preview.select.fainted_slot"));
            return false;
        }

        session.setSelectedSlot(slot);
        session.setSelectedPokemonUuid(pokemon.getUuid());

        MessageUtil.success(player, Component.translatable(
            "cobblemon_showdown.battle_preview.select.success",
            pokemon.getSpecies().getTranslatedName()));
        player.sendSystemMessage(Component.translatable("cobblemon_showdown.battle_preview.select.wait")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));

        if (session.getLinkedSession() != null && session.getLinkedSession().hasSelected()) {
            completePreview(session);
        }

        return true;
    }

    private static void completePreview(TeamPreviewSession session) {
        if (session.getTimeoutTask() != null) {
            session.getTimeoutTask().cancel(false);
        }

        TeamPreviewSession linked = session.getLinkedSession();
        if (linked != null && linked.getTimeoutTask() != null) {
            linked.getTimeoutTask().cancel(false);
        }

        activeSessions.remove(session.getPlayerUuid());
        if (linked != null) {
            activeSessions.remove(linked.getPlayerUuid());
        }

        if (session.getOnBothSelected() != null) {
            UUID lead1 = session.getSelectedPokemonUuid();
            UUID lead2 = linked != null ? linked.getSelectedPokemonUuid() : null;
            session.getOnBothSelected().accept(lead1, lead2);
        }

        LOGGER.info("Team preview completed for battle: {}", session.getBattleId());
    }

    private static void handleTimeout(UUID battleId, UUID player1Uuid, UUID player2Uuid) {
        LOGGER.info("Team preview timed out for battle: {}", battleId);

        TeamPreviewSession session1 = activeSessions.get(player1Uuid);
        TeamPreviewSession session2 = activeSessions.get(player2Uuid);

        if (session1 != null && !session1.hasSelected()) {
            autoSelectFirst(player1Uuid);
        }
        if (session2 != null && !session2.hasSelected()) {
            autoSelectFirst(player2Uuid);
        }

        if (session1 != null) {
            completePreview(session1);
        }
    }

    private static void autoSelectFirst(UUID playerUuid) {
        TeamPreviewSession session = activeSessions.get(playerUuid);
        if (session == null) return;

        LOGGER.info("Auto-selecting first Pokemon for player: {}", playerUuid);

        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
            if (player != null) {
                PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
                for (int i = 0; i < 6; i++) {
                    Pokemon pokemon = party.get(i);
                    if (pokemon != null && pokemon.getCurrentHealth() > 0) {
                        session.setSelectedSlot(i);
                        session.setSelectedPokemonUuid(pokemon.getUuid());
                        return;
                    }
                }
            }
        }
        // last resort fallback
        session.setSelectedSlot(0);
    }

    public static boolean hasActiveSession(UUID playerUuid) {
        return activeSessions.containsKey(playerUuid);
    }

    public static void cancelSession(UUID playerUuid) {
        TeamPreviewSession session = activeSessions.remove(playerUuid);
        if (session != null) {
            if (session.getTimeoutTask() != null) {
                session.getTimeoutTask().cancel(false);
            }
            if (session.getLinkedSession() != null) {
                activeSessions.remove(session.getLinkedSession().getPlayerUuid());
            }
        }
    }

    public static void shutdown() {
        scheduler.shutdown();
        activeSessions.clear();
    }

    private static class TeamPreviewSession {
        private final UUID battleId;
        private final UUID playerUuid;
        private final UUID opponentUuid;

        private int selectedSlot = -1;
        private UUID selectedPokemonUuid;
        private TeamPreviewSession linkedSession;
        private ScheduledFuture<?> timeoutTask;
        private BiConsumer<UUID, UUID> onBothSelected;

        public TeamPreviewSession(UUID battleId, UUID playerUuid, UUID opponentUuid) {
            this.battleId = battleId;
            this.playerUuid = playerUuid;
            this.opponentUuid = opponentUuid;
        }

        public UUID getBattleId() {
            return battleId;
        }

        public UUID getPlayerUuid() {
            return playerUuid;
        }

        public UUID getOpponentUuid() {
            return opponentUuid;
        }

        public int getSelectedSlot() {
            return selectedSlot;
        }

        public void setSelectedSlot(int selectedSlot) {
            this.selectedSlot = selectedSlot;
        }

        public UUID getSelectedPokemonUuid() {
            return selectedPokemonUuid;
        }

        public void setSelectedPokemonUuid(UUID selectedPokemonUuid) {
            this.selectedPokemonUuid = selectedPokemonUuid;
        }

        public boolean hasSelected() {
            return selectedSlot >= 0;
        }

        public TeamPreviewSession getLinkedSession() {
            return linkedSession;
        }

        public void setLinkedSession(TeamPreviewSession linkedSession) {
            this.linkedSession = linkedSession;
        }

        public ScheduledFuture<?> getTimeoutTask() {
            return timeoutTask;
        }

        public void setTimeoutTask(ScheduledFuture<?> timeoutTask) {
            this.timeoutTask = timeoutTask;
        }

        public BiConsumer<UUID, UUID> getOnBothSelected() {
            return onBothSelected;
        }

        public void setOnBothSelected(BiConsumer<UUID, UUID> onBothSelected) {
            this.onBothSelected = onBothSelected;
        }
    }
}
