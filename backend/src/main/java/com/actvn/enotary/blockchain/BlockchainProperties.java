package com.actvn.enotary.blockchain;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.blockchain")
@Data
public class BlockchainProperties {
    private boolean enabled = true;
    private String rpcUrl = "http://localhost:8545";
    private Long chainId = 1337L;
    private String networkName = "Hyperledger Besu Local";
    private String nodeName = "besu-rpc-1";
    private String senderPrivateKey;
    private String contractAddress;
    private String pqPrivateKeyPath;
    private String pqPublicKeyPath;
    private String pqSignerJar;
    private String pqJavaExecutable = "java";
    private Long gasLimit = 300000L;
    private Long maxPriorityFeePerGas = 0L;
    private Long maxFeePerGas = 2_000_000_000L;
    private Integer receiptPollingAttempts = 30;
    private Long receiptPollingIntervalMillis = 1000L;
}
