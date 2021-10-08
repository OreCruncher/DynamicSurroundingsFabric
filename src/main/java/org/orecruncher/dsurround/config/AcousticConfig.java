package org.orecruncher.dsurround.config;

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
    @SerializedName("soundEventId")
    public String soundEventId = null;
    @SerializedName("conditions")
    public String conditions = StringUtils.EMPTY;
    @SerializedName("weight")
    public int weight = 10;
    @SerializedName("type")
    public SoundEventType type = SoundEventType.LOOP;

    @Override
    public void validate(final AcousticConfig obj) throws ValidationException {
        ValidationHelpers.isProperResourceLocation("soundEventId", this.soundEventId, Client.LOGGER);
        ValidationHelpers.inRange("weight", this.weight, 1, Integer.MAX_VALUE, Client.LOGGER);
    }
}