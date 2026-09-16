package com.seuprojeto.ecommerce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seuprojeto.ecommerce.entity.Role;
import com.seuprojeto.ecommerce.entity.User;
import com.seuprojeto.ecommerce.repository.UserRepository;
import com.seuprojeto.ecommerce.service.ShippingGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Ponta a ponta da cotação de frete contra um Postgres real — o ShippingGateway
 * é mockado (não toca a rede real do Melhor Envio), o mesmo padrão usado para
 * o StripeGateway em PaymentIntegrationTest.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ShippingIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @MockBean private ShippingGateway shippingGateway;

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

    private void adicionarProdutoAoCarrinho(String adminToken, String buyerToken, String categoryTag) throws Exception {
        String categoryBody = """
                {"name":"Categoria-Frete-%s","description":"desc"}
                """.formatted(categoryTag);
        MvcResult categoryResult = mockMvc.perform(post("/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(categoryBody))
                .andExpect(status().isCreated())
                .andReturn();
        long categoryId = objectMapper.readTree(categoryResult.getResponse().getContentAsString()).get("id").asLong();

        String productBody = """
                {"name":"Produto Frete","description":"desc","price":49.90,"stockQuantity":10,"imageUrl":null,"weightKg":0.7,"heightCm":8,"widthCm":15,"lengthCm":20,"categoryId":%d}
                """.formatted(categoryId);
        MvcResult productResult = mockMvc.perform(post("/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(productBody))
                .andExpect(status().isCreated())
                .andReturn();
        long productId = objectMapper.readTree(productResult.getResponse().getContentAsString()).get("id").asLong();

        String cartBody = """
                {"productId":%d,"quantity":2}
                """.formatted(productId);
        mockMvc.perform(post("/cart/items")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType("application/json").content(cartBody))
                .andExpect(status().isOk());
    }

    @Test
    void devolveAsOpcoesDeFreteParaOCarrinhoDoUsuario() throws Exception {
        String adminToken = registerAndGetAccessToken("admin-frete1@teste.com");
        promoteToAdmin("admin-frete1@teste.com");
        String buyerToken = registerAndGetAccessToken("comprador-frete1@teste.com");
        adicionarProdutoAoCarrinho(adminToken, buyerToken, "1-" + System.nanoTime());

        when(shippingGateway.calculateShipping(anyString(), any()))
                .thenReturn(List.of(
                        new ShippingGateway.ShippingQuoteResult("Correios", "PAC", new BigDecimal("37.79"), 9),
                        new ShippingGateway.ShippingQuoteResult("Correios", "SEDEX", new BigDecimal("58.20"), 2)));

        String quoteBody = """
                {"destinationCep":"20040-020"}
                """;
        MvcResult result = mockMvc.perform(post("/cart/shipping-quote")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType("application/json").content(quoteBody))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode options = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(options).hasSize(2);
        assertThat(options.get(0).get("serviceName").asText()).isEqualTo("PAC");
        assertThat(options.get(0).get("carrierName").asText()).isEqualTo("Correios");
    }

    @Test
    void checkoutSomaOFreteSelecionadoAoTotalDoPedido() throws Exception {
        String adminToken = registerAndGetAccessToken("admin-frete4@teste.com");
        promoteToAdmin("admin-frete4@teste.com");
        String buyerToken = registerAndGetAccessToken("comprador-frete4@teste.com");
        adicionarProdutoAoCarrinho(adminToken, buyerToken, "4-" + System.nanoTime());

        when(shippingGateway.calculateShipping(anyString(), any()))
                .thenReturn(List.of(new ShippingGateway.ShippingQuoteResult("Correios", "PAC", new BigDecimal("37.79"), 9)));

        // 2 unidades do Produto Frete a 49.90 = 99.80 + 37.79 de frete = 137.59
        String checkoutBody = """
                {"destinationCep":"20040-020","carrierName":"Correios","serviceName":"PAC"}
                """;
        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType("application/json").content(checkoutBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shippingCost").value(37.79))
                .andExpect(jsonPath("$.shippingCarrierName").value("Correios"))
                .andExpect(jsonPath("$.totalAmount").value(137.59));
    }

    @Test
    void checkoutRejeitaOpcaoDeFreteQueNaoEstaMaisDisponivel() throws Exception {
        String adminToken = registerAndGetAccessToken("admin-frete5@teste.com");
        promoteToAdmin("admin-frete5@teste.com");
        String buyerToken = registerAndGetAccessToken("comprador-frete5@teste.com");
        adicionarProdutoAoCarrinho(adminToken, buyerToken, "5-" + System.nanoTime());

        when(shippingGateway.calculateShipping(anyString(), any())).thenReturn(List.of());

        String checkoutBody = """
                {"destinationCep":"20040-020","carrierName":"Correios","serviceName":"PAC"}
                """;
        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType("application/json").content(checkoutBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejeitaCepInvalido() throws Exception {
        String token = registerAndGetAccessToken("comprador-frete2@teste.com");

        String quoteBody = """
                {"destinationCep":"abc"}
                """;
        mockMvc.perform(post("/cart/shipping-quote")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json").content(quoteBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void carrinhoVazioRetorna400() throws Exception {
        String token = registerAndGetAccessToken("comprador-frete3@teste.com");

        String quoteBody = """
                {"destinationCep":"20040-020"}
                """;
        mockMvc.perform(post("/cart/shipping-quote")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json").content(quoteBody))
                .andExpect(status().isBadRequest());
    }
}
