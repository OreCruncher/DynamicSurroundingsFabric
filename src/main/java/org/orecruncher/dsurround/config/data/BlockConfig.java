package org.orecruncher.dsurround.config.data;

import com.google.common.collect.ImmutableList;
import com.google.gson.annotations.SerializedName;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.config.AcousticConfig;
import org.orecruncher.dsurround.lib.validation.IValidator;
import org.orecruncher.dsurround.lib.validation.ValidationException;
import org.orecruncher.dsurround.lib.validation.ValidationHelpers;

import java.util.List;

@Environment(EnvType.CLIENT)
public class BlockConfig implements IValidator<BlockConfig> {
    @SerializedName("blocks")
    public List<String> blocks = ImmutableList.of();
    @SerializedName("soundReset")
    public Boolean soundReset = null;
    @SerializedName("chance")
    public String chance = null;
    @SerializedName("acoustics")
    public List<AcousticConfig> acoustics = ImmutableList.of();

    @Override
    public void validate(final BlockConfig obj) throws ValidationException {
        ValidationHelpers.hasElements("blocks", this.blocks, Client.LOGGER);

        if (this.chance != null)
            ValidationHelpers.notNullOrWhitespace("chance", this.chance, Client.LOGGER);

        for (final String s : blocks) {
            ValidationHelpers.notNullOrWhitespace("blocks", s, Client.LOGGER);
            ValidationHelpers.mustBeLowerCase("blocks", s, Client.LOGGER);
        }
        for (final AcousticConfig ac : this.acoustics)
            ac.validate(ac);
    }
}