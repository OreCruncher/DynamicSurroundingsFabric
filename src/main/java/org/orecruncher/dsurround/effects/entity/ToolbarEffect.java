package org.orecruncher.dsurround.effects.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.orecruncher.dsurround.config.libraries.IItemLibrary;
import org.orecruncher.dsurround.sound.ISoundFactory;

@Environment(EnvType.CLIENT)
public class ToolbarEffect extends EntityEffectBase {

    private final IItemLibrary itemLibrary;

    private int lastSlot = -1;

    public ToolbarEffect(IItemLibrary itemLibrary) {
        this.itemLibrary = itemLibrary;
    }

    @Override
    public void tick(final EntityEffectInfo info) {
        final PlayerEntity player = (PlayerEntity) info.getEntity().get();
        var inventory = player.getInventory();

        // First time through we want to not trigger the equip sound
        if (this.lastSlot == -1) {
            this.lastSlot = inventory.selectedSlot;
        } else if (this.lastSlot != inventory.selectedSlot) {
            final ItemStack currentStack = inventory.getStack(inventory.selectedSlot);
            if (!currentStack.isEmpty() & !player.isSpectator()) {
                ISoundFactory factory = this.itemLibrary.getItemEquipSound(currentStack);
                if (factory != null) {
                    SoundInstance instance;
                    if (info.isCurrentPlayer(player))
                        instance = factory.createAtEntity(player);
                    else
                        instance = factory.createAtLocation(player);
                    this.playSound(instance);
                }
            }
            this.lastSlot = inventory.selectedSlot;
        }
    }
}
