package org.orecruncher.dsurround.runtime.audio;

import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

public class ConversionTests {

    private static final float RATE = 44100F;

    private static AudioFormat pcm16(int channels, boolean bigEndian) {
        // The format vanilla's Ogg decoder produces (signed, little endian)
        return new AudioFormat(RATE, 16, channels, true, bigEndian);
    }

    private static AudioFormat pcm8(int channels, boolean signed) {
        return new AudioFormat(RATE, 8, channels, signed, false);
    }

    /**
     * Stereo 16-bit frames as left, right pairs.
     */
    private static ByteBuffer stereo16(ByteOrder order, int... samples) {
        var buffer = ByteBuffer.allocate(samples.length * 2).order(order);
        for (var s : samples)
            buffer.putShort((short) s);
        return buffer.flip();
    }

    private static short[] mono16(ByteBuffer data, ByteOrder order) {
        var view = data.duplicate().order(order);
        var result = new short[view.remaining() / 2];
        for (int i = 0; i < result.length; i++)
            result[i] = view.getShort(i * 2);
        return result;
    }

    // ---- 16-bit ----------------------------------------------------------------------------------------------

    @Test
    void sixteenBitAveragesEachFrame() {
        var data = stereo16(ByteOrder.LITTLE_ENDIAN, 1000, 3000, -500, 500, 32767, 32767, -32768, -32768);

        var mono = Conversion.toMono(data, pcm16(2, false));

        assertNotNull(mono);
        assertArrayEquals(new short[]{2000, 0, 32767, -32768}, mono16(data, ByteOrder.LITTLE_ENDIAN));
    }

    @Test
    void sixteenBitAverageIsExact() {
        // Regression: (a >> 1) + (b >> 1) dropped a bit from each channel, so 1 and 1 mixed to 0
        var data = stereo16(ByteOrder.LITTLE_ENDIAN, 1, 1, 3, 4, -3, -4);

        Conversion.toMono(data, pcm16(2, false));

        assertArrayEquals(new short[]{1, 3, -4}, mono16(data, ByteOrder.LITTLE_ENDIAN));
    }

    @Test
    void sixteenBitBigEndian() {
        var data = stereo16(ByteOrder.BIG_ENDIAN, 1000, 3000, -2000, -4000);

        Conversion.toMono(data, pcm16(2, true));

        assertArrayEquals(new short[]{2000, -3000}, mono16(data, ByteOrder.BIG_ENDIAN));
    }

    @Test
    void byteOrderComesFromTheFormatNotTheBuffer() {
        // Little endian data in a buffer whose order setting says big endian (the default for a heap buffer)
        var data = stereo16(ByteOrder.LITTLE_ENDIAN, 1000, 3000).order(ByteOrder.BIG_ENDIAN);

        Conversion.toMono(data, pcm16(2, false));

        assertArrayEquals(new short[]{2000}, mono16(data, ByteOrder.LITTLE_ENDIAN));
        assertEquals(ByteOrder.BIG_ENDIAN, data.order(), "the buffer's own setting is left alone");
    }

    // ---- 8-bit -----------------------------------------------------------------------------------------------

    @Test
    void eightBitUnsignedAveragesAsUnsigned() {
        // Regression: averaging as signed bytes mixed 200 and 50 to 253 instead of 125
        var data = ByteBuffer.wrap(new byte[]{(byte) 200, (byte) 50, (byte) 255, (byte) 255, 0, 0, (byte) 128, (byte) 129});

        Conversion.toMono(data, pcm8(2, false));

        assertEquals(4, data.limit());
        assertEquals(125, data.get(0) & 0xFF);
        assertEquals(255, data.get(1) & 0xFF);
        assertEquals(0, data.get(2) & 0xFF);
        assertEquals(128, data.get(3) & 0xFF);
    }

    @Test
    void eightBitSigned() {
        var data = ByteBuffer.wrap(new byte[]{-100, 100, 127, 127, -128, -128});

        Conversion.toMono(data, pcm8(2, true));

        assertEquals(3, data.limit());
        assertEquals(0, data.get(0));
        assertEquals(127, data.get(1));
        assertEquals(-128, data.get(2));
    }

    // ---- Result ----------------------------------------------------------------------------------------------

    @Test
    void resultIsMonoInTheSameFormatOtherwise() {
        var stereo = pcm16(2, false);

        var mono = Conversion.toMono(stereo16(ByteOrder.LITTLE_ENDIAN, 1, 2), stereo);

        assertNotNull(mono);
        assertEquals(1, mono.getChannels());
        assertEquals(2, mono.getFrameSize());
        assertEquals(stereo.getSampleRate(), mono.getSampleRate());
        assertEquals(stereo.getFrameRate(), mono.getFrameRate());
        assertEquals(16, mono.getSampleSizeInBits());
        assertEquals(stereo.getEncoding(), mono.getEncoding());
        assertEquals(stereo.isBigEndian(), mono.isBigEndian());
    }

    @Test
    void limitBecomesTheEndOfTheMonoData() {
        var data = stereo16(ByteOrder.LITTLE_ENDIAN, 1, 2, 3, 4, 5, 6);

        Conversion.toMono(data, pcm16(2, false));

        assertEquals(0, data.position());
        assertEquals(6, data.limit(), "three mono frames of two bytes");
    }

    @Test
    void partialFrameAtTheEndIsDropped() {
        // Regression: the old loop read past the end when the data didn't end on a whole frame
        var data = ByteBuffer.allocate(7).order(ByteOrder.LITTLE_ENDIAN);
        data.putShort((short) 10).putShort((short) 20).put((byte) 1).put((byte) 2).put((byte) 3).flip();

        assertDoesNotThrow(() -> Conversion.toMono(data, pcm16(2, false)));

        assertEquals(2, data.limit());
        assertEquals(15, data.order(ByteOrder.LITTLE_ENDIAN).getShort(0));
    }

    // ---- Left alone ------------------------------------------------------------------------------------------

    @Test
    void unsupportedDataIsLeftUnchanged() {
        assertNull(Conversion.toMono(stereo16(ByteOrder.LITTLE_ENDIAN, 1, 2), pcm16(1, false)), "already mono");
        assertNull(Conversion.toMono(ByteBuffer.allocate(12), pcm16(6, false)), "surround: more than two channels");
        assertNull(Conversion.toMono(ByteBuffer.allocate(12), new AudioFormat(RATE, 24, 2, true, false)), "24-bit");
        assertNull(Conversion.toMono(ByteBuffer.allocate(8),
                new AudioFormat(AudioFormat.Encoding.PCM_FLOAT, RATE, 32, 2, 8, RATE, false)), "float samples");
        assertNull(Conversion.toMono(ByteBuffer.allocate(8),
                new AudioFormat(AudioFormat.Encoding.PCM_UNSIGNED, RATE, 16, 2, 4, RATE, false)), "unsigned 16-bit");

        var data = stereo16(ByteOrder.LITTLE_ENDIAN, 1000, 3000);
        Conversion.toMono(data, pcm16(6, false));
        assertEquals(4, data.limit(), "nothing written");
        assertArrayEquals(new short[]{1000, 3000}, mono16(data, ByteOrder.LITTLE_ENDIAN));
    }
}
