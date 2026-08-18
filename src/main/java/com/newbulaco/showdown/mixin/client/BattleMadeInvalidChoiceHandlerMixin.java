package com.newbulaco.showdown.mixin.client;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.SingleActionRequest;
import com.cobblemon.mod.common.client.net.battle.BattleMadeInvalidChoiceHandler;
import com.cobblemon.mod.common.net.messages.client.battle.BattleMadeInvalidChoicePacket;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// unlock the action GUI after showdown rejects a choice (upstream leaves responses set).
@Mixin(value = BattleMadeInvalidChoiceHandler.class, remap = false)
public abstract class BattleMadeInvalidChoiceHandlerMixin {

    @Inject(method = "handle(Lcom/cobblemon/mod/common/net/messages/client/battle/BattleMadeInvalidChoicePacket;Lnet/minecraft/client/Minecraft;)V", at = @At("TAIL"))
    private void cobblemonShowdown$unlockOnInvalid(BattleMadeInvalidChoicePacket packet, Minecraft client, CallbackInfo ci) {
        ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
        if (battle == null) return;
        for (SingleActionRequest request : battle.getPendingActionRequests()) {
            request.setResponse(null);
        }
        battle.setMustChoose(true);
    }
}
