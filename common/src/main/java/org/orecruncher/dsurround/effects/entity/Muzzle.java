package org.orecruncher.dsurround.effects.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Where an animal breathes from. A four-legged mob's eye position is the middle of its body; its mouth is out in
 * front, at the end of a neck that turns with the body while the head turns on its own. So the mouth is found in two
 * steps: the neck, forward along the body's yaw, then the mouth, forward along the head's yaw and pitch.
 * <p>
 * The vanilla mobs' numbers come from their models: the head part's pivot, and the front of the mouth or nose
 * relative to it, in model pixels, times the renderer's scale. A mob without one gets a guess from its size.
 */
public final class Muzzle {

    /**
     * @param neckForward  the head's pivot, in front of the body's middle, in blocks
     * @param neckHeight   the head's pivot, above the feet, in blocks
     * @param mouthForward the mouth, in front of the pivot with the head level, in blocks
     * @param mouthUp      the mouth, above the pivot with the head level, in blocks
     * @param followsPitch whether the model tips the head with the mob's pitch (a hoglin's is held at a fixed tilt)
     */
    public record Profile(double neckForward, double neckHeight, double mouthForward, double mouthUp,
                          boolean followsPitch) {

        /**
         * From a model's numbers: the pivot's offset (model y runs down from 24 at the feet, z runs toward the back)
         * and the mouth's relative to it, the model's own fixed head tilt already worked in.
         */
        static Profile model(double pivotZ, double pivotY, double mouthZ, double mouthY, double scale,
                             boolean followsPitch) {
            return new Profile(-pivotZ / 16D * scale, (24D - pivotY) / 16D * scale,
                    -mouthZ / 16D * scale, -mouthY / 16D * scale, followsPitch);
        }

        static Profile model(double pivotZ, double pivotY, double mouthZ, double mouthY, double scale) {
            return model(pivotZ, pivotY, mouthZ, mouthY, scale, true);
        }

        static Profile model(double pivotZ, double pivotY, double mouthZ, double mouthY) {
            return model(pivotZ, pivotY, mouthZ, mouthY, 1D, true);
        }
    }

    static final Map<EntityType<?>, Profile> PROFILES = new IdentityHashMap<>();

    static {
        // Cow: head pivot (0, 4, -8), its snout 7 out, the mouth 2.5 below the pivot (1.21.1's cow has no snout:
        // its face is 6 out)
        var cow = Profile.model(-8, 4, -7, 2.5);
        PROFILES.put(EntityTypes.COW, cow);
        PROFILES.put(EntityTypes.MOOSHROOM, cow);
        PROFILES.put(EntityTypes.SHEEP, Profile.model(-8, 6, -6, 1));
        // The snout sticks out past the face
        PROFILES.put(EntityTypes.PIG, Profile.model(-6, 12, -9, 1.5));
        // The nose is its own part, hung off the head and tipped 55 degrees down
        PROFILES.put(EntityTypes.GOAT, Profile.model(0, 14, -12.6, -1.45));
        // The head and neck are tipped 30 degrees forward on top of the pitch; renderers scale the horse up and the
        // donkey and mule down
        PROFILES.put(EntityTypes.HORSE, Profile.model(-12, 4, -9.56, -2.56, 1.1D));
        PROFILES.put(EntityTypes.DONKEY, Profile.model(-12, 4, -9.56, -2.56, 0.87D));
        PROFILES.put(EntityTypes.MULE, Profile.model(-12, 4, -9.56, -2.56, 0.92D));
        // Long necks: the head is up at the top
        var llama = Profile.model(-6, 7, -10, -11);
        PROFILES.put(EntityTypes.LLAMA, llama);
        PROFILES.put(EntityTypes.TRADER_LLAMA, llama);
        PROFILES.put(EntityTypes.CAMEL, Profile.model(-10, 1, -21, -17));
        PROFILES.put(EntityTypes.WOLF, Profile.model(-7, 13.5, -5, 2));
        PROFILES.put(EntityTypes.FOX, Profile.model(-3, 16.5, -8, 3));
        PROFILES.put(EntityTypes.CAT, Profile.model(-9, 15, -4, 1, 0.8D));
        PROFILES.put(EntityTypes.OCELOT, Profile.model(-9, 15, -4, 1));
        PROFILES.put(EntityTypes.POLAR_BEAR, Profile.model(-16, 10, -6, 2.5, 1.2D));
        PROFILES.put(EntityTypes.PANDA, Profile.model(-17, 11.5, -6, 3));
        // The nose is a plate out in front of the head, at the top
        PROFILES.put(EntityTypes.SNIFFER, Profile.model(-19.48, 11.5, -20.5, -5.5));
        // The snout hangs down, tipped back a little
        PROFILES.put(EntityTypes.ARMADILLO, Profile.model(-7, 19, -1.53, 3.7));
        // Held 50 degrees down whatever it looks at
        PROFILES.put(EntityTypes.HOGLIN, Profile.model(-12, 2, -12.2, 14.55, 1D, false));
        PROFILES.put(EntityTypes.RAVAGER, Profile.model(-11.5, 9, -14, -0.5));
    }

    private Muzzle() {
    }

    /**
     * Where the entity's mouth is now.
     */
    public static Vec3 of(LivingEntity entity) {
        var profile = PROFILES.get(entity.getType());
        double scale;
        if (profile != null) {
            // The models are made for a grown mob at the default scale
            scale = entity.getScale() * entity.getAgeScale();
        } else {
            profile = guess(entity.getBbWidth(), entity.getEyeHeight());
            // Measured on the entity as it is
            scale = 1D;
        }
        return position(entity.getX(), entity.getY(), entity.getZ(), entity.yBodyRot, entity.getYHeadRot(),
                entity.getXRot(), profile, scale);
    }

    /**
     * A mob nobody measured: the head at the front of its box at eye height, the mouth a little in front of it.
     */
    static Profile guess(double width, double eyeHeight) {
        return new Profile(width * 0.5D, eyeHeight, width * 0.25D, -0.1D, true);
    }

    /**
     * The mouth, from the feet's position, the body's and head's yaw and the head's pitch (degrees, as the game
     * keeps them: pitch above zero looks down).
     */
    static Vec3 position(double x, double y, double z, float bodyYaw, float headYaw, float pitch, Profile profile,
                         double scale) {
        double body = Math.toRadians(bodyYaw);
        double head = Math.toRadians(headYaw);
        double tip = profile.followsPitch() ? Math.toRadians(pitch) : 0D;

        // Tip the mouth about the pivot: looking down takes it forward and down
        double forward = profile.mouthUp() * Math.sin(tip) + profile.mouthForward() * Math.cos(tip);
        double up = profile.mouthUp() * Math.cos(tip) - profile.mouthForward() * Math.sin(tip);

        double neck = profile.neckForward() * scale;
        forward *= scale;
        return new Vec3(
                x - Math.sin(body) * neck - Math.sin(head) * forward,
                y + (profile.neckHeight() + up) * scale,
                z + Math.cos(body) * neck + Math.cos(head) * forward);
    }
}
