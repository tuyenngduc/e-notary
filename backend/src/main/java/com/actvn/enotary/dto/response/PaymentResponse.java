package com.actvn.enotary.dto.response;

import com.actvn.enotary.entity.Payment;
import com.actvn.enotary.enums.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class PaymentResponse {
    private UUID paymentId;
    private UUID requestId;
    private BigDecimal amount;
    private PaymentStatus paymentStatus;
    private String paymentMethod;
    private String transactionReference;
    private String transferContent;
    private String bankCode;
    private String accountNumber;
    private String accountName;
    private String qrImageUrl;
    private OffsetDateTime createdAt;
    private OffsetDateTime paidAt;

    public static PaymentResponse fromEntity(
            Payment payment,
            String bankCode,
            String accountNumber,
            String accountName) {
        String transferContent = payment.getTransactionReference() != null && !payment.getTransactionReference().isBlank()
                ? payment.getTransactionReference()
                : "ENOTARY " + shortRequestCode(payment);
        String encodedAccountName = URLEncoder.encode(accountName, StandardCharsets.UTF_8);
        String encodedContent = URLEncoder.encode(transferContent, StandardCharsets.UTF_8);
        String amountText = payment.getAmount() == null ? "0" : payment.getAmount().toBigInteger().toString();
        String qrImageUrl = "https://img.vietqr.io/image/"
                + bankCode + "-" + accountNumber
                + "-compact2.png?amount=" + amountText
                + "&addInfo=" + encodedContent
                + "&accountName=" + encodedAccountName;

        return PaymentResponse.builder()
                .paymentId(payment.getPaymentId())
                .requestId(payment.getRequest() != null ? payment.getRequest().getRequestId() : null)
                .amount(payment.getAmount())
                .paymentStatus(payment.getPaymentStatus())
                .paymentMethod(payment.getPaymentMethod())
                .transactionReference(payment.getTransactionReference())
                .transferContent(transferContent)
                .bankCode(bankCode)
                .accountNumber(accountNumber)
                .accountName(accountName)
                .qrImageUrl(qrImageUrl)
                .createdAt(payment.getCreatedAt())
                .paidAt(payment.getPaidAt())
                .build();
    }

    private static String shortRequestCode(Payment payment) {
        if (payment.getRequest() == null || payment.getRequest().getRequestId() == null) {
            return "";
        }
        return payment.getRequest().getRequestId().toString().substring(0, 8).toUpperCase();
    }
}
