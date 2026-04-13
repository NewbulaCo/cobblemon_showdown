package com.newbulaco.showdown.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.newbulaco.showdown.CobblemonShowdown;
import com.newbulaco.showdown.battle.BattleManager;
import com.newbulaco.showdown.battle.TeamPreview;
import com.newbulaco.showdown.format.Format;
import com.newbulaco.showdown.format.FormatManager;
import com.newbulaco.showdown.util.MessageUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

public class ShowdownCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> showdownCommand = Commands.literal("showdown");

        FormatCommand.register(showdownCommand);
        ChallengeCommand.register(showdownCommand);

        DtCommand.register(showdownCommand);

        showdownCommand.then(Commands.literal("preview_select")
                .then(Commands.argument("slot", IntegerArgumentType.integer(0, 5))
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            int slot = IntegerArgumentType.getInteger(ctx, "slot");
                            return TeamPreview.selectLead(player, slot) ? 1 : 0;
                        })));

        showdownCommand.then(Commands.literal("spectate")
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            String targetName = StringArgumentType.getString(ctx, "player");

                            BattleManager battleManager = CobblemonShowdown.getBattleManager();
                            if (battleManager == null) {
                                MessageUtil.error(player, Component.translatable("command.cobblemon_showdown.challenge.no_manager"));
                                return 0;
                            }

                            return battleManager.spectatePlayer(player, targetName) ? 1 : 0;
                        })));

        showdownCommand.then(Commands.literal("unspectate")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();

                    BattleManager battleManager = CobblemonShowdown.getBattleManager();
                    if (battleManager == null) {
                        MessageUtil.error(player, Component.translatable("command.cobblemon_showdown.challenge.no_manager"));
                        return 0;
                    }

                    return battleManager.removeSpectator(player) ? 1 : 0;
                }));

        showdownCommand.then(Commands.literal("abort")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();

                    BattleManager battleManager = CobblemonShowdown.getBattleManager();
                    if (battleManager == null) {
                        MessageUtil.error(player, Component.translatable("command.cobblemon_showdown.challenge.no_manager"));
                        return 0;
                    }

                    return battleManager.abortBattle(player) ? 1 : 0;
                }));

        showdownCommand.then(Commands.literal("formats")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    FormatManager formatManager = CobblemonShowdown.getFormatManager();

                    if (formatManager == null) {
                        MessageUtil.error(player, Component.translatable("command.cobblemon_showdown.format.no_manager"));
                        return 0;
                    }

                    Collection<String> formatIds = formatManager.getFormatIds();
                    if (formatIds.isEmpty()) {
                        MessageUtil.info(player, Component.translatable("command.cobblemon_showdown.format.no_format"));
                        return 1;
                    }

                    MessageUtil.info(player, Component.translatable("command.cobblemon_showdown.format.available_formats", formatIds.size()));
                    for (String formatId : formatIds) {
                        Format format = formatManager.getFormat(formatId);
                        if (format != null) {
                            player.sendSystemMessage(Component.literal("  §b" + formatId + " §7- " + format.getName()));
                        }
                    }
                    return 1;
                }));

        showdownCommand.then(Commands.literal("help")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    showHelp(player);
                    return 1;
                }));

        showdownCommand.executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            showHelp(player);
            return 1;
        });

        dispatcher.register(showdownCommand);
    }

    private static void showHelp(ServerPlayer player) {
        MutableComponent help = Component.translatable("command.cobblemon_showdown.help")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        player.sendSystemMessage(help);

        player.sendSystemMessage(Component.literal(""));

        player.sendSystemMessage(Component.translatable("command.cobblemon_showdown.help.challenge")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        sendHelpLine(player, "/showdown challenge <player> <format>",
            Component.translatable("command.cobblemon_showdown.help.challenge.start"));
        sendHelpLine(player, "/showdown challenge <player> <format> item_bet:<item>,<amount>",
            Component.translatable("command.cobblemon_showdown.help.challenge.start_bet"));
        sendHelpLine(player, "/showdown accept <player>",
            Component.translatable("command.cobblemon_showdown.help.challenge.accept"));
        sendHelpLine(player, "/showdown deny <player>",
            Component.translatable("command.cobblemon_showdown.help.challenge.deny"));
        sendHelpLine(player, "/showdown abort",
            Component.translatable("command.cobblemon_showdown.help.challenge.abort"));

        player.sendSystemMessage(Component.literal(""));

        player.sendSystemMessage(Component.translatable("command.cobblemon_showdown.help.info")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        sendHelpLine(player, "/showdown formats",
            Component.translatable("command.cobblemon_showdown.help.info.format_view"));
        sendHelpLine(player, "/showdown format_party_test <format>",
            Component.translatable("command.cobblemon_showdown.help.info.format_test"));
        sendHelpLine(player, "/showdown history [player]",
            Component.translatable("command.cobblemon_showdown.help.info.history"));

        player.sendSystemMessage(Component.literal(""));

        player.sendSystemMessage(Component.translatable("command.cobblemon_showdown.help.spectate")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        sendHelpLine(player, "/showdown spectate <player>",
            Component.translatable("command.cobblemon_showdown.help.spectate.start"));
        sendHelpLine(player, "/showdown unspectate",
            Component.translatable("command.cobblemon_showdown.help.spectate.end"));

        if (player.hasPermissions(2)) {
            player.sendSystemMessage(Component.literal(""));
            player.sendSystemMessage(Component.translatable("command.cobblemon_showdown.help.level2")
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
            sendHelpLine(player, "/showdown format <id> export",
                Component.translatable("command.cobblemon_showdown.help.level2.format_export"));
        }

        if (player.hasPermissions(3)) {
            player.sendSystemMessage(Component.literal(""));
            player.sendSystemMessage(Component.translatable("command.cobblemon_showdown.help.level3")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            sendHelpLine(player, "/showdown format <id> create",
                Component.translatable("command.cobblemon_showdown.help.level3.format_create"));
            sendHelpLine(player, "/showdown format <id> edit",
                Component.translatable("command.cobblemon_showdown.help.level3.format_edit"));
            sendHelpLine(player, "/showdown format <id> delete",
                Component.translatable("command.cobblemon_showdown.help.level3.format_delete"));
            sendHelpLine(player, "/showdown format <id> import <json>",
                Component.translatable("command.cobblemon_showdown.help.level3.format_import"));
        }
    }

    private static void sendHelpLine(ServerPlayer player, String command, Component description) {
        MutableComponent line = Component.literal("  " + command)
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(" - ").withStyle(ChatFormatting.GRAY)
                    .append(description));
        player.sendSystemMessage(line);
    }
}
