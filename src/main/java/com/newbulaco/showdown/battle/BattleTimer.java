package com.newbulaco.showdown.battle;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.newbulaco.showdown.CobblemonShowdown;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Matches Pokemon Showdown timer rules:
 * - total pool: 210s (does NOT reset between turns)
 * - per-turn limit: 60s (resets each turn)
 * - on move selection: gain time back to total pool
 * - either timer hits 0: player loses match
 */
public class BattleTimer {
    private static final Logger LOGGER = LoggerFactory.getLogger(BattleTimer.class);

    private static final int TOTAL_TIME_POOL = 210; // seconds
    private static final int PER_TURN_LIMIT = 60; // seconds
    private static final int TIME_GAIN_ON_MOVE = 10; // seconds gained back on move select

    private final ShowdownBattle battle;

    private int player1TotalTime = TOTAL_TIME_POOL;
    private int player1TurnTime = PER_TURN_LIMIT;
    private int player2TotalTime = TOTAL_TIME_POOL;
    private int player2TurnTime = PER_TURN_LIMIT;

    private UUID currentTurnPlayer;
    private boolean running = false;
    private boolean paused = false;
    private long lastTickTime = 0;
    private long accumulatedMs = 0;

    public BattleTimer(ShowdownBattle battle) {
        this.battle = battle;
    }

    public void start() {
        this.running = true;
        this.lastTickTime = System.currentTimeMillis();
        LOGGER.info("Battle timer started for battle {}", battle.getBattleId());
    }

    public void stop() {
        this.running = false;
        LOGGER.info("Battle timer stopped for battle {}", battle.getBattleId());
    }

    public void tick() {
        if (!running || paused || currentTurnPlayer == null) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        long deltaMs = currentTime - lastTickTime;
        lastTickTime = currentTime;

        accumulatedMs += deltaMs;

        while (accumulatedMs >= 1000) {
            accumulatedMs -= 1000;

            boolean isPlayer1Turn = currentTurnPlayer.equals(battle.getPlayer1().getUUID());

            if (isPlayer1Turn) {
                player1TotalTime--;
                player1TurnTime--;

                checkTimerExpiry(battle.getPlayer1(), player1TotalTime, player1TurnTime);
            } else {
                player2TotalTime--;
                player2TurnTime--;

                checkTimerExpiry(battle.getPlayer2(), player2TotalTime, player2TurnTime);
            }

            if (!running) {
                break;
            }
        }
    }

    public void onTurnStart() {
        player1TurnTime = PER_TURN_LIMIT;
        player2TurnTime = PER_TURN_LIMIT;

        LOGGER.debug("Turn timers reset - P1 total: {}s, P2 total: {}s",
                player1TotalTime, player2TotalTime);
    }

    public void onMoveSelected(UUID playerUuid) {
        if (playerUuid.equals(battle.getPlayer1().getUUID())) {
            player1TotalTime = Math.min(player1TotalTime + TIME_GAIN_ON_MOVE, TOTAL_TIME_POOL);
            LOGGER.debug("Player 1 gained time - total: {}s", player1TotalTime);
        } else if (playerUuid.equals(battle.getPlayer2().getUUID())) {
            player2TotalTime = Math.min(player2TotalTime + TIME_GAIN_ON_MOVE, TOTAL_TIME_POOL);
            LOGGER.debug("Player 2 gained time - total: {}s", player2TotalTime);
        }
    }

    public void setCurrentTurnPlayer(UUID playerUuid) {
        this.currentTurnPlayer = playerUuid;
    }

    private void checkTimerExpiry(ServerPlayer player, int totalTime, int turnTime) {
        if (totalTime <= 0) {
            LOGGER.info("Player {} lost by total time expiry", player.getName().getString());
            player.sendSystemMessage(Component.translatable("cobblemon_showdown.battle.timeout_total")
                .withStyle(ChatFormatting.RED));
            handleTimerLoss(player);
        } else if (turnTime <= 0) {
            LOGGER.info("Player {} lost by turn time expiry", player.getName().getString());
            player.sendSystemMessage(Component.translatable("cobblemon_showdown.battle.timeout_turn")
                .withStyle(ChatFormatting.RED));
            handleTimerLoss(player);
        } else if (totalTime <= 30) {
            player.sendSystemMessage(Component.translatable(
                "cobblemon_showdown.battle.time_warn_total",
                totalTime
            ).withStyle(ChatFormatting.YELLOW));
        } else if (turnTime <= 10) {
            player.sendSystemMessage(Component.translatable(
                "cobblemon_showdown.battle.time_warn_turn",
                turnTime
            ).withStyle(ChatFormatting.YELLOW));
        }
    }

    private void handleTimerLoss(ServerPlayer loser) {
        running = false;

        ServerPlayer winner = battle.getOpponent(loser);
        if (winner != null) {
            winner.sendSystemMessage(Component.translatable("cobblemon_showdown.battle.timeout_opponent")
                .withStyle(ChatFormatting.GREEN));

            forfeitCobblemonBattle(loser);

            // CobblemonBattleListener will receive the BATTLE_VICTORY event
        }
    }

    private void forfeitCobblemonBattle(ServerPlayer loser) {
        PokemonBattle cobblemonBattle = battle.getCobblemonBattle();
        if (cobblemonBattle == null) {
            LOGGER.warn("No Cobblemon battle to forfeit for battle {}", battle.getBattleId());
            return;
        }

        try {
            BattleActor loserActor = cobblemonBattle.getActor(loser.getUUID());
            if (loserActor == null) {
                LOGGER.warn("Could not find actor for player {} in battle", loser.getName().getString());
                return;
            }

            String showdownId = loserActor.getShowdownId();
            cobblemonBattle.writeShowdownAction(">forcelose " + showdownId);

            LOGGER.info("Forced forfeit for player {} (showdownId: {}) due to timer expiry",
                    loser.getName().getString(), showdownId);
        } catch (Exception e) {
            LOGGER.error("Error forfeiting Cobblemon battle for {}", loser.getName().getString(), e);
        }
    }

    public int getPlayer1TotalTime() {
        return player1TotalTime;
    }

    public int getPlayer1TurnTime() {
        return player1TurnTime;
    }

    public int getPlayer2TotalTime() {
        return player2TotalTime;
    }

    public int getPlayer2TurnTime() {
        return player2TurnTime;
    }

    public boolean isRunning() {
        return running;
    }

    public UUID getCurrentTurnPlayer() {
        return currentTurnPlayer;
    }

    public static String formatTime(int seconds) {
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }

    // for timer exceptions / allow_time
    public void pause() {
        this.paused = true;
        LOGGER.info("Battle timer paused for battle {}", battle.getBattleId());
    }

    public void resume() {
        this.paused = false;
        this.lastTickTime = System.currentTimeMillis();
        LOGGER.info("Battle timer resumed for battle {}", battle.getBattleId());
    }

    public boolean isPaused() {
        return paused;
    }

    public void grantExtraTime(UUID playerUuid, int seconds) {
        if (playerUuid.equals(battle.getPlayer1().getUUID())) {
            player1TotalTime = Math.min(player1TotalTime + seconds, TOTAL_TIME_POOL * 2);
            LOGGER.info("Granted {} seconds to player 1 - new total: {}s", seconds, player1TotalTime);
        } else if (playerUuid.equals(battle.getPlayer2().getUUID())) {
            player2TotalTime = Math.min(player2TotalTime + seconds, TOTAL_TIME_POOL * 2);
            LOGGER.info("Granted {} seconds to player 2 - new total: {}s", seconds, player2TotalTime);
        }
    }

    // grants a full time pool to prevent auto-DQ
    public void allowExtraTime(UUID playerUuid) {
        grantExtraTime(playerUuid, TOTAL_TIME_POOL);
    }
}
