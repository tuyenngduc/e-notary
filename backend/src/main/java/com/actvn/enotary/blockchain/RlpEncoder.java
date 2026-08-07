package com.actvn.enotary.blockchain;

import java.math.BigInteger;

public final class RlpEncoder {
    private RlpEncoder() {
    }

    public static byte[] encodeBytes(byte[] input) {
        if (input.length == 0) {
            return new byte[]{(byte) 0x80};
        }
        if (input.length == 1 && (input[0] & 0xff) < 0x80) {
            return input;
        }
        return concat(encodeLength(input.length, 0x80), input);
    }

    public static byte[] encodeScalar(long value) {
        return encodeScalar(BigInteger.valueOf(value));
    }

    public static byte[] encodeScalar(BigInteger value) {
        if (value == null || BigInteger.ZERO.equals(value)) {
            return new byte[]{(byte) 0x80};
        }
        return encodeBytes(stripLeadingZeros(value.toByteArray()));
    }

    public static byte[] encodeList(byte[]... items) {
        byte[] payload = concat(items);
        return concat(encodeLength(payload.length, 0xc0), payload);
    }

    private static byte[] encodeLength(int length, int offset) {
        if (length < 56) {
            return new byte[]{(byte) (offset + length)};
        }
        byte[] lengthBytes = stripLeadingZeros(BigInteger.valueOf(length).toByteArray());
        return concat(new byte[]{(byte) (offset + 55 + lengthBytes.length)}, lengthBytes);
    }

    private static byte[] stripLeadingZeros(byte[] bytes) {
        int first = 0;
        while (first < bytes.length - 1 && bytes[first] == 0) {
            first++;
        }
        byte[] stripped = new byte[bytes.length - first];
        System.arraycopy(bytes, first, stripped, 0, stripped.length);
        return stripped;
    }

    public static byte[] concat(byte[]... arrays) {
        int length = 0;
        for (byte[] array : arrays) {
            length += array.length;
        }
        byte[] result = new byte[length];
        int offset = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }
        return result;
    }
}
