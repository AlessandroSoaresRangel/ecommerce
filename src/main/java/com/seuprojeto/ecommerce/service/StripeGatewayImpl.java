package com.seuprojeto.ecommerce.service;

import com.seuprojeto.ecommerce.entity.Order;
import com.seuprojeto.ecommerce.exception.InvalidWebhookSignatureException;
import com.seuprojeto.ecommerce.exception.PaymentGatewayException;
import com.stripe.Stripe;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class StripeGatewayImpl implements StripeGateway {

    private static final Logger log = LoggerFactory.getLogger(StripeGatewayImpl.class);

    @Value("${stripe.secret-key}")
    private String secretKey;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    @Value("${stripe.success-url}")
    private String successUrl;

    @Value("${stripe.cancel-url}")
    private String cancelUrl;

    @Value("${stripe.currency}")
    private String currency;

    @PostConstruct
    void init() {
        Stripe.apiKey = secretKey;
    }

    @Override
    public CheckoutSessionResult createCheckoutSession(Order order, Long paymentId) {
        long amountInCents = order.getTotalAmount()
                .setScale(2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .longValueExact();

        // Um único line item agregando o total do pedido, em vez de um por
        // OrderItem: evita ter que formatar nome/imagem de produto pra
        // Stripe, fora de escopo pra este projeto.
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .setClientReferenceId(order.getId().toString())
                .putMetadata("order_id", order.getId().toString())
                .putMetadata("payment_id", paymentId.toString())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(currency)
                                .setUnitAmount(amountInCents)
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("Pedido #" + order.getId())
                                        .build())
                                .build())
                        .build())
                .build();

        try {
            Session session = Session.create(params);
            return new CheckoutSessionResult(session.getId(), session.getUrl());
        } catch (StripeException ex) {
            throw new PaymentGatewayException("Falha ao criar sessão de pagamento no Stripe: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void expireSession(String sessionId) {
        try {
            Session.retrieve(sessionId).expire();
        } catch (StripeException ex) {
            // Best-effort: se a sessão já estiver completa/expirada ou a
            // chamada falhar, apenas logamos — não é motivo para impedir a
            // criação de uma nova sessão de checkout.
            log.warn("Não foi possível expirar a sessão Stripe anterior {}: {}", sessionId, ex.getMessage());
        }
    }

    @Override
    public WebhookEventResult parseWebhookEvent(String payload, String signatureHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);
        } catch (SignatureVerificationException ex) {
            throw new InvalidWebhookSignatureException("Assinatura do webhook Stripe inválida");
        }

        String sessionId = null;
        String paymentIntentId = null;
        var deserializer = event.getDataObjectDeserializer();
        // getObject() só desserializa com segurança se a apiVersion do evento
        // bater com a da SDK; como a conta Stripe pode estar numa apiVersion
        // mais nova, caímos para deserializeUnsafe() quando isso falha.
        StripeObject dataObject = deserializer.getObject().orElseGet(() -> {
            try {
                return deserializer.deserializeUnsafe();
            } catch (EventDataObjectDeserializationException e) {
                throw new PaymentGatewayException("Falha ao desserializar evento do Stripe: " + e.getMessage(), e);
            }
        });
        if (dataObject instanceof Session session) {
            sessionId = session.getId();
            paymentIntentId = session.getPaymentIntent();
        }

        return new WebhookEventResult(event.getType(), sessionId, paymentIntentId);
    }
}
