package org.orecruncher.dsurround.effects.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.sound.SoundFactory;

@Environment(EnvType.CLIENT)
public class BowUseEffect extends EntityEffectBase {

    private static final SoundEvent BOW_PULL_SOUNDEVENT = new SoundEvent(new Identifier(Client.ModId, "bow.pull"));
    private static final SoundFactory BOW_PULL_SOUND = new SoundFactory(BOW_PULL_SOUNDEVENT);

    protected ItemStack lastActiveStack = ItemStack.EMPTY;

    @Override
    public void tick(EntityEffectInfo info) {
        var entityResult = info.getEntity();

        if (entityResult.isPresent()) {
            var entity = entityResult.get();
            final ItemStack currentStack = entity.getActiveItem();
            if (isApplicable(currentStack)) {
                if (!ItemStack.areEqual(currentStack, this.lastActiveStack)) {
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
        return !stack.isEmpty() && stack.getItem() instanceof BowItem;
    }

}