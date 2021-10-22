package org.orecruncher.dsurround.processing;

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import joptsimple.internal.Strings;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Formatting;
import org.orecruncher.dsurround.config.biome.BiomeInfo;
import org.orecruncher.dsurround.lib.TickCounter;
import org.orecruncher.dsurround.lib.math.TimerEMA;
import org.orecruncher.dsurround.processing.scanner.BiomeScanner;
import org.orecruncher.dsurround.processing.scanner.CeilingScanner;
import org.orecruncher.dsurround.processing.scanner.VillageScanner;

import java.util.Collection;

@Environment(EnvType.CLIENT)
public class Scanners extends ClientHandler {

    private static final CeilingScanner ceilingScanner = new CeilingScanner();
    private static final VillageScanner villageScanner = new VillageScanner();
    private static final BiomeScanner biomes = new BiomeScanner();

    Scanners() {
        super("Scanners");
    }

    public static boolean isInside() {
        return ceilingScanner.isReallyInside();
    }

    public static boolean isInVillage() {
        return villageScanner.isInVillage();
    }

    public static int getBiomeArea() {
        return biomes.getBiomeArea();
    }

    public static Reference2IntOpenHashMap<BiomeInfo> getBiomes() {
        return biomes.getBiomes();
    }

    @Override
    public void process(final PlayerEntity player) {

        long ticks = TickCounter.getTickCount();
        ceilingScanner.tick(ticks);
        villageScanner.tick(ticks);
        biomes.tick(ticks);
    }

    @Override
    protected void gatherDiagnostics(Collection<String> left, Collection<String> right, Collection<TimerEMA> timers) {
        right.add(Strings.EMPTY);
        right.add(Formatting.AQUA.toString() + Formatting.UNDERLINE + "Biome Survey");
        for (var e : biomes.getBiomes().reference2IntEntrySet()) {
            right.add(String.format("%s [%d]", e.getKey().getBiomeId(), e.getIntValue()));
        }
    }

}
