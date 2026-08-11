package com.newbulaco.showdown.battle;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class BattleTimer {
    private static final Logger LOGGER = LoggerFactory.getLogger(BattleTimer.class);

    private static final int TOTAL_TIME_POOL = 210;
    private static final int PER_TURN_LIMIT = 60;
    private static final int TIME_GAIN_ON_TURN = 10;

    private final ShowdownBattle battle;

    private int player1TotalTime = TOTAL_TIME_POOL;
    private int player1TurnTime = PER_TURN_LIMIT;
    private int player2TotalTime = TOTAL_TIME_POOL;
    private int player2TurnTime = PER_TURN_LIMIT;

    private boolean running = false;
    private boolean paused = false;
    private long lastTickTime = 0;
    private long accumulatedMs = 0;

    private int lastTurn = -1;
    private boolean p1WarnedTurn = false;
    private boolean p1WarnedTotal = false;
    private boolean p2WarnedTurn = false;
    private boolean p2WarnedTotal = false;

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
    }

    public void tick() {
        if (!running || paused) {
            return;
        }

        PokemonBattle cobblemonBattle = battle.getCobblemonBattle();
        if (cobblemonBattle != null) {
            int currentTurn = cobblemonBattle.getTurn();
            if (lastTurn == -1) {
                lastTurn = currentTurn;
            } else if (currentTurn > lastTurn) {
                onTurnAdvance();
                lastTurn = currentTurn;
            }
        }

        long currentTime = System.currentTimeMillis();
        long deltaMs = currentTime - lastTickTime;
        lastTickTime = currentTime;
        accumulatedMs += deltaMs;

        boolean p1Choosing = isActorChoosing(cobblemonBattle, battle.getPlayer1().getUUID());
        boolean p2Choosing = isActorChoosing(cobblemonBattle, battle.getPlayer2().getUUID());

        while (accumulatedMs >= 1000) {
            accumulatedMs -= 1000;

            if (p1Choosing) {
                player1TotalTime--;
                player1TurnTime--;
                checkTimerExpiry(battle.getPlayer1(), player1TotalTime, player1TurnTime, true);
                if (!running) return;
            }
            if (p2Choosing) {
                player2TotalTime--;
                player2TurnTime--;
                checkTimerExpiry(battle.getPlayer2(), player2TotalTime, player2TurnTime, false);
                if (!running) return;
            }
        }
    }

    private boolean isActorChoosing(PokemonBattle cobblemonBattle, UUID playerUuid) {
        if (cobblemonBattle == null) return false;
        BattleActor actor = cobblemonBattle.getActor(playerUuid);
        return actor != null && actor.getMustChoose();
    }

    private void onTurnAdvance() {
        player1TurnTime = PER_TURN_LIMIT;
        player2TurnTime = PER_TURN_LIMIT;
        player1TotalTime = Math.min(player1TotalTime + TIME_GAIN_ON_TURN, TOTAL_TIME_POOL);
        player2TotalTime = Math.min(player2TotalTime + TIME_GAIN_ON_TURN, TOTAL_TIME_POOL);
        p1WarnedTurn = false;
        p2WarnedTurn = false;
    }

    private void checkTimerExpiry(ServerPlayer player, int totalTime, int turnTime, boolean isPlayer1) {
        if (totalTime <= 0) {
            LOGGER.info("Player {} lost by total time expiry", player.getName().getString());
            player.sendSystemMessage(Component.translatable("cobblemon_showdown.battle.timeout_total")
                .withStyle(ChatFormatting.RED));
            handleTimerLoss(player);
            return;
        }
        if (turnTime <= 0) {
            LOGGER.info("Player {} lost by turn time expiry", player.getName().getString());
            player.sendSystemMessage(Component.translatable("cobblemon_showdown.battle.timeout_turn")
                .withStyle(ChatFormatting.RED));
            handleTimerLoss(player);
            return;
        }

        boolean warnedTotal = isPlayer1 ? p1WarnedTotal : p2WarnedTotal;
        boolean warnedTurn = isPlayer1 ? p1WarnedTurn : p2WarnedTurn;

        if (totalTime <= 30 && !warnedTotal) {
            player.sendSystemMessage(Component.translatable(
                "cobblemon_showdown.battle.time_warn_total", totalTime
            ).withStyle(ChatFormatting.YELLOW));
            if (isPlayer1) p1WarnedTotal = true; else p2WarnedTotal = true;
        } else if (turnTime <= 10 && !warnedTurn) {
            player.sendSystemMessage(Component.translatable(
                "cobblemon_showdown.battle.time_warn_turn", turnTime
            ).withStyle(ChatFormatting.YELLOW));
            if (isPlayer1) p1WarnedTurn = true; else p2WarnedTurn = true;
        }
    }

    private void handleTimerLoss(ServerPlayer loser) {
        running = false;

        ServerPlayer winner = battle.getOpponent(loser);
        if (winner != null) {
            winner.sendSystemMessage(Component.translatable("cobblemon_showdown.battle.timeout_opponent")
                .withStyle(ChatFormatting.GREEN));

            forfeitCobblemonBattle(loser);
        }
    }

    private void forfeitCobblemonBattle(ServerPlayer loser) {
        PokemonBattle cobblemonBattle = battle.getCobblemonBattle();
        if (cobblemonBattle == null) {
            return;
        }

        try {
            BattleActor loserActor = cobblemonBattle.getActor(loser.getUUID());
            if (loserActor == null) {
                return;
            }

            String showdownId = loserActor.getShowdownId();
            cobblemonBattle.writeShowdownAction(">forcelose " + showdownId);
        } catch (Exception e) {
            LOGGER.error("Error forfeiting Cobblemon battle for {}", loser.getName().getString(), e);
        }
    }

    public int getPlayer1TotalTime() { return player1TotalTime; }
    public int getPlayer1TurnTime() { return player1TurnTime; }
    public int getPlayer2TotalTime() { return player2TotalTime; }
    public int getPlayer2TurnTime() { return player2TurnTime; }

    public boolean isRunning() { return running; }

    public static String formatTime(int seconds) {
        if (seconds < 0) seconds = 0;
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }

    public void pause() {
        this.paused = true;
    }

    public void resume() {
        this.paused = false;
        this.lastTickTime = System.currentTimeMillis();
    }

    public boolean isPaused() { return paused; }

    public void grantExtraTime(UUID playerUuid, int seconds) {
        if (playerUuid.equals(battle.getPlayer1().getUUID())) {
            player1TotalTime = Math.min(player1TotalTime + seconds, TOTAL_TIME_POOL * 2);
        } else if (playerUuid.equals(battle.getPlayer2().getUUID())) {
            player2TotalTime = Math.min(player2TotalTime + seconds, TOTAL_TIME_POOL * 2);
        }
    }

    public void allowExtraTime(UUID playerUuid) {
        grantExtraTime(playerUuid, TOTAL_TIME_POOL);
    }
}
