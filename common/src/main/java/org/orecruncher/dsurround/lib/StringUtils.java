package org.orecruncher.dsurround.lib;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
        String caratLine = "-".repeat(caratOffset) + "^";

        return new TruncatedResult(truncatedStr, caratLine);
    }

    public static String generateStackTrace(@Nullable StackTraceElement[] trace) {
        StringBuilder sb = new StringBuilder();
        sb.append("STACK TRACE:\n");

        if  (trace == null || trace.length == 0) {
            trace = Thread.currentThread().getStackTrace();
        }

        // Start at element 1 of the stack trace because 0 the method that generated
        // the trace (either this one or the parent).
        for (int i = 1; i < trace.length; i++) {
            var e = trace[i];
            if (e != null)
                sb.append("   ").append(e).append("\n");
        }

        return sb.toString();
    }
}
