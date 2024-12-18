package org.orecruncher.dsurround.runtime.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.orecruncher.dsurround.lib.GameUtils;

public final class WorldContext {

    /**
     * Quick Minecraft reference
     */
    public final Minecraft mc;
    /**
     * Reference to the client side PlayerEntity
     */
    public final Player player;
    /**
     * Reference to the player's world
     */
    public final Level world;
    /**
     * Position of the player.
     */
    public final Vec3 playerPosition;
    /**
     * Position of the player's eyes.
     */
    public final Vec3 playerEyePosition;
    /**
     * Block position of the player.
     */
    public final BlockPos playerPos;
    /**
     * Block position of the player's eyes.
     */
    public final BlockPos playerEyePos;
    /**
     * Flag indicating if it is precipitating
     */
    public final boolean isPrecipitating;
    /**
     * Current strength of precipitation.
     */
    public final float precipitationStrength;
    /**
     * Coefficient used for dampening sound.  Usually caused by the player's head being in lava or water.
     */
    public final float auralDampening;

    public WorldContext() {
        if (GameUtils.isInGame()) {
            this.world = GameUtils.getWorld().orElseThrow();
            this.player = GameUtils.getPlayer().orElseThrow();
            this.isPrecipitating = this.world.isRaining();
            this.playerPosition = this.player.getPosition(1F);
            this.playerEyePosition = this.player.getEyePosition();
            this.playerPos = BlockPos.containing(this.playerPosition);
            this.playerEyePos = BlockPos.containing(this.playerEyePosition);

            if (this.player.isUnderWater())
                this.auralDampening = 0.6F;
            else
                this.auralDampening = 0;

            // Get our current rain strength.
            this.precipitationStrength = this.world.getRainLevel(1F);
            this.mc = GameUtils.getMC();
        } else {
            this.mc = null;
            this.player = null;
            this.world = null;
            this.isPrecipitating = false;
            this.playerPosition = Vec3.ZERO;
            this.playerEyePosition = Vec3.ZERO;
            this.playerPos = BlockPos.ZERO;
            this.playerEyePos = BlockPos.ZERO;
            this.auralDampening = 0;
            this.precipitationStrength = 0F;
        }
    }

    public boolean isNotValid() {
        return this.mc == null;
    }

}