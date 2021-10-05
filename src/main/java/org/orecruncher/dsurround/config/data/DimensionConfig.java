package org.orecruncher.dsurround.config.data;

import com.google.gson.annotations.SerializedName;
import joptsimple.internal.Strings;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.lib.validation.IValidator;
import org.orecruncher.dsurround.lib.validation.ValidationException;
import org.orecruncher.dsurround.lib.validation.ValidationHelpers;

@SuppressWarnings("unused")
@Environment(EnvType.CLIENT)
public class DimensionConfig implements IValidator<DimensionConfig> {
    @SerializedName("dimId")
    public String dimensionId = Strings.EMPTY;
    @SerializedName("seaLevel")
    public Integer seaLevel = null;
    @SerializedName("skyHeight")
    public Integer skyHeight = null;
    @SerializedName("cloudHeight")
    public Integer cloudHeight = null;
    @SerializedName("haze")
    public Boolean hasHaze;
    @SerializedName("alwaysOutside")
    public Boolean alwaysOutside;

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("dimensionId: ").append(this.dimensionId);
        if (this.seaLevel != null)
            builder.append(" seaLevel: ").append(this.seaLevel.intValue());
        if (this.skyHeight != null)
            builder.append(" skyHeight: ").append(this.skyHeight.intValue());
        if (this.cloudHeight != null)
            builder.append(" cloudHeight: ").append(this.cloudHeight.intValue());
        return builder.toString();
    }

    @Override
    public int hashCode() {
        return this.dimensionId != null ? this.dimensionId.hashCode() : 0;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj instanceof final DimensionConfig dc) {
            return (this.dimensionId != null && this.dimensionId.equals(dc.dimensionId));
        }
        return false;
    }

    @Override
    public void validate(final DimensionConfig obj) throws ValidationException {
        ValidationHelpers.isProperResourceLocation("dimId", this.dimensionId, Client.LOGGER);
    }
}