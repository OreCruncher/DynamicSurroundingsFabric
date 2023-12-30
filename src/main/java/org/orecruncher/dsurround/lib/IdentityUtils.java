package org.orecruncher.dsurround.lib;

import com.google.common.base.Preconditions;
import com.mojang.serialization.Codec;
import net.minecraft.util.Identifier;
import org.orecruncher.dsurround.Constants;

public class IdentityUtils {

    public static final Codec<Identifier> CODEC = Codec.STRING.xmap(s -> IdentityUtils.resolveIdentifier(Constants.MOD_ID, s), Identifier::toString).stable();

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
    public static Identifier resolveIdentifier(final String defaultDomain, String identifierString) {
        Preconditions.checkNotNull(defaultDomain);
        Preconditions.checkNotNull(identifierString);

        // If it looks like a tag need to strip of the prefix character
        if (identifierString.charAt(0) == '#')
            identifierString = identifierString.substring(1);

        Identifier res;

        if (identifierString.charAt(0) == '@') {
            // Sound is in the Minecraft namespace
            res = Identifier.of("minecraft", identifierString.substring(1));
        } else if (!identifierString.contains(":")) {
            // It's just a path so assume the specified namespace
            res = Identifier.of(defaultDomain, identifierString);
        } else {
            // It's a fully qualified location
            res = Identifier.splitOn(identifierString, ':');
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
    public static Identifier resolveIdentifier(String identifierString) {
        Preconditions.checkNotNull(identifierString);

        // If it looks like a tag need to strip of the prefix character
        if (identifierString.charAt(0) == '#')
            identifierString = identifierString.substring(1);

        Identifier res;

        if (identifierString.charAt(0) == '@') {
            // Sound is in the Minecraft namespace
            res = Identifier.of("minecraft", identifierString.substring(1));
        } else {
            // It's a fully qualified location
            res = Identifier.splitOn(identifierString, ':');
        }
        return res;
    }
}
