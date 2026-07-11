package com.newbulaco.showdown.mixin.client;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.SingleActionRequest;
import com.cobblemon.mod.common.client.gui.battle.BattleGUI;
import com.cobblemon.mod.common.client.gui.battle.BattleOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// draws a soft highlight over the slot whose action request the player is currently answering.
// without this, in doubles the two slots look identical and the player can't tell which
// pokemon's move panel is on screen. mirrors the selectedPNX feature added upstream in 1.21+.
@Mixin(targets = "com.cobblemon.mod.common.client.gui.battle.BattleOverlay", remap = false)
public abstract class BattleOverlayMixin {

    @Inject(method = "drawTile", at = @At("TAIL"))
    private void cobblemonShowdown$drawSelectedSlotHighlight(
            GuiGraphics context,
            float tickDelta,
            ActiveClientBattlePokemon active,
            boolean left,
            int rank,
            CallbackInfo ci) {

        ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
        if (battle == null) return;
        if (battle.getBattleFormat().getBattleType().getSlotsPerActor() <= 1) return;

        Screen current = Minecraft.getInstance().screen;
        if (!(current instanceof BattleGUI)) return;
        if (((BattleGUI) current).getCurrentActionSelection() == null) return;

        SingleActionRequest req = battle.getFirstUnansweredRequest();
        if (req == null) return;
        if (!req.getActivePokemon().getPNX().equals(active.getPNX())) return;

        float x = active.getXDisplacement();
        int y = BattleOverlay.VERTICAL_INSET + rank * BattleOverlay.VERTICAL_SPACING;
        int w = BattleOverlay.TILE_WIDTH;
        int h = BattleOverlay.TILE_HEIGHT;

        int rgba = 0x33FFFFFF; // ~20% white tint
        context.fill((int) x, y, (int) x + w, y + h, rgba);
    }
}
