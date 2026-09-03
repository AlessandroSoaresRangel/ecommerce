package com.seuprojeto.ecommerce;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /users/me é usado pelo frontend para descobrir quem está logado (nome
 * e role) a partir só do token — o JWT em si só carrega o e-mail (subject).
 * Autentica via /auth/register + Bearer token de verdade, mesmo padrão de
 * CheckoutIntegrationTest, porque o controller usa @AuthenticationPrincipal
 * com a entidade User.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class UserIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void deveRetornarDadosDoUsuarioAutenticado() throws Exception {
        String body = """
                {"name":"Maria Teste","email":"maria-me@teste.com","password":"senha12345"}
                """;
        MvcResult registerResult = mockMvc.perform(post("/auth/register").contentType("application/json").content(body))
                .andReturn();
        String accessToken = objectMapper.readTree(registerResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Maria Teste"))
                .andExpect(jsonPath("$.email").value("maria-me@teste.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void deveRecusarSemToken() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isForbidden());
    }
}
