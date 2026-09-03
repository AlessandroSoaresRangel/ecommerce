package com.seuprojeto.ecommerce.service;

import com.seuprojeto.ecommerce.exception.InvalidWebhookSignatureException;
import com.stripe.Stripe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa StripeGatewayImpl.parseWebhookEvent isoladamente: a verificação de
 * assinatura do Stripe (HMAC-SHA256) e a desserialização do evento são
 * inteiramente locais, sem chamada de rede — dá pra testar com um
 * payload/assinatura construídos à mão, sem mockar o SDK.
 *
 * O segundo teste cobre um bug real encontrado testando a integração
 * manualmente com o Stripe CLI: quando a apiVersion do evento não bate com a
 * fixada na SDK, event.getDataObjectDeserializer().getObject() volta vazio e
 * o webhook era ignorado silenciosamente (respondia 200 sem aprovar nada).
 * O fix foi cair para deserializeUnsafe() nesse caso.
 */
class StripeGatewayImplTest {

    private static final String WEBHOOK_SECRET = "whsec_test_secret";

    private StripeGatewayImpl gateway;

    @BeforeEach
    void setUp() {
        gateway = new StripeGatewayImpl();
        ReflectionTestUtils.setField(gateway, "webhookSecret", WEBHOOK_SECRET);
    }

    private String checkoutSessionCompletedPayload(String apiVersion) {
        return """
                {
                  "id": "evt_test_123",
                  "object": "event",
                  "api_version": "%s",
                  "created": 1700000000,
                  "type": "checkout.session.completed",
                  "data": {
                    "object": {
                      "id": "cs_test_123",
                      "object": "checkout.session",
                      "payment_intent": "pi_test_456",
                      "status": "complete",
                      "mode": "payment"
                    }
                  }
                }
                """.formatted(apiVersion);
    }

    private String sign(String payload, long timestamp) throws Exception {
        String signedPayload = timestamp + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) hex.append(String.format("%02x", b));
        return "t=" + timestamp + ",v1=" + hex;
    }

    @Test
    void parseiaEventoComApiVersionCompativelComASdk() throws Exception {
        String payload = checkoutSessionCompletedPayload(Stripe.API_VERSION);
        String signature = sign(payload, Instant.now().getEpochSecond());

        StripeGateway.WebhookEventResult result = gateway.parseWebhookEvent(payload, signature);

        assertThat(result.eventType()).isEqualTo("checkout.session.completed");
        assertThat(result.sessionId()).isEqualTo("cs_test_123");
        assertThat(result.paymentIntentId()).isEqualTo("pi_test_456");
    }

    @Test
    void caiParaDeserializeUnsafeQuandoApiVersionDoEventoNaoBateComADaSdk() throws Exception {
        String payload = checkoutSessionCompletedPayload("2018-01-01");
        String signature = sign(payload, Instant.now().getEpochSecond());

        StripeGateway.WebhookEventResult result = gateway.parseWebhookEvent(payload, signature);

        assertThat(result.eventType()).isEqualTo("checkout.session.completed");
        assertThat(result.sessionId()).isEqualTo("cs_test_123");
        assertThat(result.paymentIntentId()).isEqualTo("pi_test_456");
    }

    @Test
    void assinaturaInvalidaLancaInvalidWebhookSignatureException() {
        String payload = checkoutSessionCompletedPayload(Stripe.API_VERSION);

        assertThatThrownBy(() -> gateway.parseWebhookEvent(payload, "t=1,v1=assinatura-forjada"))
                .isInstanceOf(InvalidWebhookSignatureException.class);
    }

    @Test
    void assinaturaComTimestampMuitoAntigoLancaInvalidWebhookSignatureException() throws Exception {
        // Proteção contra replay: o Stripe rejeita assinaturas com timestamp
        // fora da tolerância padrão (5 minutos), mesmo que o HMAC seja válido.
        String payload = checkoutSessionCompletedPayload(Stripe.API_VERSION);
        long timestampAntigo = Instant.now().minusSeconds(3600).getEpochSecond();
        String signature = sign(payload, timestampAntigo);

        assertThatThrownBy(() -> gateway.parseWebhookEvent(payload, signature))
                .isInstanceOf(InvalidWebhookSignatureException.class);
    }
}
