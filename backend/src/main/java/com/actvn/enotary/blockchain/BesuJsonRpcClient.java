package com.actvn.enotary.blockchain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
public class BesuJsonRpcClient {
    private final BlockchainProperties properties;
    private final ObjectMapper objectMapper;
    private final AtomicLong id = new AtomicLong(1);

    public JsonNode call(String method, List<Object> params) {
        try {
            RestClient client = RestClient.builder().baseUrl(properties.getRpcUrl()).build();
            Map<String, Object> request = Map.of(
                    "jsonrpc", "2.0",
                    "method", method,
                    "params", params == null ? List.of() : params,
                    "id", id.getAndIncrement()
            );
            JsonNode response = client.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null) {
                throw new JsonRpcException("Besu RPC returned empty response");
            }
            if (response.hasNonNull("error")) {
                throw new JsonRpcException("Besu RPC " + method + " failed: " + response.get("error"));
            }
            return response.get("result");
        } catch (JsonRpcException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new JsonRpcException("Cannot call Besu RPC method " + method, ex);
        }
    }

    public String callString(String method, List<Object> params) {
        JsonNode result = call(method, params);
        return result == null || result.isNull() ? null : result.asText();
    }

    public long callHexLong(String method, List<Object> params) {
        String hex = callString(method, params);
        return hexToLong(hex);
    }

    public static long hexToLong(String hex) {
        if (hex == null || hex.isBlank() || "0x".equals(hex)) {
            return 0L;
        }
        return Long.parseUnsignedLong(hex.replaceFirst("^0x", ""), 16);
    }

    public JsonNode valueToTree(Object value) {
        return objectMapper.valueToTree(value);
    }
}
