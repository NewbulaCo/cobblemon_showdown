package com.newbulaco.showdown.util;

import net.minecraft.world.phys.Vec3;

public final class SendOutPositions {

    private SendOutPositions() {}

    private static final double BASE_FACTOR = 0.28;
    private static final double ORTHOGONAL_SCALE = 1.5;

    public static Vec3 doublesSlotOffset(Vec3 actorPos, Vec3 opposingPos, char slotLetter) {
        if (actorPos == null || opposingPos == null) return null;
        Vec3 actorOffset = opposingPos.subtract(actorPos);
        if (actorOffset.length() < 1.0e-4) return actorPos;

        Vec3 base = actorPos.add(actorOffset.scale(BASE_FACTOR));

        Vec3 orthogonal = new Vec3(actorOffset.x, 0.0, actorOffset.z).normalize();
        orthogonal = orthogonal.cross(new Vec3(0.0, 1.0, 0.0));
        if (slotLetter == 'a') orthogonal = orthogonal.scale(-1.0);
        orthogonal = orthogonal.scale(ORTHOGONAL_SCALE);

        return base.add(orthogonal);
    }
}
