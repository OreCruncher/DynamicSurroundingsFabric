// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.runtime.variables;

import org.orecruncher.dsurround.lib.scripting.VariableSet;
import org.orecruncher.dsurround.lib.scripting.IConfigureDefinition;
import org.orecruncher.dsurround.processing.Scanners;

public final class EnvironmentState extends VariableSet {

    private final Scanners scanner;

    public EnvironmentState(Scanners scanner) {
        super("state");
        this.scanner = scanner;
    }

    @Override
    public void configure(IConfigureDefinition config) {
        config.property(id("isInVillage"), () -> this.scanner.isInVillage());
        config.property(id("isInside"), () -> this.scanner.isInside());
        config.property(id("isUnderwater"), () -> this.scanner.isUnderwater());
    }
}
