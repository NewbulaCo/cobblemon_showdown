package com.newbulaco.showdown.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ComponentUtil {
    private static final StringSplitter STRING_SPLITTER = Minecraft.getInstance().font.getSplitter();

    public static List<Component> wrapText(Component text, int maxWidth) {
        // Use Minecraft Vanilla text line splitter. Width is pixel size
        List<FormattedText> fts = STRING_SPLITTER.splitLines(text, maxWidth, Style.EMPTY);
        List<Component> wrapped = new ArrayList<>();
        for(FormattedText ft : fts) {
            MutableComponent tmp = Component.empty();
            ft.visit((style, string) -> {
                tmp.append(Component.literal(string).setStyle(style));
                return Optional.empty();
            }, Style.EMPTY);
            wrapped.add(tmp);
        }
        return wrapped;
    }
}
