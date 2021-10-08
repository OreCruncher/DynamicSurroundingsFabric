package org.orecruncher.dsurround.processing;

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import org.orecruncher.dsurround.config.dimension.DimensionInfo;
import org.orecruncher.dsurround.config.DimensionLibrary;
import org.orecruncher.dsurround.lib.TickCounter;
import org.orecruncher.dsurround.processing.scanner.BiomeScanner;
import org.orecruncher.dsurround.processing.scanner.CeilingScanner;
import org.orecruncher.dsurround.processing.scanner.VillageScanner;

@Environment(EnvType.CLIENT)
public class Scanners extends ClientHandler {

    private static final CeilingScanner ceilingScanner = new CeilingScanner();
    private static final VillageScanner villageScanner = new VillageScanner();
    private static final BiomeScanner biomes = new BiomeScanner();

    private static DimensionInfo dimInfo;
    private static boolean isUnderground;
    private static boolean isInOuterspace;
    private static boolean isInClouds;

    Scanners() {
        super("Scanners");
    }

    @Override
    public void process(final PlayerEntity player) {

        World world = player.getEntityWorld();
        dimInfo = DimensionLibrary.getData(world);

        isUnderground = isInOuterspace = isInClouds = false;
        var position = player.getBlockPos();
        if ((position.getY() - 4) < dimInfo.getSeaLevel())
            isUnderground = true;
        else if (position.getY() >= dimInfo.getSpaceHeight())
            isInOuterspace = true;
        else if (position.getY() >= dimInfo.getCloudHeight())
            isInClouds = true;

        long ticks = TickCounter.getTickCount();
        biomes.tick(ticks);
        ceilingScanner.tick(ticks);
        villageScanner.tick(ticks);
    }

    public static DimensionInfo getDimInfo() {
        return dimInfo;
    }

    public static boolean isInside() {
        return ceilingScanner.isReallyInside();
    }

    public static boolean isInVillage() {
        return villageScanner.isInVillage();
    }

    public static boolean isUnderground() {
        return isUnderground;
    }

    public static boolean isInOuterspace() {
        return isInOuterspace;
    }

    public static boolean isInClouds() {
        return isInClouds();
    }

    public static int getBiomeArea() {
        return biomes.getBiomeArea();
    }

    public static Reference2IntOpenHashMap<Biome> getBiomes() {
        return biomes.getBiomes();
    }
}
