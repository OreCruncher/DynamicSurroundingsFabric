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
            .pitch(0.9F, 1.1F)
            .build();

    protected ItemStack lastActiveStack = ItemStack.EMPTY;

    @Override
    public void tick(EntityEffectInfo info) {
        if (info.isRemoved()) {
            this.lastActiveStack = ItemStack.EMPTY;
            return;
        }

        var entity = info.getEntity();
        final ItemStack currentStack = entity.getUseItem();
        if (isApplicable(currentStack)) {
            if (!ItemStack.matches(currentStack, this.lastActiveStack)) {
                var sound = BOW_PULL_SOUND.attachToEntity(entity);
                this.playSound(sound);
                this.lastActiveStack = currentStack;
            }
        } else {
            this.lastActiveStack = ItemStack.EMPTY;
        }
    }

    private static boolean isApplicable(ItemStack stack) {
        return TAG_LIBRARY.is(ItemEffectTags.BOWS, stack);
    }
}