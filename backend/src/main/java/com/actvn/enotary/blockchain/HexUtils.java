package com.actvn.enotary.blockchain;

import java.util.HexFormat;

public final class HexUtils {
    private HexUtils() {
    }

    public static String clean(String hex) {
        return hex == null ? "" : hex.replaceFirst("^0x", "");
    }

    public static boolean isFixedLengthHex(String hex, int length) {
        String cleaned = clean(hex);
        return cleaned.length() == length && cleaned.matches("[0-9a-fA-F]+");
    }

    public static byte[] bytes(String hex) {
        String cleaned = clean(hex);
        if (cleaned.isBlank()) {
            return new byte[0];
        }
        if (cleaned.length() % 2 != 0) {
            cleaned = "0" + cleaned;
        }
        return HexFormat.of().parseHex(cleaned);
    }

    public static String prefixed(byte[] bytes) {
        return "0x" + HexFormat.of().formatHex(bytes);
    }
}
