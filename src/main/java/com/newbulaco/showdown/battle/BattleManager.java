package com.newbulaco.showdown.battle;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.newbulaco.showdown.CobblemonShowdown;
import com.newbulaco.showdown.data.HistoryStorage;
import com.newbulaco.showdown.data.PlayerHistory;
import com.newbulaco.showdown.data.PrizeHandler;
import com.newbulaco.showdown.format.Format;
import com.newbulaco.showdown.format.FormatValidator;
import com.newbulaco.showdown.gui.PartySelectionSession;
import com.newbulaco.showdown.network.ShowdownNetwork;
import com.newbulaco.showdown.network.packets.BattleStatePacket;
import com.newbulaco.showdown.network.packets.SeriesStatePacket;
import com.newbulaco.showdown.util.MessageUtil;
import com.newbulaco.showdown.util.TickScheduler;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BattleManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(BattleManager.class);

    private final Map<UUID, ShowdownBattle> activeBattles = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerToBattle = new ConcurrentHashMap<>();

    // for loot drop prevention
    private final Set<UUID> pokemonInBattle = ConcurrentHashMap.newKeySet();

    // persists after battle ends to catch late loot drop events from cloned Pokemon
    private final Set<UUID> showdownCobblemonBattleIds = ConcurrentHashMap.newKeySet();

    // player UUID -> pokemon UUID -> held item
    private final Map<UUID, Map<UUID, ItemStack>> storedHeldItems = new ConcurrentHashMap<>();

    // player UUID -> pokemon UUID -> original level
    private final Map<UUID, Map<UUID, Integer>> storedLevels = new ConcurrentHashMap<>();

    private MinecraftServer server;

    public void initialize(MinecraftServer server) {
        this.server = server;
        LOGGER.info("BattleManager initialized");
    }

    public boolean startChallengeBattle(ServerPlayer player1, ServerPlayer player2, Format format,
                                         PrizeHandler.ItemBet itemBet) {
        if (isInBattle(player1.getUUID())) {
            MessageUtil.error(player1, Component.translatable("cobblemon_showdown.showdown_battle.setup.already_inA"));
            return false;
        }
        if (isInBattle(player2.getUUID())) {
            MessageUtil.error(player1, Component.translatable("cobblemon_showdown.showdown_battle.setup.already_inB", player2.getName()));
            return false;
        }

        FormatValidator validator = new FormatValidator();

        List<String> player1Errors = validatePlayerParty(player1, format, validator);
        if (!player1Errors.isEmpty()) {
            MessageUtil.error(player1, Component.translatable("cobblemon_showdown.showdown_battle.setup.not_met_requirement"));
            for (String error : player1Errors) {
                MessageUtil.error(player1, Component.literal("  - " + error));
            }
            MessageUtil.error(player2, Component.translatable("cobblemon_showdown.showdown_battle.setup.canceled", player1.getName()));
            return false;
        }

        List<String> player2Errors = validatePlayerParty(player2, format, validator);
        if (!player2Errors.isEmpty()) {
            MessageUtil.error(player2, Component.translatable("cobblemon_showdown.showdown_battle.setup.not_met_requirement"));
            for (String error : player2Errors) {
                MessageUtil.error(player2, Component.literal("  - " + error));
            }
            MessageUtil.error(player1, Component.translatable("cobblemon_showdown.showdown_battle.setup.canceled", player2.getName()));
            return false;
        }

        storeHeldItems(player1);
        storeHeldItems(player2);

        if (format.getBestOf() > 1 && !SeriesTracker.isInSeries(player1.getUUID(), player2.getUUID())) {
            SeriesTracker.Series series = SeriesTracker.startSeries(
                    player1.getUUID(), player2.getUUID(), format, "challenge");
            if (series != null) {
                MessageUtil.info(player1, Component.translatable("cobblemon_showdown.showdown_battle.setup.message", format.getBestOf()));
                MessageUtil.info(player2, Component.translatable("cobblemon_showdown.showdown_battle.setup.message", format.getBestOf()));
            }
        }

        int player1PartySize = countValidPokemon(player1);
        int player2PartySize = countValidPokemon(player2);

        // use chest GUI when party needs narrowing OR when team preview is on (for lead/order selection)
        if (player1PartySize > format.getPartySize() || player2PartySize > format.getPartySize()
                || format.hasTeamPreview()) {
            startPartySelection(player1, player2, format, "challenge", itemBet);
        } else {
            proceedToTeamPreviewOrBattle(player1, player2, format, "challenge", itemBet, null, null);
        }

        return true;
    }

    private void startPartySelection(ServerPlayer player1, ServerPlayer player2, Format format,
                                      String context, PrizeHandler.ItemBet itemBet) {
        PartySelectionSession session = new PartySelectionSession(player1, player2, format, context);

        session.setOnBothSelected((player1Slots, player2Slots) -> {
            proceedToTeamPreviewOrBattle(player1, player2, format, context, itemBet, player1Slots, player2Slots);
        });

        session.openMenus();
        LOGGER.info("Started party selection: {} ({} Pokemon) vs {} ({} Pokemon) for format {} (requires {})",
            player1.getName().getString(), countValidPokemon(player1),
            player2.getName().getString(), countValidPokemon(player2),
            format.getName(), format.getPartySize());
    }

    private void proceedToTeamPreviewOrBattle(ServerPlayer player1, ServerPlayer player2, Format format,
                                               String context, PrizeHandler.ItemBet itemBet,
                                               List<Integer> player1Slots, List<Integer> player2Slots) {
        ShowdownBattle battle = new ShowdownBattle(player1, player2, format, context);

        if (player1Slots != null) {
            battle.setSelectedSlots(player1.getUUID(), player1Slots);
        }
        if (player2Slots != null) {
            battle.setSelectedSlots(player2.getUUID(), player2Slots);
        }

        // if slots were provided from GUI, the first selected Pokemon is the lead;
        // skip chat-based TeamPreview since lead selection was already done in GUI
        if (player1Slots != null && !player1Slots.isEmpty() && player2Slots != null && !player2Slots.isEmpty()) {
            PlayerPartyStore party1 = Cobblemon.INSTANCE.getStorage().getParty(player1);
            PlayerPartyStore party2 = Cobblemon.INSTANCE.getStorage().getParty(player2);

            Pokemon lead1Pokemon = party1.get(player1Slots.get(0));
            Pokemon lead2Pokemon = party2.get(player2Slots.get(0));

            if (lead1Pokemon != null && lead2Pokemon != null) {
                UUID lead1Uuid = lead1Pokemon.getUuid();
                UUID lead2Uuid = lead2Pokemon.getUuid();

                LOGGER.info("Using GUI-selected leads: {} for {}, {} for {}",
                    lead1Pokemon.getSpecies().getName(), player1.getName().getString(),
                    lead2Pokemon.getSpecies().getName(), player2.getName().getString());

                startBattleWithLeads(battle, lead1Uuid, lead2Uuid, itemBet);
                return;
            }
        }

        if (format.hasTeamPreview()) {
            MessageUtil.info(player1, Component.translatable("cobblemon_showdown.showdown_battle.setup.preview"));
            MessageUtil.info(player2, Component.translatable("cobblemon_showdown.showdown_battle.setup.preview"));

            TeamPreview.startPreview(battle, (lead1Uuid, lead2Uuid) -> {
                startBattleWithLeads(battle, lead1Uuid, lead2Uuid, itemBet);
            });
        } else {
            startBattleImmediate(battle, itemBet);
        }
    }

    private int countValidPokemon(ServerPlayer player) {
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        int count = 0;
        for (int i = 0; i < 6; i++) {
            Pokemon pokemon = party.get(i);
            if (pokemon != null && pokemon.getCurrentHealth() > 0) {
                count++;
            }
        }
        return count;
    }

    private void startSeriesGame(ServerPlayer player1, ServerPlayer player2, Format format) {
        FormatValidator validator = new FormatValidator();

        List<String> player1Errors = validatePlayerParty(player1, format, validator);
        if (!player1Errors.isEmpty()) {
            MessageUtil.error(player1, Component.translatable("cobblemon_showdown.showdown_battle.series.not_met_requirement"));
            MessageUtil.error(player2, Component.translatable("cobblemon_showdown.showdown_battle.series.canceled.invalid_party"));
            SeriesTracker.endSeries(player1.getUUID(), player2.getUUID());
            return;
        }

        List<String> player2Errors = validatePlayerParty(player2, format, validator);
        if (!player2Errors.isEmpty()) {
            MessageUtil.error(player2, Component.translatable("cobblemon_showdown.showdown_battle.series.not_met_requirement"));
            MessageUtil.error(player1, Component.translatable("cobblemon_showdown.showdown_battle.series.canceled.invalid_party"));
            SeriesTracker.endSeries(player1.getUUID(), player2.getUUID());
            return;
        }

        storeHeldItems(player1);
        storeHeldItems(player2);

        int player1PartySize = countValidPokemon(player1);
        int player2PartySize = countValidPokemon(player2);

        if (player1PartySize > format.getPartySize() || player2PartySize > format.getPartySize()) {
            startPartySelection(player1, player2, format, "challenge", null);
        } else {
            proceedToTeamPreviewOrBattle(player1, player2, format, "challenge", null, null, null);
        }
    }

    private void startBattleImmediate(ShowdownBattle battle, PrizeHandler.ItemBet itemBet) {
        registerBattle(battle);
        boolean started = battle.start();

        if (started) {
            if (battle.getCobblemonBattle() != null) {
                registerCobblemonBattleId(battle.getCobblemonBattle().getBattleId());
            }

            MessageUtil.success(battle.getPlayer1(), Component.translatable(
                "cobblemon_showdown.showdown_battle.immediate.start",
                battle.getPlayer2().getName()));
            MessageUtil.success(battle.getPlayer2(), Component.translatable(
                "cobblemon_showdown.showdown_battle.immediate.start",
                battle.getPlayer1().getName()));

            BattleStatePacket startPacket = BattleStatePacket.battleStarted(
                    battle.getBattleId(),
                    battle.getPlayer1().getName().getString(),
                    battle.getPlayer2().getName().getString(),
                    battle.getFormat().getName()
            );
            ShowdownNetwork.sendToPlayer(startPacket, battle.getPlayer1());
            ShowdownNetwork.sendToPlayer(startPacket, battle.getPlayer2());

            if (itemBet != null) {
                battle.setItemBet(itemBet);
                LOGGER.info("Item bet stored for battle {}: {} x{}",
                        battle.getBattleId(), itemBet.getItemId(), itemBet.getAmount());
            }
        } else {
            unregisterBattle(battle.getBattleId());
            MessageUtil.error(battle.getPlayer1(), Component.translatable("cobblemon_showdown.showdown_battle.immediate.fail"));
            MessageUtil.error(battle.getPlayer2(), Component.translatable("cobblemon_showdown.showdown_battle.immediate.fail"));

            restoreHeldItems(battle.getPlayer1());
            restoreHeldItems(battle.getPlayer2());
        }
    }

    private void startBattleWithLeads(ShowdownBattle battle, UUID lead1Uuid, UUID lead2Uuid,
                                       PrizeHandler.ItemBet itemBet) {
        registerBattle(battle);
        boolean started = battle.start(lead1Uuid, lead2Uuid);

        if (started) {
            if (battle.getCobblemonBattle() != null) {
                registerCobblemonBattleId(battle.getCobblemonBattle().getBattleId());
            }

            MessageUtil.success(battle.getPlayer1(), Component.translatable("cobblemon_showdown.showdown_battle.start.success"));
            MessageUtil.success(battle.getPlayer2(), Component.translatable("cobblemon_showdown.showdown_battle.start.success"));

            BattleStatePacket startPacket = BattleStatePacket.battleStarted(
                    battle.getBattleId(),
                    battle.getPlayer1().getName().getString(),
                    battle.getPlayer2().getName().getString(),
                    battle.getFormat().getName()
            );
            ShowdownNetwork.sendToPlayer(startPacket, battle.getPlayer1());
            ShowdownNetwork.sendToPlayer(startPacket, battle.getPlayer2());

            if (itemBet != null) {
                battle.setItemBet(itemBet);
                LOGGER.info("Item bet stored for battle {}: {} x{}",
                        battle.getBattleId(), itemBet.getItemId(), itemBet.getAmount());
            }
        } else {
            unregisterBattle(battle.getBattleId());
            MessageUtil.error(battle.getPlayer1(), Component.translatable("cobblemon_showdown.showdown_battle.start.failed"));
            MessageUtil.error(battle.getPlayer2(), Component.translatable("cobblemon_showdown.showdown_battle.start.failed"));

            restoreHeldItems(battle.getPlayer1());
            restoreHeldItems(battle.getPlayer2());
        }
    }

    public void onBattleEnd(UUID battleId, UUID winnerUuid) {
        ShowdownBattle battle = activeBattles.get(battleId);
        if (battle == null) {
            LOGGER.warn("Battle end called for unknown battle: {}", battleId);
            return;
        }

        battle.onBattleEnd(winnerUuid);

        // keep ID tracked for 30 seconds to catch late loot drop events
        if (battle.getCobblemonBattle() != null) {
            unregisterCobblemonBattleIdDelayed(battle.getCobblemonBattle().getBattleId());
        }

        ServerPlayer winner = getPlayer(winnerUuid);
        ServerPlayer loser = battle.getOpponent(winner) != null ?
                getPlayer(battle.getOpponent(winner).getUUID()) : null;

        // unregister FIRST so HELD_ITEM_PRE handler doesn't block item restoration
        unregisterBattle(battleId);

        restoreHeldItems(battle.getPlayer1());
        restoreHeldItems(battle.getPlayer2());

        String winnerName = winner != null ? winner.getName().getString() : "unknown";
        BattleStatePacket endPacket = BattleStatePacket.battleEnded(
                battleId,
                battle.getPlayer1().getName().getString(),
                battle.getPlayer2().getName().getString(),
                winnerName
        );
        ShowdownNetwork.sendToPlayer(endPacket, battle.getPlayer1());
        ShowdownNetwork.sendToPlayer(endPacket, battle.getPlayer2());

        UUID player1Uuid = battle.getPlayer1().getUUID();
        UUID player2Uuid = battle.getPlayer2().getUUID();
        SeriesTracker.Series series = SeriesTracker.getSeries(player1Uuid, player2Uuid);

        if (series != null) {
            boolean seriesComplete = series.recordGameResult(winnerUuid);

            if (winner != null && loser != null) {
                SeriesTracker.sendGameResult(winner, loser, series);

                SeriesStatePacket gamePacket = SeriesStatePacket.gameEnded(
                        battle.getPlayer1().getName().getString(),
                        battle.getPlayer2().getName().getString(),
                        series.getPlayer1Wins(),
                        series.getPlayer2Wins(),
                        series.getBestOf(),
                        series.getGamesPlayed(),
                        winnerName
                );
                ShowdownNetwork.sendToPlayer(gamePacket, battle.getPlayer1());
                ShowdownNetwork.sendToPlayer(gamePacket, battle.getPlayer2());
            }

            if (seriesComplete) {
                recordMatchResult(battle, series.getSeriesWinner());
                ServerPlayer seriesWinnerPlayer = getPlayer(series.getSeriesWinner());
                String seriesWinnerName = seriesWinnerPlayer != null
                        ? seriesWinnerPlayer.getName().getString() : "unknown";
                SeriesStatePacket seriesEndPacket = SeriesStatePacket.seriesEnded(
                        battle.getPlayer1().getName().getString(),
                        battle.getPlayer2().getName().getString(),
                        series.getPlayer1Wins(),
                        series.getPlayer2Wins(),
                        series.getBestOf(),
                        seriesWinnerName
                );
                ShowdownNetwork.sendToPlayer(seriesEndPacket, battle.getPlayer1());
                ShowdownNetwork.sendToPlayer(seriesEndPacket, battle.getPlayer2());

                SeriesTracker.endSeries(player1Uuid, player2Uuid);
                LOGGER.info("Series completed - Winner: {}", series.getSeriesWinner());

                distributeBetWinnings(battle, series.getSeriesWinner());
            } else {
                LOGGER.info("Series continues: {} - Game {} of {}",
                        series.getScoreString(), series.getGamesPlayed() + 1, series.getBestOf());

                final ServerPlayer p1 = battle.getPlayer1();
                final ServerPlayer p2 = battle.getPlayer2();
                final Format fmt = battle.getFormat();
                final int nextGame = series.getGamesPlayed() + 1;

                MessageUtil.info(p1, Component.translatable("cobblemon_showdown.showdown_battle.series.next_timer", nextGame));
                MessageUtil.info(p2, Component.translatable("cobblemon_showdown.showdown_battle.series.next_timer", nextGame));

                TickScheduler.scheduleSeconds(() -> {
                    ServerPlayer currentP1 = getPlayer(p1.getUUID());
                    ServerPlayer currentP2 = getPlayer(p2.getUUID());

                    if (currentP1 == null || currentP2 == null) {
                        LOGGER.warn("Series game cancelled - player disconnected");
                        if (currentP1 != null) {
                            MessageUtil.error(currentP1, Component.translatable("cobblemon_showdown.showdown_battle.series.canceled.disconnected"));
                        }
                        if (currentP2 != null) {
                            MessageUtil.error(currentP2, Component.translatable("cobblemon_showdown.showdown_battle.series.canceled.disconnected"));
                        }
                        SeriesTracker.endSeries(p1.getUUID(), p2.getUUID());
                        return;
                    }

                    if (isInBattle(currentP1.getUUID()) || isInBattle(currentP2.getUUID())) {
                        LOGGER.warn("Series game delayed - player already in battle");
                        return;
                    }

                    MessageUtil.success(currentP1, Component.translatable("cobblemon_showdown.showdown_battle.series.next", nextGame));
                    MessageUtil.success(currentP2, Component.translatable("cobblemon_showdown.showdown_battle.series.next", nextGame));
                    startSeriesGame(currentP1, currentP2, fmt);

                }, 10, "Series game " + nextGame);
            }
        } else {
            recordMatchResult(battle, winnerUuid);

            distributeBetWinnings(battle, winnerUuid);
        }

        LOGGER.info("Battle {} ended - Winner: {}", battleId, winnerUuid);
    }

    private void distributeBetWinnings(ShowdownBattle battle, UUID winnerUuid) {
        PrizeHandler.ItemBet bet = battle.getItemBet();
        if (bet == null) {
            return;
        }

        ServerPlayer winner = getPlayer(winnerUuid);
        ServerPlayer loser = battle.getOpponent(winner);

        if (winner == null) {
            LOGGER.warn("Could not distribute bet - winner offline");
            return;
        }

        // winner receives 2x the bet (their own returned + loser's)
        boolean given = PrizeHandler.giveItem(winner, bet.getItemId(), bet.getAmount() * 2);
        if (given) {
            MessageUtil.success(winner, Component.translatable(
                "cobblemon_showdown.showdown_battle.bet.win",
                (bet.getAmount() * 2),
                PrizeHandler.getItemDisplayName(bet.getItemId())));
            LOGGER.info("Distributed bet to winner {}: {} x{}",
                    winner.getName().getString(), bet.getItemId(), bet.getAmount() * 2);

            if (loser != null) {
                MessageUtil.error(loser, Component.translatable(
                    "cobblemon_showdown.showdown_battle.bet.lose",
                    bet.getAmount(),
                    PrizeHandler.getItemDisplayName(bet.getItemId()),
                    winner.getName()));
            }
        } else {
            LOGGER.error("Failed to distribute bet to winner");
        }
    }

    private List<String> validatePlayerParty(ServerPlayer player, Format format, FormatValidator validator) {
        try {
            PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
            return validator.validateParty(party, format);
        } catch (Exception e) {
            LOGGER.error("Error validating party for {}", player.getName().getString(), e);
            return List.of("Error validating party: " + e.getMessage());
        }
    }

    private void storeHeldItems(ServerPlayer player) {
        try {
            PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
            Map<UUID, ItemStack> playerItems = new HashMap<>();

            for (int i = 0; i < party.size(); i++) {
                Pokemon pokemon = party.get(i);
                if (pokemon != null) {
                    pokemonInBattle.add(pokemon.getUuid());

                    ItemStack heldItem = pokemon.heldItem();
                    if (heldItem != null && !heldItem.isEmpty()) {
                        playerItems.put(pokemon.getUuid(), heldItem.copy());
                        LOGGER.debug("Stored held item for {}: {}",
                                pokemon.getSpecies().getName(), heldItem.getHoverName().getString());
                    }
                }
            }

            if (!playerItems.isEmpty()) {
                storedHeldItems.put(player.getUUID(), playerItems);
                LOGGER.info("Stored {} held items for {}",
                        playerItems.size(), player.getName().getString());
            }
        } catch (Exception e) {
            LOGGER.error("Error storing held items for {}", player.getName().getString(), e);
        }
    }

    /**
     * Must be called AFTER unregisterBattle() to prevent
     * HELD_ITEM_PRE event handler from blocking the restoration.
     */
    private void restoreHeldItems(ServerPlayer player) {
        Map<UUID, ItemStack> playerItems = storedHeldItems.remove(player.getUUID());

        try {
            PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);

            int removedCount = 0;
            for (int i = 0; i < party.size(); i++) {
                Pokemon pokemon = party.get(i);
                if (pokemon != null) {
                    if (pokemonInBattle.remove(pokemon.getUuid())) {
                        removedCount++;
                    }
                }
            }
            LOGGER.debug("Removed {} Pokemon from battle tracking for {}",
                    removedCount, player.getName().getString());

            if (playerItems == null || playerItems.isEmpty()) {
                LOGGER.debug("No stored held items to restore for {}", player.getName().getString());
                return;
            }

            int restoredCount = 0;
            for (int i = 0; i < party.size(); i++) {
                Pokemon pokemon = party.get(i);
                if (pokemon != null) {
                    ItemStack storedItem = playerItems.get(pokemon.getUuid());
                    if (storedItem != null) {
                        pokemon.swapHeldItem(storedItem, false);
                        restoredCount++;
                        LOGGER.debug("Restored held item for {}: {}",
                                pokemon.getSpecies().getName(), storedItem.getHoverName().getString());
                    }
                }
            }

            LOGGER.info("Restored {}/{} held items for {}",
                    restoredCount, playerItems.size(), player.getName().getString());
        } catch (Exception e) {
            LOGGER.error("Error restoring held items for {}", player.getName().getString(), e);
        }
    }

    private void applyLevelScaling(ServerPlayer player, int targetLevel) {
        try {
            PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
            Map<UUID, Integer> playerLevels = new HashMap<>();

            for (int i = 0; i < party.size(); i++) {
                Pokemon pokemon = party.get(i);
                if (pokemon != null) {
                    int originalLevel = pokemon.getLevel();
                    playerLevels.put(pokemon.getUuid(), originalLevel);

                    if (originalLevel != targetLevel) {
                        pokemon.setLevel(targetLevel);
                        LOGGER.debug("Scaled {} from level {} to {}",
                                pokemon.getSpecies().getName(), originalLevel, targetLevel);
                    }
                }
            }

            if (!playerLevels.isEmpty()) {
                storedLevels.put(player.getUUID(), playerLevels);
                LOGGER.info("Applied level scaling to {} Pokemon for {} (target: {})",
                        playerLevels.size(), player.getName().getString(), targetLevel);
            }
        } catch (Exception e) {
            LOGGER.error("Error applying level scaling for {}", player.getName().getString(), e);
        }
    }

    private void restoreLevels(ServerPlayer player) {
        Map<UUID, Integer> playerLevels = storedLevels.remove(player.getUUID());
        if (playerLevels == null || playerLevels.isEmpty()) {
            return;
        }

        try {
            PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);

            for (int i = 0; i < party.size(); i++) {
                Pokemon pokemon = party.get(i);
                if (pokemon != null) {
                    Integer originalLevel = playerLevels.get(pokemon.getUuid());
                    if (originalLevel != null && pokemon.getLevel() != originalLevel) {
                        pokemon.setLevel(originalLevel);
                        LOGGER.debug("Restored {} to level {}",
                                pokemon.getSpecies().getName(), originalLevel);
                    }
                }
            }

            LOGGER.info("Restored original levels for {}", player.getName().getString());
        } catch (Exception e) {
            LOGGER.error("Error restoring levels for {}", player.getName().getString(), e);
        }
    }

    private void recordMatchResult(ShowdownBattle battle, UUID winnerUuid) {
        HistoryStorage historyStorage = CobblemonShowdown.getHistoryStorage();
        if (historyStorage == null) return;

        UUID player1Uuid = battle.getPlayer1().getUUID();
        UUID player2Uuid = battle.getPlayer2().getUUID();
        String formatId = battle.getFormat().getName();
        PlayerHistory history1 = historyStorage.getHistory(player1Uuid);
        history1.addMatch(player2Uuid, formatId, player1Uuid.equals(winnerUuid));
        historyStorage.saveHistory(player1Uuid);

        PlayerHistory history2 = historyStorage.getHistory(player2Uuid);
        history2.addMatch(player1Uuid, formatId, player2Uuid.equals(winnerUuid));
        historyStorage.saveHistory(player2Uuid);
    }

    private void registerBattle(ShowdownBattle battle) {
        activeBattles.put(battle.getBattleId(), battle);
        playerToBattle.put(battle.getPlayer1().getUUID(), battle.getBattleId());
        playerToBattle.put(battle.getPlayer2().getUUID(), battle.getBattleId());
    }

    private void unregisterBattle(UUID battleId) {
        ShowdownBattle battle = activeBattles.remove(battleId);
        if (battle != null) {
            playerToBattle.remove(battle.getPlayer1().getUUID());
            playerToBattle.remove(battle.getPlayer2().getUUID());
        }
    }

    public boolean isInBattle(UUID playerUuid) {
        return playerToBattle.containsKey(playerUuid);
    }

    public boolean isPokemonInShowdownBattle(UUID pokemonUuid) {
        return pokemonInBattle.contains(pokemonUuid);
    }

    // persists after battle ends to catch late loot drop events from cloned Pokemon
    public boolean isCobblemonBattleShowdown(UUID cobblemonBattleId) {
        return showdownCobblemonBattleIds.contains(cobblemonBattleId);
    }

    public void registerCobblemonBattleId(UUID cobblemonBattleId) {
        showdownCobblemonBattleIds.add(cobblemonBattleId);
        LOGGER.debug("Registered Cobblemon battle ID {} as Showdown battle", cobblemonBattleId);
    }

    // delay ensures late loot drop events are still caught
    public void unregisterCobblemonBattleIdDelayed(UUID cobblemonBattleId) {
        TickScheduler.scheduleSeconds(() -> {
            showdownCobblemonBattleIds.remove(cobblemonBattleId);
            LOGGER.debug("Unregistered Cobblemon battle ID {} from Showdown tracking", cobblemonBattleId);
        }, 30, "Unregister battle ID " + cobblemonBattleId);
    }

    public ShowdownBattle getPlayerBattle(UUID playerUuid) {
        UUID battleId = playerToBattle.get(playerUuid);
        return battleId != null ? activeBattles.get(battleId) : null;
    }

    public ShowdownBattle getBattle(UUID battleId) {
        return activeBattles.get(battleId);
    }

    public Map<UUID, ShowdownBattle> getActiveBattles() {
        return Map.copyOf(activeBattles);
    }

    public boolean addSpectator(ServerPlayer spectator, UUID battleId) {
        ShowdownBattle battle = activeBattles.get(battleId);
        if (battle == null) {
            MessageUtil.error(spectator, Component.translatable("cobblemon_showdown.showdown_battle.spectate.not_found"));
            return false;
        }

        return battle.getSpectatorManager().addSpectator(spectator);
    }

    public boolean spectatePlayer(ServerPlayer spectator, String targetPlayerName) {
        ServerPlayer targetPlayer = server.getPlayerList().getPlayerByName(targetPlayerName);
        if (targetPlayer == null) {
            MessageUtil.error(spectator, Component.translatable("cobblemon_showdown.showdown_battle.spectate.no_player", targetPlayerName));
            return false;
        }

        ShowdownBattle battle = getPlayerBattle(targetPlayer.getUUID());
        if (battle == null) {
            MessageUtil.error(spectator, Component.translatable("cobblemon_showdown.showdown_battle.spectate.idle_player", targetPlayerName));
            return false;
        }

        return battle.getSpectatorManager().addSpectator(spectator);
    }

    public boolean removeSpectator(ServerPlayer spectator) {
        for (ShowdownBattle battle : activeBattles.values()) {
            if (battle.getSpectatorManager().isSpectating(spectator.getUUID())) {
                return battle.getSpectatorManager().removeSpectator(spectator);
            }
        }
        MessageUtil.error(spectator, Component.translatable("cobblemon_showdown.showdown_battle.spectate.not_active"));
        return false;
    }

    private ServerPlayer getPlayer(UUID uuid) {
        return server != null ? server.getPlayerList().getPlayer(uuid) : null;
    }

    public boolean abortBattle(ServerPlayer player) {
        UUID playerUuid = player.getUUID();

        PartySelectionSession.cancelSession(playerUuid);
        TeamPreview.cancelSession(playerUuid);

        ShowdownBattle battle = getPlayerBattle(playerUuid);

        // if not in a battle, check if in a series (between rounds of a best-of match)
        if (battle == null) {
            return abortSeries(player);
        }

        // the player who aborts loses (forfeit)
        ServerPlayer opponent = battle.getOpponent(player);
        UUID winnerUuid = opponent != null ? opponent.getUUID() : null;

        if (opponent != null) {
            PartySelectionSession.cancelSession(opponent.getUUID());
            TeamPreview.cancelSession(opponent.getUUID());
        }

        MessageUtil.info(player, Component.translatable("cobblemon_showdown.showdown_battle.abort.forfeit"));
        if (opponent != null) {
            MessageUtil.success(opponent, Component.translatable("cobblemon_showdown.showdown_battle.abort.forfeit_opponent", player.getName()));
        }

        try {
            if (battle.getCobblemonBattle() != null) {
                battle.getCobblemonBattle().end();
            }
        } catch (Exception e) {
            LOGGER.warn("Error ending Cobblemon battle: {}", e.getMessage());
        }

        onBattleEnd(battle.getBattleId(), winnerUuid);

        return true;
    }

    private boolean abortSeries(ServerPlayer player) {
        UUID playerUuid = player.getUUID();

        SeriesTracker.Series series = SeriesTracker.findSeriesByPlayer(playerUuid);

        if (series == null) {
            MessageUtil.error(player, Component.translatable("cobblemon_showdown.showdown_battle.abort_series.not_active"));
            return false;
        }

        UUID opponentUuid = series.getPlayer1().equals(playerUuid) ?
                series.getPlayer2() : series.getPlayer1();
        ServerPlayer opponent = getPlayer(opponentUuid);

        TickScheduler.cancel("Series game");

        // prevent countdowns from starting the next game after abort
        PartySelectionSession.cancelSession(playerUuid);
        PartySelectionSession.cancelSession(opponentUuid);
        TeamPreview.cancelSession(playerUuid);
        TeamPreview.cancelSession(opponentUuid);

        MessageUtil.info(player, Component.translatable("cobblemon_showdown.showdown_battle.abort_series.forfeit"));
        if (opponent != null) {
            MessageUtil.success(opponent, Component.translatable("cobblemon_showdown.showdown_battle.abort_series.forfeit_opponent", player.getName()));
        }

        String formatId = series.getFormat().getName();

        HistoryStorage historyStorage = CobblemonShowdown.getHistoryStorage();
        if (historyStorage != null) {
            PlayerHistory history1 = historyStorage.getHistory(playerUuid);
            history1.addMatch(opponentUuid, formatId, false);
            historyStorage.saveHistory(playerUuid);

            PlayerHistory history2 = historyStorage.getHistory(opponentUuid);
            history2.addMatch(playerUuid, formatId, true);
            historyStorage.saveHistory(opponentUuid);
        }

        SeriesTracker.endSeries(playerUuid, opponentUuid);

        LOGGER.info("Player {} forfeited series against {}",
                player.getName().getString(), opponentUuid);

        return true;
    }

    public void onPlayerDisconnect(UUID playerUuid) {
        ShowdownBattle battle = getPlayerBattle(playerUuid);
        if (battle != null) {
            ServerPlayer self = battle.getPlayer1().getUUID().equals(playerUuid)
                    ? battle.getPlayer1() : battle.getPlayer2();
            ServerPlayer opponent = battle.getOpponent(self);
            UUID winnerUuid = opponent != null ? opponent.getUUID() : null;

            if (opponent != null) {
                MessageUtil.info(opponent, Component.translatable("cobblemon_showdown.showdown_battle.disconnected"));
            }

            LOGGER.info("Player {} disconnected during battle, ending battle", playerUuid);

            try {
                if (battle.getCobblemonBattle() != null) {
                    battle.getCobblemonBattle().end();
                }
            } catch (Exception e) {
                LOGGER.warn("Error ending Cobblemon battle on disconnect: {}", e.getMessage());
            }

            // levels not modified - we use cloned Pokemon
            if (opponent != null) {
                restoreHeldItems(opponent);
            }

            UUID battleId = battle.getBattleId();
            activeBattles.remove(battleId);
            playerToBattle.remove(battle.getPlayer1().getUUID());
            playerToBattle.remove(battle.getPlayer2().getUUID());

            if (opponent != null) {
                BattleStatePacket endPacket = BattleStatePacket.battleEnded(
                        battleId,
                        battle.getPlayer1().getName().getString(),
                        battle.getPlayer2().getName().getString(),
                        opponent.getName().getString()
                );
                ShowdownNetwork.sendToPlayer(endPacket, opponent);
            }

            SeriesTracker.endSeries(battle.getPlayer1().getUUID(), battle.getPlayer2().getUUID());
        }
    }

    public void onPlayerJoin(ServerPlayer player) {
        UUID playerUuid = player.getUUID();

        if (playerToBattle.containsKey(playerUuid)) {
            UUID battleId = playerToBattle.get(playerUuid);
            if (!activeBattles.containsKey(battleId)) {
                playerToBattle.remove(playerUuid);
                LOGGER.info("Cleaned up stale battle registration for {}", player.getName().getString());
            } else {
                playerToBattle.remove(playerUuid);
                LOGGER.warn("Removed {} from active battle they weren't in", player.getName().getString());
            }
        }

        restoreLevelsForRejoiningPlayer(player);
        restoreHeldItemsForRejoiningPlayer(player);
    }

    private void restoreLevelsForRejoiningPlayer(ServerPlayer player) {
        Map<UUID, Integer> playerLevels = storedLevels.remove(player.getUUID());
        if (playerLevels == null || playerLevels.isEmpty()) {
            return;
        }

        try {
            PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
            int restored = 0;

            for (int i = 0; i < party.size(); i++) {
                Pokemon pokemon = party.get(i);
                if (pokemon != null) {
                    Integer originalLevel = playerLevels.get(pokemon.getUuid());
                    if (originalLevel != null && pokemon.getLevel() != originalLevel) {
                        pokemon.setLevel(originalLevel);
                        restored++;
                        LOGGER.debug("Restored {} to level {} on rejoin",
                                pokemon.getSpecies().getName(), originalLevel);
                    }
                }
            }

            if (restored > 0) {
                MessageUtil.info(player, Component.translatable("cobblemon_showdown.showdown_battle.rejoin.restore_level"));
                LOGGER.info("Restored {} Pokemon levels for rejoining player {}",
                        restored, player.getName().getString());
            }
        } catch (Exception e) {
            LOGGER.error("Error restoring levels for rejoining player {}", player.getName().getString(), e);
        }
    }

    private void restoreHeldItemsForRejoiningPlayer(ServerPlayer player) {
        Map<UUID, ItemStack> playerItems = storedHeldItems.remove(player.getUUID());
        if (playerItems == null || playerItems.isEmpty()) {
            return;
        }

        try {
            PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
            int restored = 0;

            for (int i = 0; i < party.size(); i++) {
                Pokemon pokemon = party.get(i);
                if (pokemon != null) {
                    ItemStack storedItem = playerItems.get(pokemon.getUuid());
                    if (storedItem != null) {
                        pokemon.swapHeldItem(storedItem, false);
                        restored++;
                        LOGGER.debug("Restored held item for {} on rejoin: {}",
                                pokemon.getSpecies().getName(), storedItem.getHoverName().getString());
                    }
                }
            }

            if (restored > 0) {
                MessageUtil.info(player, Component.translatable("cobblemon_showdown.showdown_battle.rejoin.restore_item"));
                LOGGER.info("Restored {} held items for rejoining player {}",
                        restored, player.getName().getString());
            }
        } catch (Exception e) {
            LOGGER.error("Error restoring held items for rejoining player {}", player.getName().getString(), e);
        }
    }

    public void shutdown() {
        for (ShowdownBattle battle : activeBattles.values()) {
            battle.onBattleEnd(null);
        }
        activeBattles.clear();
        playerToBattle.clear();

        pokemonInBattle.clear();
        showdownCobblemonBattleIds.clear();

        SeriesTracker.clearAll();

        TeamPreview.shutdown();

        LOGGER.info("BattleManager shut down");
    }
}
