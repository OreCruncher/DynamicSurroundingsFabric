package org.orecruncher.dsurround.runtime.audio.effects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests which reverbs a context's sends carry. The effects themselves need an OpenAL device, so aren't tested here.
 */
public class EfxSendMappingTests {

    @Test
    void withASendPerReverbEachReverbHasItsOwn() {
        for (int reverb = 0; reverb < Efx.SENDS; reverb++) {
            assertEquals(reverb, Efx.sendOfReverb(reverb, Efx.SENDS));
            assertEquals(reverb, Efx.reverbOfSend(reverb, Efx.SENDS));
        }
    }

    @Test
    void withFewerSendsTheLongestReverbsAreKept() {
        // OpenAL Soft's default, which voice chat's context gets if the device won't give it more
        int sends = 2;

        assertEquals(-1, Efx.sendOfReverb(0, sends));
        assertEquals(-1, Efx.sendOfReverb(1, sends));
        assertEquals(0, Efx.sendOfReverb(2, sends));
        assertEquals(1, Efx.sendOfReverb(3, sends));

        assertEquals(2, Efx.reverbOfSend(0, sends));
        assertEquals(3, Efx.reverbOfSend(1, sends));
    }

    @Test
    void sendsAndReverbsMapBothWays() {
        for (int sends = 0; sends <= Efx.SENDS; sends++) {
            for (int send = 0; send < sends; send++)
                assertEquals(send, Efx.sendOfReverb(Efx.reverbOfSend(send, sends), sends), "sends: " + sends);
        }
    }

    @Test
    void withNoSendsNoReverbIsSent() {
        for (int reverb = 0; reverb < Efx.SENDS; reverb++)
            assertEquals(-1, Efx.sendOfReverb(reverb, 0));
    }
}
