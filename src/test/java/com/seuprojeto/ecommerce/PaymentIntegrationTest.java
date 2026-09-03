package com.seuprojeto.ecommerce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seuprojeto.ecommerce.entity.Role;
import com.seuprojeto.ecommerce.entity.User;
import com.seuprojeto.ecommerce.repository.UserRepository;
import com.seuprojeto.ecommerce.service.StripeGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Ponta a ponta do pagamento contra um Postgres real — cobre especificamente
 * o bug de IDOR (usuário sem relação com o pedido conseguia iniciar/ver o
 * pagamento dele), a falta de validação de status (pedido já pago podia ser
 * "pago" de novo) e o fluxo completo Checkout Session + webhook do Stripe
 * (mockado via StripeGateway — nenhum teste toca a rede real do Stripe).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class PaymentIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @MockBean private StripeGateway stripeGateway;

    private String registerAndGetAccessToken(String email) throws Exception {
        String body = """
                {"name":"Usuário","email":"%s","password":"senha12345"}
                """.formatted(email);
        MvcResult result = mockMvc.perform(post("/auth/register").contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private void promoteToAdmin(String email) {
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setRole(Role.ADMIN);
        userRepository.save(user);
    }

    private long criarPedidoPendente(String adminToken, String buyerToken, String categoryTag) throws Exception {
        String categoryBody = """
                {"name":"Categoria-Pagamento-%s","description":"desc"}
                """.formatted(categoryTag);
        MvcResult categoryResult = mockMvc.perform(post("/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(categoryBody))
                .andExpect(status().isCreated())
                .andReturn();
        long categoryId = objectMapper.readTree(categoryResult.getResponse().getContentAsString()).get("id").asLong();

        String productBody = """
                {"name":"Produto Pagamento","description":"desc","price":30.00,"stockQuantity":10,"imageUrl":null,"weightKg":0.5,"heightCm":10,"widthCm":10,"lengthCm":10,"categoryId":%d}
                """.formatted(categoryId);
        MvcResult productResult = mockMvc.perform(post("/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(productBody))
                .andExpect(status().isCreated())
                .andReturn();
        long productId = objectMapper.readTree(productResult.getResponse().getContentAsString()).get("id").asLong();

        String cartBody = """
                {"productId":%d,"quantity":1}
                """.formatted(productId);
        mockMvc.perform(post("/cart/items")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType("application/json").content(cartBody))
                .andExpect(status().isOk());

        MvcResult checkoutResult = mockMvc.perform(post("/orders").header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(checkoutResult.getResponse().getContentAsString()).get("id").asLong();
    }

    // Consulta a API HTTP do Mailpit (o SMTP fake usado nos testes) pra
    // confirmar que o e-mail de mudança de status realmente "chegou" —
    // não basta confiar que o código chamou o mailSender, isso prova o
    // efeito de ponta a ponta.
    private JsonNode buscarEmailsRecebidosPor(String destinatario) throws Exception {
        String query = URLEncoder.encode("to:" + destinatario, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mailpitApiUrl() + "/api/v1/search?query=" + query))
                .GET()
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(response.body());
    }

    private String stubCheckoutSession() {
        String sessionId = "cs_test_" + UUID.randomUUID();
        when(stripeGateway.createCheckoutSession(any(), anyLong()))
                .thenReturn(new StripeGateway.CheckoutSessionResult(sessionId, "https://checkout.stripe.com/c/" + sessionId));
        return sessionId;
    }

    // Simula a chamada HTTP que o Stripe faria em /webhooks/stripe após o
    // pagamento ser concluído. O header de assinatura é um valor qualquer:
    // StripeGateway.parseWebhookEvent está mockado, então a verificação HMAC
    // real nunca acontece — é exatamente por isso que a interface existe.
    private void simularWebhookConcluido(String sessionId) throws Exception {
        when(stripeGateway.parseWebhookEvent(anyString(), anyString()))
                .thenReturn(new StripeGateway.WebhookEventResult(
                        "checkout.session.completed", sessionId, "pi_test_" + UUID.randomUUID()));

        mockMvc.perform(post("/webhooks/stripe")
                        .header("Stripe-Signature", "t=1,v1=fake-signature-bypassed-by-mock")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void donoConseguePagarOProprioPedidoEEleMudaParaPagoENotificaPorEmail() throws Exception {
        String adminToken = registerAndGetAccessToken("admin-pag1@teste.com");
        promoteToAdmin("admin-pag1@teste.com");
        String donoEmail = "dono-pag1@teste.com";
        String donoToken = registerAndGetAccessToken(donoEmail);
        long orderId = criarPedidoPendente(adminToken, donoToken, "1-" + System.nanoTime());

        String sessionId = stubCheckoutSession();
        mockMvc.perform(post("/orders/" + orderId + "/payment").header("Authorization", "Bearer " + donoToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId))
                .andExpect(jsonPath("$.checkoutUrl").value("https://checkout.stripe.com/c/" + sessionId));

        // Antes do webhook, o pedido continua PENDING.
        mockMvc.perform(get("/orders/" + orderId).header("Authorization", "Bearer " + donoToken))
                .andExpect(jsonPath("$.status").value("PENDING"));

        simularWebhookConcluido(sessionId);

        mockMvc.perform(get("/orders/" + orderId).header("Authorization", "Bearer " + donoToken))
                .andExpect(jsonPath("$.status").value("PAID"));

        JsonNode emailsRecebidos = buscarEmailsRecebidosPor(donoEmail);
        assertThat(emailsRecebidos.get("messages_count").asInt()).isEqualTo(1);
        assertThat(emailsRecebidos.get("messages").get(0).get("Subject").asText()).contains(String.valueOf(orderId));
    }

    @Test
    void usuarioSemRelacaoComOPedidoNaoConseguePagarNemVerOPagamento() throws Exception {
        String adminToken = registerAndGetAccessToken("admin-pag2@teste.com");
        promoteToAdmin("admin-pag2@teste.com");
        String donoToken = registerAndGetAccessToken("dono-pag2@teste.com");
        long orderId = criarPedidoPendente(adminToken, donoToken, "2-" + System.nanoTime());

        String estranhoToken = registerAndGetAccessToken("estranho-pag2@teste.com");

        mockMvc.perform(post("/orders/" + orderId + "/payment").header("Authorization", "Bearer " + estranhoToken))
                .andExpect(status().isNotFound());

        // O pedido continua PENDING: a tentativa indevida não teve efeito.
        mockMvc.perform(get("/orders/" + orderId).header("Authorization", "Bearer " + donoToken))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void naoConsegueRepagarUmPedidoQueJaEstaPago() throws Exception {
        String adminToken = registerAndGetAccessToken("admin-pag3@teste.com");
        promoteToAdmin("admin-pag3@teste.com");
        String donoToken = registerAndGetAccessToken("dono-pag3@teste.com");
        long orderId = criarPedidoPendente(adminToken, donoToken, "3-" + System.nanoTime());

        String sessionId = stubCheckoutSession();
        mockMvc.perform(post("/orders/" + orderId + "/payment").header("Authorization", "Bearer " + donoToken))
                .andExpect(status().isOk());
        simularWebhookConcluido(sessionId);

        mockMvc.perform(post("/orders/" + orderId + "/payment").header("Authorization", "Bearer " + donoToken))
                .andExpect(status().isConflict());
    }

    @Test
    void usuarioSemRelacaoComOPedidoNaoConsegueVerODetalheDoPagamento() throws Exception {
        String adminToken = registerAndGetAccessToken("admin-pag4@teste.com");
        promoteToAdmin("admin-pag4@teste.com");
        String donoToken = registerAndGetAccessToken("dono-pag4@teste.com");
        long orderId = criarPedidoPendente(adminToken, donoToken, "4-" + System.nanoTime());

        stubCheckoutSession();
        MvcResult checkoutResult = mockMvc.perform(post("/orders/" + orderId + "/payment")
                        .header("Authorization", "Bearer " + donoToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode checkout = objectMapper.readTree(checkoutResult.getResponse().getContentAsString());
        long paymentId = checkout.get("paymentId").asLong();

        mockMvc.perform(get("/payments/" + paymentId).header("Authorization", "Bearer " + donoToken))
                .andExpect(status().isOk());

        String estranhoToken = registerAndGetAccessToken("estranho-pag4@teste.com");
        mockMvc.perform(get("/payments/" + paymentId).header("Authorization", "Bearer " + estranhoToken))
                .andExpect(status().isNotFound());
    }
}
