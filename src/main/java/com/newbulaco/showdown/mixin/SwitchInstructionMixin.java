package com.newbulaco.showdown.mixin;

import com.cobblemon.mod.common.api.battles.interpreter.BattleMessage;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.battles.model.actor.EntityBackedBattleActor;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.interpreter.instructions.SwitchInstruction;
import com.newbulaco.showdown.util.SendOutPositions;
import kotlin.Pair;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Offsets the pre-battle send-out so doubles slot 'a' and 'b' don't spawn on
// top of each other. We compute the override at HEAD (where the publicMessage
// pnx and battle context are both reachable), stash it on a ThreadLocal, and
// apply it via @ModifyArg at the sendOutWithAnimation call site. Mixin 0.8.5
// has no @Local capture, hence the sandwich pattern.
//
// Path #2 (in-battle switches via createEntitySwitch) is intentionally deferred
// here: that sendOutWithAnimation lives inside a CompletableFuture lambda in a
// separate companion-object method, which needs a different mixin target.
@Mixin(value = SwitchInstruction.class, remap = false)
public abstract class SwitchInstructionMixin {

    private static final ThreadLocal<Vec3> POSITION_OVERRIDE = new ThreadLocal<>();

    @Inject(method = "invoke", at = @At("HEAD"))
    private void cobblemonShowdown$computeDoublesOverride(PokemonBattle battle, CallbackInfo ci) {
        POSITION_OVERRIDE.remove();
        if (battle.getFormat().getBattleType().getSlotsPerActor() <= 1) return;
        if (battle.getStarted()) return;

        SwitchInstruction self = (SwitchInstruction) (Object) this;
        BattleMessage message = self.getPublicMessage();
        if (message == null) return;
        Pair<String, String> pnxAndUuid = message.pnxAndUuid(0);
        if (pnxAndUuid == null) return;
        String pnx = pnxAndUuid.getFirst();
        if (pnx == null || pnx.length() < 3) return;

        Pair<BattleActor, ActiveBattlePokemon> actorSlot = battle.getActorAndActiveSlotFromPNX(pnx);
        if (actorSlot == null) return;
        BattleActor actor = actorSlot.getFirst();
        if (!(actor instanceof EntityBackedBattleActor<?> ebba)) return;
        LivingEntity entity = ebba.getEntity();
        if (entity == null) return;

        Vec3 opposing = findOpposingActorPos(actor);
        if (opposing == null) return;

        Vec3 override = SendOutPositions.doublesSlotOffset(entity.position(), opposing, pnx.charAt(2));
        if (override != null) POSITION_OVERRIDE.set(override);
    }

    @ModifyArg(
            method = "invoke",
            at = @At(value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/pokemon/Pokemon;sendOutWithAnimation$default"),
            index = 3
    )
    private Vec3 cobblemonShowdown$applyDoublesOverride(Vec3 original) {
        Vec3 override = POSITION_OVERRIDE.get();
        return override != null ? override : original;
    }

    @Inject(method = "invoke", at = @At("RETURN"))
    private void cobblemonShowdown$clearOverride(PokemonBattle battle, CallbackInfo ci) {
        POSITION_OVERRIDE.remove();
    }

    private static Vec3 findOpposingActorPos(BattleActor actor) {
        for (BattleActor opp : actor.getSide().getOppositeSide().getActors()) {
            if (opp instanceof EntityBackedBattleActor<?> ebba) {
                LivingEntity entity = ebba.getEntity();
                if (entity != null) return entity.position();
            }
        }
        return null;
    }
}
