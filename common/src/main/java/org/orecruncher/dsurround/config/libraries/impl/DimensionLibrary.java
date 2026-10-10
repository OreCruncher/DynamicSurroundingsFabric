package org.orecruncher.dsurround.config.libraries.impl;

import com.mojang.serialization.Codec;
import net.minecraft.world.level.Level;
import org.orecruncher.dsurround.config.data.DimensionConfigRule;
import org.orecruncher.dsurround.config.DimensionInfo;
import org.orecruncher.dsurround.config.libraries.IDimensionLibrary;
import org.orecruncher.dsurround.eventing.IClientDisconnect;
import org.orecruncher.dsurround.eventing.IReloadEvent;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.logging.ModLog;
import org.orecruncher.dsurround.lib.resources.ResourceUtilities;

import java.util.*;
import java.util.stream.Stream;

public final class DimensionLibrary implements IDimensionLibrary {

    private static final String FILE_NAME = "dimensions.json";
    private static final Codec<List<DimensionConfigRule>> CODEC = Codec.list(DimensionConfigRule.CODEC);

    private final IModLog logger;
    private final ObjectArray<DimensionConfigRule> dimensionRules = new ObjectArray<>();
    private int version = 0;

    public DimensionLibrary(IModLog logger) {
        this.logger = ModLog.createChild(logger, "DimensionLibrary");

        // A different world can have different values for the same dimension key (a superflat overworld after a
        // normal one, say), so nothing carries over between worlds. Leaving a world (including a server transfer) is
        // a disconnect. The dimension oracle caches the built info per level.
        IClientDisconnect.EVENT.register(client -> {
            this.version++;
        });
    }

    @Override
    public void reload(ResourceUtilities resourceUtilities, IReloadEvent.Scope scope) {

        this.version++;

        if (scope == IReloadEvent.Scope.TAGS) {
            this.logger.info("received tag update notification; version is now %d", this.version);
            return;
        }

        this.dimensionRules.clear();

        var findResults = resourceUtilities.findModResources(CODEC, FILE_NAME);
        findResults.forEach(result -> this.dimensionRules.addAll(result.resourceContent()));

        this.logger.info("%d dimension rules loaded; version is now %d", this.dimensionRules.size(), this.version);
    }

    @Override
    public int getVersion() {
        return this.version;
    }

    @Override
    public DimensionInfo getData(final Level world) {
        var dimInfo = new DimensionInfo(world);
        this.dimensionRules.forEach(dimInfo::update);
        return dimInfo.finish();
    }

    @Override
    public Stream<String> dump() {
        return this.dimensionRules.stream().map(Object::toString).sorted();
    }
}