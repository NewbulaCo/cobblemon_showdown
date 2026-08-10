package com.newbulaco.showdown.mixin.client;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.SingleActionRequest;
import com.cobblemon.mod.common.client.gui.battle.BattleGUI;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleActionSelection;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleBackButton;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleGeneralActionSelection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

// doubles: back arrow on later slots undoes the previous slot's choice.
// showdown's canCancel flag is ignored because our undo stays on the client;
// the request only ships once every slot has answered.
@Mixin(BattleGeneralActionSelection.class)
public abstract class BattleGeneralActionSelectionMixin {

    @Unique
    private BattleBackButton cobblemonShowdown$backButton;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void cobblemonShowdown$initBack(BattleGUI battleGUI, SingleActionRequest request, CallbackInfo ci) {
        cobblemonShowdown$backButton = new BattleBackButton(
                9F,
                Minecraft.getInstance().getWindow().getGuiScaledHeight() - 105F);
    }

    @Inject(method = "renderWidget", at = @At("TAIL"))
    private void cobblemonShowdown$renderBack(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (cobblemonShowdown$findPreviousAnswered() == null) return;
        cobblemonShowdown$backButton.render(context.pose(), mouseX, mouseY, delta);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void cobblemonShowdown$clickBack(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button != 0) return;
        SingleActionRequest previous = cobblemonShowdown$findPreviousAnswered();
        if (previous == null) return;
        if (!cobblemonShowdown$backButton.isHovered(mouseX, mouseY)) return;

        previous.setResponse(null);
        BattleActionSelection self = (BattleActionSelection) (Object) this;
        self.getBattleGUI().changeActionSelection(null);
        cir.setReturnValue(true);
    }

    @Unique
    private SingleActionRequest cobblemonShowdown$findPreviousAnswered() {
        BattleActionSelection self = (BattleActionSelection) (Object) this;
        ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
        if (battle == null) return null;

        List<SingleActionRequest> requests = battle.getPendingActionRequests();
        int idx = requests.indexOf(self.getRequest());
        for (int i = idx - 1; i >= 0; i--) {
            SingleActionRequest prev = requests.get(i);
            if (prev.getResponse() != null) return prev;
        }
        return null;
    }
}
