package com.newbulaco.showdown.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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

    public static void success(ServerPlayer player, MutableComponent message) {
        send(player, message.withStyle(ChatFormatting.GREEN));
    }

    public static void error(ServerPlayer player, MutableComponent message) {
        send(player, message.withStyle(ChatFormatting.RED));
    }

    public static void warning(ServerPlayer player, MutableComponent message) {
        send(player, message.withStyle(ChatFormatting.YELLOW));
    }

    public static void info(ServerPlayer player, MutableComponent message) {
        send(player, message.withStyle(ChatFormatting.AQUA));
    }

    public static MutableComponent prefixed(String message) {
        return Component.literal(PREFIX)
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(message).withStyle(ChatFormatting.WHITE));
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
}
