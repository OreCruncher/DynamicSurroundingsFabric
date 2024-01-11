package org.orecruncher.dsurround.processing;

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.minecraft.world.entity.player.Player;
import org.orecruncher.dsurround.config.Configuration;
import org.orecruncher.dsurround.config.biome.BiomeInfo;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.lib.system.ITickCount;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.processing.scanner.BiomeScanner;
import org.orecruncher.dsurround.processing.scanner.CeilingScanner;
import org.orecruncher.dsurround.processing.scanner.VillageScanner;

public class Scanners extends AbstractClientHandler {

    private final BiomeScanner biomeScanner;
    private final VillageScanner villageScanner;
    private final CeilingScanner ceilingScanner;
    private final ITickCount tickCount;

    public Scanners(BiomeScanner biomeScanner, VillageScanner villageScanner, CeilingScanner ceilingScanner, Configuration config, ITickCount tickCount, IModLog logger) {
        super("Scanners", config, logger);

        this.biomeScanner = biomeScanner;
        this.villageScanner = villageScanner;
        this.ceilingScanner = ceilingScanner;
        this.tickCount = tickCount;
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
    public void process(final Player player) {

        long ticks = this.tickCount.getTickCount();
        this.ceilingScanner.tick(ticks);
        this.villageScanner.tick(ticks);
        this.biomeScanner.tick(ticks);
    }

    @Override
    protected void gatherDiagnostics(CollectDiagnosticsEvent event) {
        var panelText = event.getSectionText(CollectDiagnosticsEvent.Section.Survey);
        panelText.add("ceiling coverage: %.2f".formatted(this.ceilingScanner.getCoverageRatio()));
        this.biomeScanner.getBiomes().reference2IntEntrySet().stream()
                .map(kvp -> "%s [%d]".formatted(kvp.getKey().getBiomeId(), kvp.getIntValue()))
                .sorted()
                .forEach(panelText::add);
    }
}
