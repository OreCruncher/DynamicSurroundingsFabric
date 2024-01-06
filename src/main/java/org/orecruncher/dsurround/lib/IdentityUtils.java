package org.orecruncher.dsurround.lib;

import com.google.common.base.Preconditions;
import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import org.orecruncher.dsurround.Constants;

import java.util.Optional;

public class IdentityUtils {

    public static final Codec<ResourceLocation> CODEC = Codec.STRING.xmap(s -> IdentityUtils.resolveIdentifier(Constants.MOD_ID, s), ResourceLocation::toString).stable();

    /**
     * Parses a string into an Identifier based on the input.  If the input is prefixed with the tag signature '#'
     * it will be removed before parsing.  Identifiers prefixed with '@' will be assumed to be in the "minecraft"
     * namespace.  It is possible that an identifier could be prefixed with "#@" which would imply that it is
     * an identifier for the "minecraft" namespace and would appear to be a tag.
     *
     * @param defaultDomain The namespace name to use when parsing an identifier string that does not have a namespace specified.
     * @param identifierString The identifier string to be parsed
     * @return The resulting Identifier instance
     */
    public static ResourceLocation resolveIdentifier(final String defaultDomain, String identifierString) {
        Preconditions.checkNotNull(defaultDomain);
        Preconditions.checkNotNull(identifierString);

        // If it looks like a tag need to strip of the prefix character
        if (identifierString.charAt(0) == '#')
            identifierString = identifierString.substring(1);

        ResourceLocation res;

        if (identifierString.charAt(0) == '@') {
            // Sound is in the Minecraft namespace
            res = ResourceLocation.tryBuild("minecraft", identifierString.substring(1));
        } else if (!identifierString.contains(":")) {
            // It's just a path so assume the specified namespace
            res = ResourceLocation.tryBuild(defaultDomain, identifierString);
        } else {
            // It's a fully qualified location
            res = ResourceLocation.of(identifierString, ':');
        }
        return res;
    }

    /**
     * Parses a string into an Identifier based on the input.  If the input is prefixed with the tag signature '#'
     * it will be removed before parsing.  Identifiers prefixed with '@' will be assumed to be in the "minecraft"
     * namespace.  It is possible that an identifier could be prefixed with "#@" which would imply that it is
     * an identifier for the "minecraft" namespace and would appear to be a tag.
     *
     * @param identifierString The identifier string to be parsed
     * @return The resulting Identifier instance
     */
    public static ResourceLocation resolveIdentifier(String identifierString) {
        Preconditions.checkNotNull(identifierString);

        // If it looks like a tag need to strip of the prefix character
        if (identifierString.charAt(0) == '#')
            identifierString = identifierString.substring(1);

        ResourceLocation res;

        if (identifierString.charAt(0) == '@') {
            // Sound is in the Minecraft namespace
            res = ResourceLocation.tryBuild("minecraft", identifierString.substring(1));
        } else {
            // It's a fully qualified location
            res = ResourceLocation.of(identifierString, ':');
        }
        return res;
    }

    public static Optional<ResourceLocation> tryParse(String identifierString) {
        return Optional.ofNullable(ResourceLocation.tryParse(identifierString));
    }
}
