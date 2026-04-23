package com.newbulaco.showdown.mixin;

import com.cobblemon.mod.common.api.battles.interpreter.BasicContext;
import com.cobblemon.mod.common.api.battles.interpreter.BattleContext;
import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage;
import com.cobblemon.mod.common.api.battles.interpreter.Effect;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.interpreter.instructions.FieldStartInstruction;
import com.newbulaco.showdown.api.ShowdownAPI;
import com.newbulaco.showdown.api.content.CustomFieldCondition;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// cobblemon's Effect.parse() fails for custom content not in its registries,
// so we intercept field start instructions to handle custom terrains/weather/rooms
@Mixin(value = FieldStartInstruction.class, remap = false)
public abstract class FieldStartInstructionMixin {

    @Shadow
    @Final
    private BattleMessage message;

    @Inject(method = "invoke", at = @At("HEAD"), cancellable = true)
    private void cobblemonShowdown$handleCustomFieldStart(PokemonBattle battle, CallbackInfo ci) {
        Effect effect = message.effectAt(0);
        String effectId = null;

        if (effect != null) {
            effectId = effect.getId();
        } else {
            // Effect.parse() fails for custom content not in cobblemon's registries
            String rawArg = message.argumentAt(0);
            if (rawArg != null && !rawArg.isEmpty()) {
                String displayName;
                // raw args may have "move: " prefix from showdown
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

        CustomFieldCondition customCondition = ShowdownAPI.getFieldCondition(effectId);

        if (customCondition != null) {
            final String finalEffectId = effectId;
            final String finalDisplayName = customCondition.getDisplayName();

            battle.dispatchWaiting(1.5F, () -> {
                Component lang = Component.translatable("cobblemon_showdown.custom.field.start", finalDisplayName);
                battle.broadcastChatMessage(lang);

                BattleContext.Type type = switch (customCondition.getType()) {
                    case TERRAIN -> BattleContext.Type.TERRAIN;
                    case WEATHER -> BattleContext.Type.WEATHER;
                    case ROOM -> BattleContext.Type.ROOM;
                };

                // BasicContext directly because getContextFromAction() produces garbage IDs
                // when Effect.parse() fails
                battle.getContextManager().add(
                    new BasicContext(finalEffectId, battle.getTurn(), type, null)
                );

                return kotlin.Unit.INSTANCE;
            });

            ci.cancel();
        }
    }
}
