package org.orecruncher.dsurround.lib.scripting;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

record ScriptIdentifier(String md5Hash) {

    static ScriptIdentifier from(String script) {
        try {
            // Step 1: Initialize MD5 MessageDigest instance
            MessageDigest md = MessageDigest.getInstance("MD5");

            // Step 2: Convert input string to UTF-8 bytes and update digest
            byte[] inputBytes = script.getBytes(StandardCharsets.UTF_8);
            md.update(inputBytes);

            // Step 3: Generate hash bytes
            byte[] hashBytes = md.digest();

            // Step 4: Convert hash bytes to 32-character hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0'); // Pad with leading zero
                }
                hexString.append(hex);
            }
            return new ScriptIdentifier(hexString.toString());

        } catch (NoSuchAlgorithmException e) {
            // Handle case where MD5 is not supported (extremely rare)
            throw new RuntimeException("MD5 algorithm not found in the JDK", e);
        }
    }
}
