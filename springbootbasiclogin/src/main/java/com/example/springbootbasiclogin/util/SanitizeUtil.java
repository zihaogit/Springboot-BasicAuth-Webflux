package com.example.springbootbasiclogin.util;

/**
 * Utility for sanitizing untrusted inputs before logging or processing,
 * preventing CRLF Log Injection (CWE-117) and log flooding.
 */
public final class SanitizeUtil {

    private static final int MAX_LOG_LENGTH = 255;

    private SanitizeUtil() {
        // Prevent instantiation
    }

    /**
     * Sanitizes untrusted user input before passing it to logger arguments.
     * Replaces CR and LF characters to prevent log forgery / CRLF injection,
     * and bounds the string length to prevent log flooding.
     *
     * @param input the raw untrusted input string
     * @return the sanitized string safe for logging
     */
    public static String forLog(String input) {
        if (input == null) {
            return "";
        }
        // Replace carriage return and line feed to neutralize CRLF log injection
        String sanitized = input.replace('\r', '_').replace('\n', '_');
        if (sanitized.length() > MAX_LOG_LENGTH) {
            return sanitized.substring(0, MAX_LOG_LENGTH) + "...[truncated]";
        }
        return sanitized;
    }
}
