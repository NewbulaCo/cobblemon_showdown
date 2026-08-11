package com.newbulaco.showdown.client.gui;

import com.newbulaco.showdown.CobblemonShowdown;
import com.newbulaco.showdown.battle.BattleTimer;
import com.newbulaco.showdown.client.ClientBattleStateManager;
import com.newbulaco.showdown.client.ClientBattleStateManager.ActiveBattle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CobblemonShowdown.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class BattleTimerOverlay {

    private static final int PADDING = 4;
    private static final int MARGIN = 6;
    private static final int LINE_SPACING = 2;

    private static final int COLOR_NORMAL = 0xFFFFFFFF;
    private static final int COLOR_WARN = 0xFFFFCC44;
    private static final int COLOR_CRITICAL = 0xFFFF5555;
    private static final int COLOR_BG = 0xB0000000;

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().getPath().equals("hotbar")) return;

        ActiveBattle battle = ClientBattleStateManager.getInstance().getCurrentBattle();
        if (battle == null || battle.ended) return;
        if (battle.player1TotalTime == 0 && battle.player2TotalTime == 0
                && battle.player1TurnTime == 0 && battle.player2TurnTime == 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        String selfName = mc.player.getName().getString();
        boolean isP1 = selfName.equals(battle.player1Name);
        boolean isP2 = selfName.equals(battle.player2Name);
        if (!isP1 && !isP2) return;

        int youTotal = isP1 ? battle.player1TotalTime : battle.player2TotalTime;
        int youTurn = isP1 ? battle.player1TurnTime : battle.player2TurnTime;
        int oppTotal = isP1 ? battle.player2TotalTime : battle.player1TotalTime;
        int oppTurn = isP1 ? battle.player2TurnTime : battle.player1TurnTime;
        String oppName = isP1 ? battle.player2Name : battle.player1Name;
        if (oppName == null || oppName.isEmpty()) oppName = "Opp";

        Font font = mc.font;
        Component youLabel = Component.translatable("cobblemon_showdown.battle_hud.timer.you");
        Component oppLabel = Component.literal(shorten(oppName, 10));

        Component youLine = Component.translatable(
                "cobblemon_showdown.battle_hud.timer.line",
                youLabel,
                BattleTimer.formatTime(youTotal),
                BattleTimer.formatTime(youTurn));
        Component oppLine = Component.translatable(
                "cobblemon_showdown.battle_hud.timer.line",
                oppLabel,
                BattleTimer.formatTime(oppTotal),
                BattleTimer.formatTime(oppTurn));

        int width = Math.max(font.width(youLine), font.width(oppLine));
        int height = font.lineHeight * 2 + LINE_SPACING;

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int boxX = screenW - width - PADDING * 2 - MARGIN;
        int boxY = screenH - height - PADDING * 2 - MARGIN;

        GuiGraphics graphics = event.getGuiGraphics();
        graphics.fill(boxX, boxY, boxX + width + PADDING * 2, boxY + height + PADDING * 2, COLOR_BG);

        int textX = boxX + PADDING;
        int textY = boxY + PADDING;
        graphics.drawString(font, youLine, textX, textY, colorFor(youTotal, youTurn), true);
        graphics.drawString(font, oppLine, textX, textY + font.lineHeight + LINE_SPACING,
                colorFor(oppTotal, oppTurn), true);
    }

    private static int colorFor(int total, int turn) {
        if (total <= 10 || turn <= 5) return COLOR_CRITICAL;
        if (total <= 30 || turn <= 10) return COLOR_WARN;
        return COLOR_NORMAL;
    }

    private static String shorten(String name, int max) {
        if (name.length() <= max) return name;
        return name.substring(0, max - 3) + "...";
    }
}
