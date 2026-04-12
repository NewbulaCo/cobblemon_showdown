package com.newbulaco.showdown.mixin;

import com.cobblemon.mod.common.api.battles.interpreter.BasicContext;
import com.cobblemon.mod.common.api.battles.interpreter.BattleContext;
import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage;
import com.cobblemon.mod.common.api.battles.interpreter.Effect;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.interpreter.instructions.SideStartInstruction;
import com.newbulaco.showdown.api.ShowdownAPI;
import com.newbulaco.showdown.api.content.CustomSideCondition;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SideStartInstruction.class, remap = false)
public abstract class SideStartInstructionMixin {

    @Shadow
    @Final
    private BattleMessage message;

    @Inject(method = "invoke", at = @At("HEAD"), cancellable = true)
    private void cobblemonShowdown$handleCustomSideCondition(PokemonBattle battle, CallbackInfo ci) {
        Effect effect = message.effectAt(1);
        String effectId = null;

        if (effect != null) {
            effectId = effect.getId();
        } else {
            // Effect.parse() fails for custom content not in cobblemon's registries
            String rawArg = message.argumentAt(1);
            if (rawArg != null && !rawArg.isEmpty()) {
                String displayName;
                if (rawArg.startsWith("move: ")) {
                    displayName = rawArg.substring(6);
                } else {
                    displayName = rawArg;
                }
                effectId = displayName.toLowerCase().replaceAll("[^a-z0-9]", "");
            }
        }

        if (effectId == null) {
            return;
        }

        CustomSideCondition customCondition = ShowdownAPI.getSideCondition(effectId);

        if (customCondition != null) {
            final String finalEffectId = effectId;
            final String finalDisplayName = customCondition.getDisplayName();

            battle.dispatchWaiting(2F, () -> {
                var sideArg = message.argumentAt(0);
                var side = (sideArg != null && sideArg.length() > 1 && sideArg.charAt(1) == '1')
                        ? battle.getSide1() : battle.getSide2();

                for (var battleSide : battle.getSides()) {
                    Component subject = battleSide == side
                        ? Component.translatable("cobblemon_showdown.custom.condition.ally")
                        : Component.translatable("cobblemon_showdown.custom.condition.opponent");
                    Component lang = Component.translatable("cobblemon_showdown.custom.condition.start", finalDisplayName, subject);
                    battleSide.broadcastChatMessage(lang);
                }

                var bucket = switch (customCondition.getCategory()) {
                    case HAZARD -> BattleContext.Type.HAZARD;
                    case SCREEN -> BattleContext.Type.SCREEN;
                    default -> BattleContext.Type.MISC;
                };

                // BasicContext directly because getContextFromAction() produces garbage IDs
                // when Effect.parse() fails
                side.getContextManager().add(
                    new BasicContext(finalEffectId, battle.getTurn(), bucket, null)
                );

                return kotlin.Unit.INSTANCE;
            });

            ci.cancel();
        }
    }
}
