package org.orecruncher.dsurround.effects.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.UseAction;
import net.minecraft.util.hit.HitResult;
import org.orecruncher.dsurround.config.libraries.IItemLibrary;

@Environment(EnvType.CLIENT)
public class ItemSwingEffect extends EntityEffectBase {

    private final IItemLibrary itemLibrary;
    private boolean isSwinging;

    public ItemSwingEffect(IItemLibrary itemLibrary) {
        this.itemLibrary = itemLibrary;
    }

    @Override
    public void tick(final EntityEffectInfo info) {

        var optional = info.getEntity();
        if (optional.isEmpty())
            return;

        final LivingEntity entity = optional.get();

        // Boats are strange - ignore them for now
        if (entity.getVehicle() instanceof BoatEntity)
            return;

        // Don't use entity.isBlocking() - it has a 5 tick delay which would cause the
        // animation and the sound play to be out of sync.
        var isTriggered = entity.getHandSwingProgress(1F) > 0 || looksToBeBlocking(entity);

        if (isTriggered) {
            if (!this.isSwinging) {
                ItemStack currentItem;
                if (entity.handSwinging)
                    currentItem = entity.getStackInHand(Hand.MAIN_HAND);
                else
                    currentItem = entity.getActiveItem();

                var factory = this.itemLibrary.getItemSwingSound(currentItem);

                if (factory != null && freeSwing(entity)) {
                    SoundInstance instance;
                    if (info.isCurrentPlayer(entity)) {
                        instance = factory.createAsAdditional();
                    } else {
                        instance = factory.createAtEntity(entity);
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
        if (!entity.isUsingItem() || entity.getActiveItem().isEmpty()) {
            return false;
        }
        Item item = entity.getActiveItem().getItem();
        return item.getUseAction(entity.getActiveItem()) == UseAction.BLOCK;
    }

    protected static boolean freeSwing(LivingEntity entity) {
        var result = rayTraceBlock(entity);
        return result.getType() == HitResult.Type.MISS;
    }

    protected static double getReach(final LivingEntity entity) {
        if (entity instanceof PlayerEntity p)
            return p.isCreative() ? 5D : 3D;

        var dist = entity.getWidth();
        dist *= 2;
        dist *= dist;
        dist += entity.getWidth();
        return dist;
    }

    protected static HitResult rayTraceBlock(final LivingEntity entity) {
        double range = getReach(entity);
        return entity.raycast(range, 1F, true);
    }
}
