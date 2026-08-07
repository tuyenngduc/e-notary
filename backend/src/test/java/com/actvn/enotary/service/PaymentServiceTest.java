package com.actvn.enotary.service;

import com.actvn.enotary.entity.NotaryRequest;
import com.actvn.enotary.entity.Payment;
import com.actvn.enotary.entity.User;
import com.actvn.enotary.enums.PaymentStatus;
import com.actvn.enotary.enums.RequestStatus;
import com.actvn.enotary.exception.AppException;
import com.actvn.enotary.repository.NotaryRequestRepository;
import com.actvn.enotary.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    @Mock
    PaymentRepository paymentRepository;

    @Mock
    NotaryRequestRepository notaryRequestRepository;

    PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, notaryRequestRepository);
        ReflectionTestUtils.setField(paymentService, "bankCode", "MB");
        ReflectionTestUtils.setField(paymentService, "accountNumber", "123456789");
        ReflectionTestUtils.setField(paymentService, "accountName", "E NOTARY");
    }

    @Test
    void confirmBankTransfer_marksPaymentSuccessAndRequestCompleted() {
        UUID paymentId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        User client = new User();
        client.setUserId(UUID.randomUUID());
        client.setEmail("client@example.com");

        NotaryRequest request = new NotaryRequest();
        request.setRequestId(requestId);
        request.setClient(client);
        request.setStatus(RequestStatus.AWAITING_PAYMENT);

        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setRequest(request);
        payment.setAmount(BigDecimal.valueOf(500000));
        payment.setPaymentStatus(PaymentStatus.PENDING);

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = paymentService.confirmBankTransfer(paymentId, "client@example.com");

        assertEquals(PaymentStatus.SUCCESS, payment.getPaymentStatus());
        assertEquals("BANK_TRANSFER", payment.getPaymentMethod());
        assertEquals(RequestStatus.COMPLETED, request.getStatus());
        assertEquals(PaymentStatus.SUCCESS, response.getPaymentStatus());
        verify(notaryRequestRepository).save(request);
    }

    @Test
    void confirmBankTransfer_rejectsDifferentClient() {
        UUID paymentId = UUID.randomUUID();
        User client = new User();
        client.setUserId(UUID.randomUUID());
        client.setEmail("client@example.com");

        NotaryRequest request = new NotaryRequest();
        request.setRequestId(UUID.randomUUID());
        request.setClient(client);
        request.setStatus(RequestStatus.AWAITING_PAYMENT);

        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setRequest(request);
        payment.setPaymentStatus(PaymentStatus.PENDING);

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        assertThrows(AppException.class, () -> paymentService.confirmBankTransfer(paymentId, "other@example.com"));
    }
}
