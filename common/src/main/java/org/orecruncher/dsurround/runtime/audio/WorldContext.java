package org.orecruncher.dsurround.runtime.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.block.NoBlockEntityView;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.function.SingletonSupplier;
import org.orecruncher.dsurround.runtime.weather.IWeatherSimulation;

public final class WorldContext {

    // Resolved on first use in game: the first context is made out of game, possibly before the container is ready
    private static final SingletonSupplier<IWeatherSimulation> WEATHER = SingletonSupplier.from(() -> ContainerManager.resolve(IWeatherSimulation.class));

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
     * The world's blocks, for the worker threads to trace through: never its block entities, which reading from
     * another thread can create (see {@link NoBlockEntityView})
     */
    public final BlockGetter blocks;
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
     * Current strength of precipitation: the storm's, not the world's.
     */
    public final float precipitationStrength;
    /**
     * What the player's head is in, which muffles what they hear.
     */
    public final Submersion submersion;

    public WorldContext() {
        if (GameUtils.isInGame()) {
            this.world = GameUtils.getWorld().orElseThrow();
            this.blocks = new NoBlockEntityView(this.world);
            this.player = GameUtils.getPlayer().orElseThrow();
            this.isPrecipitating = this.world.isRaining();
            this.playerPosition = this.player.getPosition(1F);
            this.playerEyePosition = this.player.getEyePosition();
            this.playerPos = BlockPos.containing(this.playerPosition);
            this.playerEyePos = BlockPos.containing(this.playerEyePosition);

            this.submersion = Submersion.of(this.player);

            this.precipitationStrength = WEATHER.get().rainLevel(this.world.getRainLevel(1F));
            this.mc = GameUtils.getMC();
        } else {
            this.mc = null;
            this.player = null;
            this.world = null;
            this.blocks = null;
            this.isPrecipitating = false;
            this.playerPosition = Vec3.ZERO;
            this.playerEyePosition = Vec3.ZERO;
            this.playerPos = BlockPos.ZERO;
            this.playerEyePos = BlockPos.ZERO;
            this.submersion = Submersion.NONE;
            this.precipitationStrength = 0F;
        }
    }

    public boolean isNotValid() {
        return this.mc == null;
    }

}