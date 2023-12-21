package org.orecruncher.dsurround.processing.accents;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import org.orecruncher.dsurround.config.Configuration;
import org.orecruncher.dsurround.config.libraries.IItemLibrary;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.sound.ISoundFactory;

@Environment(EnvType.CLIENT)
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
    public void provide(LivingEntity entity, BlockPos pos, BlockState posState, ObjectArray<ISoundFactory> acoustics) {
        var footAccent = this.itemLibrary.getEquipableStepAccentSound(entity.getEquippedStack(EquipmentSlot.FEET));
        footAccent.ifPresent(acoustics::add);

        var legs = this.itemLibrary.getEquipableStepAccentSound(entity.getEquippedStack(EquipmentSlot.LEGS));
        legs.ifPresentOrElse(
                acoustics::add,
                () -> {
                    var chest = this.itemLibrary.getEquipableStepAccentSound(entity.getEquippedStack(EquipmentSlot.CHEST));
                    chest.ifPresent(acoustics::add);
                }
        );
    }
}