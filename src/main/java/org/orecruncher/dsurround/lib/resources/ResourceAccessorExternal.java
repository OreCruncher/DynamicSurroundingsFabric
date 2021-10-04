package org.orecruncher.dsurround.lib.resources;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Environment(EnvType.CLIENT)
final class ResourceAccessorExternal extends ResourceAccessorBase {

    final Path filePath;

    public ResourceAccessorExternal(final File root, final Identifier location) {
        super(location);
        this.filePath = Paths.get(root.getPath(), location.getNamespace(), location.getPath());
    }

    @Override
    public boolean exists() {
        return Files.exists(this.filePath);
    }

    @Override
    protected byte[] getAsset() {
        try {
            return Files.readAllBytes(this.filePath);
        } catch (final Throwable t) {
            logError(t);
        }
        return null;
    }

    @Override
    public String toString() {
        return String.format("%s (%s)", super.toString(), this.filePath);
    }
}