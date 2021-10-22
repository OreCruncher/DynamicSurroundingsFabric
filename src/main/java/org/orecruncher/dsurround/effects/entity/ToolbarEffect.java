package org.orecruncher.dsurround.effects.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.orecruncher.dsurround.config.ItemLibrary;
import org.orecruncher.dsurround.sound.ISoundFactory;
import org.orecruncher.dsurround.sound.MinecraftAudioPlayer;

@Environment(EnvType.CLIENT)
public class ToolbarEffect extends EntityEffectBase {

    protected static class HandTracker {

        protected final Hand hand;
        protected Item lastHeld;

        protected HandTracker(final PlayerEntity player) {
            this(player, Hand.OFF_HAND);
        }

        protected HandTracker(final PlayerEntity player, final Hand hand) {
            this.hand = hand;
            this.lastHeld = getItemForHand(player, hand);
        }

        protected Item getItemForHand(final PlayerEntity player, final Hand hand) {
            final ItemStack stack = player.getStackInHand(hand);
            return stack.getItem();
        }

        protected boolean triggerNewEquipSound(final PlayerEntity player) {
            final Item heldItem = getItemForHand(player, this.hand);
            return heldItem != this.lastHeld;
        }

        public void tick(final EntityEffectInfo info, PlayerEntity player) {
            if (triggerNewEquipSound(player)) {
                final ItemStack currentStack = player.getStackInHand(this.hand);
                if (!currentStack.isEmpty() & !player.isSpectator()) {
                    ISoundFactory factory = ItemLibrary.getItemEquipSound(currentStack);
                    if (factory != null) {
                        SoundInstance instance;
                        if (info.isCurrentPlayer(player))
                            instance = factory.createAtEntity(player);
                        else
                            instance = factory.createAtLocation(player);
                        MinecraftAudioPlayer.INSTANCE.play(instance);
                    }
                }
                this.lastHeld = currentStack.getItem();
            }
        }
    }

    protected static class MainHandTracker extends HandTracker {

        protected int lastSlot;

        public MainHandTracker(final PlayerEntity player) {
            super(player, Hand.MAIN_HAND);
            this.lastSlot = player.getInventory().selectedSlot;
        }

        @Override
        protected boolean triggerNewEquipSound(final PlayerEntity player) {
            return this.lastSlot != player.getInventory().selectedSlot || super.triggerNewEquipSound(player);
        }

        @Override
        public void tick(final EntityEffectInfo info, PlayerEntity player) {
            super.tick(info, player);
            this.lastSlot = player.getInventory().selectedSlot;
        }
    }

    protected MainHandTracker mainHand;
    protected HandTracker offHand;

    public void activate(final EntityEffectInfo info) {
        super.activate(info);
        final PlayerEntity player = (PlayerEntity) info.getEntity().get();
        this.mainHand = new MainHandTracker(player);
        this.offHand = new HandTracker(player);
    }

    @Override
    public void tick(final EntityEffectInfo info) {
        final PlayerEntity player = (PlayerEntity) info.getEntity().get();
        this.mainHand.tick(info, player);
        this.offHand.tick(info, player);
    }
}
