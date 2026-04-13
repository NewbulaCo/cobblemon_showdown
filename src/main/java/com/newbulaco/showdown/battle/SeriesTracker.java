package com.newbulaco.showdown.battle;

import com.newbulaco.showdown.format.Format;
import com.newbulaco.showdown.util.MessageUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class SeriesTracker {
    private static final Logger LOGGER = LoggerFactory.getLogger(SeriesTracker.class);

    private static final Map<String, Series> activeSeries = new ConcurrentHashMap<>();

    public static class Series {
        private final UUID player1;
        private final UUID player2;
        private final Format format;
        private final int bestOf;
        private final int winsNeeded; // 1->1, 3->2, 5->3
        private final String context;

        private int player1Wins = 0;
        private int player2Wins = 0;
        private int gamesPlayed = 0;
        private boolean completed = false;
        private UUID seriesWinner = null;

        private BiConsumer<UUID, Series> onSeriesComplete;

        public Series(UUID player1, UUID player2, Format format, String context) {
            this.player1 = player1;
            this.player2 = player2;
            this.format = format;
            this.bestOf = format.getBestOf();
            this.winsNeeded = (bestOf / 2) + 1;
            this.context = context;
        }

        public boolean recordGameResult(UUID gameWinner) {
            if (completed) {
                return true;
            }

            gamesPlayed++;

            if (gameWinner.equals(player1)) {
                player1Wins++;
                LOGGER.debug("Series: Player 1 wins game {} ({}/{})",
                        gamesPlayed, player1Wins, winsNeeded);
            } else if (gameWinner.equals(player2)) {
                player2Wins++;
                LOGGER.debug("Series: Player 2 wins game {} ({}/{})",
                        gamesPlayed, player2Wins, winsNeeded);
            }

            if (player1Wins >= winsNeeded) {
                completed = true;
                seriesWinner = player1;
                LOGGER.info("Series complete: Player 1 wins {}-{}", player1Wins, player2Wins);
            } else if (player2Wins >= winsNeeded) {
                completed = true;
                seriesWinner = player2;
                LOGGER.info("Series complete: Player 2 wins {}-{}", player2Wins, player1Wins);
            }

            if (completed && onSeriesComplete != null) {
                onSeriesComplete.accept(seriesWinner, this);
            }

            return completed;
        }

        public UUID getPlayer1() {
            return player1;
        }

        public UUID getPlayer2() {
            return player2;
        }

        public Format getFormat() {
            return format;
        }

        public int getBestOf() {
            return bestOf;
        }

        public int getWinsNeeded() {
            return winsNeeded;
        }

        public String getContext() {
            return context;
        }

        public int getPlayer1Wins() {
            return player1Wins;
        }

        public int getPlayer2Wins() {
            return player2Wins;
        }

        public int getGamesPlayed() {
            return gamesPlayed;
        }

        public boolean isCompleted() {
            return completed;
        }

        public UUID getSeriesWinner() {
            return seriesWinner;
        }

        public void setOnSeriesComplete(BiConsumer<UUID, Series> callback) {
            this.onSeriesComplete = callback;
        }

        public int getWins(UUID playerUuid) {
            if (playerUuid.equals(player1)) {
                return player1Wins;
            } else if (playerUuid.equals(player2)) {
                return player2Wins;
            }
            return 0;
        }

        public int getRemainingGames() {
            return Math.max(0, bestOf - gamesPlayed);
        }

        public String getScoreString() {
            return player1Wins + "-" + player2Wins;
        }
    }

    // consistent ordering ensures the same key regardless of argument order
    private static String getSeriesId(UUID player1, UUID player2) {
        if (player1.compareTo(player2) < 0) {
            return player1.toString() + ":" + player2.toString();
        } else {
            return player2.toString() + ":" + player1.toString();
        }
    }

    public static Series startSeries(UUID player1, UUID player2, Format format, String context) {
        if (format.getBestOf() <= 1) {
            return null;
        }

        String seriesId = getSeriesId(player1, player2);

        if (activeSeries.containsKey(seriesId)) {
            LOGGER.warn("Series already exists between {} and {}", player1, player2);
            return activeSeries.get(seriesId);
        }

        Series series = new Series(player1, player2, format, context);
        activeSeries.put(seriesId, series);

        LOGGER.info("Started best-of-{} series between {} and {}",
                format.getBestOf(), player1, player2);

        return series;
    }

    public static Series getSeries(UUID player1, UUID player2) {
        String seriesId = getSeriesId(player1, player2);
        return activeSeries.get(seriesId);
    }

    public static boolean recordGameResult(UUID player1, UUID player2, UUID gameWinner) {
        Series series = getSeries(player1, player2);
        if (series == null) {
            return true; // no series tracking, game counts as complete
        }

        return series.recordGameResult(gameWinner);
    }

    public static void endSeries(UUID player1, UUID player2) {
        String seriesId = getSeriesId(player1, player2);
        Series removed = activeSeries.remove(seriesId);

        if (removed != null) {
            LOGGER.info("Series ended: {} vs {} - Final score: {}",
                    player1, player2, removed.getScoreString());
        }
    }

    public static boolean isInSeries(UUID player1, UUID player2) {
        return getSeries(player1, player2) != null;
    }

    public static Series findSeriesByPlayer(UUID playerUuid) {
        for (Series series : activeSeries.values()) {
            if (series.getPlayer1().equals(playerUuid) || series.getPlayer2().equals(playerUuid)) {
                return series;
            }
        }
        return null;
    }

    public static boolean isPlayerInAnySeries(UUID playerUuid) {
        return findSeriesByPlayer(playerUuid) != null;
    }

    public static void sendSeriesStatus(ServerPlayer player1, ServerPlayer player2, Series series) {
        MutableComponent status = Component.translatable("cobblemon_showdown.showdown_battle.series.stat",
            player1.getName(),
            player2.getName(),
            series.getScoreString(),
            series.getBestOf(),
            series.getWinsNeeded());

        MessageUtil.info(player1, status);
        MessageUtil.info(player2, status);
    }

    public static void sendGameResult(ServerPlayer winner, ServerPlayer loser, Series series) {
        if (series.isCompleted()) {
            MessageUtil.success(winner, Component.translatable(
                "cobblemon_showdown.showdown_battle.series.win",
                series.getScoreString()));
            MessageUtil.error(loser, Component.translatable(
                "cobblemon_showdown.showdown_battle.series.lose",
                series.getScoreString()));
        } else {
            MutableComponent status = Component.translatable("cobblemon_showdown.showdown_battle.series.part",
                series.getGamesPlayed(), series.getScoreString());
            MessageUtil.info(winner, status.copy().append(Component.translatable("cobblemon_showdown.showdown_battle.series.part.winning")));
            MessageUtil.info(loser, status);

            int gamesNeeded = series.getWinsNeeded() - series.getWins(loser.getUUID());
            MessageUtil.info(loser, Component.translatable("cobblemon_showdown.showdown_battle.series.part.losing", gamesNeeded));
        }
    }

    public static void clearAll() {
        activeSeries.clear();
    }

    public static int getActiveSeriesCount() {
        return activeSeries.size();
    }
}
