package org.orecruncher.dsurround.runtime.sets;

@SuppressWarnings("unused")
public interface IPlayerVariables {

    boolean isCreative();

    boolean isBurning();

    boolean isSuffocating();

    boolean isFlying();

    boolean isSprinting();

    boolean isInLava();

    boolean isInvisible();

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

    boolean hasEffect(String effect);

}