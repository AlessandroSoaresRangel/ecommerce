package com.seuprojeto.ecommerce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seuprojeto.ecommerce.entity.Role;
import com.seuprojeto.ecommerce.entity.User;
import com.seuprojeto.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Ponta a ponta do pagamento contra um Postgres real — cobre especificamente
 * o bug de IDOR (usuário sem relação com o pedido conseguia pagá-lo/vê-lo) e
 * a falta de validação de status (pedido já pago podia ser "pago" de novo),
 * ambos corrigidos em PaymentService.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class PaymentIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;

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
                {"name":"Produto Pagamento","description":"desc","price":30.00,"stockQuantity":10,"imageUrl":null,"categoryId":%d}
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

    @Test
    void donoConseguePagarOProprioPedidoEEleMudaParaPagoENotificaPorEmail() throws Exception {
        String adminToken = registerAndGetAccessToken("admin-pag1@teste.com");
        promoteToAdmin("admin-pag1@teste.com");
        String donoEmail = "dono-pag1@teste.com";
        String donoToken = registerAndGetAccessToken(donoEmail);
        long orderId = criarPedidoPendente(adminToken, donoToken, "1-" + System.nanoTime());

        String payBody = """
                {"method":"PIX"}
                """;
        mockMvc.perform(post("/orders/" + orderId + "/payment")
                        .header("Authorization", "Bearer " + donoToken)
                        .contentType("application/json").content(payBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

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
        String payBody = """
                {"method":"PIX"}
                """;

        mockMvc.perform(post("/orders/" + orderId + "/payment")
                        .header("Authorization", "Bearer " + estranhoToken)
                        .contentType("application/json").content(payBody))
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

        String payBody = """
                {"method":"PIX"}
                """;
        mockMvc.perform(post("/orders/" + orderId + "/payment")
                        .header("Authorization", "Bearer " + donoToken)
                        .contentType("application/json").content(payBody))
                .andExpect(status().isOk());

        mockMvc.perform(post("/orders/" + orderId + "/payment")
                        .header("Authorization", "Bearer " + donoToken)
                        .contentType("application/json").content(payBody))
                .andExpect(status().isConflict());
    }

    @Test
    void usuarioSemRelacaoComOPedidoNaoConsegueVerODetalheDoPagamento() throws Exception {
        String adminToken = registerAndGetAccessToken("admin-pag4@teste.com");
        promoteToAdmin("admin-pag4@teste.com");
        String donoToken = registerAndGetAccessToken("dono-pag4@teste.com");
        long orderId = criarPedidoPendente(adminToken, donoToken, "4-" + System.nanoTime());

        String payBody = """
                {"method":"PIX"}
                """;
        MvcResult payResult = mockMvc.perform(post("/orders/" + orderId + "/payment")
                        .header("Authorization", "Bearer " + donoToken)
                        .contentType("application/json").content(payBody))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode payment = objectMapper.readTree(payResult.getResponse().getContentAsString());
        long paymentId = payment.get("id").asLong();

        mockMvc.perform(get("/payments/" + paymentId).header("Authorization", "Bearer " + donoToken))
                .andExpect(status().isOk());

        String estranhoToken = registerAndGetAccessToken("estranho-pag4@teste.com");
        mockMvc.perform(get("/payments/" + paymentId).header("Authorization", "Bearer " + estranhoToken))
                .andExpect(status().isNotFound());
    }
}
