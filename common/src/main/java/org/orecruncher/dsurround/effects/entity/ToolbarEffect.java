package org.orecruncher.dsurround.effects.entity;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.orecruncher.dsurround.config.libraries.IItemLibrary;
import org.orecruncher.dsurround.lib.McCompat;

public class ToolbarEffect extends EntityEffectBase {

    private final IItemLibrary itemLibrary;

    private int lastSlot = -1;

    public ToolbarEffect(IItemLibrary itemLibrary) {
        this.itemLibrary = itemLibrary;
    }

    @Override
    public void tick(final EntityEffectInfo info) {
        if (info.isRemoved())
            return;

        final Player player = (Player) info.getEntity();
        var inventory = player.getInventory();

        // First time through we want to not trigger the equip sound
        int selected = McCompat.selectedHotbarSlot(inventory);
        if (this.lastSlot == -1) {
            this.lastSlot = selected;
        } else if (this.lastSlot != selected) {
            final ItemStack currentStack = inventory.getItem(selected);
            if (!currentStack.isEmpty() & !player.isSpectator()) {
                this.itemLibrary.getItemEquipSound(currentStack).ifPresent(factory -> {
                    SoundInstance instance;
                    if (info.isCurrentPlayer(player))
                        instance = factory.attachToEntity(player);
                    else
                        instance = factory.createAtLocation(player);
                    this.playSound(instance);
                });
            }
            this.lastSlot = selected;
        }
    }
}
