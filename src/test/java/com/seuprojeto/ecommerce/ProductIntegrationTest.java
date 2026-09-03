package com.seuprojeto.ecommerce;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seuprojeto.ecommerce.dto.product.ProductRequest;
import com.seuprojeto.ecommerce.entity.Category;
import com.seuprojeto.ecommerce.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

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

    private Long categoryId;

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
                new BigDecimal("0.500"), 10, 10, 10, categoryId);

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
                new BigDecimal("0.500"), 10, 10, 10, categoryId);

        mockMvc.perform(post("/products")
                        .with(user("admin@teste.com").roles("ADMIN"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Mouse gamer"))
                .andExpect(jsonPath("$.stockQuantity").value(25));
    }

    @Test
    void deveRetornar400QuandoPrecoForNegativo() throws Exception {
        ProductRequest request = new ProductRequest(
                "Produto inválido", null, new BigDecimal("-10.00"), 5, null,
                new BigDecimal("0.500"), 10, 10, 10, categoryId);

        mockMvc.perform(post("/products")
                        .with(user("admin@teste.com").roles("ADMIN"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.price").exists());
    }
}
