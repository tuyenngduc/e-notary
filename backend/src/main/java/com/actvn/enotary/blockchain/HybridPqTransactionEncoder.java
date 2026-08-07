package com.actvn.enotary.blockchain;

import com.actvn.enotary.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Sign;

import java.math.BigInteger;

@Component
@RequiredArgsConstructor
@Slf4j
public class HybridPqTransactionEncoder {
    private static final byte HYBRID_PQ_TYPE = 0x05;

    private final PqSignerClient pqSignerClient;

    public String encodeSigned(HybridPqTransaction tx, String senderPrivateKey) {
        if (!HexUtils.isFixedLengthHex(senderPrivateKey, 64)) {
            throw new AppException("Chua cau hinh private key gui giao dich Besu", HttpStatus.BAD_GATEWAY);
        }
        byte[] unsignedTx = encodeUnsigned(tx);
        String signingHash = Hash.sha3(HexUtils.prefixed(unsignedTx));
        log.info("[TX-SIGN] chainId={} nonce={} gasLimit={} to={} value={} dataLen={} hash={}",
                tx.chainId(), tx.nonce(), tx.gasLimit(), shortHex(tx.to()), tx.value(), tx.data() == null ? 0 : tx.data().length(), shortHex(signingHash));
        Sign.SignatureData ecdsa = Sign.signMessage(
                HexUtils.bytes(signingHash),
                ECKeyPair.create(new BigInteger(HexUtils.clean(senderPrivateKey), 16)),
                false
        );
        int yParity = ecdsa.getV()[0] >= 27 ? ecdsa.getV()[0] - 27 : ecdsa.getV()[0];
        byte[] pqSignature = pqSignerClient.sign(signingHash);
        byte[] pqPublicKey = pqSignerClient.publicKey();
        log.info("[TX-SIGN] ecdsa v={} r={} s={}",
                yParity, shortHex(HexUtils.prefixed(ecdsa.getR())), shortHex(HexUtils.prefixed(ecdsa.getS())));
        log.info("[TX-SIGN] pqSigBytes={} pqPubBytes={}", pqSignature.length, pqPublicKey.length);

        byte[] signedFields = RlpEncoder.encodeList(
                RlpEncoder.encodeScalar(tx.chainId()),
                RlpEncoder.encodeScalar(tx.nonce()),
                RlpEncoder.encodeScalar(tx.maxPriorityFeePerGas()),
                RlpEncoder.encodeScalar(tx.maxFeePerGas()),
                RlpEncoder.encodeScalar(tx.gasLimit()),
                RlpEncoder.encodeBytes(HexUtils.bytes(tx.to())),
                RlpEncoder.encodeScalar(tx.value()),
                RlpEncoder.encodeBytes(HexUtils.bytes(tx.data())),
                RlpEncoder.encodeList(),
                RlpEncoder.encodeScalar(yParity),
                RlpEncoder.encodeScalar(new BigInteger(1, ecdsa.getR())),
                RlpEncoder.encodeScalar(new BigInteger(1, ecdsa.getS())),
                RlpEncoder.encodeBytes(pqSignature),
                RlpEncoder.encodeBytes(pqPublicKey)
        );
        String raw = HexUtils.prefixed(RlpEncoder.concat(new byte[]{HYBRID_PQ_TYPE}, signedFields));
        log.info("[TX-SIGN] rawTxBytes={} rawTx={}", HexUtils.bytes(raw).length, shortHex(raw));
        return raw;
    }

    public byte[] encodeUnsigned(HybridPqTransaction tx) {
        byte[] unsignedFields = RlpEncoder.encodeList(
                RlpEncoder.encodeScalar(tx.chainId()),
                RlpEncoder.encodeScalar(tx.nonce()),
                RlpEncoder.encodeScalar(tx.maxPriorityFeePerGas()),
                RlpEncoder.encodeScalar(tx.maxFeePerGas()),
                RlpEncoder.encodeScalar(tx.gasLimit()),
                RlpEncoder.encodeBytes(HexUtils.bytes(tx.to())),
                RlpEncoder.encodeScalar(tx.value()),
                RlpEncoder.encodeBytes(HexUtils.bytes(tx.data())),
                RlpEncoder.encodeList()
        );
        return RlpEncoder.concat(new byte[]{HYBRID_PQ_TYPE}, unsignedFields);
    }

    public record HybridPqTransaction(
            long chainId,
            long nonce,
            long maxPriorityFeePerGas,
            long maxFeePerGas,
            long gasLimit,
            String to,
            long value,
            String data
    ) {
    }

    private String shortHex(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String cleaned = value.replaceFirst("^0x", "");
        if (cleaned.length() <= 24) {
            return cleaned;
        }
        return cleaned.substring(0, 12) + "..." + cleaned.substring(cleaned.length() - 10);
    }
}
