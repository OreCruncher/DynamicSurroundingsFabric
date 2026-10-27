package org.orecruncher.dsurround.effects.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.panda.Panda;
import net.minecraft.world.entity.animal.polarbear.PolarBear;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.phys.Vec3;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;
import org.orecruncher.dsurround.lib.system.ITickCount;

/**
 * Breath for four-legged mobs: from the mouth out in front of the body ({@link Muzzle}), not the eyes. A pen of
 * sheep is many more breaths than a player and a few villagers, so animals breathe out frost every other tick and
 * only near the player.
 */
public class AnimalBreathEffect extends BreathEffect {

    // How near the player an animal's breath shows, in blocks
    static final int RANGE = 16;
    private static final double RANGE_SQ = RANGE * RANGE;
    // A grown player's box, width by height, which a puff of the player's size goes with
    private static final double PLAYER_AREA = 0.6D * 1.8D;

    public AnimalBreathEffect(ITickCount tickCount, ISeasonalInformation seasonalInformation) {
        super(tickCount, seasonalInformation);
    }

    @Override
    protected boolean canBreathe(final LivingEntity entity) {
        var player = GameUtils.getPlayer().orElse(null);
        if (player == null || entity.distanceToSqr(player) > RANGE_SQ)
            return false;
        return isHeadUp(entity);
    }

    /**
     * Whether the head is where {@link Muzzle} puts it. A sheep eating grass, a goat lowering its head to ram, a
     * horse rearing, a sleeping fox, a panda on its back: the model moves the head somewhere else, so they don't
     * breathe until they are done.
     */
    static boolean isHeadUp(final LivingEntity entity) {
        var pose = entity.getPose();
        if (pose != Pose.STANDING && pose != Pose.CROUCHING && pose != Pose.LONG_JUMPING)
            return false;
        if (entity.isSleeping())
            return false;
        return switch (entity) {
            case Sheep sheep -> sheep.getHeadEatPositionScale(1F) == 0F;
            case Goat goat -> goat.getRammingXHeadRot() == 0F;
            // A camel is a horse to the game, so before the horses
            case Camel camel -> !camel.isCamelVisuallySitting() && !camel.isInPoseTransition();
            case AbstractHorse horse -> !horse.isEating() && !horse.isStanding();
            case Cat cat -> !cat.isLying();
            case Fox fox -> !fox.isSleeping() && !fox.isFaceplanted();
            case PolarBear bear -> !bear.isStanding();
            case Panda panda -> !panda.isSitting() && !panda.isOnBack() && !panda.isRolling() && !panda.isEating();
            case Armadillo armadillo -> !armadillo.shouldHideInShell();
            default -> true;
        };
    }

    @Override
    protected boolean isFrostTick(int c) {
        return (c & 1) == 0;
    }

    @Override
    protected Vec3 getMouth(final LivingEntity entity) {
        return Muzzle.of(entity);
    }

    @Override
    protected BlockPos getHeadPosition(final LivingEntity entity, final Vec3 mouth) {
        return BlockPos.containing(mouth);
    }

    /**
     * Three times a player's: a puff that stays at the mouth is lost against the head, so it is blown out clear of
     * it, three quarters of a block
     */
    @Override
    protected double getPuffSpeed() {
        return 0.03D;
    }

    @Override
    protected float getPuffSize(final LivingEntity entity) {
        return puffSize(entity.getBbWidth(), entity.getBbHeight());
    }

    /**
     * A player's puff, scaled by how big the mob is next to a player: a cat's smaller, a cow's a little bigger, a
     * ravager's twice the size. A baby's box is already smaller.
     */
    static float puffSize(double width, double height) {
        return 0.25F * (float) Mth.clamp(Math.sqrt(width * height / PLAYER_AREA), 0.5D, 2D);
    }
}
