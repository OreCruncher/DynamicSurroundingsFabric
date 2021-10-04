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
    @SerializedName("conditions")
    public String conditions = StringUtils.EMPTY;
    @SerializedName("_comment")
    public String comment = null;
    @SerializedName("aurora")
    public Boolean hasAurora = null;
    @SerializedName("fogColor")
    public String fogColor = null;
    @SerializedName("visibility")
    public Float visibility = null;
    @SerializedName("soundReset")
    public Boolean soundReset = null;
    @SerializedName("spotSoundChance")
    public Integer spotSoundChance = null;
    @SerializedName("acoustics")
    public List<AcousticConfig> acoustics = ImmutableList.of();

    @Override
    public String toString() {
        return this.comment == null ? this.conditions : this.comment;
    }

    @Override
    public void validate(final BiomeConfig obj) throws ValidationException {
        if (this.visibility != null)
            ValidationHelpers.inRange("visibility", this.visibility, 0F, 1F, Client.LOGGER::warn);

        for (final AcousticConfig ac : this.acoustics)
            ac.validate(ac);
    }
}