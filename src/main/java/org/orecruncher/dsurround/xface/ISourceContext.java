package org.orecruncher.dsurround.xface;

import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.runtime.audio.SourceContext;

import java.util.Optional;

public interface ISourceContext {

    int dsurround_getId();

    Optional<SourceContext> dsurround_getData();

    void dsurround_setData(@Nullable SourceContext data);
}