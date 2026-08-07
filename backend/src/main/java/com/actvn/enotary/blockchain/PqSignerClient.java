package com.actvn.enotary.blockchain;

import com.actvn.enotary.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class PqSignerClient {
    private final BlockchainProperties properties;

    public byte[] sign(String txHash) {
        requireFile(properties.getPqSignerJar(), "PQ signer JAR");
        requireFile(properties.getPqPrivateKeyPath(), "PQ private key");
        log.info("[PQ-SIGNER] sign hash={} jar={} key={}",
                shorten(txHash), properties.getPqSignerJar(), properties.getPqPrivateKeyPath());
        String signature = run("sign", properties.getPqPrivateKeyPath(), txHash);
        log.info("[PQ-SIGNER] signature bytes={} prefix={}", HexUtils.bytes(signature).length, shorten(signature));
        return HexUtils.bytes(signature);
    }

    public byte[] publicKey() {
        requireFile(properties.getPqSignerJar(), "PQ signer JAR");
        requireFile(properties.getPqPublicKeyPath(), "PQ public key");
        log.info("[PQ-SIGNER] publicKey jar={} key={}", properties.getPqSignerJar(), properties.getPqPublicKeyPath());
        String publicKey = run("get-public-key", properties.getPqPublicKeyPath());
        log.info("[PQ-SIGNER] publicKey bytes={} prefix={}", HexUtils.bytes(publicKey).length, shorten(publicKey));
        return HexUtils.bytes(publicKey);
    }

    private String run(String... args) {
        try {
            String[] command = new String[args.length + 3];
            command[0] = properties.getPqJavaExecutable() == null || properties.getPqJavaExecutable().isBlank()
                    ? "java"
                    : properties.getPqJavaExecutable();
            command[1] = "-jar";
            command[2] = properties.getPqSignerJar();
            System.arraycopy(args, 0, command, 3, args.length);

            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            boolean finished = process.waitFor(Duration.ofSeconds(20).toMillis(), TimeUnit.MILLISECONDS);
            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.lines().reduce((first, second) -> second).orElse("");
            }
            if (!finished || process.exitValue() != 0 || output.isBlank()) {
                throw new AppException("Khong the ky giao dich bang khoa hau luong tu: " + output, HttpStatus.BAD_GATEWAY);
            }
            return output.trim().replaceFirst("^0x", "");
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AppException("Khong the chay PQ signer", HttpStatus.BAD_GATEWAY);
        }
    }

    private void requireFile(String file, String label) {
        if (file == null || file.isBlank() || !Files.isRegularFile(Path.of(file))) {
            throw new AppException(label + " chua duoc cau hinh hoac khong ton tai", HttpStatus.BAD_GATEWAY);
        }
    }

    private String shorten(String value) {
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
