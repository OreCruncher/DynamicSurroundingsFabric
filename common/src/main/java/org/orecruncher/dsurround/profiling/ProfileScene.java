package org.orecruncher.dsurround.profiling;

import net.minecraft.network.chat.Component;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.processing.GroundFogHandler;
import org.orecruncher.dsurround.processing.WindowFrostHandler;
import org.orecruncher.dsurround.runtime.weather.IWeatherSimulation;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * What /dsprofile sets up before it records: {@link #GENERAL} changes nothing, and the player plays as usual; the others
 * show an effect at its costliest, held there by the same settings the testing command (/dsweather) holds it
 * with, and put back as they were once it is done. Some also need a time and weather, which they set with server
 * commands, in single player only (on a server those would change the world for everyone); they can't be read back
 * from the client to put back, so the command says what it sent.
 *
 * @param settle   seconds to wait before recording, for the effect to fade in
 * @param commands the server commands it sends in single player (without the slash; they need cheats)
 */
public enum ProfileScene {
    GENERAL(3, List.of()) {
        @Override
        Runnable setUp() {
            return () -> {};
        }
    },
    GROUND_FOG(6, List.of("weather clear", "gamerule doWeatherCycle false")) {
        @Override
        Runnable setUp() {
            var handler = ContainerManager.resolve(GroundFogHandler.class);
            return hold(handler.forced(), () -> handler.setForced(1F), handler::setForced, handler::clearForced);
        }
    },
    WEATHER(6, List.of("weather thunder", "gamerule doWeatherCycle false")) {
        @Override
        Runnable setUp() {
            var simulation = ContainerManager.resolve(IWeatherSimulation.class);
            return hold(simulation.strengthSet(), () -> simulation.setStrength(1F), simulation::setStrength, simulation::clearStrength);
        }
    },
    FROST(6, List.of()) {
        @Override
        Runnable setUp() {
            var handler = ContainerManager.resolve(WindowFrostHandler.class);
            return hold(handler.forced(), () -> handler.setForced(1F), handler::setForced, handler::clearForced);
        }
    };

    private final int settle;
    private final List<String> commands;

    ProfileScene(int settle, List<String> commands) {
        this.settle = settle;
        this.commands = commands;
    }

    /**
     * Sets the scene up, on the render thread
     *
     * @return what puts it back as it was, on the render thread
     */
    abstract Runnable setUp();

    /**
     * Its name in the command
     */
    public String commandName() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    /**
     * What it shows
     */
    public Component what() {
        return Component.translatable(this.translationKey() + ".what");
    }

    /**
     * Where to look, or what to do, while it records
     */
    public Component tip() {
        return Component.translatable(this.translationKey() + ".tip");
    }

    String translationKey() {
        return "dsurround.profile.scene." + this.commandName();
    }

    public int settle() {
        return this.settle;
    }

    public List<String> commands() {
        return this.commands;
    }

    /**
     * Sets a value held by a testing command, and what puts back the one there was, if any
     */
    private static Runnable hold(Optional<Float> was, Runnable set, Consumer<Float> restore, Runnable clear) {
        set.run();
        return () -> was.ifPresentOrElse(restore, clear);
    }
}
