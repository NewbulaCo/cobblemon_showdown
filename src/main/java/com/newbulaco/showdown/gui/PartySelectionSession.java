package com.newbulaco.showdown.gui;

import com.newbulaco.showdown.CobblemonShowdown;
import com.newbulaco.showdown.battle.BattleManager;
import com.newbulaco.showdown.format.Format;
import com.newbulaco.showdown.util.MessageUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

// tracks both players' selections and triggers battle start when both are ready
public class PartySelectionSession {
    private static final Logger LOGGER = LoggerFactory.getLogger(PartySelectionSession.class);

    public static final int SELECTION_TIMEOUT_MILLIS = 90000; // 90 seconds

    private static final Map<UUID, PartySelectionSession> activeSessions = new ConcurrentHashMap<>();

    private final UUID sessionId;
    private final long creationTime;
    private final ServerPlayer player1;
    private final ServerPlayer player2;
    private final Format format;
    private final String context;

    private final PartySelectionMenuProvider player1Menu;
    private final PartySelectionMenuProvider player2Menu;

    private BiConsumer<List<Integer>, List<Integer>> onBothSelected;
    private boolean timedOut = false;
    private boolean cancelled = false;
    private boolean completed = false;

    public PartySelectionSession(ServerPlayer player1, ServerPlayer player2, Format format, String context) {
        this.sessionId = UUID.randomUUID();
        this.creationTime = System.currentTimeMillis();
        this.player1 = player1;
        this.player2 = player2;
        this.format = format;
        this.context = context;

        this.player1Menu = new PartySelectionMenuProvider(this, player1, player2, format);
        this.player2Menu = new PartySelectionMenuProvider(this, player2, player1, format);

        activeSessions.put(player1.getUUID(), this);
        activeSessions.put(player2.getUUID(), this);
    }

    public void setOnBothSelected(BiConsumer<List<Integer>, List<Integer>> callback) {
        this.onBothSelected = callback;
    }

    public void openMenus() {
        player1.openMenu(player1Menu);
        player2.openMenu(player2Menu);

        MessageUtil.info(player1, "Select " + format.getPartySize() + " Pokemon for the battle");
        MessageUtil.info(player2, "Select " + format.getPartySize() + " Pokemon for the battle");

        LOGGER.info("Started party selection session: {} vs {} for format {}",
            player1.getName().getString(), player2.getName().getString(), format.getName());
    }

    public void onPokemonSelected(PartySelectionMenuProvider provider) {
        if (provider == player1Menu) {
            player2Menu.updateOpponentCount(player1Menu.getSelectedSlots().size());
        } else {
            player1Menu.updateOpponentCount(player2Menu.getSelectedSlots().size());
        }
    }

    public void onPlayerReadyChanged(PartySelectionMenuProvider provider, boolean ready) {
        if (provider == player1Menu) {
            player2Menu.updateOpponentReady(ready);
        } else {
            player1Menu.updateOpponentReady(ready);
        }

        if (isBattleReady()) {
            beginBattle();
        }
    }

    private boolean isBattleReady() {
        return player1Menu.isSelectionComplete() && player2Menu.isSelectionComplete();
    }

    private void beginBattle() {
        if (completed || cancelled || timedOut) return;
        completed = true;

        LOGGER.info("Party selection complete, initiating battle");

        player1Menu.forceCloseMenu();
        player2Menu.forceCloseMenu();

        cleanup();

        if (onBothSelected != null) {
            List<Integer> player1Slots = player1Menu.getSelectedSlots();
            List<Integer> player2Slots = player2Menu.getSelectedSlots();
            onBothSelected.accept(player1Slots, player2Slots);
        }
    }

    public void onPlayerCloseMenu(ServerPlayer player) {
        if (timedOut || completed || cancelled) return;
        cancelled = true;

        ServerPlayer otherPlayer = player.getUUID().equals(player1.getUUID()) ? player2 : player1;

        player.sendSystemMessage(
            Component.translatable("cobblemon_showdown.battle_gui.session.cancel")
                .withStyle(ChatFormatting.RED));
        otherPlayer.sendSystemMessage(
            Component.translatable("cobblemon_showdown.battle_gui.session.cancelled", player.getName())
                .withStyle(ChatFormatting.RED));

        player1Menu.forceCloseMenu();
        player2Menu.forceCloseMenu();

        cleanup();

        LOGGER.info("Party selection cancelled by {}", player.getName().getString());
    }

    public void timeout() {
        if (completed || cancelled) return;
        timedOut = true;

        player1.sendSystemMessage(Component.translatable("cobblemon_showdown.battle_gui.session.timeout")
            .withStyle(ChatFormatting.RED));
        player2.sendSystemMessage(Component.translatable("cobblemon_showdown.battle_gui.session.timeout")
            .withStyle(ChatFormatting.RED));

        player1Menu.forceCloseMenu();
        player2Menu.forceCloseMenu();

        cleanup();

        LOGGER.info("Party selection timed out: {} vs {}", player1.getName().getString(), player2.getName().getString());
    }

    public void tick() {
        if (System.currentTimeMillis() - creationTime > SELECTION_TIMEOUT_MILLIS) {
            timeout();
            return;
        }

        player1Menu.timedGuiUpdate();
        player2Menu.timedGuiUpdate();
    }

    private void cleanup() {
        activeSessions.remove(player1.getUUID());
        activeSessions.remove(player2.getUUID());
    }

    public long getCreationTime() {
        return creationTime;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public Format getFormat() {
        return format;
    }

    public String getContext() {
        return context;
    }

    public ServerPlayer getPlayer1() {
        return player1;
    }

    public ServerPlayer getPlayer2() {
        return player2;
    }

    public boolean isCompleted() {
        return completed;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    public static PartySelectionSession getSession(UUID playerUuid) {
        return activeSessions.get(playerUuid);
    }

    public static boolean hasActiveSession(UUID playerUuid) {
        return activeSessions.containsKey(playerUuid);
    }

    public static Iterable<PartySelectionSession> getAllSessions() {
        return activeSessions.values();
    }

    public static void cancelSession(UUID playerUuid) {
        PartySelectionSession session = activeSessions.get(playerUuid);
        if (session != null && !session.completed && !session.cancelled && !session.timedOut) {
            session.cancelled = true;
            session.player1Menu.forceCloseMenu();
            session.player2Menu.forceCloseMenu();
            session.cleanup();
        }
    }

    public static void tickAll() {
        // copy to avoid ConcurrentModificationException since tick() can call cleanup()
        for (PartySelectionSession session : activeSessions.values().toArray(new PartySelectionSession[0])) {
            if (!session.completed && !session.cancelled && !session.timedOut) {
                session.tick();
            }
        }
    }

    public static void clearAll() {
        for (PartySelectionSession session : activeSessions.values()) {
            session.player1Menu.forceCloseMenu();
            session.player2Menu.forceCloseMenu();
        }
        activeSessions.clear();
    }
}
