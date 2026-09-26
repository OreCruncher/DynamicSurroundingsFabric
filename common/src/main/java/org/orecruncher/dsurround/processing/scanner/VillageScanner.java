package org.orecruncher.dsurround.processing.scanner;

import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.phys.AABB;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.compat.LevelCompat;
import org.orecruncher.dsurround.runtime.oracle.IDimensionOracle;
import org.orecruncher.dsurround.runtime.oracle.ILevelOracle;

public class VillageScanner extends AbstractScanner {

    private static final double VILLAGE_RANGE = 64;
    private static final int SCAN_INTERVAL = 20;

    private final ILevelOracle levelOracle;
    private boolean isInVillage;

    public VillageScanner(ILevelOracle levelOracle) {
        this.levelOracle = levelOracle;
    }

    public void tick(long tickCount) {
        // Only check once a second
        if (tickCount % SCAN_INTERVAL != 0)
            return;

        this.isInVillage = false;

        // Only for surface worlds.  Other types of worlds are interpreted as not having villages.
        if (this.levelOracle.natural()) {
            Player player = GameUtils.getPlayer().orElseThrow();
            var playerEyes = player.getEyePosition();
            AABB box = AABB.unitCubeFromLowerCorner(playerEyes).inflate(VILLAGE_RANGE);
            var villagerEntities = this.levelOracle.getEntitiesOfClass(Villager.class, box);

            if (!villagerEntities.isEmpty()) {
                // We have villagers.  Now find a bell!
                this.isInVillage = this.levelOracle.doesBlockEntityExist(blockEntity -> blockEntity instanceof BellBlockEntity && blockEntity.getBlockPos().closerToCenterThan(playerEyes, VILLAGE_RANGE));;
            }
        }
    }

    public boolean isInVillage() {
        return this.isInVillage;
    }
}
