package org.orecruncher.dsurround.effects.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import org.orecruncher.dsurround.config.ItemLibrary;
import org.orecruncher.dsurround.sound.MinecraftAudioPlayer;

import java.util.function.Function;

@Environment(EnvType.CLIENT)
public class ItemSwingEffect extends EntityEffectBase {

    private HandHandler offHand;
    private HandHandler mainHand;

    @Override
    public void activate(final EntityEffectInfo info) {
        this.mainHand = new HandHandler(
                entity -> entity.handSwinging,
                entity -> entity.getStackInHand(Hand.MAIN_HAND)
        );

        this.offHand = new HandHandler(
                LivingEntity::isUsingItem,
                LivingEntity::getActiveItem
        ) {
            @Override
            public boolean freeSwing(LivingEntity entity) {
                return true;
            }
        };
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

        this.mainHand.tick(info, entity);
        this.offHand.tick(info, entity);
    }

    private static class HandHandler {

        private final Function<LivingEntity, Boolean> isTriggered;
        private final Function<LivingEntity, ItemStack> getActiveItemStack;

        private boolean isSwinging;

        public HandHandler(Function<LivingEntity, Boolean> isTriggered, Function<LivingEntity, ItemStack> getActiveItemStack) {
            this.isTriggered = isTriggered;
            this.getActiveItemStack = getActiveItemStack;
        }

        public void tick(final EntityEffectInfo info, final LivingEntity entity) {
            if (this.isTriggered.apply(entity)) {
                if (!this.isSwinging) {
                    var currentItem = this.getActiveItemStack.apply(entity);
                    if (!currentItem.isEmpty()) {
                        if (freeSwing(entity)) {
                            var factory = ItemLibrary.getItemSwingSound(currentItem);
                            if (factory != null) {
                                SoundInstance instance;
                                if (info.isCurrentPlayer(entity)) {
                                    instance = factory.createAsAdditional();
                                } else {
                                    instance = factory.createAtEntity(entity);
                                }

                                if (instance != null)
                                    MinecraftAudioPlayer.INSTANCE.play(instance);
                            }
                        }
                    }
                }
                this.isSwinging = true;
            } else {
                this.isSwinging = false;
            }
        }

        protected boolean freeSwing(LivingEntity entity) {
            var result = rayTraceBlock(entity);
            return result.getType() == HitResult.Type.MISS;
        }
    }

    protected static double getReach(final LivingEntity entity) {
        if (entity instanceof PlayerEntity)
            return 3D;

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
