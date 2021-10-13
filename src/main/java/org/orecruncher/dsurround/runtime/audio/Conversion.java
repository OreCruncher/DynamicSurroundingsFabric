package org.orecruncher.dsurround.runtime.audio;

import net.minecraft.client.sound.AudioStream;
import net.minecraft.client.sound.StaticSound;
import org.orecruncher.dsurround.mixins.audio.MixinStaticSoundAccessor;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;

public final class Conversion {

    /**
     * Handles the conversion of the incoming IAudioStream into mono format as needed.
     *
     * @param inputStream The audio stream that is to be played
     * @return An IAudioStream that is in mono format
     */
    public static AudioStream convert(final AudioStream inputStream) {
        final AudioFormat format = inputStream.getFormat();
        if (format.getChannels() == 1)
            return inputStream;

        return new MonoStream(inputStream);
    }

    /**
     * Converts the AudioStreamBuffer into mono if needed.
     *
     * @param buffer Audio stream buffer to convert
     * @return Converted audio buffer
     */
    public static void convert(final StaticSound buffer) {

        MixinStaticSoundAccessor accessor = (MixinStaticSoundAccessor) buffer;
        final AudioFormat format = accessor.getFormat();

        // If it is already mono return original buffer
        if (format.getChannels() == 1)
            return;

        // If the sample size is not 8 or 16 bits just return the original
        int bits = format.getSampleSizeInBits();
        if (bits != 8 && bits != 16)
            return;

        // Do the conversion.  Essentially it averages the values in the source buffer based on the sample size.
        boolean bigendian = format.isBigEndian();
        final AudioFormat monoformat = new AudioFormat(
                format.getEncoding(),
                format.getSampleRate(),
                bits,
                1, // Mono - single channel
                format.getFrameSize() >> 1,
                format.getFrameRate(),
                bigendian);

        final ByteBuffer source = accessor.getSample();
        if (source == null) {
            return;
        }

        final int sourceLength = source.limit();
        final int skip = format.getFrameSize();
        for (int i = 0; i < sourceLength; i += skip) {
            final int targetIdx = i >> 1;
            if (bits == 8) {
                final int c1 = source.get(i) >> 1;
                final int c2 = source.get(i + 1) >> 1;
                final int v = c1 + c2;
                source.put(targetIdx, (byte) v);
            } else {
                final int c1 = source.getShort(i) >> 1;
                final int c2 = source.getShort(i + 2) >> 1;
                final int v = c1 + c2;
                source.putShort(targetIdx, (short) v);
            }
        }

        // Patch up the old object
        accessor.setFormat(monoformat);
        source.rewind();
        source.limit(sourceLength >> 1);
    }

    private static class MonoStream implements AudioStream {

        private final AudioStream source;

        public MonoStream( final AudioStream source) {
            this.source = source;
        }

        @Override
        public AudioFormat getFormat() {
            return this.source.getFormat();
        }

        @Override
        public ByteBuffer getBuffer(int size) throws IOException {
            return this.source.getBuffer(size);
        }

        @Override
        public void close() throws IOException {
            this.source.close();
        }
    }
}