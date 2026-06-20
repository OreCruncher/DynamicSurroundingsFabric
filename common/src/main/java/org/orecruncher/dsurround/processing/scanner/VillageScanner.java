package org.orecruncher.dsurround.processing.scanner;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.world.WorldUtils;

public class VillageScanner extends AbstractScanner {

    private static final double VILLAGE_RANGE = 64;
    private static final int SCAN_INTERVAL = 20;

    private boolean isInVillage;

    public void tick(long tickCount) {
        if (tickCount % SCAN_INTERVAL != 0)
            return;

        this.isInVillage = false;
        var world = GameUtils.getWorld().orElseThrow();
        Player player = GameUtils.getPlayer().orElseThrow();

        if (world.dimension() == Level.OVERWORLD) {
            var playerEyes = player.getEyePosition();
            var bell = WorldUtils.getLoadedBlockEntities(world, blockEntity ->
                    blockEntity instanceof BellBlockEntity && blockEntity.getBlockPos().closerToCenterThan(playerEyes, VILLAGE_RANGE));
            this.isInVillage = !bell.isEmpty();
        }
    }

    public boolean isInVillage() {
        return this.isInVillage;
    }
}
