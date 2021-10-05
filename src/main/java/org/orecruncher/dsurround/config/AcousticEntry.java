package org.orecruncher.dsurround.config;

import com.google.common.base.MoreObjects;
import net.minecraft.sound.SoundEvent;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.runtime.ConditionEvaluator;

public class AcousticEntry {

    private final SoundEvent acoustic;
    private final String conditions;

    public AcousticEntry(final SoundEvent acoustic, @Nullable final String condition) {
        this.acoustic = acoustic;
        this.conditions = condition != null ? condition : StringUtils.EMPTY;
    }

    public SoundEvent getAcoustic() {
        return this.acoustic;
    }

    public String getConditions() {
        return this.conditions;
    }

    public boolean matches() {
        return ConditionEvaluator.INSTANCE.check(this.conditions);
    }

    protected String getConditionsForLogging() {
        final String cond = getConditions();
        return cond.length() > 0 ? cond : "No Conditions";
    }

    public String toString() {
        return MoreObjects.toStringHelper(this)
                .addValue(getAcoustic().toString())
                .addValue(getConditionsForLogging())
                .toString();
    }
}