package com.newbulaco.showdown.command;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.newbulaco.showdown.CobblemonShowdown;
import com.newbulaco.showdown.format.Format;
import com.newbulaco.showdown.format.FormatManager;
import com.newbulaco.showdown.format.FormatValidator;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class FormatCommand {

    // TODO: integrate with Forge permission API
    private static final int OP_EXPORT = 2;

    private static final SuggestionProvider<CommandSourceStack> FORMAT_SUGGESTIONS = (context, builder) -> {
        FormatManager formatManager = CobblemonShowdown.getFormatManager();
        if (formatManager != null) {
            return SharedSuggestionProvider.suggest(
                    formatManager.getAllFormats().keySet(),
                    builder
            );
        }
        return builder.buildFuture();
    };

    public static void register(LiteralArgumentBuilder<CommandSourceStack> parent) {
        parent.then(Commands.literal("format")
                .then(Commands.argument("formatId", StringArgumentType.word())
                        .suggests(FORMAT_SUGGESTIONS)
                        .then(Commands.literal("export")
                                .requires(source -> source.hasPermission(OP_EXPORT))
                                .executes(FormatCommand::exportFormat))
                        .executes(FormatCommand::showFormatInfo))
                .then(Commands.literal("list")
                        .executes(FormatCommand::listFormats)))
                .then(Commands.literal("format_party_test")
                        .then(Commands.argument("formatId", StringArgumentType.word())
                                .suggests(FORMAT_SUGGESTIONS)
                                .executes(FormatCommand::testParty)));
    }

    private static int showFormatInfo(CommandContext<CommandSourceStack> context) {
        String formatId = StringArgumentType.getString(context, "formatId");
        FormatManager formatManager = CobblemonShowdown.getFormatManager();

        if (formatManager == null) {
            sendError(context.getSource(), Component.translatable("command.cobblemon_showdown.format.no_manager"));
            return 0;
        }

        Format format = formatManager.getFormat(formatId);
        if (format == null) {
            sendError(context.getSource(), Component.translatable("command.cobblemon_showdown.format.unknown_format", formatId));
            return 0;
        }

        FormatValidator validator = new FormatValidator();
        String summary = validator.getFormatSummary(format);

        sendSuccess(context.getSource(), Component.literal(summary));
        return 1;
    }

    private static int listFormats(CommandContext<CommandSourceStack> context) {
        FormatManager formatManager = CobblemonShowdown.getFormatManager();

        if (formatManager == null) {
            sendError(context.getSource(), Component.translatable("command.cobblemon_showdown.format.no_manager"));
            return 0;
        }

        var formats = formatManager.getAllFormats();
        if (formats.isEmpty()) {
            sendWarning(context.getSource(), Component.translatable("command.cobblemon_showdown.format.no_format"));
            return 0;
        }

        MutableComponent message = Component.translatable("command.cobblemon_showdown.format.available_formats", formats.size())
                .withStyle(ChatFormatting.AQUA);

        for (var entry : formats.entrySet()) {
            String id = entry.getKey();
            Format format = entry.getValue();

            MutableComponent formatLine = Component.literal("  ")
                    .append(Component.literal(id).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" - " + format.getName()).withStyle(ChatFormatting.GRAY));

            formatLine.withStyle(style -> style
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.translatable("command.cobblemon_showdown.format.detail")))
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                            "/showdown format " + id)));

            message.append("\n").append(formatLine);
        }

        context.getSource().sendSuccess(() -> message, false);
        return formats.size();
    }

    private static int exportFormat(CommandContext<CommandSourceStack> context) {
        String formatId = StringArgumentType.getString(context, "formatId");
        FormatManager formatManager = CobblemonShowdown.getFormatManager();

        if (formatManager == null) {
            sendError(context.getSource(), Component.translatable("command.cobblemon_showdown.format.no_manager"));
            return 0;
        }

        String json = formatManager.exportFormat(formatId);
        if (json == null) {
            sendError(context.getSource(), Component.translatable("command.cobblemon_showdown.format.unknown_format", formatId));
            return 0;
        }

        MutableComponent message = Component.translatable("command.cobblemon_showdown.format.export.success", formatId)
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);

        MutableComponent jsonComponent = Component.literal(json)
                .withStyle(ChatFormatting.GRAY)
                .withStyle(style -> style
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("command.cobblemon_showdown.format.export.click")))
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, json)));

        context.getSource().sendSuccess(() -> message, false);
        context.getSource().sendSuccess(() -> jsonComponent, false);

        sendInfo(context.getSource(), Component.translatable("command.cobblemon_showdown.format.export.clipboard"));
        return 1;
    }

    private static int testParty(CommandContext<CommandSourceStack> context) {
        String formatId = StringArgumentType.getString(context, "formatId");
        FormatManager formatManager = CobblemonShowdown.getFormatManager();

        if (formatManager == null) {
            sendError(context.getSource(), Component.translatable("command.cobblemon_showdown.format.no_manager"));
            return 0;
        }

        Format format = formatManager.getFormat(formatId);
        if (format == null) {
            sendError(context.getSource(), Component.translatable("command.cobblemon_showdown.format.unknown_format", formatId));
            return 0;
        }

        try {
            ServerPlayer player = context.getSource().getPlayerOrException();

            PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
            if (party == null) {
                sendError(context.getSource(), Component.translatable("command.cobblemon_showdown.format.test.no_party"));
                return 0;
            }

            FormatValidator validator = new FormatValidator();
            List<String> errors = validator.validateParty(party, format);

            sendInfo(context.getSource(), Component.translatable("command.cobblemon_showdown.format.test.run", format.getName()));

            if (errors.isEmpty()) {
                sendSuccess(context.getSource(), Component.translatable("command.cobblemon_showdown.format.test.success"));
            } else {
                sendError(context.getSource(), Component.translatable("command.cobblemon_showdown.format.test.failed", errors.size()));
                for (String error : errors) {
                    sendWarning(context.getSource(), Component.literal("  - " + error));
                }
            }

            return errors.isEmpty() ? 1 : 0;
        } catch (Exception e) {
            sendError(context.getSource(), Component.translatable("command.cobblemon_showdown.format.test.error", e.getMessage()));
            return 0;
        }
    }

    private static void sendSuccess(CommandSourceStack source, MutableComponent message) {
        source.sendSuccess(() -> message.withStyle(ChatFormatting.GREEN), false);
    }

    private static void sendError(CommandSourceStack source, MutableComponent message) {
        source.sendFailure(message.withStyle(ChatFormatting.RED));
    }

    private static void sendWarning(CommandSourceStack source, MutableComponent message) {
        source.sendSuccess(() -> message.withStyle(ChatFormatting.YELLOW), false);
    }

    private static void sendInfo(CommandSourceStack source, MutableComponent message) {
        source.sendSuccess(() -> message.withStyle(ChatFormatting.GRAY), false);
    }
}
