package org.orecruncher.dsurround.runtime.variables;

import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.scripting.VariableSet;
import org.orecruncher.dsurround.lib.scripting.IConfigureDefinition;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;

public final class SeasonVariables extends VariableSet {

    private final ISeasonalInformation seasonalInformation;

    private boolean isSpring;
    private boolean isSummer;
    private boolean isAutumn;
    private boolean isWinter;
    private boolean isEarly;
    private boolean isMiddle;
    private boolean isLate;

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
            this.isEarly = this.seasonalInformation.isEarly();
            this.isMiddle = this.seasonalInformation.isMiddle();
            this.isLate = this.seasonalInformation.isLate();
        } else {
            this.isSpring = false;
            this.isSummer = false;
            this.isAutumn = false;
            this.isWinter = false;
            this.isEarly = false;
            this.isMiddle = false;
            this.isLate = false;
        }
    }

    @Override
    public void configure(IConfigureDefinition config) {
        config.defineFunction(id("isSpring"), l -> this.isSpring);
        config.defineFunction(id("isSummer"),l -> this.isSummer);
        config.defineFunction(id("isAutumn"), l -> this.isAutumn);
        config.defineFunction(id("isWinter"), l -> this.isWinter);

        config.defineFunction(id("isEarly"), l -> this.isEarly);
        config.defineFunction(id("isMiddle"), l -> this.isMiddle);
        config.defineFunction(id("isLate"), l -> this.isLate);
    }
}
