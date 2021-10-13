package org.orecruncher.dsurround.runtime.audio;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.orecruncher.dsurround.lib.GameUtils;

public final class WorldContext {

    /**
     * Quick Minecraft reference
     */
    public final MinecraftClient mc;
    /**
     * Reference to the client side PlayerEntity
     */
    public final PlayerEntity player;
    /**
     * Reference to the player's world
     */
    public final World world;
    /**
     * Position of the player.
     */
    public final Vec3d playerPosition;
    /**
     * Position of the player's eyes.
     */
    public final Vec3d playerEyePosition;
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
            final World w = GameUtils.getWorld();
            this.world = w;
            assert world != null;

            this.player = GameUtils.getPlayer();
            assert this.player != null;

            this.isPrecipitating = w.isRaining();
            this.playerPosition = this.player.getPos();
            this.playerEyePosition = this.player.getEyePos();
            this.playerPos = new BlockPos(this.playerPosition);
            this.playerEyePos = new BlockPos(this.playerEyePosition);

            this.auralDampening = 0;

            // Get our current rain strength.
            this.precipitationStrength = w.getRainGradient(1F);
            this.mc = GameUtils.getMC();
        } else {
            this.mc = null;
            this.player = null;
            this.world = null;
            this.isPrecipitating = false;
            this.playerPosition = Vec3d.ZERO;
            this.playerEyePosition = Vec3d.ZERO;
            this.playerPos = BlockPos.ORIGIN;
            this.playerEyePos = BlockPos.ORIGIN;
            this.auralDampening = 0;
            this.precipitationStrength = 0F;
        }
    }

    public boolean isNotValid() {
        return this.mc == null;
    }

}