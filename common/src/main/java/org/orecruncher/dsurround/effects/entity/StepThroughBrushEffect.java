package org.orecruncher.dsurround.effects.entity;

import org.orecruncher.dsurround.lib.math.Motion;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.system.ITickCount;
import org.orecruncher.dsurround.tags.BlockEffectTags;

public class StepThroughBrushEffect extends EntityEffectBase {

    private static final long BRUSH_INTERVAL = 2;
    private static final Identifier BRUSH_SOUND = Constants.asId("brush_step/brush");
    private static final Identifier STRAW_SOUND = Constants.asId("brush_step/straw");

    private final ITickCount tickCount;
    private final ITagLibrary tagLibrary;
    private final ISoundLibrary soundLibrary;
    private long lastBrushCheck;

    public StepThroughBrushEffect(ITickCount tickCount, ITagLibrary tagLibrary, ISoundLibrary soundLibrary) {
        this.tickCount = tickCount;
        this.tagLibrary = tagLibrary;
        this.soundLibrary = soundLibrary;
    }

    @Override
    public void tick(final EntityEffectInfo info) {
        var currentCount = this.tickCount.getTickCount();
        if (currentCount > this.lastBrushCheck) {
            this.lastBrushCheck = currentCount + BRUSH_INTERVAL;
            var entity = info.getEntity();
            if (shouldProcess(entity)) {
                var world = entity.level();
                var pos = entity.blockPosition();
                var feetPos = BlockPos.containing(pos.getX(), pos.getY() + 0.25D, pos.getZ());

                if (!this.process(BlockEffectTags.STRAW_STEP, STRAW_SOUND, world, feetPos))
                    this.process(BlockEffectTags.BRUSH_STEP, BRUSH_SOUND, world, feetPos);
            }
        }
    }

    private boolean process(TagKey<Block> effectTag, Identifier factory, Level world, BlockPos blockPos) {
        var block = world.getBlockState(blockPos);
        if (this.tagLibrary.is(effectTag, block)) {
            this.playSoundEffect(blockPos, factory, getVolumeScaling(world, blockPos, block));
            return true;
        } else {
            var headPos = blockPos.above();
            block = world.getBlockState(headPos);
            if (this.tagLibrary.is(effectTag, block)) {
                this.playSoundEffect(headPos, factory, getVolumeScaling(world, headPos, block));
                return true;
            }
        }

        return false;
    }

    private static float getVolumeScaling(Level world, BlockPos pos, BlockState state) {
        final VoxelShape shape = state.getShape(world, pos);
        return shape.isEmpty() ? 1F : (float) shape.bounds().maxY;
    }

    private static boolean shouldProcess(LivingEntity entity) {
        if (entity.isSilent() || entity.isSpectator())
            return false;
        return isMoving(entity.getX() - entity.xo, entity.getZ() - entity.zo, entity.jumping);
    }

    /**
     * Whether the entity moved this tick. Uses how far it actually moved rather than movement input: input is only
     * known for the local player, so other players would never make a sound.
     */
    static boolean isMoving(double dx, double dz, boolean jumping) {
        return jumping || Motion.isMovingHorizontally(dx, dz);
    }

    private void playSoundEffect(BlockPos pos, Identifier factory, float volumeScale) {
       this.soundLibrary.getSoundFactory(factory)
               .ifPresent(f -> {
                   var soundInstance = f.createAtLocation(pos, volumeScale);
                   this.playSound(soundInstance);
               });
    }
}
