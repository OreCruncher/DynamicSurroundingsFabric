package org.orecruncher.dsurround.xface;

import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.runtime.audio.SourceContext;

public interface ISourceContext {

    int getId();

    @Nullable
    SourceContext getData();

    void setData(@Nullable SourceContext data);
}