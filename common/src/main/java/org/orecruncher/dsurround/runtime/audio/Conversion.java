package org.orecruncher.dsurround.runtime.audio;

import com.mojang.blaze3d.audio.SoundBuffer;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.mixins.audio.MixinSoundBuffer;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Converts stereo sound data to mono, so OpenAL positions the sound: it only spatializes mono sources.
 */
public final class Conversion {

    private Conversion() {
    }

    /**
     * Converts the buffer's data to mono in place, if it is stereo PCM it can handle. Only possible before the
     * buffer is uploaded to OpenAL: vanilla discards the data after that.
     *
     * @param buffer Audio stream buffer to convert
     */
    public static void convert(final SoundBuffer buffer) {
        final MixinSoundBuffer accessor = (MixinSoundBuffer) buffer;
        final ByteBuffer data = accessor.dsurround$getSample();
        if (data == null)
            return;

        final AudioFormat mono = toMono(data, accessor.dsurround$getFormat());
        if (mono != null)
            accessor.dsurround$setFormat(mono);
    }

    /**
     * Mixes stereo PCM down to mono in place: each frame's two samples are averaged into one, written from the
     * start of the data, and the limit is set to the end of the mono data. A partial frame at the end is dropped.
     * <p>
     * Handles 8-bit samples, signed or unsigned (OpenAL's 8-bit format is unsigned, silence at 128), and signed
     * 16-bit samples in either byte order. The buffer's own byte order setting is left as it was.
     *
     * @return the mono format, or null if the data isn't stereo in a supported format (it is left unchanged)
     */
    @Nullable
    static AudioFormat toMono(final ByteBuffer data, final AudioFormat format) {
        if (format.getChannels() != 2)
            return null;

        final int bits = format.getSampleSizeInBits();
        final var encoding = format.getEncoding();
        final boolean signed = AudioFormat.Encoding.PCM_SIGNED.equals(encoding);
        final boolean unsigned = AudioFormat.Encoding.PCM_UNSIGNED.equals(encoding);
        if (!(bits == 8 && (signed || unsigned)) && !(bits == 16 && signed))
            return null;

        // A view in the data's byte order, so the caller's buffer settings aren't touched
        final ByteBuffer view = data.duplicate().order(format.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        final int start = data.position();
        final int frameSize = format.getFrameSize();
        final int frames = (data.limit() - start) / frameSize;

        if (bits == 8) {
            for (int f = 0; f < frames; f++) {
                final int in = start + f * 2;
                final int a = view.get(in);
                final int b = view.get(in + 1);
                // Unsigned samples have to be averaged as unsigned values (0 to 255)
                final int mixed = unsigned ? ((a & 0xFF) + (b & 0xFF)) >> 1 : (a + b) >> 1;
                view.put(start + f, (byte) mixed);
            }
        } else {
            for (int f = 0; f < frames; f++) {
                final int in = start + f * 4;
                // Exact average: the sum of two shorts fits in an int
                final int mixed = (view.getShort(in) + view.getShort(in + 2)) >> 1;
                view.putShort(start + f * 2, (short) mixed);
            }
        }

        final int monoFrameSize = frameSize / 2;
        data.limit(start + frames * monoFrameSize);

        return new AudioFormat(
                encoding,
                format.getSampleRate(),
                bits,
                1,
                monoFrameSize,
                format.getFrameRate(),
                format.isBigEndian());
    }
}
