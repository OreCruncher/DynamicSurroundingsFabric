package org.orecruncher.dsurround.processing.accents;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.config.Configuration;
import org.orecruncher.dsurround.config.libraries.IItemLibrary;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.sound.ISoundFactory;

class ArmorAccents implements IFootstepAccentProvider {

    private final Configuration config;
    private final IItemLibrary itemLibrary;

    ArmorAccents(Configuration config, IItemLibrary itemLibrary) {
        this.config = config;
        this.itemLibrary = itemLibrary;
    }

    @Override
    public boolean isEnabled() {
        return this.config.footstepAccents.enableArmorAccents;
    }

    @Override
    public void provide(LivingEntity entity, BlockPos pos, BlockState posState, boolean isWaterLogged, ObjectArray<ISoundFactory> acoustics) {
        var footAccent = this.itemLibrary.getEquipableStepAccentSound(entity.getItemBySlot(EquipmentSlot.FEET));
        footAccent.ifPresent(acoustics::add);

        var legs = this.itemLibrary.getEquipableStepAccentSound(entity.getItemBySlot(EquipmentSlot.LEGS));
        legs.ifPresentOrElse(
                acoustics::add,
                () -> {
                    var chest = this.itemLibrary.getEquipableStepAccentSound(entity.getItemBySlot(EquipmentSlot.CHEST));
                    chest.ifPresent(acoustics::add);
                }
        );
    }
}