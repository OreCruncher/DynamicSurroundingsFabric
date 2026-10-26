package org.orecruncher.dsurround.processing;

import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that a handler that throws doesn't stop the handlers after it.
 */
public class HandlerIsolationTests {

    private final List<String> processed = new ArrayList<>();
    private final List<String> failed = new ArrayList<>();

    private class FakeHandler extends AbstractClientHandler {
        private final RuntimeException failure;
        private final boolean wantsTick;

        FakeHandler(String name, RuntimeException failure, boolean wantsTick) {
            super(name, null, null);
            this.failure = failure;
            this.wantsTick = wantsTick;
        }

        @Override
        public boolean doTick(long tick) {
            return this.wantsTick;
        }

        @Override
        public void process(Player player) {
            if (this.failure != null)
                throw this.failure;
            HandlerIsolationTests.this.processed.add(this.getHandlerName());
        }
    }

    private void tick(AbstractClientHandler... handlers) {
        for (var handler : handlers)
            Handlers.tickHandler(handler, 0, null, (h, t) -> this.failed.add(h.getHandlerName()));
    }

    @Test
    void handlersAreProcessed() {
        tick(new FakeHandler("a", null, true), new FakeHandler("b", null, true));

        assertEquals(List.of("a", "b"), this.processed);
        assertTrue(this.failed.isEmpty());
    }

    @Test
    void handlersThatDontWantTheTickAreSkipped() {
        tick(new FakeHandler("a", null, false), new FakeHandler("b", null, true));

        assertEquals(List.of("b"), this.processed);
    }

    @Test
    void aFailingHandlerIsReportedAndTheRestStillRun() {
        // Regression: an exception from one handler ended Handlers.tick, so every handler after it stopped too
        tick(new FakeHandler("a", null, true), new FakeHandler("bad", new IllegalStateException("boom"), true), new FakeHandler("c", null, true));

        assertEquals(List.of("a", "c"), this.processed);
        assertEquals(List.of("bad"), this.failed);
    }

    @Test
    void aTickSaysWhetherTheHandlerHadWork() {
        // For the recording's handler ticks: a failed handler still ran
        BiConsumer<AbstractClientHandler, Throwable> ignore = (h, t) -> { };
        assertTrue(Handlers.tickHandler(new FakeHandler("a", null, true), 0, null, ignore));
        assertFalse(Handlers.tickHandler(new FakeHandler("b", null, false), 0, null, ignore));
        assertTrue(Handlers.tickHandler(new FakeHandler("bad", new IllegalStateException("boom"), true), 0, null, ignore));
    }

    @Test
    void fatalErrorsAreNotSwallowed() {
        var handler = new FakeHandler("fatal", null, true) {
            @Override
            public void process(Player player) {
                throw new StackOverflowError("pretend");
            }
        };

        assertThrows(StackOverflowError.class, () -> tick(handler));
        assertTrue(this.failed.isEmpty());
    }
}
