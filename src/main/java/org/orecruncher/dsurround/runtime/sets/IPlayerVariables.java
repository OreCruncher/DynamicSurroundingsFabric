package org.orecruncher.dsurround.runtime.sets;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public interface IPlayerVariables {

    boolean isCreative();

    boolean isBurning();

    boolean isSuffocating();

    boolean isFlying();

    boolean isSprintnig();

    boolean isInLava();

    boolean isInvisible();

    boolean isBlind();

    boolean isInWater();

    boolean isMoving();

    boolean isWet();

    boolean isRiding();

    boolean isOnGround();

    boolean canRainOn();

    boolean canSeeSky();

    float getHealth();

    float getMaxHealth();

    float getFoodLevel();

    float getFoodSaturationLevel();

    double getX();

    double getY();

    double getZ();

    default boolean isHurt() {
        return false;
    }

    default boolean isHungry() {
        return false;
    }

}