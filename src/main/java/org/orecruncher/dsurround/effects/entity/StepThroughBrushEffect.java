package org.orecruncher.dsurround.effects.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.system.ITickCount;
import org.orecruncher.dsurround.sound.ISoundFactory;
import org.orecruncher.dsurround.sound.SoundFactoryBuilder;
import org.orecruncher.dsurround.tags.BlockEffectTags;
import org.orecruncher.dsurround.mixinutils.ILivingEntityExtended;

public class StepThroughBrushEffect extends EntityEffectBase {

    private static final long BRUSH_INTERVAL = 2;
    private static final ISoundFactory BRUSH_SOUND = SoundFactoryBuilder
            .create(new ResourceLocation(Constants.MOD_ID, "footsteps.brush_through"))
            .category(SoundSource.PLAYERS).volume(0.3F).pitchRange(0.8F, 1.2F).build();

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
            var entity = info.getEntity().orElseThrow();
            if (this.shouldProcess(entity)) {

                var world = entity.level();
                var pos = entity.blockPosition();
                var feetPos = BlockPos.containing(pos.getX(), pos.getY() + 0.25D, pos.getZ());

                var block = world.getBlockState(feetPos).getBlock();
                if (this.tagLibrary.isIn(BlockEffectTags.BRUSH_STEP, block)) {
                    this.playBrushSound(feetPos);
                } else {
                    var headPos = feetPos.above();
                    block = world.getBlockState(headPos).getBlock();
                    if (this.tagLibrary.isIn(BlockEffectTags.BRUSH_STEP, block))
                        this.playBrushSound(headPos);
                }
            }
        }
    }

    private boolean shouldProcess(LivingEntity entity) {
        if (entity.isSilent() || entity.isSpectator())
            return false;
        if (entity.xxa != 0 || entity.zza != 0 || entity.yya != 0)
            return true;
        return ((ILivingEntityExtended)entity).dsurround_isJumping();
    }

    private void playBrushSound(BlockPos pos) {
        var soundInstance = BRUSH_SOUND.createAtLocation(pos);
        this.playSound(soundInstance);
    }
}
