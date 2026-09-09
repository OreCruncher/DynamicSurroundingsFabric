package org.orecruncher.dsurround.runtime.variables;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.registry.RegistryUtils;
import org.orecruncher.dsurround.lib.scripting.IVariableAccess;
import org.orecruncher.dsurround.lib.scripting.VariableSet;
import org.orecruncher.dsurround.lib.compat.LevelCompat;
import org.orecruncher.dsurround.lib.scripting.IConfigureDefinition;

public class PlayerVariables extends VariableSet {

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
    public void update(IVariableAccess variableAccess) {

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
        config.defineFunction(id("isCreative"), 0, l -> this.isCreative);
        config.defineFunction(id("isBurning"), 0, l -> this.isBurning);
        config.defineFunction(id("isSuffocating"), 0, l -> this.isSuffocating);
        config.defineFunction(id("isFlying"), 0, l -> this.isFlying);
        config.defineFunction(id("isSprinting"), 0, l -> this.isSprinting);
        config.defineFunction(id("isInLava"), 0, l -> this.isInLava);
        config.defineFunction(id("isInvisible"), 0, l -> this.isInvisible);
        config.defineFunction(id("isInWater"), 0, l -> this.isInWater);
        config.defineFunction(id("isMoving"), 0, l -> this.isMoving);
        config.defineFunction(id("isWet"), 0, l -> this.isWet);
        config.defineFunction(id("isRiding"), 0, l -> this.isRiding);
        config.defineFunction(id("isOnGround"), 0, l -> this.isOnGround);
        config.defineFunction(id("canRainOn"), 0, l -> this.canRainOn);
        config.defineFunction(id("canSeeSky"), 0, l -> this.canSeeSky);
        config.defineFunction(id("getHealth"), 0, l -> this.health);
        config.defineFunction(id("getMaxHealth"), 0, l -> this.maxHealth);
        config.defineFunction(id("getFoodLevel"), 0, l -> this.foodLevel);
        config.defineFunction(id("getFoodSaturationLevel"), 0, l -> this.foodSaturationLevel);
        config.defineFunction(id("getX"), 0, l -> this.x);
        config.defineFunction(id("getY"), 0, l -> this.y);
        config.defineFunction(id("getZ"), 0, l -> this.z);
        config.defineFunction(id("hasEffect"), 1, l -> this.hasEffect(l[0].toString()));
    }

    private boolean hasEffect(String effect) {
        try {
            var id = ResourceLocation.parse(effect);
            var r = RegistryUtils.getRegistryEntry(Registries.MOB_EFFECT, id).orElseThrow();
            return GameUtils.getPlayer().map(p -> p.hasEffect(r)).orElse(false);
        } catch (Throwable ignore) {
        }

        return false;
    }
}