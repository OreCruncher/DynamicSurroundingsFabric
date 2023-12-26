package org.orecruncher.dsurround.xface;

import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.effects.entity.EntityEffectInfo;

public interface ILivingEntityExtended {
    EntityEffectInfo dsurround_getEffectInfo();

    void dsurround_setEffectInfo(@Nullable EntityEffectInfo info);

    boolean dsurround_isJumping();

    int dsurround_getPotionSwirlColor();
    void dsurround_setPotionSwirlColor(int color);
}
