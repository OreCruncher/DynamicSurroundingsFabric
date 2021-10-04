package org.orecruncher.dsurround.config.data;

import com.google.gson.annotations.SerializedName;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.apache.commons.lang3.StringUtils;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.lib.validation.IValidator;
import org.orecruncher.dsurround.lib.validation.ValidationException;
import org.orecruncher.dsurround.lib.validation.ValidationHelpers;

@Environment(EnvType.CLIENT)
public class AcousticConfig implements IValidator<AcousticConfig> {
    @SerializedName("soundId")
    public String soundId = null;
    @SerializedName("conditions")
    public String conditions = StringUtils.EMPTY;
    @SerializedName("weight")
    public int weight = 10;
    @SerializedName("type")
    public String type = "loop";

    @Override
    public void validate(final AcousticConfig obj) throws ValidationException {
        ValidationHelpers.isProperResourceLocation("soundId", this.soundId, Client.LOGGER::warn);
        ValidationHelpers.inRange("weight", this.weight, 1, Integer.MAX_VALUE, Client.LOGGER::warn);
        ValidationHelpers.isOneOf("type", this.type, false, new String[]{"loop", "mood", "addition", "music"}, Client.LOGGER::warn);
    }
}