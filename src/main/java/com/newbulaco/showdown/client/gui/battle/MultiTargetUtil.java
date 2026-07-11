/*
 * Portions of this file are derived from Cobblemon (https://gitlab.com/cobblemon/cobblemon),
 * Copyright (C) 2023 Cobblemon Contributors, licensed under the Mozilla Public License, v. 2.0.
 * A copy of the MPL is available at https://mozilla.org/MPL/2.0/.
 */
package com.newbulaco.showdown.client.gui.battle;

import com.cobblemon.mod.common.battles.MoveTarget;
import com.cobblemon.mod.common.battles.Targetable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// targets that get hit by a multi-target move (spread, side, etc.) but aren't user-selectable.
// the target screen highlights these so the player can see who the move will land on.
// 1.21+ cobblemon adds this as a default method on Targetable; on 1.20.1 the interface
// doesn't have it, so we host it here.
public final class MultiTargetUtil {

    private MultiTargetUtil() {}

    public static List<Targetable> getMultiTargetList(Targetable user, MoveTarget targetType) {
        switch (targetType) {
            case all:
                return toList(user.getAllActivePokemon());
            case allAdjacent:
                return user.getAdjacent();
            case allAdjacentFoes:
                return user.getAdjacentOpponents();
            case self:
                return Collections.singletonList(user);
            case randomNormal:
                return Collections.singletonList(user);
            case allies:
            case allySide:
            case allyTeam:
                return filterAllied(user, true);
            case foeSide:
                return filterAllied(user, false);
            case scripted:
                return Collections.singletonList(user);
            default:
                return null;
        }
    }

    private static List<Targetable> filterAllied(Targetable user, boolean wantAllied) {
        List<Targetable> out = new ArrayList<>();
        for (Targetable t : user.getAllActivePokemon()) {
            if (user.isAllied(t) == wantAllied) {
                out.add(t);
            }
        }
        return out;
    }

    private static List<Targetable> toList(Iterable<Targetable> iter) {
        List<Targetable> out = new ArrayList<>();
        for (Targetable t : iter) out.add(t);
        return out;
    }
}
