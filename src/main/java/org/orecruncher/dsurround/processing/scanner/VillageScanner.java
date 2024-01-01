package org.orecruncher.dsurround.processing.scanner;

import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.phys.AABB;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.world.WorldUtils;

public class VillageScanner {

    private static final double VILLAGE_RANGE = 64;
    private static final int SCAN_INTERVAL = 20;

    private boolean isInVillage;

    public void tick(long tickCount) {
        // Only check once a second
        if (tickCount % SCAN_INTERVAL != 0)
            return;

        this.isInVillage = false;
        var world = GameUtils.getWorld().orElseThrow();
        Player player = GameUtils.getPlayer().orElseThrow();

        // Only for surface worlds.  Other types of worlds are interpreted as not having villages.
        if (world.dimensionType().natural()) {
            var playerEyes = player.getEyePosition();
            AABB box = AABB.unitCubeFromLowerCorner(playerEyes).inflate(VILLAGE_RANGE);

            var villagerEntities = world.getEntitiesOfClass(Villager.class, box);

            if (!villagerEntities.isEmpty()) {
                // We have villagers.  Now find a bell!
                var bell = WorldUtils.getLoadedBlockEntities(world, blockEntity -> blockEntity instanceof BellBlockEntity && blockEntity.getBlockPos().closerToCenterThan(playerEyes, VILLAGE_RANGE));
                this.isInVillage = !bell.isEmpty();
            }
        }
    }

    public boolean isInVillage() {
        return this.isInVillage;
    }
}
