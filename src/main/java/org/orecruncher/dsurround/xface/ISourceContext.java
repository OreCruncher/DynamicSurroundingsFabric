package org.orecruncher.dsurround.xface;

import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.runtime.audio.SourceContext;

import java.util.Optional;

public interface ISourceContext {

    int getId();

    Optional<SourceContext> getData();

    void setData(@Nullable SourceContext data);
}