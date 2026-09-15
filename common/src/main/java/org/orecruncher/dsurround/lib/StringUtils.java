package org.orecruncher.dsurround.lib;

import org.jetbrains.annotations.NotNull;

public class StringUtils {

    private static final String ELLIPSIS = "...";
    private static final int MAX_LENGTH_DEFAULT = 50;

    public record TruncatedResult(String text, String caratLine) {
        @Override
        public @NotNull String toString() {
            return this.text + "\n" + this.caratLine;
        }
    }

    public static TruncatedResult truncateWithCarat(String text, int focusIndex) {
        return truncateWithCarat(text, focusIndex, MAX_LENGTH_DEFAULT);
    }

    public static TruncatedResult truncateWithCarat(String text, int focusIndex, int maxLength) {
        if (text == null) return null;

        int totalLen = text.codePointCount(0, text.length());
        int ellipsisLen = ELLIPSIS.codePointCount(0, ELLIPSIS.length());

        focusIndex = Math.clamp(focusIndex, 0, totalLen - 1);

        if (totalLen <= maxLength) {
            String caratLine = " ".repeat(focusIndex) + "^";
            return new TruncatedResult(text, caratLine);
        }

        int initialContentLen = maxLength - (2 * ellipsisLen);
        if (initialContentLen <= 0) {
            throw new IllegalArgumentException("maxLength too small for ellipses");
        }

        int start = focusIndex - (initialContentLen / 2);
        int end = start + initialContentLen;

        if (start < 0) {
            start = 0;
            end = Math.min(totalLen, maxLength);
        } else if (end > totalLen) {
            end = totalLen;
            start = Math.max(0, totalLen - maxLength);
        }

        boolean needsPrefix = start > 0;
        boolean needsSuffix = end < totalLen;

        int numEllipses = (needsPrefix ? 1 : 0) + (needsSuffix ? 1 : 0);
        int finalContentLen = maxLength - (numEllipses * ellipsisLen);

        start = focusIndex - (finalContentLen / 2);
        end = start + finalContentLen;

        if (start <= 0) {
            start = 0;
            end = finalContentLen;
        } else if (end >= totalLen) {
            end = totalLen;
            start = totalLen - finalContentLen;
        }

        int startByteIdx = text.offsetByCodePoints(0, start);
        int endByteIdx = text.offsetByCodePoints(0, end);

        String prefix = (start > 0) ? ELLIPSIS : "";
        String suffix = (end < totalLen) ? ELLIPSIS : "";
        String truncatedStr = prefix + text.substring(startByteIdx, endByteIdx) + suffix;

        // Calculate offset in code points
        int caratOffset = (focusIndex - start) + prefix.codePointCount(0, prefix.length());
        String caratLine = " ".repeat(caratOffset) + "^";

        return new TruncatedResult(truncatedStr, caratLine);
    }
}
