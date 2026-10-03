package org.orecruncher.dsurround.runtime.variables;

import org.orecruncher.dsurround.lib.math.Motion;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.registry.RegistryUtils;
import org.orecruncher.dsurround.lib.scripting.ArgType;
import org.orecruncher.dsurround.lib.scripting.VariableSet;
import org.orecruncher.dsurround.lib.compat.LevelCompat;
import org.orecruncher.dsurround.lib.scripting.IConfigureDefinition;

public final class PlayerVariables extends VariableSet {

    /**
     * A registry id such as "minecraft:night_vision". Constant ids are parsed when the script is compiled, so a
     * malformed id is reported as an error instead of failing (and being ignored) on every evaluation.
     */
    private static final ArgType<ResourceLocation> RESOURCE_ID = ArgType.of("resource id", PlayerVariables::toResourceId);

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
    // Numeric values are stored boxed when updated each tick, so that reading them from scripts does not allocate
    private Float health = 0F;
    private Float maxHealth = 0F;
    private Float foodLevel = 0F;
    private Float foodSaturationLevel = 0F;
    private Double x = 0D;
    private Double y = 0D;
    private Double z = 0D;

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
            // Ticked at the start of the client tick, so this is the move made during the last tick
            this.isMoving = Motion.isMovingHorizontally(player.getX() - player.xo, player.getZ() - player.zo);
            this.health = player.getHealth();
            this.maxHealth = player.getMaxHealth();
            this.foodLevel = (float) hm.getFoodLevel();
            this.foodSaturationLevel = hm.getSaturationLevel();
            this.x = player.getX();
            this.y = player.getY();
            this.z = player.getZ();

            this.isSuffocating = isSuffocating(player.isCreative(), player.getAirSupply());
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
            this.isMoving = false;
            this.health = 20F;
            this.maxHealth = 20F;
            this.foodLevel = 20F;
            this.foodSaturationLevel = 20F;
            this.x = 0D;
            this.y = 0D;
            this.z = 0D;

            this.isSuffocating = false;
            this.canRainOn = false;
            this.canSeeSky = false;
        }
    }

    @Override
    public void configure(IConfigureDefinition config) {
        config.property(id("isCreative"), () -> this.isCreative);
        config.property(id("isBurning"), () -> this.isBurning);
        config.property(id("isSuffocating"), () -> this.isSuffocating);
        config.property(id("isFlying"), () -> this.isFlying);
        config.property(id("isSprinting"), () -> this.isSprinting);
        config.property(id("isInLava"), () -> this.isInLava);
        config.property(id("isInvisible"), () -> this.isInvisible);
        // Eyes under water, not feet in water: player.isUnderWater(). Kept as it is for existing scripts.
        config.property(id("isInWater"), () -> this.isInWater);
        config.property(id("isMoving"), () -> this.isMoving);
        config.property(id("isWet"), () -> this.isWet);
        config.property(id("isRiding"), () -> this.isRiding);
        config.property(id("isOnGround"), () -> this.isOnGround);
        config.property(id("canRainOn"), () -> this.canRainOn);
        config.property(id("canSeeSky"), () -> this.canSeeSky);
        config.property(id("getHealth"), () -> this.health);
        config.property(id("getMaxHealth"), () -> this.maxHealth);
        config.property(id("getFoodLevel"), () -> this.foodLevel);
        config.property(id("getFoodSaturationLevel"), () -> this.foodSaturationLevel);
        config.property(id("getX"), () -> this.x);
        config.property(id("getY"), () -> this.y);
        config.property(id("getZ"), () -> this.z);
        config.function(id("hasEffect"))
                .param(RESOURCE_ID)
                .handler(args -> this.hasEffect(args.get(0)));
    }

    /**
     * Out of air. Air runs down to -20 before drowning damage resets it to 0, so both count; checking only below
     * zero dropped out for a tick each cycle.
     */
    static boolean isSuffocating(boolean isCreative, int airSupply) {
        return !isCreative && airSupply <= 0;
    }

    private static ResourceLocation toResourceId(Object value) {
        if (value instanceof ResourceLocation id)
            return id;
        if (!(value instanceof String text))
            return null;
        var id = ResourceLocation.tryParse(text);
        return id != null ? id : ArgType.reject("invalid resource id '%s'".formatted(text));
    }

    private boolean hasEffect(ResourceLocation effect) {
        // An id that is well-formed but not registered (such as an effect from a mod that is not installed) is
        // not an error: the player simply does not have it.
        var player = GameUtils.getPlayer();
        if (player.isEmpty())
            return false;
        return RegistryUtils.getRegistryEntry(Registries.MOB_EFFECT, effect)
                .map(r -> player.get().hasEffect(r))
                .orElse(false);
    }
}