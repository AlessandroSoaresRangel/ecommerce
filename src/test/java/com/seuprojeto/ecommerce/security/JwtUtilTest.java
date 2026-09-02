package com.seuprojeto.ecommerce.security;

import com.seuprojeto.ecommerce.entity.Role;
import com.seuprojeto.ecommerce.entity.User;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cobre a geração e validação de token JWT, incluindo os casos que motivaram
 * o try/catch adicionado no JwtAuthFilter: token expirado, adulterado e
 * malformado devem falhar de forma previsível (lançando JwtException), nunca
 * silenciosamente aceitar algo inválido.
 */
class JwtUtilTest {

    private static final String SECRET =
            "chave-de-teste-precisa-ter-pelo-menos-256-bits-para-o-hmac-sha";

    private JwtUtil jwtUtil;
    private User user;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", 3_600_000L);
        ReflectionTestUtils.setField(jwtUtil, "refreshExpirationMs", 604_800_000L);

        user = User.builder().id(1L).email("user@teste.com").role(Role.CUSTOMER).build();
    }

    @Test
    void deveGerarTokenEExtrairOUsernameCorretamente() {
        String token = jwtUtil.generateToken(user);

        assertThat(token).isNotBlank();
        assertThat(jwtUtil.extractUsername(token)).isEqualTo("user@teste.com");
    }

    @Test
    void tokenGeradoDeveSerValidoParaOMesmoUsuario() {
        String token = jwtUtil.generateToken(user);

        assertThat(jwtUtil.isTokenValid(token, user)).isTrue();
    }

    @Test
    void tokenDeveSerInvalidoParaUmUsuarioDiferente() {
        String token = jwtUtil.generateToken(user);
        User outroUsuario = User.builder().id(2L).email("outro@teste.com").role(Role.CUSTOMER).build();

        assertThat(jwtUtil.isTokenValid(token, outroUsuario)).isFalse();
    }

    @Test
    void deveLancarExcecaoParaTokenExpirado() {
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", -1_000L);
        String tokenJaExpirado = jwtUtil.generateToken(user);

        assertThatThrownBy(() -> jwtUtil.extractUsername(tokenJaExpirado))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void deveLancarExcecaoParaTokenComAssinaturaAdulterada() {
        String token = jwtUtil.generateToken(user);
        String tokenAdulterado = token.substring(0, token.length() - 4) + "abcd";

        assertThatThrownBy(() -> jwtUtil.extractUsername(tokenAdulterado))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void deveLancarExcecaoParaTokenMalformado() {
        assertThatThrownBy(() -> jwtUtil.extractUsername("isto-nao-e-um-jwt"))
                .isInstanceOf(MalformedJwtException.class);
    }
}
