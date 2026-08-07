package com.actvn.enotary.blockchain;

import org.web3j.crypto.Hash;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class DocumentAnchorContract {
    public static final String REGISTER_SELECTOR = selector("registerDocument(bytes32,string,string)");
    public static final String IS_ANCHORED_SELECTOR = selector("isAnchored(bytes32)");

    private DocumentAnchorContract() {
    }

    public static String registerDocumentCalldata(String documentHash, String requestId, String documentId) {
        byte[] hash = HexUtils.bytes(documentHash);
        if (hash.length != 32) {
            throw new IllegalArgumentException("documentHash must be bytes32");
        }
        byte[] request = requestId.getBytes(StandardCharsets.UTF_8);
        byte[] document = documentId.getBytes(StandardCharsets.UTF_8);
        int dynamicStart = 32 * 3;
        byte[] encoded = RlpEncoder.concat(
                hash,
                word(dynamicStart),
                word(dynamicStart + dynamicArgLength(request)),
                dynamicArg(request),
                dynamicArg(document)
        );
        return "0x" + REGISTER_SELECTOR + HexUtils.clean(HexUtils.prefixed(encoded));
    }

    public static String isAnchoredCalldata(String documentHash) {
        return "0x" + IS_ANCHORED_SELECTOR + pad32(HexUtils.clean(documentHash));
    }

    public static boolean decodeBool(String value) {
        return value != null && !value.isBlank()
                && new java.math.BigInteger(HexUtils.clean(value), 16).signum() != 0;
    }

    private static String selector(String signature) {
        return Hash.sha3String(signature).substring(2, 10);
    }

    private static byte[] dynamicArg(byte[] value) {
        return RlpEncoder.concat(word(value.length), rightPad32(value));
    }

    private static int dynamicArgLength(byte[] value) {
        return 32 + rightPad32(value).length;
    }

    private static byte[] word(long value) {
        return HexUtils.bytes(pad32(Long.toHexString(value)));
    }

    private static String pad32(String hex) {
        String cleaned = HexUtils.clean(hex);
        return "0".repeat(Math.max(0, 64 - cleaned.length())) + cleaned;
    }

    private static byte[] rightPad32(byte[] value) {
        int paddedLength = ((value.length + 31) / 32) * 32;
        List<Byte> out = new ArrayList<>(paddedLength);
        for (byte b : value) {
            out.add(b);
        }
        while (out.size() < paddedLength) {
            out.add((byte) 0);
        }
        byte[] bytes = new byte[out.size()];
        for (int i = 0; i < out.size(); i++) {
            bytes[i] = out.get(i);
        }
        return bytes;
    }
}
