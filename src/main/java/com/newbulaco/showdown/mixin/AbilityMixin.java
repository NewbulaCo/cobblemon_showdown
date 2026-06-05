package com.newbulaco.showdown.mixin;

import com.cobblemon.mod.common.api.abilities.Ability;
import com.cobblemon.mod.common.api.abilities.AbilityTemplate;
import com.newbulaco.showdown.api.ShowdownAPI;
import com.newbulaco.showdown.api.content.AbilityModification;
import com.newbulaco.showdown.api.content.CustomAbility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Ability.class, remap = false)
public abstract class AbilityMixin {

    @Shadow
    public abstract AbilityTemplate getTemplate();

    @Inject(method = "getDescription", at = @At("HEAD"), cancellable = true)
    private void cobblemonShowdown$getDescription(CallbackInfoReturnable<String> cir) {
        String abilityName = getTemplate().getName();

        // ability modifications (reworked vanilla abilities) take priority
        AbilityModification mod = ShowdownAPI.getAbilityModification(abilityName);
        if (mod != null && mod.getDescription() != null) {
            // description is already the final text, not a translation key
            cir.setReturnValue(mod.getDescription());
            return;
        }

        CustomAbility custom = ShowdownAPI.getAbility(abilityName);
        if (custom != null && !custom.getDescription().isEmpty()) {
            cir.setReturnValue(custom.getDescription());
        }
    }
}
