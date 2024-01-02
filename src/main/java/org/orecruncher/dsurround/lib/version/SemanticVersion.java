/**
 * Copyright 2019 Patrick Ahlbrecht
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package org.orecruncher.dsurround.lib.version;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * <a href="https://raccoon.onyxbits.de/blog/single-class-java-semantic-versioning-parser-implementation/">Source link...</a>
 */
@SuppressWarnings("unused")
public final class SemanticVersion implements Comparable<SemanticVersion> {

    public static final Codec<SemanticVersion> CODEC = Codec.STRING
            .comapFlatMap(
                    SemanticVersion::manifest,
                    SemanticVersion::toString).stable();

    private static DataResult<SemanticVersion> manifest(String versionData) {
        try {
            return DataResult.success(SemanticVersion.parse(versionData));
        } catch (Throwable t) {
            return DataResult.error(t::getMessage);
        }
    }

    /**
     * Major version number
     */
    public final int major;

    /**
     * Minor version number
     */
    public final int minor;

    /**
     * Patch level
     */
    public final int patch;

    /**
     * Pre-release tags (potentially empty, but never null). This is private to
     * ensure read only access.
     */
    private final String[] preRelease;

    /**
     * Build meta-data tags (potentially empty, but never null). This is private
     * to ensure read only access.
     */
    private final String[] buildMeta;

    /**
     * Cached hashCode
     */
    private final int hashCode;

    /**
     * Construct a new plain version object
     *
     * @param major major version number. Must not be negative
     * @param minor minor version number. Must not be negative
     * @param patch patchlevel. Must not be negative.
     */
    public SemanticVersion(int major, int minor, int patch) {
        this(major, minor, patch, new String[0], new String[0]);
    }

    /**
     * Construct a fully featured version object with all bells and whistles.
     *
     * @param major      major version number (must not be negative)
     * @param minor      minor version number (must not be negative)
     * @param patch      patch level (must not be negative).
     * @param preRelease pre-release identifiers. Must not be null, all parts must match
     *                   "[0-9A-Za-z-]+".
     * @param buildMeta  build meta-identifiers. Must not be null, all parts must match
     *                   "[0-9A-Za-z-]+".
     */
    public SemanticVersion(int major, int minor, int patch, String[] preRelease,
                           String[] buildMeta) {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("Version numbers must be positive!");
        }
        this.buildMeta = new String[buildMeta.length];
        this.preRelease = new String[preRelease.length];
        Pattern p = Pattern.compile("[0-9A-Za-z-]+");
        for (int i = 0; i < preRelease.length; i++) {
            if (preRelease[i] == null || !p.matcher(preRelease[i]).matches()) {
                throw new IllegalArgumentException("Pre Release tag: " + i);
            }
            this.preRelease[i] = preRelease[i];
        }
        for (int i = 0; i < buildMeta.length; i++) {
            if (buildMeta[i] == null || !p.matcher(buildMeta[i]).matches()) {
                throw new IllegalArgumentException("Build Meta tag: " + i);
            }
            this.buildMeta[i] = buildMeta[i];
        }

        this.major = major;
        this.minor = minor;
        this.patch = patch;

        this.hashCode = this.toString().hashCode();
    }

    /**
     * Check if this version has a given build Meta tags.
     *
     * @param tag the tag to check for.
     * @return true if the tag is found in {@link SemanticVersion#buildMeta}.
     */
    public boolean hasBuildMeta(String tag) {
        for (String s : this.buildMeta) {
            if (s.equals(tag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if this version has a given pre-release tag.
     *
     * @param tag the tag to check for
     * @return true if the tag is found in {@link SemanticVersion#preRelease}.
     */
    public boolean hasPreRelease(String tag) {
        for (String s : this.preRelease) {
            if (s.equals(tag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the pre-release tags
     *
     * @return a potentially empty array, but never null.
     */
    public String[] getPreRelease() {
        return this.preRelease.clone();
    }

    /**
     * Get the build meta-tags
     *
     * @return a potentially empty array, but never null.
     */
    public String[] getBuildMeta() {
        return this.buildMeta.clone();
    }

    /**
     * Convenience method to check if this version is an update.
     *
     * @param v the other version object
     * @return true if this version is newer than the other one.
     */
    public boolean isUpdateFor(SemanticVersion v) {
        return compareTo(v) > 0;
    }

    /**
     * Convenience method to check if this version is a compatible update.
     *
     * @param v the other version object.
     * @return true if this version is newer and both have the same major version.
     */
    public boolean isCompatibleUpdateFor(SemanticVersion v) {
        return isUpdateFor(v) && this.major == v.major && this.major != 0;
    }

    /**
     * Convenience method to check if this is a stable version.
     *
     * @return true if the major version number is greater than zero and there are
     * no pre-release tags.
     */
    public boolean isStable() {
        return this.major > 0 && this.preRelease.length == 0;
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder();
        ret.append(this.major);
        ret.append('.');
        ret.append(this.minor);
        ret.append('.');
        ret.append(this.patch);
        if (this.preRelease.length > 0) {
            ret.append('-');
            for (int i = 0; i < this.preRelease.length; i++) {
                ret.append(this.preRelease[i]);
                if (i < this.preRelease.length - 1) {
                    ret.append('.');
                }
            }
        }
        if (this.buildMeta.length > 0) {
            ret.append('+');
            for (int i = 0; i < this.buildMeta.length; i++) {
                ret.append(this.buildMeta[i]);
                if (i < this.buildMeta.length - 1) {
                    ret.append('.');
                }
            }
        }
        return ret.toString();
    }

    // The Parser implementation below

    @Override
    public int hashCode() {
        return this.hashCode;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SemanticVersion ov)) {
            return false;
        }
        if (ov.major != this.major || ov.minor != this.minor || ov.patch != this.patch) {
            return false;
        }
        if (ov.preRelease.length != this.preRelease.length) {
            return false;
        }
        for (int i = 0; i < this.preRelease.length; i++) {
            if (!this.preRelease[i].equals(ov.preRelease[i])) {
                return false;
            }
        }
        if (ov.buildMeta.length != this.buildMeta.length) {
            return false;
        }
        for (int i = 0; i < this.buildMeta.length; i++) {
            if (!this.buildMeta[i].equals(ov.buildMeta[i])) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int compareTo(SemanticVersion v) {
        int result = this.major - v.major;
        if (result == 0) { // Same major
            result = this.minor - v.minor;
            if (result == 0) { // Same minor
                result = this.patch - v.patch;
                if (result == 0) { // Same patch
                    if (this.preRelease.length == 0 && v.preRelease.length > 0) {
                        result = 1; // No pre-release wins over pre-release
                    }
                    if (v.preRelease.length == 0 && this.preRelease.length > 0) {
                        result = -1; // No pre-release wins over pre-release
                    }
                    if (this.preRelease.length > 0 && v.preRelease.length > 0) {
                        int len = Math.min(this.preRelease.length, v.preRelease.length);
                        int count = 0;
                        for (count = 0; count < len; count++) {
                            result = comparePreReleaseTag(count, v);
                            if (result != 0) {
                                break;
                            }
                        }
                        if (result == 0 && count == len) { // Longer version wins.
                            result = this.preRelease.length - v.preRelease.length;
                        }
                    }
                }
            }
        }
        return result;
    }

    private int comparePreReleaseTag(int pos, SemanticVersion ov) {
        Integer here = null;
        Integer there = null;
        try {
            here = Integer.parseInt(this.preRelease[pos], 10);
        } catch (NumberFormatException ignored) {
        }
        try {
            there = Integer.parseInt(ov.preRelease[pos], 10);
        } catch (NumberFormatException ignored) {
        }
        if (here != null && there == null) {
            return -1; // Strings take precedence over numbers
        }
        if (here == null && there != null) {
            return 1; // Strings take precedence over numbers
        }
        if (here == null) {
            return (this.preRelease[pos].compareTo(ov.preRelease[pos])); // ASCII compare
        }
        return here.compareTo(there); // Number compare
    }

    public static SemanticVersion parse(String versionString) throws ParseException {
        var parser = new Parser();
        if (parser.parse(versionString)) {
            return new SemanticVersion(
                    parser.vParts[0],
                    parser.vParts[1],
                    parser.vParts[2],
                    parser.preParts.toArray(new String[0]),
                    parser.metaParts.toArray(new String[0]));
        }
        throw new ParseException(versionString, parser.errPos);
    }

    private static class Parser {

        private int errPos;
        private char[] input;
        public final int[] vParts;
        public ArrayList<String> preParts, metaParts;

        public Parser() {
            this.vParts = new int[3];
            this.preParts = new ArrayList<>();
            this.metaParts = new ArrayList<>();
        }

        public boolean parse(String versionString) {
            Arrays.fill(this.vParts, 0);
            this.preParts.clear();
            this.metaParts.clear();
            this.input = versionString.toCharArray();
            this.errPos = 0;
            return this.stateMajor();
        }

        private boolean stateMajor() {
            int pos = 0;
            while (pos < this.input.length && this.input[pos] >= '0' && this.input[pos] <= '9') {
                pos++; // match [0..9]+
            }
            if (pos == 0) { // Empty String -> Error
                return false;
            }
            if (this.input[0] == '0' && pos > 1) { // Leading zero
                return false;
            }

            this.vParts[0] = Integer.parseInt(new String(this.input, 0, pos), 10);

            if (this.input[pos] == '.') {
                return stateMinor(pos + 1);
            }

            return false;
        }

        private boolean stateMinor(int index) {
            int pos = index;
            while (pos < this.input.length && this.input[pos] >= '0' && this.input[pos] <= '9') {
                pos++;// match [0..9]+
            }
            if (pos == index) { // Empty String -> Error
                this.errPos = index;
                return false;
            }
            if (this.input[index] == '0' && pos - index > 1) { // Leading zero
                this.errPos = index;
                return false;
            }
            this.vParts[1] = Integer.parseInt(new String(this.input, index, pos - index), 10);

            if (this.input[pos] == '.') {
                return statePatch(pos + 1);
            }

            this.errPos = pos;
            return false;
        }

        private boolean statePatch(int index) {
            int pos = index;
            while (pos < this.input.length && this.input[pos] >= '0' && this.input[pos] <= '9') {
                pos++; // match [0..9]+
            }
            if (pos == index) { // Empty String -> Error
                this.errPos = index;
                return false;
            }
            if (this.input[index] == '0' && pos - index > 1) { // Leading zero
                this.errPos = index;
                return false;
            }

            this.vParts[2] = Integer.parseInt(new String(this.input, index, pos - index), 10);

            if (pos == this.input.length) { // We have a clean version string
                return true;
            }

            if (this.input[pos] == '+') { // We have build meta tags -> descend
                return stateMeta(pos + 1);
            }

            if (this.input[pos] == '-') { // We have pre-release tags -> descend
                return stateRelease(pos + 1);
            }

            this.errPos = pos; // We have junk
            return false;
        }

        private boolean stateRelease(int index) {
            int pos = index;
            while ((pos < this.input.length)
                    && ((this.input[pos] >= '0' && this.input[pos] <= '9')
                    || (this.input[pos] >= 'a' && this.input[pos] <= 'z')
                    || (this.input[pos] >= 'A' && this.input[pos] <= 'Z') || this.input[pos] == '-')) {
                pos++; // match [0..9a-zA-Z-]+
            }
            if (pos == index) { // Empty String -> Error
                this.errPos = index;
                return false;
            }

            this.preParts.add(new String(this.input, index, pos - index));
            if (pos == this.input.length) { // End of input
                return true;
            }
            if (this.input[pos] == '.') { // More parts -> descend
                return stateRelease(pos + 1);
            }
            if (this.input[pos] == '+') { // Build meta -> descend
                return stateMeta(pos + 1);
            }

            this.errPos = pos;
            return false;
        }

        private boolean stateMeta(int index) {
            int pos = index;
            while ((pos < this.input.length)
                    && ((this.input[pos] >= '0' && this.input[pos] <= '9')
                    || (this.input[pos] >= 'a' && this.input[pos] <= 'z')
                    || (this.input[pos] >= 'A' && this.input[pos] <= 'Z') || this.input[pos] == '-')) {
                pos++; // match [0..9a-zA-Z-]+
            }
            if (pos == index) { // Empty String -> Error
                this.errPos = index;
                return false;
            }

            this.metaParts.add(new String(this.input, index, pos - index));
            if (pos == this.input.length) { // End of input
                return true;
            }
            if (this.input[pos] == '.') { // More parts -> descend
                return stateMeta(pos + 1);
            }
            this.errPos = pos;
            return false;
        }
    }
}