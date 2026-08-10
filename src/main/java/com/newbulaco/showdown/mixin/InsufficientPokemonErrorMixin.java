package com.newbulaco.showdown.mixin;

import com.cobblemon.mod.common.battles.InsufficientPokemonError;
import com.cobblemon.mod.common.util.LocalizationUtilsKt;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// cobblemon swaps hadCount and requiredCount when formatting the lang strings,
// so the message reads backwards; rebuild it with the counts in the right slots.
@Mixin(value = InsufficientPokemonError.class, remap = false)
public abstract class InsufficientPokemonErrorMixin {

    @Shadow @Final private ServerPlayer player;
    @Shadow @Final private int requiredCount;
    @Shadow @Final private int hadCount;

    @Inject(method = "getMessageFor", at = @At("HEAD"), cancellable = true)
    private void cobblemonShowdown$fixWording(Entity entity, CallbackInfoReturnable<MutableComponent> cir) {
        MutableComponent message;
        if (this.player == entity) {
            String key = this.hadCount == 0 ? "no_pokemon" : "insufficient_pokemon.personal";
            message = LocalizationUtilsKt.battleLang(
                "error." + key, new Object[]{ this.hadCount, this.requiredCount }
            );
        } else {
            message = LocalizationUtilsKt.battleLang(
                "error.insufficient_pokemon",
                new Object[]{ this.player.getDisplayName(), this.hadCount, this.requiredCount }
            );
        }
        cir.setReturnValue(message);
    }
}
