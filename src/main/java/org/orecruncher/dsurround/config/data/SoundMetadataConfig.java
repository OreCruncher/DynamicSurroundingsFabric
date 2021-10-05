package org.orecruncher.dsurround.config.data;

import com.google.common.collect.ImmutableList;
import com.google.gson.annotations.SerializedName;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.apache.commons.lang3.StringUtils;
import org.orecruncher.dsurround.lib.validation.IValidator;
import org.orecruncher.dsurround.lib.validation.ValidationException;

import java.util.List;

@Environment(EnvType.CLIENT)
public class SoundMetadataConfig implements IValidator<SoundMetadataConfig> {
    @SerializedName("title")
    public String title = null;
    @SerializedName("subtitle")
    public String caption = null;
    @SerializedName("credits")
    public List<String> credits = ImmutableList.of();

    /**
     * Indicates whether the settings in the instance are the default settings.
     *
     * @return true if the properties are the same as defaults; false otherwise
     */
    public boolean isDefault() {
        return StringUtils.isEmpty(this.title)
                && StringUtils.isEmpty(this.caption)
                && this.credits.size() == 0;
    }

    @Override
    public void validate(final SoundMetadataConfig obj) throws ValidationException {

    }
}