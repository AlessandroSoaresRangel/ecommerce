package com.seuprojeto.ecommerce.service;

import com.seuprojeto.ecommerce.entity.Order;

/**
 * Isola as duas chamadas ao SDK do Stripe usadas pelo projeto (que, por sua
 * vez, expõe métodos estáticos difíceis de mockar) atrás de uma interface
 * fina que devolve records simples, para que PaymentService possa ser
 * testado sem tocar a rede real do Stripe.
 */
public interface StripeGateway {

    CheckoutSessionResult createCheckoutSession(Order order, Long paymentId);

    WebhookEventResult parseWebhookEvent(String payload, String signatureHeader);

    record CheckoutSessionResult(String sessionId, String checkoutUrl) {}

    record WebhookEventResult(String eventType, String sessionId, String paymentIntentId) {}
}
