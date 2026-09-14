package org.orecruncher.dsurround.runtime.variables;

import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.scripting.VariableSet;
import org.orecruncher.dsurround.lib.scripting.IConfigureDefinition;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;

public class SeasonVariables extends VariableSet {

    private final ISeasonalInformation seasonalInformation;

    private boolean isSpring;
    private boolean isSummer;
    private boolean isAutumn;
    private boolean isWinter;

    public SeasonVariables(ISeasonalInformation seasonalInformation) {
        super("season");
        this.seasonalInformation = seasonalInformation;
    }

    @Override
    public void tick() {
        if (GameUtils.isInGame()) {
            this.isSpring = this.seasonalInformation.isSpring();
            this.isSummer = this.seasonalInformation.isSummer();
            this.isAutumn = this.seasonalInformation.isAutumn();
            this.isWinter = this.seasonalInformation.isWinter();
        } else {
            this.isSpring = false;
            this.isSummer = false;
            this.isAutumn = false;
            this.isWinter = false;
        }
    }

    @Override
    public void configure(IConfigureDefinition config) {
        config.defineFunction(id("isSpring"), l -> this.isSpring);
        config.defineFunction(id("isSummer"), l -> this.isSummer);
        config.defineFunction(id("isAutumn"), l -> this.isAutumn);
        config.defineFunction(id("isWinter"), l -> this.isWinter);
    }
}
