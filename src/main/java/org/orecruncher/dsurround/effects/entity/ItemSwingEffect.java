package org.orecruncher.dsurround.effects.entity;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.HitResult;
import org.orecruncher.dsurround.config.libraries.IItemLibrary;

public class ItemSwingEffect extends EntityEffectBase {

    private final IItemLibrary itemLibrary;
    private boolean isSwinging;

    public ItemSwingEffect(IItemLibrary itemLibrary) {
        this.itemLibrary = itemLibrary;
    }

    @Override
    public void tick(final EntityEffectInfo info) {
        if (info.isRemoved())
            return;

        final LivingEntity entity = info.getEntity();

        // Boats are strange - ignore them for now
        if (entity.getVehicle() instanceof Boat)
            return;

        // Don't use entity.isBlocking() - it has a 5 tick delay which would cause the
        // animation and the sound play to be out of sync.
        var isTriggered = entity.getAttackAnim(1F) > 0 || looksToBeBlocking(entity);

        if (isTriggered) {
            if (!this.isSwinging) {
                ItemStack currentItem;
                if (entity.swinging)
                    currentItem = entity.getItemInHand(InteractionHand.MAIN_HAND);
                else
                    currentItem = entity.getUseItem();

                var factory = this.itemLibrary.getItemSwingSound(currentItem);

                if (factory.isPresent() && freeSwing(entity)) {
                    SoundInstance instance;
                    if (info.isCurrentPlayer(entity)) {
                        instance = factory.get().createAsAdditional();
                    } else {
                        instance = factory.get().createAtEntity(entity);
                    }

                    if (instance != null)
                        this.playSound(instance);
                }

                this.isSwinging = true;
            }
        } else
            this.isSwinging = false;
    }

    protected static boolean looksToBeBlocking(LivingEntity entity) {
        if (!entity.isUsingItem() || entity.getUseItem().isEmpty()) {
            return false;
        }
        Item item = entity.getUseItem().getItem();
        return item.getUseAnimation(entity.getUseItem()) == UseAnim.BLOCK;
    }

    protected static boolean freeSwing(LivingEntity entity) {
        var result = rayTraceBlock(entity);
        return result.getType() == HitResult.Type.MISS;
    }

    protected static double getReach(final LivingEntity entity) {
        if (entity instanceof LocalPlayer p)
            return p.isCreative() ? 5D : 3D;

        var dist = entity.getBbWidth();
        dist *= 2;
        dist *= dist;
        dist += entity.getBbWidth();
        return dist;
    }

    protected static HitResult rayTraceBlock(final LivingEntity entity) {
        double range = getReach(entity);
        return entity.pick(range, 1F, true);
    }
}
