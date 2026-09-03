package com.seuprojeto.ecommerce.controller;

import com.seuprojeto.ecommerce.service.PaymentService;
import com.seuprojeto.ecommerce.service.StripeGateway;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequiredArgsConstructor
@Tag(name = "Webhooks")
public class StripeWebhookController {

    private final StripeGateway stripeGateway;
    private final PaymentService paymentService;

    @PostMapping("/webhooks/stripe")
    public ResponseEntity<Void> receiveStripeEvent(
            HttpServletRequest request,
            @RequestHeader("Stripe-Signature") String signatureHeader) throws IOException {

        // Precisa dos bytes brutos do corpo (não um @RequestBody já
        // desserializado) para a verificação HMAC da assinatura do Stripe.
        String payload = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        StripeGateway.WebhookEventResult event = stripeGateway.parseWebhookEvent(payload, signatureHeader);
        paymentService.handleWebhookEvent(event);
        return ResponseEntity.ok().build();
    }
}
