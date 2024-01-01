package org.orecruncher.dsurround.effects.entity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.sound.ISoundFactory;
import org.orecruncher.dsurround.sound.SoundFactoryBuilder;
import org.orecruncher.dsurround.tags.ItemEffectTags;

public class BowUseEffect extends EntityEffectBase {

    private static final SoundEvent BOW_PULL_SOUNDEVENT = SoundEvent.createVariableRangeEvent(new ResourceLocation(Constants.MOD_ID, "item.bow.pull"));
    private static final ISoundFactory BOW_PULL_SOUND = SoundFactoryBuilder
            .create(BOW_PULL_SOUNDEVENT)
            .pitchRange(0.9F, 1.1F)
            .build();

    protected ItemStack lastActiveStack = ItemStack.EMPTY;

    @Override
    public void tick(EntityEffectInfo info) {
        var entityResult = info.getEntity();

        if (entityResult.isPresent()) {
            var entity = entityResult.get();
            final ItemStack currentStack = entity.getUseItem();
            if (isApplicable(currentStack)) {
                if (!ItemStack.matches(currentStack, this.lastActiveStack)) {
                    if (isApplicable(currentStack)) {
                        var sound = BOW_PULL_SOUND.createAtEntity(entity);
                        this.playSound(sound);
                    }

                    this.lastActiveStack = currentStack;
                }
            } else {
                this.lastActiveStack = ItemStack.EMPTY;
            }
        } else {
            this.lastActiveStack = ItemStack.EMPTY;
        }
    }

    private static boolean isApplicable(ItemStack stack) {
        return TAG_LIBRARY.isIn(ItemEffectTags.BOWS, stack.getItem());
    }
}