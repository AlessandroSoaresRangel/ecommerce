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

    /**
     * Invalida uma Checkout Session que ainda esteja aberta (best-effort:
     * falhas são apenas logadas, nunca propagadas). Usado ao reemitir o
     * checkout de um pedido para que a URL antiga pare de ser pagável.
     */
    void expireSession(String sessionId);

    WebhookEventResult parseWebhookEvent(String payload, String signatureHeader);

    record CheckoutSessionResult(String sessionId, String checkoutUrl) {}

    record WebhookEventResult(String eventType, String sessionId, String paymentIntentId) {}
}
