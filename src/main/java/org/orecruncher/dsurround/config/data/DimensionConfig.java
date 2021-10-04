package org.orecruncher.dsurround.config.data;

import com.google.gson.annotations.SerializedName;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.lib.validation.IValidator;
import org.orecruncher.dsurround.lib.validation.ValidationException;
import org.orecruncher.dsurround.lib.validation.ValidationHelpers;

@Environment(EnvType.CLIENT)
public class DimensionConfig implements IValidator<DimensionConfig> {
    @SerializedName("dimId")
    public String dimensionId = null;
    @SerializedName("seaLevel")
    public Integer seaLevel = null;
    @SerializedName("skyHeight")
    public Integer skyHeight = null;
    @SerializedName("cloudHeight")
    public Integer cloudHeight = null;
    @SerializedName("haze")
    public Boolean hasHaze = null;
    @SerializedName("alwaysOutside")
    public Boolean alwaysOutside = null;

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        if (this.dimensionId != null)
            builder.append("dimensionId: ").append(this.dimensionId).append(" ");
        if (this.seaLevel != null)
            builder.append("seaLevel: ").append(this.seaLevel.intValue()).append(" ");
        if (this.skyHeight != null)
            builder.append("skyHeight: ").append(this.skyHeight.intValue()).append(" ");
        if (this.cloudHeight != null)
            builder.append("cloudHeight: ").append(this.cloudHeight.intValue()).append(" ");
        if (this.alwaysOutside != null)
            builder.append("alwaysOutside: ").append(this.alwaysOutside).append(" ");
        return builder.toString();
    }

    @Override
    public int hashCode() {
        return this.dimensionId != null ? this.dimensionId.hashCode() : 0;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj instanceof DimensionConfig) {
            final DimensionConfig dc = (DimensionConfig) obj;
            return (this.dimensionId != null && this.dimensionId.equals(dc.dimensionId));
        }
        return false;
    }

    @Override
    public void validate(final DimensionConfig obj) throws ValidationException {
        ValidationHelpers.isProperResourceLocation("dimId", this.dimensionId, Client.LOGGER::warn);
    }
}