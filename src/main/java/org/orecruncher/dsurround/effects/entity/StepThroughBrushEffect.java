package org.orecruncher.dsurround.effects.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.system.ITickCount;
import org.orecruncher.dsurround.sound.ISoundFactory;
import org.orecruncher.dsurround.sound.SoundFactoryBuilder;
import org.orecruncher.dsurround.tags.BlockEffectTags;
import org.orecruncher.dsurround.mixinutils.ILivingEntityExtended;

public class StepThroughBrushEffect extends EntityEffectBase {

    private static final long BRUSH_INTERVAL = 2;
    private static final ResourceLocation BRUSH_SOUND = new ResourceLocation(Constants.MOD_ID, "brush_step/brush");
    private static final ResourceLocation STRAW_SOUND = new ResourceLocation(Constants.MOD_ID, "brush_step/straw");

    private final ITickCount tickCount;
    private final ITagLibrary tagLibrary;
    private long lastBrushCheck;

    public StepThroughBrushEffect(ITickCount tickCount, ITagLibrary tagLibrary) {
        this.tickCount = tickCount;
        this.tagLibrary = tagLibrary;
    }

    @Override
    public void tick(final EntityEffectInfo info) {
        var currentCount = this.tickCount.getTickCount();
        if (currentCount > this.lastBrushCheck) {
            this.lastBrushCheck = currentCount + BRUSH_INTERVAL;
            if (info.isRemoved())
                return;
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

    private boolean process(TagKey<Block> effectTag, ResourceLocation factory, Level world, BlockPos blockPos) {
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
        if (entity.xxa != 0 || entity.zza != 0 || entity.yya != 0)
            return true;
        return ((ILivingEntityExtended)entity).dsurround_isJumping();
    }

    private void playSoundEffect(BlockPos pos, ResourceLocation factory, float volumeScale) {
       SOUND_LIBRARY.getSoundFactory(factory)
               .ifPresent(f -> {
                   var soundInstance = f.createAtLocation(pos, volumeScale);
                   this.playSound(soundInstance);
               });
    }
}
