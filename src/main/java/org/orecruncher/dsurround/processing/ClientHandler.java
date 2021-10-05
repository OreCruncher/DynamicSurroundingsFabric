package org.orecruncher.dsurround.processing;

import com.google.common.base.MoreObjects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerEntity;
import org.orecruncher.dsurround.lib.math.TimerEMA;
import org.orecruncher.dsurround.lib.random.XorShiftRandom;

import java.util.Collection;
import java.util.Random;

@Environment(EnvType.CLIENT)
abstract class ClientHandler {

    protected static final Random RANDOM = XorShiftRandom.current();

    private final String handlerName;
    private final TimerEMA timer;

    ClientHandler(final String name) {
        this.handlerName = name;
        this.timer = new TimerEMA(this.handlerName);
    }

    public TimerEMA getTimer() {
        return this.timer;
    }

    /**
     * Used to obtain the handler name for logging purposes.
     *
     * @return Name of the handler
     */

    public final String getHandlerName() {
        return this.handlerName;
    }

    /**
     * Indicates whether the handler needs to be invoked for the given tick.
     *
     * @return true that the handler needs to be invoked, false otherwise
     */
    public boolean doTick(final long tick) {
        return true;
    }

    /**
     * Meat of the handlers processing logic. Will be invoked if doTick() returns
     * true.
     *
     * @param player The player currently behind the keyboard.
     */
    public void process(final PlayerEntity player) {

    }

    /**
     * Called when the client is connecting to a server. Useful for initializing
     * data to a baseline state.
     */
    public void onConnect() {
    }

    /**
     * Called when the client disconnects from a server. Useful for cleaning up
     * state space.
     */
    public void onDisconnect() {
    }

    /**
     * Called when gather diagnostic information for the debug hud
     */
    protected void gatherDiagnostics(Collection<String> left, Collection<String> right, Collection<TimerEMA> timers) {

    }

    //////////////////////////////
    //
    // DO NOT HOOK THESE EVENTS!
    //
    //////////////////////////////
    final void updateTimer(final long nanos) {
        this.timer.update(nanos);
    }

    final void connect0() {
        onConnect();
    }

    final void disconnect0() {
        onDisconnect();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("name", getHandlerName()).toString();
    }
}