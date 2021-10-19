package org.orecruncher.dsurround.runtime.sets;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.orecruncher.dsurround.lib.scripting.VariableSet;
import org.orecruncher.dsurround.processing.Scanners;
import org.orecruncher.dsurround.processing.scanner.BiomeScanner;

@Environment(EnvType.CLIENT)
public class EnvironmentState extends VariableSet<IEnvironmentState> implements IEnvironmentState {

    public EnvironmentState() {
        super("state");
    }

    @Override
    public IEnvironmentState getInterface() {
        return this;
    }

    @Override
    public boolean isInVillage() {
        return Scanners.isInVillage();
    }

    @Override
    public boolean isInside() {
        return Scanners.isInside();
    }

    @Override
    public boolean isUnderWater() {
        return BiomeScanner.isUnderWater();
    }
}
