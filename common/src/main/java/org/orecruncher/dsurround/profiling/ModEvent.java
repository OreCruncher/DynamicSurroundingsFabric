// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.profiling;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

import java.util.List;

/**
 * A Java Flight Recorder event naming one installed mod (see {@link ProfileMod}), one for each, written as /dsprofile
 * starts recording: what else is in a pack often explains what a recording shows. Only loaded while recording: the
 * {@code jdk.jfr} module may not be there at all in a player's Java.
 */
@Name("dsurround.Mod")
@Label("Mod")
@Category("Dynamic Surroundings")
@Description("An installed mod's id and version, as a Dynamic Surroundings profile was recorded with")
@StackTrace(false)
public final class ModEvent extends Event {

    @Label("Id")
    String id;

    @Label("Version")
    String version;

    static void write(List<ProfileMod> mods) {
        for (var mod : mods) {
            var event = new ModEvent();
            event.id = mod.id();
            event.version = mod.version();
            event.commit();
        }
    }
}
