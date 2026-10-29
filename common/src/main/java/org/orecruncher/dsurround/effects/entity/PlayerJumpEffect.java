package org.orecruncher.dsurround.effects.entity;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;

/**
 * The player's breath as they jump, heard by the player only: when they leave the ground under their own power, with
 * the jump key down and moving up, so stepping off an edge or climbing a stair isn't a jump. Not while sneaking, in
 * water, riding, asleep, flying, or as a spectator. As 1.12.2's footstep generator played its "jump breath".
 */
public class PlayerJumpEffect extends EntityEffectBase {

    private static final Identifier JUMP_FACTORY = Constants.asId("player_jump");

    private final ISoundLibrary soundLibrary;
    // Whether the player was off the ground last tick: true to start, so arriving in the air isn't taken for a jump
    private boolean wasAirborne = true;

    public PlayerJumpEffect(ISoundLibrary soundLibrary) {
        this.soundLibrary = soundLibrary;
    }

    @Override
    public void tick(EntityEffectInfo info) {
        if (!(info.getEntity() instanceof Player player) || !info.isCurrentPlayer(player))
            return;
        // On a ladder or a vine counts as on the ground, as 1.12.2 had it: climbing off one isn't a jump
        boolean airborne = !player.onGround() && !player.onClimbable();
        boolean jumped = jumped(this.wasAirborne, airborne, player.jumping, player.getDeltaMovement().y);
        this.wasAirborne = airborne;
        if (!jumped || cantJump(player))
            return;
        this.soundLibrary.getSoundFactory(JUMP_FACTORY).ifPresent(f -> this.playSound(f.attachToEntity(player)));
    }

    /**
     * Whether this tick is a jump: off the ground now and not last tick, with the jump key down and moving up. Climbing
     * a stair lifts the player with the key up; walking off an edge moves them down.
     */
    static boolean jumped(boolean wasAirborne, boolean airborne, boolean jumping, double motionY) {
        return !wasAirborne && airborne && jumping && motionY > 0D;
    }

    /**
     * Whether the player is doing something a jump breath doesn't go with
     */
    private static boolean cantJump(Player player) {
        return player.isShiftKeyDown() || player.isInWater() || player.isPassenger() || player.isSleeping()
                || player.isSpectator() || player.getAbilities().flying;
    }
}
