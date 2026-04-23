package com.newbulaco.showdown.client.gui;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.battles.InBattleMove;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.ClientBattleActor;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattleSide;
import com.cobblemon.mod.common.client.battle.SingleActionRequest;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.newbulaco.showdown.CobblemonShowdown;
import com.newbulaco.showdown.api.ShowdownAPI;
import com.newbulaco.showdown.api.content.CustomFieldCondition;
import com.newbulaco.showdown.client.battle.BattleStatusTracker;
import com.newbulaco.showdown.client.battle.ClientPartyStatusManager;
import com.newbulaco.showdown.client.battle.ClientSideConditionManager;
import com.newbulaco.showdown.client.battle.ClientStatChangeManager;
import com.newbulaco.showdown.client.battle.ClientVolatileEffectManager;
import com.newbulaco.showdown.client.battle.PokemonBattleStatus;
import com.newbulaco.showdown.client.battle.TypeEffectiveness;
import com.newbulaco.showdown.network.packets.PartyStatusPacket;
import com.newbulaco.showdown.client.tooltip.PokemonTooltipRenderer;
import com.newbulaco.showdown.util.ComponentUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import com.newbulaco.showdown.client.ShowdownKeybinds;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * uses Forge events instead of mixins to avoid Kotlin class signature issues with Cobblemon.
 */
@Mod.EventBusSubscriber(modid = CobblemonShowdown.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class BattleOverlayRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(BattleOverlayRenderer.class);
    private static long lastLogTime = 0;

    // move tile layout constants (matching Cobblemon's BattleMoveSelection)
    private static final int MOVE_WIDTH = 92;
    private static final int MOVE_HEIGHT = 24;
    private static final float MOVE_VERTICAL_SPACING = 5F;
    private static final float MOVE_HORIZONTAL_SPACING = 13F;
    private static final int MOVE_BASE_X = 20;

    // ally pokemon tile constants (matching Cobblemon's BattleOverlay)
    private static final int ALLY_TILE_X = 12;
    private static final int ALLY_TILE_Y = 10;
    private static final int ALLY_TILE_WIDTH = 131;
    private static final int ALLY_TILE_HEIGHT = 40;

    private static Method getCurrentActionSelectionMethod = null;
    private static boolean reflectionFailed = false;

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        String screenName = event.getScreen().getClass().getName();
        if (!screenName.contains("BattleGUI")) {
            return;
        }

        try {
            ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
            if (battle == null) return;

            Minecraft mc = Minecraft.getInstance();
            GuiGraphics graphics = event.getGuiGraphics();

            renderMoveEffectiveness(graphics, battle, mc);
            renderFieldStatus(graphics, mc);

            int allyNextY = renderPartyStatus(graphics, battle, mc, true);
            int opponentNextY = renderPartyStatus(graphics, battle, mc, false);

            renderStatChanges(graphics, battle, mc, allyNextY, opponentNextY);
            renderMoveTooltip(graphics, battle, mc);
            renderAllyPokemonInfo(graphics, battle, mc);

        } catch (Exception e) {
            // silently ignore to prevent crashes
        }
    }

    /**
     * uses reflection to call getCurrentActionSelection() on BattleGUI
     * because Cobblemon's Kotlin classes can't be mixed into directly.
     */
    private static boolean isInMoveSelection(Screen screen) {
        if (reflectionFailed) return false;

        try {
            if (getCurrentActionSelectionMethod == null) {
                getCurrentActionSelectionMethod = screen.getClass().getMethod("getCurrentActionSelection");
            }

            Object actionSelection = getCurrentActionSelectionMethod.invoke(screen);
            if (actionSelection == null) return false;

            String className = actionSelection.getClass().getSimpleName();
            return className.contains("BattleMoveSelection");

        } catch (Exception e) {
            reflectionFailed = true;
            return false;
        }
    }

    private static void renderFieldStatus(GuiGraphics graphics, Minecraft mc) {
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int centerX = screenWidth / 2;
        int y = 10;

        var font = mc.font;

        String weather = BattleStatusTracker.getWeather();
        if (!weather.isEmpty()) {
            int turns = BattleStatusTracker.getWeatherTurns();
            MutableComponent text = formatFieldEffect(weather);
            if (turns > 0) text = Component.translatable("cobblemon_showdown.battle_overlay.long_effect", text.copy(), turns);;

            int textWidth = font.width(text);
            int x = centerX - textWidth / 2;

            graphics.fill(x - 2, y - 1, x + textWidth + 2, y + font.lineHeight + 1, 0xAA000000);
            graphics.drawString(font, text, x, y, getWeatherColor(weather), true);
            y += font.lineHeight + 4;
        }

        String terrain = BattleStatusTracker.getTerrain();
        if (!terrain.isEmpty()) {
            int turns = BattleStatusTracker.getTerrainTurns();
            MutableComponent text = formatFieldEffect(terrain);
            if (turns > 0) text = Component.translatable("cobblemon_showdown.battle_overlay.long_effect", text.copy(), turns);

            int textWidth = font.width(text);
            int x = centerX - textWidth / 2;

            graphics.fill(x - 2, y - 1, x + textWidth + 2, y + font.lineHeight + 1, 0xAA000000);
            graphics.drawString(font, text, x, y, getTerrainColor(terrain), true);
            y += font.lineHeight + 4;
        }

        for (var entry : BattleStatusTracker.getRooms().entrySet()) {
            MutableComponent text = Component.translatable(
                "cobblemon_showdown.battle_overlay.long_effect",
                formatFieldEffect(entry.getKey()),
                entry.getValue());

            int textWidth = font.width(text);
            int x = centerX - textWidth / 2;

            graphics.fill(x - 2, y - 1, x + textWidth + 2, y + font.lineHeight + 1, 0xAA000000);
            graphics.drawString(font, text, x, y, 0xFFFF88FF, true);
            y += font.lineHeight + 4;
        }
    }

    private static MutableComponent formatFieldEffect(String effect) {
        if (effect == null || effect.isEmpty()) return Component.empty();

        CustomFieldCondition custom = ShowdownAPI.getFieldCondition(effect);
        if (custom != null) {
            return Component.literal(custom.getDisplayName());
        }

        String lower = effect.toLowerCase();
        return switch (lower) {
            case "raindance", "rain" -> Component.translatable("cobblemon_showdown.battle_overlay.field.rain");
            case "sunnyday", "sun", "harshsunshine" -> Component.translatable("cobblemon_showdown.battle_overlay.field.sun");
            case "sandstorm", "sand" -> Component.translatable("cobblemon_showdown.battle_overlay.field.sandstorm");
            case "hail", "snow" -> Component.translatable("cobblemon_showdown.battle_overlay.field.hail");
            case "primordialsea" -> Component.translatable("cobblemon_showdown.battle_overlay.field.primordialsea");
            case "desolateland" -> Component.translatable("cobblemon_showdown.battle_overlay.field.desolateland");
            case "deltastream" -> Component.translatable("cobblemon_showdown.battle_overlay.field.deltastream");
            case "electricterrain" -> Component.translatable("cobblemon_showdown.battle_overlay.field.electricterrain");
            case "grassyterrain" -> Component.translatable("cobblemon_showdown.battle_overlay.field.grassyterrain");
            case "psychicterrain" -> Component.translatable("cobblemon_showdown.battle_overlay.field.psychicterrain");
            case "mistyterrain" -> Component.translatable("cobblemon_showdown.battle_overlay.field.mistyterrain");
            case "trickroom" -> Component.translatable("cobblemon_showdown.battle_overlay.field.trickroom");
            case "wonderroom" -> Component.translatable("cobblemon_showdown.battle_overlay.field.wonderroom");
            case "magicroom" -> Component.translatable("cobblemon_showdown.battle_overlay.field.magicroom");
            default -> {
                String[] words = effect.replace("_", " ").split(" ");
                StringBuilder result = new StringBuilder();
                for (String word : words) {
                    if (!word.isEmpty()) {
                        result.append(Character.toUpperCase(word.charAt(0)))
                              .append(word.substring(1).toLowerCase())
                              .append(" ");
                    }
                }
                yield Component.literal(result.toString().trim());
            }
        };
    }

    private static int getWeatherColor(String weather) {
        CustomFieldCondition custom = ShowdownAPI.getFieldCondition(weather);
        if (custom != null && custom.getType() == CustomFieldCondition.Type.WEATHER) {
            return custom.getColor();
        }

        String lower = weather.toLowerCase();
        if (lower.contains("sun") || lower.contains("harsh")) return 0xFFFFAA00;
        if (lower.contains("rain") || lower.contains("primordial")) return 0xFF6688FF;
        if (lower.contains("sand")) return 0xFFDDCC88;
        if (lower.contains("hail") || lower.contains("snow")) return 0xFFAADDFF;
        return 0xFFFFFFFF;
    }

    private static int getTerrainColor(String terrain) {
        CustomFieldCondition custom = ShowdownAPI.getFieldCondition(terrain);
        if (custom != null && custom.getType() == CustomFieldCondition.Type.TERRAIN) {
            return custom.getColor();
        }

        String lower = terrain.toLowerCase();
        if (lower.contains("electric")) return 0xFFFFFF00;
        if (lower.contains("grassy")) return 0xFF88FF88;
        if (lower.contains("psychic")) return 0xFFFF88FF;
        if (lower.contains("misty")) return 0xFFFFAAFF;
        return 0xFFFFFFFF;
    }

    /**
     * renders below the party status indicators.
     * order: volatile effects -> side conditions.
     */
    private static void renderStatChanges(GuiGraphics graphics, ClientBattle battle, Minecraft mc,
                                           int allyStartY, int opponentStartY) {
        int screenWidth = mc.getWindow().getGuiScaledWidth();

        try {
            var allyStats = ClientStatChangeManager.getInstance().getAllyStatStages();
            var allyEffects = ClientVolatileEffectManager.getInstance().getAllyEffects();
            var allyConditions = ClientSideConditionManager.getInstance().getAllyConditions();

            int allyX = ALLY_TILE_X;
            int allyY = allyStartY;
            int maxWidth = ALLY_TILE_WIDTH;

            int allyStatusHeight = renderBattleStatus(graphics, mc, allyStats, allyEffects, allyX, allyY, maxWidth, true);

            if (!allyConditions.isEmpty()) {
                int sideCondY = allyY + Math.max(allyStatusHeight, 12) + 2;
                renderSideConditionsForCombatant(graphics, mc, allyConditions, allyX, sideCondY, maxWidth);
            }

            var opponentStats = ClientStatChangeManager.getInstance().getOpponentStatStages();
            var opponentEffects = ClientVolatileEffectManager.getInstance().getOpponentEffects();
            var opponentConditions = ClientSideConditionManager.getInstance().getOpponentConditions();

            int opponentX = screenWidth - ALLY_TILE_X - ALLY_TILE_WIDTH;
            int opponentY = opponentStartY;

            int opponentStatusHeight = renderBattleStatus(graphics, mc, opponentStats, opponentEffects, opponentX, opponentY, maxWidth, false);

            if (!opponentConditions.isEmpty()) {
                int sideCondY = opponentY + Math.max(opponentStatusHeight, 12) + 2;
                renderSideConditionsForCombatant(graphics, mc, opponentConditions, opponentX, sideCondY, maxWidth);
            }

        } catch (Exception e) {
            // silently ignore
        }
    }

    private static final String[] STAT_ORDER = {"atk", "def", "spa", "spd", "spe", "accuracy", "evasion"};

    /**
     * renders status items horizontally with wrapping using slightly smaller text.
     * returns the total height used for rendering.
     */
    private static int renderBattleStatus(GuiGraphics graphics, Minecraft mc,
                                            Map<String, Integer> stats, java.util.Set<String> effects,
                                            int startX, int startY, int maxWidth, boolean isAlly) {
        List<StatusItem> items = new ArrayList<>();

        for (String statKey : STAT_ORDER) {
            Integer stages = stats.get(statKey);
            if (stages != null && stages != 0) {
                Component statName = ClientStatChangeManager.getStatDisplayName(statKey);
                String prefix = stages > 0 ? "+" : "";
                Component text = Component.translatable(
                    "cobblemon_showdown.battle_overlay.status",
                    Component.literal(prefix + stages),
                    statName);
                int color = stages > 0 ? 0xFF55FF55 : 0xFFFF5555;
                items.add(new StatusItem(text, color));
            }
        }

        List<String> sortedEffects = new ArrayList<>(effects);
        sortedEffects.sort(String::compareToIgnoreCase);
        for (String effectId : sortedEffects) {
            Component displayName = ClientVolatileEffectManager.getEffectDisplayName(effectId);
            items.add(new StatusItem(displayName, 0xFFFF88FF));
        }

        if (items.isEmpty()) return 0;

        var font = mc.font;
        float scale = 0.85f;
        int spacing = 4;
        int lineHeight = (int) (font.lineHeight * scale) + 3;

        List<List<StatusItem>> rows = new ArrayList<>();
        List<StatusItem> currentRow = new ArrayList<>();
        int currentRowWidth = 0;

        for (StatusItem item : items) {
            int itemWidth = (int) (font.width(item.text) * scale);
            if (currentRowWidth > 0 && currentRowWidth + spacing + itemWidth > maxWidth) {
                rows.add(currentRow);
                currentRow = new ArrayList<>();
                currentRowWidth = 0;
            }
            currentRow.add(item);
            currentRowWidth += (currentRowWidth > 0 ? spacing : 0) + itemWidth;
        }
        if (!currentRow.isEmpty()) {
            rows.add(currentRow);
        }

        graphics.pose().pushPose();
        graphics.pose().translate(startX, startY, 0);
        graphics.pose().scale(scale, scale, 1.0f);

        int y = 0;
        for (List<StatusItem> row : rows) {
            int x = 0;
            for (StatusItem item : row) {
                int textWidth = font.width(item.text);
                graphics.fill(x - 1, y - 1, x + textWidth + 1, y + font.lineHeight, 0x99000000);
                graphics.drawString(font, item.text, x, y, item.color, true);
                x += textWidth + (int)(spacing / scale);
            }
            y += lineHeight / scale;
        }

        graphics.pose().popPose();

        return (int)(rows.size() * lineHeight);
    }

    private static void renderSideConditionsForCombatant(GuiGraphics graphics, Minecraft mc,
                                                          Map<String, Integer> conditions,
                                                          int startX, int startY, int maxWidth) {
        if (conditions.isEmpty()) return;

        var font = mc.font;
        float scale = 0.8f;
        int spacing = 4;
        int lineHeight = (int) (font.lineHeight * scale) + 2;

        List<StatusItem> items = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : conditions.entrySet()) {
            String conditionId = entry.getKey();
            MutableComponent displayName = ClientSideConditionManager.getConditionDisplayName(conditionId);
            int color = ClientSideConditionManager.getConditionColor(conditionId);

            int turns = entry.getValue();
            if (turns > 0) {
                displayName = Component.translatable("cobblemon_showdown.battle_overlay.long_effect", displayName.copy(), turns);;
            }

            items.add(new StatusItem(displayName, color));
        }

        List<List<StatusItem>> rows = new ArrayList<>();
        List<StatusItem> currentRow = new ArrayList<>();
        int currentRowWidth = 0;

        for (StatusItem item : items) {
            int itemWidth = (int) (font.width(item.text) * scale);
            if (currentRowWidth > 0 && currentRowWidth + spacing + itemWidth > maxWidth) {
                rows.add(currentRow);
                currentRow = new ArrayList<>();
                currentRowWidth = 0;
            }
            currentRow.add(item);
            currentRowWidth += (currentRowWidth > 0 ? spacing : 0) + itemWidth;
        }
        if (!currentRow.isEmpty()) {
            rows.add(currentRow);
        }

        graphics.pose().pushPose();
        graphics.pose().translate(startX, startY, 0);
        graphics.pose().scale(scale, scale, 1.0f);

        int y = 0;
        for (List<StatusItem> row : rows) {
            int x = 0;
            for (StatusItem item : row) {
                int textWidth = font.width(item.text);
                graphics.fill(x - 1, y - 1, x + textWidth + 1, y + font.lineHeight, 0x99000000);
                graphics.drawString(font, item.text, x, y, item.color, true);
                x += textWidth + (int)(spacing / scale);
            }
            y += lineHeight / scale;
        }

        graphics.pose().popPose();
    }

    private static class StatusItem {
        final Component text;
        final int color;

        StatusItem(Component text, int color) {
            this.text = text;
            this.color = color;
        }
    }

    /**
     * returns the Y position where the next element should be rendered below party status.
     */
    private static int renderPartyStatus(GuiGraphics graphics, ClientBattle battle, Minecraft mc, boolean isAlly) {
        int screenWidth = mc.getWindow().getGuiScaledWidth();

        // 75% of original (8→6, 3→2)
        int indicatorSize = 6;
        int indicatorSpacing = 2;

        try {
            var partyStatus = isAlly
                ? ClientPartyStatusManager.getInstance().getAllyPartyStatus()
                : ClientPartyStatusManager.getInstance().getOpponentPartyStatus();

            int slotCount = Math.max(partyStatus.size(), 1);
            int totalWidth = (indicatorSize + indicatorSpacing) * slotCount - indicatorSpacing;

            // centered on the tile, at the top edge
            // opponent tile has right-side padding; shift left to align with HP bar content
            int tileX = isAlly ? ALLY_TILE_X : (screenWidth - ALLY_TILE_X - ALLY_TILE_WIDTH);
            int x = tileX + (ALLY_TILE_WIDTH - totalWidth) / 2;
            if (!isAlly) x -= 32;
            int y = ALLY_TILE_Y;

            if (!partyStatus.isEmpty()) {
                renderPartyIndicators(graphics, partyStatus, x, y, indicatorSize, indicatorSpacing, isAlly);
            }

        } catch (Exception e) {
            // silently ignore
        }

        // volatile effects start right below the tile (no gap for indicators)
        return ALLY_TILE_Y + ALLY_TILE_HEIGHT + 2;
    }

    private static void renderPartyIndicators(GuiGraphics graphics,
                                               java.util.List<PartyStatusPacket.SlotInfo> slots,
                                               int x, int y, int size, int spacing, boolean isAlly) {
        int totalWidth = (size + spacing) * slots.size() - spacing;
        graphics.fill(x - 1, y - 1, x + totalWidth + 1, y + size + 1, 0x99000000);

        for (int i = 0; i < slots.size(); i++) {
            PartyStatusPacket.SlotInfo slot = slots.get(i);
            // opponent renders right-to-left to mirror ally side
            int idx = isAlly ? i : (slots.size() - 1 - i);
            int indicatorX = x + idx * (size + spacing);

            int color = getPartyIndicatorColor(slot);
            int borderColor = getPartyIndicatorBorderColor(slot);

            graphics.fill(indicatorX - 1, y - 1, indicatorX + size + 1, y + size + 1, borderColor);
            graphics.fill(indicatorX, y, indicatorX + size, y + size, color);

            if (slot.getStatus() == PartyStatusPacket.SlotStatus.HEALTHY ||
                slot.getStatus() == PartyStatusPacket.SlotStatus.DAMAGED) {
                graphics.fill(indicatorX, y, indicatorX + 1, y + 1, 0x44FFFFFF);
            }
        }
    }

    private static int getPartyIndicatorColor(PartyStatusPacket.SlotInfo slot) {
        return switch (slot.getStatus()) {
            case HEALTHY -> 0xFF44DD44;
            case DAMAGED -> {
                int health = slot.getHealthPercent();
                if (health > 25) {
                    yield 0xFFDDDD44;
                } else {
                    yield 0xFFDD8844;
                }
            }
            case FAINTED -> 0xFF444444;
            case EMPTY -> 0x00000000;
        };
    }

    private static int getPartyIndicatorBorderColor(PartyStatusPacket.SlotInfo slot) {
        return switch (slot.getStatus()) {
            case HEALTHY -> 0xFF228822;
            case DAMAGED -> 0xFF888822;
            case FAINTED -> 0xFF222222;
            case EMPTY -> 0x00000000;
        };
    }

    private static void renderMoveEffectiveness(GuiGraphics graphics, ClientBattle battle, Minecraft mc) {
        try {
            if (mc.screen == null || !isInMoveSelection(mc.screen)) return;

            var pendingRequests = battle.getPendingActionRequests();
            if (pendingRequests == null || pendingRequests.isEmpty()) return;

            SingleActionRequest request = pendingRequests.get(0);

            var moveSet = request.getMoveSet();
            if (moveSet == null) return;

            List<InBattleMove> moves = moveSet.getMoves();
            if (moves == null || moves.isEmpty()) return;

            var ourPokemon = request.getActivePokemon();
            if (ourPokemon == null) return;

            var ourActor = ourPokemon.getActor();
            if (ourActor == null) return;

            ClientBattleSide ourSide = ourActor.getSide();
            if (ourSide == null) return;

            ClientBattleSide opponentSide = (ourSide == battle.getSide1()) ? battle.getSide2() : battle.getSide1();

            ActiveClientBattlePokemon opponentActivePokemon = null;
            for (ActiveClientBattlePokemon active : opponentSide.getActiveClientBattlePokemon()) {
                opponentActivePokemon = active;
                break;
            }
            if (opponentActivePokemon == null) return;

            ClientBattlePokemon opponentBattlePokemon = opponentActivePokemon.getBattlePokemon();
            if (opponentBattlePokemon == null) return;

            // use getTypes() to get all types reliably for dual-type pokemon
            Species opponentSpecies = opponentBattlePokemon.getSpecies();
            var formData = opponentSpecies.getStandardForm();
            var types = formData.getTypes();

            String primaryType = null;
            String secondaryType = null;
            int typeIndex = 0;
            for (ElementalType type : types) {
                if (typeIndex == 0) {
                    primaryType = type.getName();
                } else if (typeIndex == 1) {
                    secondaryType = type.getName();
                    break;
                }
                typeIndex++;
            }

            if (primaryType == null) return;

            int baseY = mc.getWindow().getGuiScaledHeight() - 84;

            for (int i = 0; i < moves.size(); i++) {
                InBattleMove move = moves.get(i);
                MoveTemplate moveTemplate = Moves.INSTANCE.getByNameOrDummy(move.getId());

                String moveType = moveTemplate.getElementalType().getName();
                double effectiveness = TypeEffectiveness.getEffectiveness(moveType, primaryType, secondaryType);

                if (effectiveness == 1.0) continue;

                // status moves don't deal type-based damage, but immunities still apply
                // (e.g., Thunder Wave doesn't affect Ground types)
                boolean isStatusMove = moveTemplate.getDamageCategory() == DamageCategories.INSTANCE.getSTATUS();
                if (isStatusMove && effectiveness > 0) continue;

                boolean isEven = i % 2 == 0;
                float tileX = isEven ? MOVE_BASE_X : MOVE_BASE_X + MOVE_HORIZONTAL_SPACING + MOVE_WIDTH;
                float tileY = (i > 1) ? baseY + MOVE_HEIGHT + MOVE_VERTICAL_SPACING : baseY;

                drawEffectivenessIndicator(graphics, mc, tileX, tileY, effectiveness);
            }

        } catch (Exception e) {
            // silently ignore
        }
    }

    /**
     * renders at lower z-level so move tooltip (Shift) appears on top.
     * scaled to 85% size and positioned to the right of move name.
     */
    private static void drawEffectivenessIndicator(GuiGraphics graphics, Minecraft mc, float tileX, float tileY, double effectiveness) {
        TypeEffectiveness.EffectivenessCategory category = TypeEffectiveness.EffectivenessCategory.fromMultiplier(effectiveness);

        if (category == TypeEffectiveness.EffectivenessCategory.NEUTRAL) return;

        Component text = category.translatedText;
        if (text.getString().isEmpty()) return;

        var font = mc.font;

        float x = tileX + 18;
        float y = tileY + 14;

        // z=100 so move tooltip at z=500 renders on top
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 100);

        float scale = 0.85f;
        graphics.pose().scale(scale, scale, 1.0f);

        graphics.drawString(font, text, 0, 0, category.color, true);

        graphics.pose().popPose();
    }

    private static void renderMoveTooltip(GuiGraphics graphics, ClientBattle battle, Minecraft mc) {
        try {
            if (!ShowdownKeybinds.isShowInfoDown()) return;
            if (mc.screen == null || !isInMoveSelection(mc.screen)) return;

            var pendingRequests = battle.getPendingActionRequests();
            if (pendingRequests == null || pendingRequests.isEmpty()) return;

            SingleActionRequest request = pendingRequests.get(0);
            var moveSet = request.getMoveSet();
            if (moveSet == null) return;

            List<InBattleMove> moves = moveSet.getMoves();
            if (moves == null || moves.isEmpty()) return;

            double mouseX = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getWidth();
            double mouseY = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getHeight();

            int baseY = mc.getWindow().getGuiScaledHeight() - 84;

            for (int i = 0; i < moves.size(); i++) {
                boolean isEven = i % 2 == 0;
                float tileX = isEven ? MOVE_BASE_X : MOVE_BASE_X + MOVE_HORIZONTAL_SPACING + MOVE_WIDTH;
                float tileY = (i > 1) ? baseY + MOVE_HEIGHT + MOVE_VERTICAL_SPACING : baseY;

                if (mouseX >= tileX && mouseX <= tileX + MOVE_WIDTH &&
                    mouseY >= tileY && mouseY <= tileY + MOVE_HEIGHT) {

                    InBattleMove move = moves.get(i);
                    MoveTemplate moveTemplate = Moves.INSTANCE.getByNameOrDummy(move.getId());

                    List<Component> lines = new ArrayList<>();

                    Component moveName = moveTemplate.getDisplayName().copy()
                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
                    lines.add(moveName);

                    ElementalType type = moveTemplate.getElementalType();
                    Component typeName = type.getDisplayName().copy().setStyle(Style.EMPTY.withColor(type.getHue()));
                    lines.add(Component.translatable("tooltip.cobblemon_showdown.move_info.type", typeName)
                        .withStyle(ChatFormatting.GRAY));

                    MutableComponent category = moveTemplate.getDamageCategory().getDisplayName().copy();
                    if (moveTemplate.getDamageCategory() == DamageCategories.INSTANCE.getPHYSICAL()) {
                        category.setStyle(Style.EMPTY.withColor(ChatFormatting.RED));
                    } else if (moveTemplate.getDamageCategory() == DamageCategories.INSTANCE.getSPECIAL()) {
                        category.setStyle(Style.EMPTY.withColor(ChatFormatting.BLUE));
                    } else {
                        category.setStyle(Style.EMPTY.withColor(ChatFormatting.WHITE));
                    }
                    lines.add(Component.translatable("tooltip.cobblemon_showdown.move_info.category", category)
                        .withStyle(ChatFormatting.GRAY));

                    double power = moveTemplate.getPower();
                    double accuracy = moveTemplate.getAccuracy();
                    int maxPP = moveTemplate.getPp();

                    if (power > 0) {
                        lines.add(Component.translatable(
                            "tooltip.cobblemon_showdown.move_info.power",
                                Component.literal((int) power + "").withStyle(ChatFormatting.GREEN))
                            .withStyle(ChatFormatting.GRAY));
                    }
                    if (accuracy > 0) {
                        lines.add(Component.translatable(
                            "tooltip.cobblemon_showdown.move_info.accuracy",
                                Component.literal( (int) accuracy + "%").withStyle(ChatFormatting.AQUA))
                            .withStyle(ChatFormatting.GRAY));
                    }

                    int currentPP = move.getPp();
                    ChatFormatting ppColor = currentPP == 0 ? ChatFormatting.RED :
                                            currentPP <= maxPP / 4 ? ChatFormatting.GOLD :
                                            currentPP <= maxPP / 2 ? ChatFormatting.YELLOW :
                                            ChatFormatting.GREEN;
                    lines.add(Component.translatable(
                        "tooltip.cobblemon_showdown.move_info.pp",
                            Component.literal(currentPP + "/" + maxPP).withStyle(ppColor))
                        .withStyle(ChatFormatting.GRAY));

                    int priority = (int) moveTemplate.getPriority();
                    if (priority != 0) {
                        ChatFormatting priorityColor = priority > 0 ? ChatFormatting.GREEN : ChatFormatting.RED;
                        lines.add(Component.translatable(
                            "tooltip.cobblemon_showdown.move_info.priority",
                                Component.literal((priority > 0 ? "+" : "") + priority).withStyle(priorityColor))
                            .withStyle(ChatFormatting.GRAY));
                    }

                    Component desc = moveTemplate.getDescription().copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
                    if (desc != null && !desc.getString().isEmpty()) {
                        lines.add(Component.literal(""));
                        lines.addAll(ComponentUtil.wrapText(desc, 140));
                    }

                    renderTooltip(graphics, mc, (int) mouseX + 10, (int) mouseY - 10, lines);
                    break;
                }
            }
        } catch (Exception e) {
            // silently ignore
        }
    }

    private static String formatMoveName(String name) {
        if (name == null || name.isEmpty()) return name;
        String[] words = name.replace("_", " ").split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                      .append(word.substring(1).toLowerCase())
                      .append(" ");
            }
        }
        return result.toString().trim();
    }

    private static ChatFormatting getTypeColor(String type) {
        if (type == null) return ChatFormatting.WHITE;
        String lower = type.toLowerCase();
        return switch (lower) {
            case "fire" -> ChatFormatting.RED;
            case "water" -> ChatFormatting.BLUE;
            case "grass" -> ChatFormatting.GREEN;
            case "electric" -> ChatFormatting.YELLOW;
            case "ice" -> ChatFormatting.AQUA;
            case "fighting" -> ChatFormatting.DARK_RED;
            case "poison" -> ChatFormatting.DARK_PURPLE;
            case "ground" -> ChatFormatting.GOLD;
            case "flying" -> ChatFormatting.AQUA;
            case "psychic" -> ChatFormatting.LIGHT_PURPLE;
            case "bug" -> ChatFormatting.DARK_GREEN;
            case "rock" -> ChatFormatting.GOLD;
            case "ghost" -> ChatFormatting.DARK_PURPLE;
            case "dragon" -> ChatFormatting.DARK_PURPLE;
            case "dark" -> ChatFormatting.DARK_GRAY;
            case "steel" -> ChatFormatting.GRAY;
            case "fairy" -> ChatFormatting.LIGHT_PURPLE;
            case "normal" -> ChatFormatting.WHITE;
            default -> ChatFormatting.WHITE;
        };
    }

    /**
     * the ally pokemon's info tile is in the top-left area of the battle screen (BattleOverlay).
     * uses PokemonTooltipRenderer for consistent styling.
     */
    private static void renderAllyPokemonInfo(GuiGraphics graphics, ClientBattle battle, Minecraft mc) {
        try {
            double mouseX = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getWidth();
            double mouseY = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getHeight();

            if (mouseX < ALLY_TILE_X || mouseX > ALLY_TILE_X + ALLY_TILE_WIDTH ||
                mouseY < ALLY_TILE_Y || mouseY > ALLY_TILE_Y + ALLY_TILE_HEIGHT) {
                return;
            }

            Pokemon foundPokemon = null;

            var pendingRequests = battle.getPendingActionRequests();
            if (pendingRequests != null && !pendingRequests.isEmpty()) {
                SingleActionRequest request = pendingRequests.get(0);
                var ourActivePokemon = request.getActivePokemon();
                if (ourActivePokemon != null) {
                    ClientBattlePokemon battlePokemon = ourActivePokemon.getBattlePokemon();
                    if (battlePokemon != null) {
                        var clientStorage = CobblemonClient.INSTANCE.getStorage();
                        if (clientStorage != null) {
                            var myParty = clientStorage.getMyParty();
                            if (myParty != null) {
                                for (var pokemon : myParty) {
                                    if (pokemon != null && pokemon.getUuid().equals(battlePokemon.getUuid())) {
                                        foundPokemon = pokemon;
                                        break;
                                    }
                                }
                                // safeCopy clones have different UUIDs; fall back to species match
                                if (foundPokemon == null) {
                                    String battleName = battlePokemon.getDisplayName().getString();
                                    for (var pokemon : myParty) {
                                        if (pokemon != null && pokemon.getSpecies().getName().equalsIgnoreCase(battleName)) {
                                            foundPokemon = pokemon;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // only render tooltip if we found the pokemon (prevents empty box)
            if (foundPokemon != null) {
                PokemonTooltipRenderer.renderTooltip(graphics, foundPokemon, (int) mouseX, (int) mouseY, false);
            }

        } catch (Exception e) {
            // silently ignore
        }
    }

    private static void renderTooltip(GuiGraphics graphics, Minecraft mc, int x, int y, List<Component> lines) {
        if (lines.isEmpty()) return;

        var font = mc.font;
        int lineHeight = font.lineHeight + 2;

        int maxWidth = 0;
        for (Component line : lines) {
            maxWidth = Math.max(maxWidth, font.width(line));
        }

        int tooltipWidth = maxWidth + 8;
        int tooltipHeight = lines.size() * lineHeight + 6;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        if (x + tooltipWidth > screenWidth) {
            x = screenWidth - tooltipWidth - 5;
        }
        if (y + tooltipHeight > screenHeight) {
            y = screenHeight - tooltipHeight - 5;
        }
        if (y < 5) y = 5;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);

        graphics.fill(x - 3, y - 3, x + tooltipWidth + 3, y + tooltipHeight + 1, 0xF0100010);
        graphics.fill(x - 2, y - 2, x + tooltipWidth + 2, y + tooltipHeight, 0xF0100010);

        graphics.fill(x - 3, y - 2, x - 2, y + tooltipHeight, 0xFF5000FF);
        graphics.fill(x + tooltipWidth + 2, y - 2, x + tooltipWidth + 3, y + tooltipHeight, 0xFF5000FF);
        graphics.fill(x - 2, y - 3, x + tooltipWidth + 2, y - 2, 0xFF5000FF);
        graphics.fill(x - 2, y + tooltipHeight, x + tooltipWidth + 2, y + tooltipHeight + 1, 0xFF5000FF);

        int textY = y;
        for (Component line : lines) {
            graphics.drawString(font, line, x, textY, 0xFFFFFFFF, true);
            textY += lineHeight;
        }

        graphics.pose().popPose();
    }
}
