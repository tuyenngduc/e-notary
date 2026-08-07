package com.actvn.enotary.service;

import com.actvn.enotary.dto.response.PaymentResponse;
import com.actvn.enotary.entity.NotaryRequest;
import com.actvn.enotary.entity.NotaryServiceType;
import com.actvn.enotary.entity.Payment;
import com.actvn.enotary.enums.PaymentStatus;
import com.actvn.enotary.enums.RequestStatus;
import com.actvn.enotary.exception.AppException;
import com.actvn.enotary.repository.NotaryRequestRepository;
import com.actvn.enotary.repository.NotaryServiceTypeRepository;
import com.actvn.enotary.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final NotaryRequestRepository notaryRequestRepository;
    private final NotaryServiceTypeRepository notaryServiceTypeRepository;

    @Value("${app.payment.bank-code:MB}")
    private String bankCode;

    @Value("${app.payment.account-number:0000000000}")
    private String accountNumber;

    @Value("${app.payment.account-name:E NOTARY}")
    private String accountName;

    @Transactional
    public PaymentResponse getByRequestForClient(UUID requestId, String clientEmail) {
        NotaryRequest request = notaryRequestRepository.findById(requestId)
                .orElseThrow(() -> new AppException("Khong tim thay ho so", HttpStatus.NOT_FOUND));
        assertOwner(request, clientEmail);

        Payment payment = paymentRepository.findByRequest_RequestId(requestId)
                .orElseGet(() -> createMissingPaymentInvoice(request));
        return toResponse(payment);
    }

    @Transactional
    public PaymentResponse confirmBankTransfer(UUID paymentId, String clientEmail) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new AppException("Khong tim thay hoa don thanh toan", HttpStatus.NOT_FOUND));
        NotaryRequest request = payment.getRequest();
        if (request == null) {
            throw new AppException("Du lieu thanh toan khong hop le", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        assertOwner(request, clientEmail);

        if (payment.getPaymentStatus() == PaymentStatus.SUCCESS) {
            return toResponse(payment);
        }
        if (payment.getPaymentStatus() == PaymentStatus.FAILED) {
            throw new AppException("Hoa don da that bai, khong the xac nhan lai", HttpStatus.BAD_REQUEST);
        }
        if (request.getStatus() != RequestStatus.AWAITING_PAYMENT) {
            throw new AppException("Ho so chua o trang thai cho thanh toan", HttpStatus.BAD_REQUEST);
        }

        ensureTransactionReference(payment);
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setPaymentMethod("BANK_TRANSFER");
        payment.setPaidAt(OffsetDateTime.now());
        Payment saved = paymentRepository.save(payment);

        request.setStatus(RequestStatus.COMPLETED);
        request.setUpdatedAt(OffsetDateTime.now());
        notaryRequestRepository.save(request);

        return toResponse(saved);
    }

    private void assertOwner(NotaryRequest request, String clientEmail) {
        if (request.getClient() == null
                || request.getClient().getEmail() == null
                || !request.getClient().getEmail().equalsIgnoreCase(clientEmail)) {
            throw new AppException("Ban khong co quyen truy cap hoa don nay", HttpStatus.FORBIDDEN);
        }
    }

    private void ensureTransactionReference(Payment payment) {
        if (payment.getTransactionReference() == null || payment.getTransactionReference().isBlank()) {
            String suffix = payment.getRequest() == null || payment.getRequest().getRequestId() == null
                    ? payment.getPaymentId().toString().substring(0, 8)
                    : payment.getRequest().getRequestId().toString().substring(0, 8);
            payment.setTransactionReference("ENOTARY " + suffix.toUpperCase());
        }
    }

    private Payment createMissingPaymentInvoice(NotaryRequest request) {
        if (request.getStatus() != RequestStatus.AWAITING_PAYMENT) {
            throw new AppException("Ho so chua co hoa don thanh toan", HttpStatus.NOT_FOUND);
        }

        Payment payment = new Payment();
        payment.setRequest(request);
        payment.setAmount(resolvePaymentAmount(request));
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment.setCreatedAt(OffsetDateTime.now());
        return paymentRepository.save(payment);
    }

    private BigDecimal resolvePaymentAmount(NotaryRequest request) {
        if (request.getContractType() == null) {
            return BigDecimal.ZERO;
        }

        return notaryServiceTypeRepository.findByServiceCode(request.getContractType().name())
                .map(NotaryServiceType::getBasePrice)
                .orElse(BigDecimal.ZERO);
    }

    private PaymentResponse toResponse(Payment payment) {
        return PaymentResponse.fromEntity(payment, bankCode, accountNumber, accountName);
    }
}
