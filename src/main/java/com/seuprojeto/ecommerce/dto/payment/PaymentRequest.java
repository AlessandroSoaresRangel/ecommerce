package com.seuprojeto.ecommerce.dto.payment;

import jakarta.validation.constraints.NotBlank;

public record PaymentRequest(
        @NotBlank(message = "O método de pagamento é obrigatório")
        String method   // ex: "CREDIT_CARD", "PIX", "BOLETO"
) {}
