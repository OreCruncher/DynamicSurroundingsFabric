package org.orecruncher.dsurround.config;

import com.google.gson.annotations.SerializedName;

public enum SoundEventType {
    @SerializedName(value = "loop", alternate = {"LOOP"})
    LOOP,
    @SerializedName(value = "mood", alternate = {"MOOD"})
    MOOD,
    @SerializedName(value = "addition", alternate = {"ADDITION"})
    ADDITION,
    @SerializedName(value = "music", alternate = {"MUSIC"})
    MUSIC
}
