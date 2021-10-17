package org.orecruncher.dsurround.lib.audio;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.Arrays;

@Environment(EnvType.CLIENT)
@SuppressWarnings("unused")
public class AlAttributeBuilder {

    private int[] buffer = new int[8];
    private int idx = 0;

    public AlAttributeBuilder add(int code) {
        this.addImpl(code);
        return this;
    }

    public AlAttributeBuilder add(int code, int param1) {
        this.addImpl(code);
        this.addImpl(param1);
        return this;
    }

    public AlAttributeBuilder add(int code, int param1, int param2) {
        this.addImpl(code);
        this.addImpl(param1);
        this.addImpl(param2);
        return this;
    }

    public AlAttributeBuilder add(int code, int param1, int param2, int param3) {
        this.addImpl(code);
        this.addImpl(param1);
        this.addImpl(param2);
        this.addImpl(param3);
        return this;
    }

    private void addImpl(int value) {
        if (this.idx == this.buffer.length)
            this.buffer = Arrays.copyOf(this.buffer, this.buffer.length * 2);
        this.buffer[this.idx++] = value;
    }

    public int[] build() {
        this.addImpl(0);
        return Arrays.copyOf(this.buffer, this.idx);
    }
}
