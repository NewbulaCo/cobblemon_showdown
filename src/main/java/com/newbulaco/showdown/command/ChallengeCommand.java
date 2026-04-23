package com.newbulaco.showdown.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.newbulaco.showdown.CobblemonShowdown;
import com.newbulaco.showdown.config.ShowdownConfig;
import com.newbulaco.showdown.challenge.Challenge;
import com.newbulaco.showdown.challenge.ChallengeManager;
import com.newbulaco.showdown.data.HistoryStorage;
import com.newbulaco.showdown.data.PlayerHistory;
import com.newbulaco.showdown.battle.BattleManager;
import com.newbulaco.showdown.data.PrizeHandler;
import com.newbulaco.showdown.format.Format;
import com.newbulaco.showdown.format.FormatManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class ChallengeCommand {

    public static void register(LiteralArgumentBuilder<CommandSourceStack> parent) {
        parent.then(Commands.literal("challenge")
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ChallengeCommand::showFormats)
                        .then(Commands.argument("format", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    FormatManager fm = CobblemonShowdown.getFormatManager();
                                    if (fm != null) {
                                        for (String formatId : fm.getFormatIds()) {
                                            if (formatId.toLowerCase().startsWith(builder.getRemainingLowerCase())) {
                                                builder.suggest(formatId);
                                            }
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ChallengeCommand::challengePlayer)
                                .then(Commands.argument("item_bet", StringArgumentType.greedyString())
                                        .executes(ChallengeCommand::challengePlayerWithBet)))));

        parent.then(Commands.literal("accept")
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ChallengeCommand::acceptChallenge)));

        parent.then(Commands.literal("deny")
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ChallengeCommand::denyChallenge)));

        parent.then(Commands.literal("history")
                .executes(ChallengeCommand::showOwnHistory)
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ChallengeCommand::showPlayerHistory)));
    }

    private static int showFormats(CommandContext<CommandSourceStack> context) {
        FormatManager formatManager = CobblemonShowdown.getFormatManager();
        if (formatManager == null) {
            sendError(context.getSource(), Component.translatable("command.cobblemon_showdown.show_format.no_manager"));
            return 0;
        }

        Collection<String> formatIds = formatManager.getFormatIds();
        if (formatIds.isEmpty()) {
            sendError(context.getSource(), Component.translatable("command.cobblemon_showdown.show_format.no_formats"));
            return 0;
        }

        sendInfo(context.getSource(), Component.translatable("command.cobblemon_showdown.show_format.format_list"));
        for (String formatId : formatIds) {
            Format format = formatManager.getFormat(formatId);
            if (format != null) {
                MutableComponent formatLine = Component.literal("  ")
                        .append(Component.literal(formatId)
                                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                                .withStyle(style -> style
                                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                                format.getDescription() != null
                                                    ? Component.literal(format.getDescription())
                                                    : Component.translatable("command.cobblemon_showdown.show_format.no_description")))
                                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                                                "/showdown challenge @p " + formatId))))
                        .append(Component.literal(" - " + format.getName())
                                .withStyle(ChatFormatting.GRAY));
                context.getSource().sendSuccess(() -> formatLine, false);
            }
        }
        sendInfo(context.getSource(), Component.translatable("command.cobblemon_showdown.show_format.use"));
        return 1;
    }

    private static int challengePlayer(CommandContext<CommandSourceStack> context) {
        return challengePlayerInternal(context, null);
    }

    private static int challengePlayerWithBet(CommandContext<CommandSourceStack> context) {
        String betString = StringArgumentType.getString(context, "item_bet");
        Challenge.ItemBet itemBet = parseItemBet(betString);

        if (itemBet == null) {
            sendError(context.getSource(), Component.translatable("command.cobblemon_showdown.challenge.item_bet.invalid"));
            sendInfo(context.getSource(), Component.translatable("command.cobblemon_showdown.challenge.item_bet.example"));
            return 0;
        }

        return challengePlayerInternal(context, itemBet);
    }

    private static int challengePlayerInternal(CommandContext<CommandSourceStack> context, Challenge.ItemBet itemBet) {
        try {
            ServerPlayer challenger = context.getSource().getPlayerOrException();
            ServerPlayer challenged = EntityArgument.getPlayer(context, "player");
            String formatId = StringArgumentType.getString(context, "format");

            if (challenger.getUUID().equals(challenged.getUUID())) {
                sendError(context.getSource(), Component.translatable("command.cobblemon_showdown.challenge.player.cannot_self"));
                return 0;
            }

            if (ShowdownConfig.isChallengeRadiusEnabled()) {
                double distance = challenger.distanceTo(challenged);
                double maxDistance = ShowdownConfig.getChallengeRadius();
                if (distance > maxDistance) {
                    sendError(context.getSource(), Component.translatable(
                        "command.cobblemon_showdown.challenge.player.out_of_range",
                        (int) maxDistance,
                        challenged.getName()));
                    sendInfo(context.getSource(), Component.translatable(
                        "command.cobblemon_showdown.challenge.player.out_of_range.msg",
                        String.format("%.1f", distance)));
                    return 0;
                }
            }

            FormatManager formatManager = CobblemonShowdown.getFormatManager();
            if (formatManager == null || !formatManager.hasFormat(formatId)) {
                sendError(context.getSource(), Component.translatable(
                    "command.cobblemon_showdown.challenge.player.invalid_format",
                    formatId));
                Collection<String> formatIds = formatManager != null ? formatManager.getFormatIds() : Collections.emptyList();
                if (!formatIds.isEmpty()) {
                    sendInfo(context.getSource(), Component.translatable(
                        "command.cobblemon_showdown.challenge.player.format_list",
                        String.join(", ", formatIds)));
                }
                return 0;
            }

            Format format = formatManager.getFormat(formatId);
            ChallengeManager challengeManager = CobblemonShowdown.getChallengeManager();

            if (challengeManager.hasChallenge(challenged.getUUID())) {
                sendError(context.getSource(), Component.translatable(
                    "command.cobblemon_showdown.challenge.player.pending_player",
                    challenged.getName()));
                return 0;
            }

            boolean created = challengeManager.createChallenge(
                    challenger.getUUID(),
                    challenged.getUUID(),
                    formatId,
                    itemBet
            );

            if (!created) {
                sendError(context.getSource(), Component.translatable("command.cobblemon_showdown.challenge.player.failed"));
                return 0;
            }

            sendSuccess(context.getSource(), Component.translatable(
                "command.cobblemon_showdown.challenge.player.sent",
                challenged.getName()));
            if (itemBet != null) {
                sendInfo(context.getSource(), Component.translatable(
                    "command.cobblemon_showdown.challenge.player.bet",
                    itemBet));
            }

            MutableComponent bettedItem = Component.empty();
            if (itemBet != null) {
                bettedItem.append(Component.empty().withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("\n  "))
                        .append(Component.translatable("command.cobblemon_showdown.challenge.player.bet",
                            Component.literal(String.valueOf(itemBet)).withStyle(ChatFormatting.GOLD))));
            }

            MutableComponent acceptButton = Component.translatable("command.cobblemon_showdown.challenge.player.receive.accept")
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                    .withStyle(style -> style
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.translatable("command.cobblemon_showdown.challenge.player.receive.accept.msg")))
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                    "/showdown accept " + challenger.getName().getString())));

            MutableComponent denyButton = Component.translatable("command.cobblemon_showdown.challenge.player.receive.deny")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                    .withStyle(style -> style
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.translatable("command.cobblemon_showdown.challenge.player.receive.deny.msg")))
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                    "/showdown deny " + challenger.getName().getString())));

            Component message = Component.translatable(
                "command.cobblemon_showdown.challenge.player.receive",
                challenger.getName().copy().withStyle(ChatFormatting.YELLOW),
                Component.literal(format.getName()).withStyle(ChatFormatting.AQUA),
                bettedItem,
                acceptButton,
                denyButton
                ).withStyle(ChatFormatting.GOLD);

            challenged.sendSystemMessage(message);

            return 1;

        } catch (Exception e) {
            sendError(context.getSource(), Component.translatable("command.cobblemon_showdown.challenge.player.error", e.getMessage()));
            return 0;
        }
    }

    private static int acceptChallenge(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer accepter = context.getSource().getPlayerOrException();
            ServerPlayer challenger = EntityArgument.getPlayer(context, "player");

            ChallengeManager challengeManager = CobblemonShowdown.getChallengeManager();
            Challenge challenge = challengeManager.getChallengeFrom(challenger.getUUID(), accepter.getUUID());

            if (challenge == null) {
                sendError(context.getSource(), Component.translatable(
                    "command.cobblemon_showdown.challenge.common.no_pending",
                    challenger.getName()));
                return 0;
            }

            if (challenge.isExpired()) {
                challengeManager.removeChallenge(accepter.getUUID());
                sendError(context.getSource(), Component.translatable("command.cobblemon_showdown.challenge.accept.expired"));
                return 0;
            }

            FormatManager formatManager = CobblemonShowdown.getFormatManager();
            Format format = formatManager.getFormat(challenge.getFormatId());

            if (format == null) {
                sendError(context.getSource(), Component.translatable(
                    "command.cobblemon_showdown.challenge.accept.invalid_format",
                    challenge.getFormatId()));
                challengeManager.removeChallenge(accepter.getUUID());
                return 0;
            }

            PrizeHandler.ItemBet prizeBet = null;
            if (challenge.getItemBet() != null) {
                prizeBet = new PrizeHandler.ItemBet(
                        challenge.getItemBet().getItemId(),
                        challenge.getItemBet().getAmount()
                );
            }

            BattleManager battleManager = CobblemonShowdown.getBattleManager();
            if (battleManager == null) {
                sendError(context.getSource(), Component.translatable("command.cobblemon_showdown.challenge.no_manager"));
                return 0;
            }

            challengeManager.acceptChallenge(accepter.getUUID());

            boolean started = battleManager.startChallengeBattle(challenger, accepter, format, prizeBet);

            if (started) {
                sendSuccess(context.getSource(), Component.translatable("command.cobblemon_showdown.challenge.accept.starting"));
                MutableComponent challengerMsg = Component.translatable(
                    "command.cobblemon_showdown.challenge.accept.starting.msg",
                        accepter.getName().copy().withStyle(ChatFormatting.YELLOW)
                ).withStyle(ChatFormatting.GREEN);
                challenger.sendSystemMessage(challengerMsg);
            } else {
                sendError(context.getSource(), Component.translatable("command.cobblemon_showdown.challenge.accept.failed"));
            }

            return started ? 1 : 0;

        } catch (Exception e) {
            sendError(context.getSource(), Component.translatable("command.cobblemon_showdown.challenge.accept.error", e.getMessage()));
            return 0;
        }
    }

    private static int denyChallenge(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer denier = context.getSource().getPlayerOrException();
            ServerPlayer challenger = EntityArgument.getPlayer(context, "player");

            ChallengeManager challengeManager = CobblemonShowdown.getChallengeManager();
            Challenge challenge = challengeManager.getChallengeFrom(challenger.getUUID(), denier.getUUID());

            if (challenge == null) {
                sendError(context.getSource(), Component.translatable(
                    "command.cobblemon_showdown.challenge.common.no_pending",
                    challenger.getName()));
                return 0;
            }

            challengeManager.removeChallenge(denier.getUUID());
            sendSuccess(context.getSource(), Component.translatable("command.cobblemon_showdown.challenge.deny.denied"));

            MutableComponent challengerMsg = Component.translatable(
                "command.cobblemon_showdown.challenge.deny.denied.msg",
                denier.getName().copy().withStyle(ChatFormatting.YELLOW)
            ).withStyle(ChatFormatting.RED);
            challenger.sendSystemMessage(challengerMsg);

            return 1;

        } catch (Exception e) {
            sendError(context.getSource(), Component.translatable("command.cobblemon_showdown.challenge.deny.error", e.getMessage()));
            return 0;
        }
    }

    private static int showOwnHistory(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            return showHistoryForPlayer(context.getSource(), player);
        } catch (Exception e) {
            sendError(context.getSource(), Component.translatable("command.cobblemon_showdown.challenge.history.error", e.getMessage()));
            return 0;
        }
    }

    private static int showPlayerHistory(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(context, "player");
            return showHistoryForPlayer(context.getSource(), target);
        } catch (Exception e) {
            sendError(context.getSource(), Component.translatable("command.cobblemon_showdown.challenge.history.error", e.getMessage()));
            return 0;
        }
    }

    private static int showHistoryForPlayer(CommandSourceStack source, ServerPlayer player) {
        HistoryStorage storage = CobblemonShowdown.getHistoryStorage();
        if (storage == null) {
            sendError(source, Component.translatable("command.cobblemon_showdown.challenge.history.no_storage"));
            return 0;
        }

        PlayerHistory history = storage.getHistory(player.getUUID());

        MutableComponent winRate = Component.empty().withStyle(ChatFormatting.GRAY);
        if (history.getTotalMatches() > 0) {
            winRate.append(Component.translatable(
                "command.cobblemon_showdown.challenge.history.win_rate",
                Component.literal(String.format("%.1f%%", history.getWinRate())).withStyle(ChatFormatting.YELLOW)
            ));
        }

        MutableComponent recentMatches = Component.empty().withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
        List<PlayerHistory.MatchRecord> matches = history.getMatches();
        if (!matches.isEmpty()) {
            recentMatches.append(Component.translatable("command.cobblemon_showdown.challenge.history.recent"));

            int count = Math.min(5, matches.size());
            for (int i = matches.size() - 1; i >= matches.size() - count; i--) {
                PlayerHistory.MatchRecord match = matches.get(i);

                ChatFormatting resultColor = match.isWin() ? ChatFormatting.GREEN : ChatFormatting.RED;

                recentMatches.append(
                    Component.literal("\n  ")
                        .append(Component.translatable(match.isWin()
                                ? "command.cobblemon_showdown.challenge.history.win"
                                : "command.cobblemon_showdown.challenge.history.lost",
                            Component.literal(match.getFormatId()).withStyle(ChatFormatting.GRAY)
                        ).withStyle(resultColor, ChatFormatting.BOLD)));

            }
        }

        MutableComponent message = Component.translatable(
            "command.cobblemon_showdown.challenge.history",
            player.getName().copy().withStyle(ChatFormatting.YELLOW),
            Component.literal(String.valueOf(history.getStats().getWins())).withStyle(ChatFormatting.GREEN),
            Component.literal(String.valueOf(history.getStats().getLosses())).withStyle(ChatFormatting.RED),
            Component.literal(String.valueOf(history.getTotalMatches())).withStyle(ChatFormatting.WHITE),
            winRate,
            recentMatches
        ).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);

        source.sendSuccess(() -> message, false);
        return 1;
    }

    private static Challenge.ItemBet parseItemBet(String betString) {
        if (!betString.startsWith("item_bet:")) return null;

        String[] parts = betString.substring(9).split(",");
        if (parts.length != 2) return null;

        try {
            String itemId = parts[0].trim();
            int amount = Integer.parseInt(parts[1].trim());
            if (amount <= 0 || amount > 64) return null;
            return new Challenge.ItemBet(itemId, amount);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void sendSuccess(CommandSourceStack source, MutableComponent message) {
        source.sendSuccess(() -> message.withStyle(ChatFormatting.GREEN), false);
    }

    private static void sendError(CommandSourceStack source, MutableComponent message) {
        source.sendFailure(message.withStyle(ChatFormatting.RED));
    }

    private static void sendInfo(CommandSourceStack source, MutableComponent message) {
        source.sendSuccess(() -> message.withStyle(ChatFormatting.GRAY), false);
    }
}
