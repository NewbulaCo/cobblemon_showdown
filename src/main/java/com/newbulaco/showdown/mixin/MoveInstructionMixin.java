package com.newbulaco.showdown.mixin;

import com.cobblemon.mod.common.api.battles.interpreter.Effect;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.moves.animations.TargetsProvider;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.interpreter.instructions.MoveInstruction;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.util.LocalizationUtilsKt;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

// spread-move support. showdown sends e.g. earthquake as |move|p1a|...|p2a|[spread] p1a,p1b,p2a,p2b.
// 1.20.1 cobblemon ignores the spread tag, so the chat line names a single target ("X used Y on Z")
// and the animation only fires against one entity. 1.21+ fixes both inside the dispatch body. we
// match its behavior with two redirects on the dispatch lambda's inner class, leaving the rest of
// invoke untouched. singles path is byte-for-byte unchanged (optionalArgument("spread") is null).
//
// the lambda's outer-instance and captured args (this$0, $battle, $optionalEffect) would normally be
// pulled via @Shadow, but the mixin annotation processor crashes when validating synthetic-named
// fields on the kotlin lambda class. reflection sidesteps the AP bug; the fields are looked up once.
@Mixin(targets = "com.cobblemon.mod.common.battles.interpreter.instructions.MoveInstruction$invoke$2", remap = false)
public abstract class MoveInstructionMixin {

    private static volatile Field outerField;
    private static volatile Field battleField;
    private static volatile Field optEffectField;

    private static void bindFields(Class<?> cls) {
        if (outerField != null) return;
        synchronized (MoveInstructionMixin.class) {
            if (outerField != null) return;
            try {
                Field o = cls.getDeclaredField("this$0");
                Field b = cls.getDeclaredField("$battle");
                Field e = cls.getDeclaredField("$optionalEffect");
                o.setAccessible(true);
                b.setAccessible(true);
                e.setAccessible(true);
                battleField = b;
                optEffectField = e;
                outerField = o;
            } catch (NoSuchFieldException ex) {
                throw new RuntimeException("cobblemon_showdown: MoveInstruction$invoke$2 lambda captures changed shape", ex);
            }
        }
    }

    private MoveInstruction outer() {
        bindFields(this.getClass());
        try {
            return (MoveInstruction) outerField.get(this);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private PokemonBattle capturedBattle() {
        bindFields(this.getClass());
        try {
            return (PokemonBattle) battleField.get(this);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private Effect capturedOptionalEffect() {
        bindFields(this.getClass());
        try {
            return (Effect) optEffectField.get(this);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Redirect(
        method = "invoke",
        at = @At(
            value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/api/battles/model/PokemonBattle;broadcastChatMessage(Lnet/minecraft/network/chat/Component;)V"
        )
    )
    private void cobblemonShowdown$rewriteSpreadChat(PokemonBattle battle, Component original) {
        MoveInstruction move = outer();
        String spread = move.getMessage().optionalArgument("spread");
        Effect optEffect = capturedOptionalEffect();
        boolean isMagicbounce = optEffect != null && "magicbounce".equals(optEffect.getId());
        if (spread == null || isMagicbounce) {
            battle.broadcastChatMessage(original);
            return;
        }
        Component used = LocalizationUtilsKt.battleLang(
            "used_move",
            new Object[] { move.getUserPokemon().getName(), move.getMove().getDisplayName() }
        );
        battle.broadcastChatMessage(used);
    }

    @Redirect(
        method = "invoke",
        at = @At(
            value = "NEW",
            target = "([Lnet/minecraft/world/entity/Entity;)Lcom/cobblemon/mod/common/api/moves/animations/TargetsProvider;"
        )
    )
    private TargetsProvider cobblemonShowdown$expandSpreadTargets(Entity[] originalTargets) {
        MoveInstruction move = outer();
        String spread = move.getMessage().optionalArgument("spread");
        if (spread == null) {
            return new TargetsProvider(originalTargets);
        }
        PokemonBattle battle = capturedBattle();
        List<Entity> resolved = new ArrayList<>();
        for (String pnx : spread.split(",")) {
            String key = pnx.trim();
            if (key.isEmpty()) continue;
            for (ActiveBattlePokemon active : battle.getActivePokemon()) {
                if (!key.equals(active.getPNX())) continue;
                BattlePokemon bp = active.getBattlePokemon();
                if (bp == null) break;
                Entity entity = bp.getEntity();
                if (entity != null) resolved.add(entity);
                break;
            }
        }
        if (resolved.isEmpty()) {
            return new TargetsProvider(originalTargets);
        }
        return new TargetsProvider(resolved.toArray(new Entity[0]));
    }
}
