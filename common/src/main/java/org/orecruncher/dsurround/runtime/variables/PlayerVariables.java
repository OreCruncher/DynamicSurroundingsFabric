package org.orecruncher.dsurround.runtime.variables;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.registry.RegistryUtils;
import org.orecruncher.dsurround.lib.scripting.VariableSet;
import org.orecruncher.dsurround.lib.compat.LevelCompat;
import org.orecruncher.dsurround.lib.scripting.IConfigureDefinition;

public final class PlayerVariables extends VariableSet {

    private boolean isSuffocating;
    private boolean canSeeSky;
    private boolean canRainOn;
    private boolean isCreative;
    private boolean isBurning;
    private boolean isFlying;
    private boolean isSprinting;
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
    public void tick() {

        if (GameUtils.isInGame()) {
            final var player = GameUtils.getPlayer().orElseThrow();

            var hm = player.getFoodData();
            var world = player.level();

            this.isCreative = player.isCreative();
            this.isBurning = player.isOnFire();
            this.isFlying = player.getAbilities().flying;
            this.isSprinting = player.isSprinting();
            this.isInLava = player.isInLava();
            this.isInvisible = player.isInvisible();
            this.isInWater = player.isUnderWater();
            this.isWet = player.isInWaterOrRain();
            this.isRiding = player.isPassenger();
            this.isOnGround = player.onGround();
            this.isMoving = player.xo!= player.xOld;    // TODO: Need to figure out new moving calculation
            this.health = player.getHealth();
            this.maxHealth = player.getMaxHealth();
            this.foodLevel = hm.getFoodLevel();
            this.foodSaturationLevel = hm.getSaturationLevel();
            this.x = player.getX();
            this.y = player.getY();
            this.z = player.getZ();

            this.isSuffocating = !player.isCreative() && player.getAirSupply() < 0;
            this.canRainOn = world.canSeeSky(player.blockPosition().offset(0, 2, 0));
            this.canSeeSky = this.canRainOn && LevelCompat.getTopSolidOrLiquidBlock(world, player.blockPosition()).getY() <= player.blockPosition().getY();

        } else {

            this.isCreative = false;
            this.isBurning = false;
            this.isFlying = false;
            this.isSprinting = false;
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
    public void configure(IConfigureDefinition config) {
        config.defineFunction(id("isCreative"), l -> this.isCreative);
        config.defineFunction(id("isBurning"), l -> this.isBurning);
        config.defineFunction(id("isSuffocating"), l -> this.isSuffocating);
        config.defineFunction(id("isFlying"), l -> this.isFlying);
        config.defineFunction(id("isSprinting"), l -> this.isSprinting);
        config.defineFunction(id("isInLava"), l -> this.isInLava);
        config.defineFunction(id("isInvisible"), l -> this.isInvisible);
        config.defineFunction(id("isInWater"), l -> this.isInWater);
        config.defineFunction(id("isMoving"), l -> this.isMoving);
        config.defineFunction(id("isWet"), l -> this.isWet);
        config.defineFunction(id("isRiding"), l -> this.isRiding);
        config.defineFunction(id("isOnGround"), l -> this.isOnGround);
        config.defineFunction(id("canRainOn"), l -> this.canRainOn);
        config.defineFunction(id("canSeeSky"), l -> this.canSeeSky);
        config.defineFunction(id("getHealth"), l -> this.health);
        config.defineFunction(id("getMaxHealth"), l -> this.maxHealth);
        config.defineFunction(id("getFoodLevel"), l -> this.foodLevel);
        config.defineFunction(id("getFoodSaturationLevel"), l -> this.foodSaturationLevel);
        config.defineFunction(id("getX"), l -> this.x);
        config.defineFunction(id("getY"), l -> this.y);
        config.defineFunction(id("getZ"), l -> this.z);
        config.defineFunction(id("hasEffect"), 1, false,l -> this.hasEffect(l[0].toString()));
    }

    private boolean hasEffect(String effect) {
        try {
            var id = Identifier.parse(effect);
            var r = RegistryUtils.getRegistryEntry(Registries.MOB_EFFECT, id).orElseThrow();
            return GameUtils.getPlayer().map(p -> p.hasEffect(r)).orElse(false);
        } catch (Throwable ignore) {
        }

        return false;
    }
}