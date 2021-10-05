package org.orecruncher.dsurround.config.data;

import com.google.common.collect.ImmutableList;
import com.google.gson.annotations.SerializedName;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.apache.commons.lang3.StringUtils;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.lib.validation.IValidator;
import org.orecruncher.dsurround.lib.validation.ValidationException;
import org.orecruncher.dsurround.lib.validation.ValidationHelpers;

import java.util.List;

@Environment(EnvType.CLIENT)
public final class BiomeConfig implements IValidator<BiomeConfig> {
    @SerializedName("biomeSelector")
    public String biomeSelector = StringUtils.EMPTY;
    @SerializedName("_comment")
    public String comment = null;
    @SerializedName("clearSounds")
    public Boolean clearSounds = null;
    @SerializedName("fogColor")
    public String fogColor = null;
    @SerializedName("visibility")
    public Float visibility = null;
    @SerializedName("soundChance")
    public Integer soundChance = null;
    @SerializedName("acoustics")
    public List<AcousticConfig> acoustics = ImmutableList.of();

    @Override
    public void validate(final BiomeConfig obj) throws ValidationException {
        ValidationHelpers.notNull("biomeSelector", this.biomeSelector, Client.LOGGER::warn);
        if (this.visibility != null)
            ValidationHelpers.inRange("visibility", this.visibility, 0F, 1F, Client.LOGGER::warn);

        for (final AcousticConfig ac : this.acoustics)
            ac.validate(ac);
    }
}