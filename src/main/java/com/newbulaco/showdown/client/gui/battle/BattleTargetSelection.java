/*
 * Portions of this file are derived from Cobblemon (https://gitlab.com/cobblemon/cobblemon),
 * Copyright (C) 2023 Cobblemon Contributors, licensed under the Mozilla Public License, v. 2.0.
 * A copy of the MPL is available at https://mozilla.org/MPL/2.0/.
 */
package com.newbulaco.showdown.client.gui.battle;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.battles.InBattleMove;
import com.cobblemon.mod.common.battles.MoveActionResponse;
import com.cobblemon.mod.common.battles.MoveTarget;
import com.cobblemon.mod.common.battles.Targetable;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.SingleActionRequest;
import com.cobblemon.mod.common.client.gui.battle.BattleGUI;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleActionSelection;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleBackButton;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleMoveSelection;
import com.mojang.blaze3d.systems.RenderSystem;
import com.newbulaco.showdown.client.battle.TypeEffectiveness;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// target picker subscreen for 2v2+ battles. shown when the player picks a move whose
// targetlist has more than one valid target (showdown won't accept a null target in doubles).
// users click a tile to commit moveactionresponse(moveid, pnx); back returns to move screen.
public class BattleTargetSelection extends BattleActionSelection {

    private static final int TARGET_WIDTH = 93;
    private static final int TARGET_HEIGHT = 33;
    private static final float MOVE_HORIZONTAL_SPACING = 2F;
    private static final float MOVE_VERTICAL_SPACING = 1F;
    private static final int BACKGROUND_HEIGHT = 148;

    private static final String NS = "cobblemon_showdown";
    private static final ResourceLocation UNDERLAY = tex("selection_underlay");
    private static final ResourceLocation TILE = tex("target_select");
    private static final ResourceLocation TILE_DISABLED = tex("target_select_disabled");
    private static final ResourceLocation ROLE_LOWER = tex("target_select_role");
    private static final ResourceLocation ARROW_UP = tex("arrow_pointer_up");
    private static final ResourceLocation ARROW_DOWN = tex("arrow_pointer_down");
    private static final ResourceLocation ARROW_LEFT = tex("arrow_pointer_left");
    private static final ResourceLocation ARROW_RIGHT = tex("arrow_pointer_right");

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(NS, "textures/gui/battle/" + name + ".png");
    }

    private enum ArrowDir { UP, DOWN, LEFT, RIGHT }

    private final InBattleMove move;
    private final List<Targetable> selectableTargets; // null when no user-selectable target exists
    private final List<Targetable> multiTargets;      // set instead of selectableTargets for spread/side moves
    private final List<TargetTile> tiles;
    private final BattleBackButton backButton;

    public BattleTargetSelection(BattleGUI battleGUI, SingleActionRequest request, InBattleMove move) {
        super(battleGUI, request, 0, computeY(), 100, 100, Component.translatable("cobblemon.battle.ui.select_move"));
        this.move = move;

        ActiveClientBattlePokemon active = request.getActivePokemon();
        List<Targetable> targets = toList(active.getAllActivePokemon());

        MoveTarget targetType = move.getTarget();
        this.selectableTargets = computeSelectableTargets(targetType, active);
        this.multiTargets = (selectableTargets == null)
                ? MultiTargetUtil.getMultiTargetList(active, targetType)
                : null;

        int teamSize = countSide(active);
        this.tiles = new ArrayList<>(targets.size());
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        for (int i = 0; i < targets.size(); i++) {
            Targetable target = targets.get(i);
            boolean isAlly = target.isAllied(active);
            int fieldPos = isAlly ? (i % teamSize) : (teamSize - 1 - (i % teamSize));
            float tileX = this.getX() + screenWidth / 2f
                    - (TARGET_WIDTH + MOVE_HORIZONTAL_SPACING) * teamSize / 2f
                    + MOVE_HORIZONTAL_SPACING + fieldPos * TARGET_WIDTH;
            float tileY = this.getY() + 44F + (isAlly ? (TARGET_HEIGHT + MOVE_VERTICAL_SPACING) : 0F);
            ArrowDir dir;
            if (fieldPos == 0) {
                dir = ArrowDir.RIGHT;
            } else if (fieldPos == teamSize - 1) {
                dir = ArrowDir.LEFT;
            } else {
                dir = isAlly ? ArrowDir.UP : ArrowDir.DOWN;
            }
            tiles.add(new TargetTile(target, tileX, tileY, dir));
        }

        this.backButton = new BattleBackButton(this.getX() + 9F,
                Minecraft.getInstance().getWindow().getGuiScaledHeight() - 22F);
    }

    private static int computeY() {
        int h = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        return h > 304 ? (h / 2) - (BACKGROUND_HEIGHT / 2) : h - (BACKGROUND_HEIGHT + 78);
    }

    // mirrors MoveTarget.targetList from kotlin; returning null means "no user-pickable single
    // target", which triggers multitarget rendering instead.
    private static List<Targetable> computeSelectableTargets(MoveTarget mt, Targetable user) {
        switch (mt) {
            case any: {
                List<Targetable> out = new ArrayList<>();
                for (Targetable t : user.getAllActivePokemon()) {
                    if (t != user) out.add(t);
                }
                return out;
            }
            case normal:
                return user.getAdjacent();
            case adjacentAlly:
                return user.getAdjacentAllies();
            case adjacentAllyOrSelf: {
                List<Targetable> out = new ArrayList<>(user.getAdjacentAllies());
                out.add(user);
                return out;
            }
            case adjacentFoe:
                return user.getAdjacentOpponents();
            default:
                return null;
        }
    }

    private static int countSide(Targetable t) {
        int c = 0;
        for (Object ignored : t.getSidePokemon()) c++;
        return c;
    }

    private static List<Targetable> toList(Iterable<? extends Targetable> it) {
        List<Targetable> out = new ArrayList<>();
        for (Targetable t : it) out.add(t);
        return out;
    }

    @Override
    protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float partialTick) {
        // background underlay across the full screen
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        drawTinted(context, UNDERLAY, this.getX(), this.getY(), screenW, BACKGROUND_HEIGHT, 0, 0, screenW, BACKGROUND_HEIGHT, 1F, 1F, 1F, this.getOpacity());

        // title
        Font font = Minecraft.getInstance().font;
        Component title = Component.translatable("cobblemon.battle.select_target");
        int titleW = font.width(title);
        int titleY = this.getY() + (countSide(getRequest().getActivePokemon()) == 2 ? 25 : 16);
        context.drawString(font, title, (screenW - titleW) / 2, titleY, withAlpha(0xFFFFFF, this.getOpacity()), true);

        for (TargetTile tile : tiles) {
            tile.render(context, mouseX, mouseY, partialTick);
        }
        backButton.render(context.pose(), mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        for (TargetTile tile : tiles) {
            if (tile.isHovered(mouseX, mouseY)) {
                tile.onClick();
                return true;
            }
        }
        if (backButton.isHovered(mouseX, mouseY)) {
            getBattleGUI().changeActionSelection(new BattleMoveSelection(getBattleGUI(), getRequest()));
            return true;
        }
        return false;
    }

    private static int withAlpha(int rgb, float opacity) {
        int a = Math.max(0, Math.min(255, (int) (opacity * 255F)));
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    // null when no indicator should be drawn (neutral, status move on a non-immune target,
    // or chart lookup failed). matches BattleOverlayRenderer.renderMoveEffectiveness gating
    // so the singles and doubles paths agree on what counts as worth flagging.
    private TypeEffectiveness.EffectivenessCategory effectivenessAgainst(ClientBattlePokemon target) {
        var form = target.getSpecies().getStandardForm();
        String primary = null;
        String secondary = null;
        int idx = 0;
        for (ElementalType t : form.getTypes()) {
            if (idx == 0) primary = t.getName();
            else if (idx == 1) { secondary = t.getName(); break; }
            idx++;
        }
        if (primary == null) return null;

        MoveTemplate template = Moves.INSTANCE.getByNameOrDummy(move.getId());
        String moveType = template.getElementalType().getName();
        double eff = TypeEffectiveness.getEffectiveness(moveType, primary, secondary);

        if (eff == 1.0) return null;
        boolean isStatus = template.getDamageCategory() == DamageCategories.INSTANCE.getSTATUS();
        if (isStatus && eff > 0) return null;

        TypeEffectiveness.EffectivenessCategory cat = TypeEffectiveness.EffectivenessCategory.fromMultiplier(eff);
        return cat == TypeEffectiveness.EffectivenessCategory.NEUTRAL ? null : cat;
    }

    private static void drawTinted(GuiGraphics context, ResourceLocation tex,
                                   float x, float y, int width, int height,
                                   int uOffset, int vOffset, int texWidth, int texHeight,
                                   float r, float g, float b, float a) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(r, g, b, a);
        context.blit(tex, (int) x, (int) y, uOffset, vOffset, width, height, texWidth, texHeight);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.disableBlend();
    }

    // per-target tile: background, name + level, role bar, hover arrow.
    private class TargetTile {
        private final Targetable target;
        private final float x;
        private final float y;
        private final ArrowDir arrowDir;
        private final String responsePnx; // null if not user-selectable as a single target
        private final boolean isMulti;
        private final float arrowX;
        private final float arrowY;
        private final ResourceLocation arrowTex;

        TargetTile(Targetable target, float x, float y, ArrowDir arrowDir) {
            this.target = target;
            this.x = x;
            this.y = y;
            this.arrowDir = arrowDir;

            String pnx = target.getPNX();
            this.responsePnx = findByPnx(selectableTargets, pnx) != null ? pnx : null;
            this.isMulti = findByPnx(multiTargets, pnx) != null;

            switch (arrowDir) {
                case LEFT:
                    this.arrowTex = ARROW_LEFT;
                    this.arrowX = x + TARGET_WIDTH + 3F;
                    this.arrowY = y + 13F;
                    break;
                case RIGHT:
                    this.arrowTex = ARROW_RIGHT;
                    this.arrowX = x - 7F;
                    this.arrowY = y + 13F;
                    break;
                case UP:
                    this.arrowTex = ARROW_UP;
                    this.arrowX = x + TARGET_WIDTH / 2F - 4F;
                    this.arrowY = y + TARGET_HEIGHT + 3F;
                    break;
                case DOWN:
                default:
                    this.arrowTex = ARROW_DOWN;
                    this.arrowX = x + TARGET_WIDTH / 2F - 4F;
                    this.arrowY = y - 7F;
                    break;
            }
        }

        private Targetable findByPnx(List<Targetable> list, String pnx) {
            if (list == null) return null;
            for (Targetable t : list) {
                if (pnx.equals(t.getPNX())) return t;
            }
            return null;
        }

        boolean selectable() {
            return isMulti || responsePnx != null;
        }

        boolean isHovered(double mx, double my) {
            // multitarget tiles share a single hover hotbox; any tile being hovered counts for all.
            if (isMulti) {
                for (TargetTile t : tiles) {
                    if (t.selectable()
                            && mx >= t.x && mx <= t.x + TARGET_WIDTH
                            && my >= t.y && my <= t.y + TARGET_HEIGHT) {
                        return true;
                    }
                }
                return false;
            }
            return mx >= x && mx <= x + TARGET_WIDTH && my >= y && my <= y + TARGET_HEIGHT;
        }

        void onClick() {
            if (!selectable()) return;
            MoveActionResponse response = new MoveActionResponse(move.getId(), responsePnx, null);
            getBattleGUI().selectAction(getRequest(), response);
        }

        void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
            float selOpacity = getOpacity() * (selectable() ? 1F : 0.5F);
            boolean hovered = isHovered(mouseX, mouseY);

            ResourceLocation tileTex = selectable() ? TILE : TILE_DISABLED;
            int vOff = (selectable() && hovered) ? TARGET_HEIGHT : 0;
            int texH = selectable() ? TARGET_HEIGHT * 2 : TARGET_HEIGHT;
            drawTinted(context, tileTex, x, y, TARGET_WIDTH, TARGET_HEIGHT, 0, vOff, TARGET_WIDTH, texH, 1F, 1F, 1F, selOpacity);

            ClientBattlePokemon bp = null;
            if (target instanceof ActiveClientBattlePokemon) {
                bp = ((ActiveClientBattlePokemon) target).getBattlePokemon();
            }
            if (bp == null || bp.getHpValue() <= 0) return;

            int hue = (target instanceof ActiveClientBattlePokemon) ? ((ActiveClientBattlePokemon) target).getHue() : 0xFFFFFF;
            float r = ((hue >> 16) & 0xFF) / 255F;
            float g = ((hue >> 8) & 0xFF) / 255F;
            float b = (hue & 0xFF) / 255F;

            Font font = Minecraft.getInstance().font;
            int textColour = withAlpha(0xFFFFFF, selOpacity);

            // name (display name -> string for plain font path)
            String name = bp.getDisplayName().getString();
            context.drawString(font, name, (int) x + 5, (int) y + 8, textColour, true);

            // level
            String lv = "Lv " + bp.getLevel();
            context.drawString(font, lv, (int) x + 5, (int) y + 18, textColour, true);

            // lower role bar (team hue)
            drawTinted(context, ROLE_LOWER, x + 4, y + 29, 82, 3, 0, 0, 82, 3, r, g, b, selOpacity);

            TypeEffectiveness.EffectivenessCategory cat = effectivenessAgainst(bp);
            if (cat != null) {
                Font f = Minecraft.getInstance().font;
                Component label = cat.translatedText;
                int labelW = f.width(label);
                context.drawString(f, label,
                        (int) x + TARGET_WIDTH - labelW - 4, (int) y + 4,
                        withAlpha(cat.color & 0x00FFFFFF, selOpacity), true);
            }

            // hover arrow with sine throbber
            if (selectable() && hovered) {
                float passed = com.cobblemon.mod.common.client.CobblemonClient.INSTANCE.getBattleOverlay().getPassedSeconds();
                float offset = (float) (Math.sin(passed * 2 * Math.PI) + 1);
                float ax = arrowX;
                float ay = arrowY;
                switch (arrowDir) {
                    case LEFT: ax -= offset; break;
                    case RIGHT: ax += offset; break;
                    case UP: ay -= offset; break;
                    case DOWN: ay += offset; break;
                }
                int aw = (arrowDir == ArrowDir.LEFT || arrowDir == ArrowDir.RIGHT) ? 10 : 17;
                int ah = (arrowDir == ArrowDir.LEFT || arrowDir == ArrowDir.RIGHT) ? 17 : 10;
                drawTinted(context, arrowTex, ax, ay, aw, ah, 0, 0, aw, ah, 1F, 1F, 1F, selOpacity);
            }
        }
    }
}
