package com.seuprojeto.ecommerce.dto.payment;

public record StripeCheckoutResponse(
        Long paymentId,
        Long orderId,
        String checkoutUrl,
        String sessionId
) {}
