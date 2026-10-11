package org.orecruncher.dsurround.lib.platform;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the other mods Dynamic Surroundings knows of, and how they are found. The loader can't be asked in tests,
 * so it is stood in for by a set of the ids installed.
 */
public class KnownModTests {

    @Test
    void aModIsFoundByAnyOfItsIds() {
        assertEquals(Optional.of("iris"), KnownMod.IRIS.installedId(Set.of("iris")::contains));
        assertEquals(Optional.of("oculus"), KnownMod.IRIS.installedId(Set.of("oculus")::contains), "the NeoForge fork");
        assertEquals(Optional.of("cloth-config"), KnownMod.CLOTH_CONFIG.installedId(Set.of("cloth-config")::contains), "on Fabric");
        assertEquals(Optional.of("cloth_config"), KnownMod.CLOTH_CONFIG.installedId(Set.of("cloth_config")::contains), "on NeoForge");
    }

    @Test
    void aModNotInstalledIsNotFound() {
        assertEquals(Optional.empty(), KnownMod.PARTICLE_RAIN.installedId(Set.of("iris", "sereneseasons")::contains));
    }

    @Test
    void noIdBelongsToTwoMods() {
        var seen = new HashSet<String>();
        for (var mod : KnownMod.values())
            for (var id : mod.ids())
                assertTrue(seen.add(id), () -> id + " is listed twice");
    }

    @Test
    void theIdsAreThoseTheModsUse() {
        // As they were as constants, so moving them didn't change any
        assertEquals(List.of("presencefootsteps"), KnownMod.PRESENCE_FOOTSTEPS.ids());
        assertEquals(List.of("sound_physics_remastered"), KnownMod.SOUND_PHYSICS_REMASTERED.ids());
        assertEquals(List.of("particlerain"), KnownMod.PARTICLE_RAIN.ids());
        assertEquals(List.of("rsls"), KnownMod.RAISE_SOUND_LIMIT_SIMPLIFIED.ids());
        assertEquals(List.of("cloth-config", "cloth_config"), KnownMod.CLOTH_CONFIG.ids());
        assertEquals(List.of("yet_another_config_lib_v3"), KnownMod.YACL.ids());
        assertEquals(List.of("connectormod"), KnownMod.SINYTRA_CONNECTOR.ids());
        assertEquals(List.of("sereneseasons"), KnownMod.SERENE_SEASONS.ids());
        assertEquals(List.of("quilt_loader"), KnownMod.QUILTED_LOADER.ids());
        assertEquals(List.of("modmenu"), KnownMod.MODMENU.ids());
        assertEquals(List.of("iris", "oculus"), KnownMod.IRIS.ids());
    }

    @Test
    void theDiagnosticsListAllButModMenu() {
        // As the list of mods of interest did
        var shown = Arrays.stream(KnownMod.values()).filter(KnownMod::showInDiagnostics).toList();
        assertEquals(KnownMod.values().length - 1, shown.size());
        assertFalse(KnownMod.MODMENU.showInDiagnostics());
    }
}
