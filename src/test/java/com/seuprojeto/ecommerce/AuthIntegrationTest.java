package com.seuprojeto.ecommerce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seuprojeto.ecommerce.repository.CartRepository;
import com.seuprojeto.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testa a porta de entrada da API de ponta a ponta contra um Postgres real:
 * registro (com criação do carrinho), login e refresh — incluindo o caso de
 * refresh token malformado, que era tratado como erro de servidor (500) até
 * ser corrigido em AuthService.refresh().
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AuthIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private CartRepository cartRepository;

    private String registerBody(String email) {
        return """
                {"name":"Usuário Teste","email":"%s","password":"senha12345"}
                """.formatted(email);
    }

    @Test
    void deveRegistrarNovoUsuarioComCarrinhoAssociadoERetornarTokens() throws Exception {
        String email = "registro1@teste.com";

        mockMvc.perform(post("/auth/register")
                        .contentType("application/json")
                        .content(registerBody(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));

        var user = userRepository.findByEmail(email).orElseThrow();
        assertThat(cartRepository.findByUserId(user.getId())).isPresent();
    }

    @Test
    void deveRecusarRegistroComEmailJaCadastrado() throws Exception {
        String email = "duplicado@teste.com";
        mockMvc.perform(post("/auth/register").contentType("application/json").content(registerBody(email)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/register").contentType("application/json").content(registerBody(email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void deveRecusarRegistroComSenhaMenorQueOMinimo() throws Exception {
        String body = """
                {"name":"Usuário","email":"senhacurta@teste.com","password":"123"}
                """;

        mockMvc.perform(post("/auth/register").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void deveAutenticarUsuarioRegistradoComCredenciaisCorretas() throws Exception {
        String email = "login1@teste.com";
        mockMvc.perform(post("/auth/register").contentType("application/json").content(registerBody(email)))
                .andExpect(status().isCreated());

        String loginBody = """
                {"email":"%s","password":"senha12345"}
                """.formatted(email);

        mockMvc.perform(post("/auth/login").contentType("application/json").content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void deveRecusarLoginComSenhaIncorreta() throws Exception {
        String email = "login2@teste.com";
        mockMvc.perform(post("/auth/register").contentType("application/json").content(registerBody(email)))
                .andExpect(status().isCreated());

        String loginBody = """
                {"email":"%s","password":"senha-errada"}
                """.formatted(email);

        mockMvc.perform(post("/auth/login").contentType("application/json").content(loginBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveRenovarTokensComUmRefreshTokenValido() throws Exception {
        String email = "refresh1@teste.com";
        MvcResult registerResult = mockMvc.perform(post("/auth/register")
                        .contentType("application/json").content(registerBody(email)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(registerResult.getResponse().getContentAsString());
        String refreshToken = json.get("refreshToken").asText();

        String refreshBody = """
                {"refreshToken":"%s"}
                """.formatted(refreshToken);

        mockMvc.perform(post("/auth/refresh").contentType("application/json").content(refreshBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void deveRecusarRefreshComTokenMalformadoComoUnauthorizedEmVezDeErroDeServidor() throws Exception {
        String body = """
                {"refreshToken":"isto-nao-e-um-jwt-valido"}
                """;

        mockMvc.perform(post("/auth/refresh").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }
}
