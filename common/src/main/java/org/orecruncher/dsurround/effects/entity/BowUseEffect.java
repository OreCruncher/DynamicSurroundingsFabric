package org.orecruncher.dsurround.effects.entity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.tags.ItemEffectTags;

public class BowUseEffect extends EntityEffectBase {

    private static final ResourceLocation BOW_PULL_FACTORY = Constants.asId("bow_pull");

    private final ISoundLibrary soundLibrary;
    private final ITagLibrary tagLibrary;
    protected ItemStack lastActiveStack = ItemStack.EMPTY;

    public BowUseEffect(ISoundLibrary soundLibrary, ITagLibrary tagLibrary) {
        this.soundLibrary = soundLibrary;
        this.tagLibrary = tagLibrary;
    }

    @Override
    public void tick(EntityEffectInfo info) {
        var entity = info.getEntity();
        final ItemStack currentStack = entity.getUseItem();
        if (isApplicable(currentStack)) {
            if (!ItemStack.matches(currentStack, this.lastActiveStack)) {
                this.soundLibrary.getSoundFactory(BOW_PULL_FACTORY)
                        .ifPresent(f -> {
                            var sound = f.attachToEntity(entity);
                            this.playSound(sound);
                        });
                this.lastActiveStack = currentStack;
            }
        } else {
            this.lastActiveStack = ItemStack.EMPTY;
        }
    }

    private boolean isApplicable(ItemStack stack) {
        return this.tagLibrary.is(ItemEffectTags.BOWS, stack);
    }
}