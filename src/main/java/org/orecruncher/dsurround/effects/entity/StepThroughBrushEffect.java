package org.orecruncher.dsurround.effects.entity;

import net.minecraft.entity.LivingEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
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
            .create(new Identifier(Constants.MOD_ID, "footsteps.brush_through"))
            .category(SoundCategory.PLAYERS).volume(0.3F).pitchRange(0.8F, 1.2F).build();

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

                var world = entity.getEntityWorld();
                var pos = entity.getPos();
                var feetPos = BlockPos.ofFloored(pos.getX(), pos.getY() + 0.25D, pos.getZ());

                var block = world.getBlockState(feetPos).getBlock();
                if (this.tagLibrary.isIn(BlockEffectTags.BRUSH_STEP, block)) {
                    this.playBrushSound(feetPos);
                } else {
                    var headPos = feetPos.up();
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
        if (entity.sidewaysSpeed != 0 || entity.forwardSpeed != 0 || entity.upwardSpeed != 0)
            return true;
        return ((ILivingEntityExtended)entity).dsurround_isJumping();
    }

    private void playBrushSound(BlockPos pos) {
        var soundInstance = BRUSH_SOUND.createAtLocation(pos);
        this.playSound(soundInstance);
    }
}
