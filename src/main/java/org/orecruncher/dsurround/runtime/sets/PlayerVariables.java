package org.orecruncher.dsurround.runtime.sets;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.scripting.IVariableAccess;
import org.orecruncher.dsurround.lib.scripting.VariableSet;
import org.orecruncher.dsurround.lib.world.WorldUtils;

public class PlayerVariables extends VariableSet<IPlayerVariables> implements IPlayerVariables {

    private boolean isSuffocating;
    private boolean canSeeSky;
    private boolean canRainOn;
    private boolean isCreative;
    private boolean isBurning;
    private boolean isFlying;
    private boolean isSprintnig;
    private boolean isInLava;
    private boolean isInvisible;
    private boolean isInWater;
    private boolean isWet;
    private boolean isRiding;
    private boolean isOnGround;
    private boolean isMoving;
    private float health;
    private float maxHealth;
    private float foodLevel;
    private float foodSaturationLevel;
    private double x;
    private double y;
    private double z;

    public PlayerVariables() {
        super("player");
    }

    @Override
    public void update(IVariableAccess variableAccess) {

        if (GameUtils.isInGame()) {
            final var player = GameUtils.getPlayer().orElseThrow();

            var hm = player.getFoodData();
            var world = player.level();

            this.isCreative = player.isCreative();
            this.isBurning = player.isOnFire();
            this.isFlying = player.getAbilities().flying;
            this.isSprintnig = player.isSprinting();
            this.isInLava = player.isInLava();
            this.isInvisible = player.isInvisible();
            this.isInWater = player.isUnderWater();
            this.isWet = player.isInWaterOrRain();
            this.isRiding = player.isPassenger();
            this.isOnGround = player.onGround();
            this.isMoving = player.bob != player.oBob;
            this.health = player.getHealth();
            this.maxHealth = player.getMaxHealth();
            this.foodLevel = hm.getFoodLevel();
            this.foodSaturationLevel = hm.getSaturationLevel();
            this.x = player.getX();
            this.y = player.getY();
            this.z = player.getZ();

            this.isSuffocating = !player.isCreative() && player.getAirSupply() < 0;
            this.canRainOn = world.canSeeSky(player.blockPosition().offset(0, 2, 0));
            this.canSeeSky = this.canRainOn && WorldUtils.getTopSolidOrLiquidBlock(world, player.blockPosition()).getY() <= player.blockPosition().getY();

        } else {

            this.isCreative = false;
            this.isBurning = false;
            this.isFlying = false;
            this.isSprintnig = false;
            this.isInLava = false;
            this.isInvisible = false;
            this.isInWater = false;
            this.isWet = false;
            this.isRiding = false;
            this.isOnGround = false;
            this.health = 20F;
            this.maxHealth = 20F;
            this.foodLevel = 20F;
            this.foodSaturationLevel = 20F;
            this.x = 0;
            this.y = 0;
            this.z = 0;

            this.isSuffocating = false;
            this.canRainOn = false;
            this.canSeeSky = false;
        }
    }

    @Override
    public IPlayerVariables getInterface() {
        return this;
    }

    @Override
    public boolean isCreative() {
        return this.isCreative;
    }

    @Override
    public boolean isBurning() {
        return this.isBurning;
    }

    @Override
    public boolean isSuffocating() {
        return this.isSuffocating;
    }

    @Override
    public boolean isFlying() {
        return this.isFlying;
    }

    @Override
    public boolean isSprintnig() {
        return this.isSprintnig;
    }

    @Override
    public boolean isInLava() {
        return this.isInLava;
    }

    @Override
    public boolean isInvisible() {
        return this.isInvisible;
    }

    @Override
    public boolean isInWater() {
        return this.isInWater;
    }

    @Override
    public boolean isMoving() {
        return this.isMoving;
    }

    @Override
    public boolean isWet() {
        return this.isWet;
    }

    @Override
    public boolean isRiding() {
        return this.isRiding;
    }

    @Override
    public boolean isOnGround() {
        return this.isOnGround;
    }

    @Override
    public boolean canRainOn() {
        return this.canRainOn;
    }

    @Override
    public boolean canSeeSky() {
        return this.canSeeSky;
    }

    @Override
    public float getHealth() {
        return this.health;
    }

    @Override
    public float getMaxHealth() {
        return this.maxHealth;
    }

    @Override
    public float getFoodLevel() {
        return this.foodLevel;
    }

    @Override
    public float getFoodSaturationLevel() {
        return this.foodSaturationLevel;
    }

    @Override
    public double getX() {
        return this.x;
    }

    @Override
    public double getY() {
        return this.y;
    }

    @Override
    public double getZ() {
        return this.z;
    }

    @Override
    public boolean hasEffect(String effect) {
        try {
            var id = new ResourceLocation(effect);
            var r = GameUtils.getRegistryEntry(Registries.MOB_EFFECT, id).orElseThrow();
            return GameUtils.getPlayer().map(p -> p.hasEffect(r.value())).orElse(false);
        } catch (Throwable ignore) {
        }

        return false;
    }
}