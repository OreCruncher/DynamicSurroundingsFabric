package org.orecruncher.dsurround.xface;

import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.effects.entity.EntityEffectInfo;

public interface ILivingEntityExtended {
    EntityEffectInfo getEffectInfo();

    void setEffectInfo(@Nullable EntityEffectInfo info);
}
