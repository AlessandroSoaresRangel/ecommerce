package com.seuprojeto.ecommerce.dto.payment;

import com.seuprojeto.ecommerce.entity.PaymentStatus;

import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        Long orderId,
        String method,
        PaymentStatus status,
        String transactionId,
        LocalDateTime paidAt
) {}
