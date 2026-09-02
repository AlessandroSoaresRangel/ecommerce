package com.seuprojeto.ecommerce;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Cobre as violações de constraint do banco que caíam no handler genérico
 * como 500 antes da correção no GlobalExceptionHandler: nome de categoria
 * duplicado e exclusão de categoria com produtos vinculados. Também cobre a
 * autorização básica (só ADMIN cria/exclui).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CategoryIntegrationTest extends AbstractIntegrationTest {

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

    @Test
    void listagemDeCategoriasEPublica() throws Exception {
        mockMvc.perform(get("/categories"))
                .andExpect(status().isOk());
    }

    @Test
    void usuarioComumNaoConsegueCriarCategoria() throws Exception {
        String token = registerAndGetAccessToken("customer-cat1@teste.com");
        String body = """
                {"name":"Categoria-%s","description":"desc"}
                """.formatted(System.nanoTime());

        mockMvc.perform(post("/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void deveRecusarCategoriaComNomeDuplicadoComoConflitoEmVezDeErroDeServidor() throws Exception {
        String adminToken = registerAndGetAccessToken("admin-cat2@teste.com");
        promoteToAdmin("admin-cat2@teste.com");
        String nome = "Categoria-Duplicada-" + System.nanoTime();
        String body = """
                {"name":"%s","description":"desc"}
                """.formatted(nome);

        mockMvc.perform(post("/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void deveRecusarExclusaoDeCategoriaComProdutoVinculadoComoConflitoEmVezDeErroDeServidor() throws Exception {
        String adminToken = registerAndGetAccessToken("admin-cat3@teste.com");
        promoteToAdmin("admin-cat3@teste.com");

        String categoryBody = """
                {"name":"Categoria-Com-Produto-%s","description":"desc"}
                """.formatted(System.nanoTime());
        MvcResult categoryResult = mockMvc.perform(post("/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(categoryBody))
                .andExpect(status().isCreated())
                .andReturn();
        long categoryId = objectMapper.readTree(categoryResult.getResponse().getContentAsString()).get("id").asLong();

        String productBody = """
                {"name":"Produto Vinculado","description":"desc","price":10.00,"stockQuantity":1,"imageUrl":null,"categoryId":%d}
                """.formatted(categoryId);
        mockMvc.perform(post("/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(productBody))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/categories/" + categoryId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void adminConsegueExcluirCategoriaSemProdutosVinculados() throws Exception {
        String adminToken = registerAndGetAccessToken("admin-cat4@teste.com");
        promoteToAdmin("admin-cat4@teste.com");

        String categoryBody = """
                {"name":"Categoria-Sem-Produto-%s","description":"desc"}
                """.formatted(System.nanoTime());
        MvcResult categoryResult = mockMvc.perform(post("/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(categoryBody))
                .andExpect(status().isCreated())
                .andReturn();
        long categoryId = objectMapper.readTree(categoryResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete("/categories/" + categoryId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void deveRetornar404AoExcluirCategoriaInexistente() throws Exception {
        String adminToken = registerAndGetAccessToken("admin-cat5@teste.com");
        promoteToAdmin("admin-cat5@teste.com");

        mockMvc.perform(delete("/categories/999999").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }
}
