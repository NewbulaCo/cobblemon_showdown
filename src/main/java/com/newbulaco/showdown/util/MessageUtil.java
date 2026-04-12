package com.newbulaco.showdown.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;

public class MessageUtil {

    private static final String PREFIX = "[Showdown] ";

    public static void send(ServerPlayer player, String message) {
        player.sendSystemMessage(prefixed(message));
    }

    public static void send(ServerPlayer player, Component message) {
        MutableComponent prefixedMsg = Component.literal(PREFIX)
                .withStyle(ChatFormatting.GOLD)
                .append(message);
        player.sendSystemMessage(prefixedMsg);
    }

    public static void success(ServerPlayer player, String message) {
        send(player, Component.literal(message).withStyle(ChatFormatting.GREEN));
    }

    public static void success(ServerPlayer player, MutableComponent message) {
        send(player, message.withStyle(ChatFormatting.GREEN));
    }

    public static void error(ServerPlayer player, String message) {
        send(player, Component.literal(message).withStyle(ChatFormatting.RED));
    }

    public static void error(ServerPlayer player, MutableComponent message) {
        send(player, message.withStyle(ChatFormatting.RED));
    }

    public static void warning(ServerPlayer player, String message) {
        send(player, Component.literal(message).withStyle(ChatFormatting.YELLOW));
    }

    public static void info(ServerPlayer player, String message) {
        send(player, Component.literal(message).withStyle(ChatFormatting.AQUA));
    }

    public static MutableComponent prefixed(String message) {
        return Component.literal(PREFIX)
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(message).withStyle(ChatFormatting.WHITE));
    }

    public static MutableComponent button(String label, String command, String hoverText) {
        return Component.literal("[" + label + "]")
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.GREEN)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal(hoverText))));
    }

    public static MutableComponent button(String label, String command, String hoverText, ChatFormatting color) {
        return Component.literal("[" + label + "]")
                .withStyle(Style.EMPTY
                        .withColor(color)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal(hoverText))));
    }

    public static MutableComponent challengeButtons(String challengerName) {
        return Component.empty()
                .append(button("Accept", "/showdown accept " + challengerName, "Accept the challenge"))
                .append(Component.literal(" "))
                .append(button("Deny", "/showdown deny " + challengerName, "Deny the challenge")
                        .withStyle(Style.EMPTY.withColor(ChatFormatting.RED).withBold(true)));
    }

    public static MutableComponent header(Component title) {
        return Component.literal("=== ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.empty().withStyle(ChatFormatting.GOLD).append(title))
                .append(Component.literal(" ===").withStyle(ChatFormatting.GRAY));
    }

    public static MutableComponent separator() {
        return Component.literal("------------------------").withStyle(ChatFormatting.GRAY);
    }

    public static MutableComponent keyValue(String key, String value) {
        return Component.literal(key + ": ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(ChatFormatting.WHITE));
    }

    public static MutableComponent keyValue(String key, String value, ChatFormatting valueColor) {
        return Component.literal(key + ": ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(valueColor));
    }

    public static String formatTime(int seconds) {
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            int mins = seconds / 60;
            int secs = seconds % 60;
            return mins + "m " + secs + "s";
        } else {
            int hours = seconds / 3600;
            int mins = (seconds % 3600) / 60;
            return hours + "h " + mins + "m";
        }
    }

    public static MutableComponent playerWithRecord(String name, int wins, int losses) {
        return Component.literal(name)
                .withStyle(ChatFormatting.WHITE)
                .append(Component.literal(" (")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(wins))
                        .withStyle(ChatFormatting.GREEN))
                .append(Component.literal("-")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(losses))
                        .withStyle(ChatFormatting.RED))
                .append(Component.literal(")")
                        .withStyle(ChatFormatting.GRAY));
    }

    public static String formatPlacement(int placement) {
        if (placement == 1) return "1st";
        if (placement == 2) return "2nd";
        if (placement == 3) return "3rd";
        return placement + "th";
    }

    public static ChatFormatting placementColor(int placement) {
        switch (placement) {
            case 1: return ChatFormatting.GOLD;
            case 2: return ChatFormatting.GRAY;
            case 3: return ChatFormatting.DARK_RED;
            default: return ChatFormatting.WHITE;
        }
    }
}
