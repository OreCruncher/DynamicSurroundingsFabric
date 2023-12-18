package org.orecruncher.dsurround.processing;

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import joptsimple.internal.Strings;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Formatting;
import org.orecruncher.dsurround.config.Configuration;
import org.orecruncher.dsurround.config.biome.BiomeInfo;
import org.orecruncher.dsurround.lib.TickCounter;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.math.ITimer;
import org.orecruncher.dsurround.processing.scanner.BiomeScanner;
import org.orecruncher.dsurround.processing.scanner.CeilingScanner;
import org.orecruncher.dsurround.processing.scanner.VillageScanner;

import java.util.Collection;

@Environment(EnvType.CLIENT)
public class Scanners extends ClientHandler {

    private final BiomeScanner biomeScanner;
    private final VillageScanner villageScanner;
    private final CeilingScanner ceilingScanner;

    public Scanners(BiomeScanner biomeScanner, VillageScanner villageScanner, CeilingScanner ceilingScanner, Configuration config, IModLog logger) {
        super("Scanners", config, logger);

        this.biomeScanner = biomeScanner;
        this.villageScanner = villageScanner;
        this.ceilingScanner = ceilingScanner;
    }

    public boolean isInside() {
        return this.ceilingScanner.isReallyInside();
    }

    public boolean isInVillage() {
        return this.villageScanner.isInVillage();
    }

    public boolean isUnderwater() {
        return this.biomeScanner.isUnderWater();
    }

    public BiomeInfo playerLogicBiomeInfo() {
        return this.biomeScanner.playerLogicBiomeInfo();
    }

    public int getBiomeArea() {
        return this.biomeScanner.getBiomeArea();
    }

    public Reference2IntOpenHashMap<BiomeInfo> getBiomes() {
        return this.biomeScanner.getBiomes();
    }

    @Override
    public void process(final PlayerEntity player) {

        long ticks = TickCounter.getTickCount();
        this.ceilingScanner.tick(ticks);
        this.villageScanner.tick(ticks);
        this.biomeScanner.tick(ticks);
    }

    @Override
    protected void gatherDiagnostics(Collection<String> left, Collection<String> right, Collection<ITimer> timers) {
        right.add(Strings.EMPTY);
        right.add(Formatting.AQUA.toString() + Formatting.UNDERLINE + "Biome Survey");
        for (var e : this.biomeScanner.getBiomes().reference2IntEntrySet()) {
            right.add(String.format("%s [%d]", e.getKey().getBiomeId(), e.getIntValue()));
        }
    }
}
