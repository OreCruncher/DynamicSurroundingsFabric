package org.orecruncher.dsurround.config.data;

import com.google.common.collect.ImmutableList;
import com.google.gson.annotations.SerializedName;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.apache.commons.lang3.StringUtils;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.config.AcousticConfig;
import org.orecruncher.dsurround.lib.validation.IValidator;
import org.orecruncher.dsurround.lib.validation.ValidationException;
import org.orecruncher.dsurround.lib.validation.ValidationHelpers;

import java.util.List;

@Environment(EnvType.CLIENT)
public final class BiomeConfigRule implements IValidator<BiomeConfigRule> {
    @SerializedName("biomeSelector")
    public String biomeSelector = StringUtils.EMPTY;
    @SerializedName("_comment")
    public String comment = null;
    @SerializedName("clearSounds")
    public boolean clearSounds = false;
    @SerializedName("fogColor")
    public String fogColor = null;
    @SerializedName("visibility")
    public Float visibility = null;
    @SerializedName("additionalSoundChance")
    public String additionalSoundChance;
    @SerializedName("moodSoundChance")
    public String moodSoundChance;
    @SerializedName("acoustics")
    public List<AcousticConfig> acoustics = ImmutableList.of();

    @Override
    public void validate(final BiomeConfigRule obj) throws ValidationException {
        ValidationHelpers.notNull("biomeSelector", this.biomeSelector, Client.LOGGER);

        if (this.fogColor != null)
            ValidationHelpers.isValidColorCode("fogColor", this.fogColor, Client.LOGGER);

        if (this.visibility != null)
            ValidationHelpers.inRange("visibility", this.visibility, 0F, 1F, Client.LOGGER);

        if (this.additionalSoundChance != null)
            ValidationHelpers.notNullOrWhitespace("additionalSoundChance", this.additionalSoundChance, Client.LOGGER);

        if (this.moodSoundChance != null)
            ValidationHelpers.notNullOrWhitespace("moodSoundChance", this.moodSoundChance, Client.LOGGER);

        for (final AcousticConfig ac : this.acoustics)
            ac.validate(ac);
    }
}