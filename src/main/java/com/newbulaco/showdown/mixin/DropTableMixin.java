package com.newbulaco.showdown.mixin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;

// placeholder; actual drop prevention is in PokemonEntityDropMixin
@Mixin(targets = "com.cobblemon.mod.common.api.drop.DropTable", remap = false)
public class DropTableMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("DropTableMixin");

    static {
        LOGGER.info("DropTableMixin loaded (item drop prevention is handled by PokemonEntityDropMixin)");
    }
}
