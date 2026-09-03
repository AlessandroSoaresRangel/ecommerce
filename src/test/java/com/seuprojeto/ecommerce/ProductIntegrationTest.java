package com.seuprojeto.ecommerce;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seuprojeto.ecommerce.dto.product.ProductRequest;
import com.seuprojeto.ecommerce.entity.Category;
import com.seuprojeto.ecommerce.entity.Role;
import com.seuprojeto.ecommerce.entity.User;
import com.seuprojeto.ecommerce.repository.CategoryRepository;
import com.seuprojeto.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Teste de integração completo: sobe um Postgres real via Testcontainers
 * (não H2), roda as migrations do Flyway e exercita a API através do
 * MockMvc de ponta a ponta, incluindo autenticação/autorização.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ProductIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    private Long categoryId;

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

    @BeforeEach
    void setUp() {
        Category category = categoryRepository.save(
                Category.builder().name("Eletrônicos-" + System.nanoTime()).build());
        categoryId = category.getId();
    }

    @Test
    void deveListarProdutosPublicamenteSemAutenticacao() throws Exception {
        mockMvc.perform(get("/products"))
                .andExpect(status().isOk());
    }

    @Test
    void deveRecusarCriacaoDeProdutoSemPermissaoDeAdmin() throws Exception {
        ProductRequest request = new ProductRequest(
                "Teclado mecânico", "Switches azuis", new BigDecimal("299.90"), 10, null,
                new BigDecimal("0.500"), 10, 10, 10, categoryId, null);

        mockMvc.perform(post("/products")
                        .with(user("cliente@teste.com").roles("CUSTOMER"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deveCriarProdutoComoAdmin() throws Exception {
        ProductRequest request = new ProductRequest(
                "Mouse gamer", "16000 DPI", new BigDecimal("199.90"), 25, null,
                new BigDecimal("0.500"), 10, 10, 10, categoryId, null);

        mockMvc.perform(post("/products")
                        .with(user("admin@teste.com").roles("ADMIN"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Mouse gamer"))
                .andExpect(jsonPath("$.stockQuantity").value(25));
    }

    @Test
    void includeInactiveSoTemEfeitoParaAdmin() throws Exception {
        String adminToken = registerAndGetAccessToken("admin-inactive@teste.com");
        promoteToAdmin("admin-inactive@teste.com");
        String customerToken = registerAndGetAccessToken("cliente-inactive@teste.com");

        ProductRequest request = new ProductRequest(
                "Produto Descontinuado " + System.nanoTime(), null, new BigDecimal("10.00"), 5, null,
                new BigDecimal("0.500"), 10, 10, 10, categoryId, null);
        MvcResult created = mockMvc.perform(post("/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        long productId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete("/products/" + productId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // Anônimo e cliente autenticado: includeInactive=true é ignorado, produto inativo não aparece.
        mockMvc.perform(get("/products").param("includeInactive", "true").param("name", request.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        mockMvc.perform(get("/products").param("includeInactive", "true").param("name", request.name())
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        // Admin com includeInactive=true enxerga o produto desativado.
        mockMvc.perform(get("/products").param("includeInactive", "true").param("name", request.name())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(productId))
                .andExpect(jsonPath("$.content[0].active").value(false));

        // Mesmo admin, sem o flag: comportamento padrão inalterado (não vê inativos).
        mockMvc.perform(get("/products").param("name", request.name())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        // Único jeito de reverter o soft delete: PUT com active=true.
        ProductRequest reactivate = new ProductRequest(
                request.name(), null, new BigDecimal("10.00"), 5, null,
                new BigDecimal("0.500"), 10, 10, 10, categoryId, true);
        mockMvc.perform(put("/products/" + productId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(reactivate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(get("/products").param("name", request.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(productId));
    }

    @Test
    void deveRetornar400QuandoPrecoForNegativo() throws Exception {
        ProductRequest request = new ProductRequest(
                "Produto inválido", null, new BigDecimal("-10.00"), 5, null,
                new BigDecimal("0.500"), 10, 10, 10, categoryId, null);

        mockMvc.perform(post("/products")
                        .with(user("admin@teste.com").roles("ADMIN"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.price").exists());
    }
}
