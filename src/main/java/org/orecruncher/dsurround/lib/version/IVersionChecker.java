package org.orecruncher.dsurround.lib.version;

import java.util.Optional;

public interface IVersionChecker {

    Optional<VersionResult> getUpdateText();
}
