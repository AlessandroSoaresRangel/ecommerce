package com.seuprojeto.ecommerce.service;

import com.seuprojeto.ecommerce.dto.auth.AuthResponse;
import com.seuprojeto.ecommerce.dto.auth.LoginRequest;
import com.seuprojeto.ecommerce.dto.auth.RefreshTokenRequest;
import com.seuprojeto.ecommerce.dto.auth.RegisterRequest;
import com.seuprojeto.ecommerce.entity.Cart;
import com.seuprojeto.ecommerce.entity.Role;
import com.seuprojeto.ecommerce.entity.User;
import com.seuprojeto.ecommerce.exception.EmailAlreadyInUseException;
import com.seuprojeto.ecommerce.repository.CartRepository;
import com.seuprojeto.ecommerce.repository.UserRepository;
import com.seuprojeto.ecommerce.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Cobre os três fluxos de autenticação: registro (com o carrinho criado
 * junto), login e refresh de token — a porta de entrada de toda a API.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private CartRepository cartRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtUtil jwtUtil;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, cartRepository, passwordEncoder, authenticationManager, jwtUtil);
    }

    @Test
    void deveRegistrarNovoUsuarioComCarrinhoAssociadoERetornarTokens() {
        RegisterRequest request = new RegisterRequest("Novo Usuário", "novo@teste.com", "senha12345");

        when(userRepository.existsByEmail("novo@teste.com")).thenReturn(false);
        when(passwordEncoder.encode("senha12345")).thenReturn("hash-da-senha");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(jwtUtil.generateToken(any(UserDetails.class))).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(any(UserDetails.class))).thenReturn("refresh-token");

        AuthResponse response = authService.register(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getPassword()).isEqualTo("hash-da-senha");
        assertThat(savedUser.getRole()).isEqualTo(Role.CUSTOMER);

        ArgumentCaptor<Cart> cartCaptor = ArgumentCaptor.forClass(Cart.class);
        verify(cartRepository).save(cartCaptor.capture());
        assertThat(cartCaptor.getValue().getUser()).isEqualTo(savedUser);
    }

    @Test
    void deveRecusarRegistroComEmailJaExistente() {
        RegisterRequest request = new RegisterRequest("Fulano", "duplicado@teste.com", "senha12345");
        when(userRepository.existsByEmail("duplicado@teste.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(EmailAlreadyInUseException.class);

        verify(userRepository, never()).save(any());
        verify(cartRepository, never()).save(any());
    }

    @Test
    void deveAutenticarUsuarioComCredenciaisValidas() {
        LoginRequest request = new LoginRequest("user@teste.com", "senha12345");
        User user = User.builder().id(1L).email("user@teste.com").role(Role.CUSTOMER).build();

        when(userRepository.findByEmail("user@teste.com")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken(any(UserDetails.class))).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(any(UserDetails.class))).thenReturn("refresh-token");

        AuthResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        verify(authenticationManager).authenticate(any());
    }

    @Test
    void loginDevePropagarFalhaDeCredenciaisSemConsultarOUsuario() {
        LoginRequest request = new LoginRequest("user@teste.com", "senha-errada");
        doThrow(new BadCredentialsException("Credenciais inválidas"))
                .when(authenticationManager).authenticate(any());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);

        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void refreshDeveGerarNovosTokensParaUmRefreshTokenValido() {
        RefreshTokenRequest request = new RefreshTokenRequest("refresh-valido");
        User user = User.builder().id(1L).email("user@teste.com").role(Role.CUSTOMER).build();

        when(jwtUtil.extractUsername("refresh-valido")).thenReturn("user@teste.com");
        when(userRepository.findByEmail("user@teste.com")).thenReturn(Optional.of(user));
        when(jwtUtil.isTokenValid(eq("refresh-valido"), any(UserDetails.class))).thenReturn(true);
        when(jwtUtil.isTokenType("refresh-valido", JwtUtil.TYPE_REFRESH)).thenReturn(true);
        when(jwtUtil.generateToken(any(UserDetails.class))).thenReturn("novo-access-token");
        when(jwtUtil.generateRefreshToken(any(UserDetails.class))).thenReturn("novo-refresh-token");

        AuthResponse response = authService.refresh(request);

        assertThat(response.accessToken()).isEqualTo("novo-access-token");
        assertThat(response.refreshToken()).isEqualTo("novo-refresh-token");
    }

    @Test
    void refreshDeveRecusarTokenInvalidoOuExpirado() {
        RefreshTokenRequest request = new RefreshTokenRequest("refresh-invalido");
        User user = User.builder().id(1L).email("user@teste.com").role(Role.CUSTOMER).build();

        when(jwtUtil.extractUsername("refresh-invalido")).thenReturn("user@teste.com");
        when(userRepository.findByEmail("user@teste.com")).thenReturn(Optional.of(user));
        when(jwtUtil.isTokenValid(eq("refresh-invalido"), any(UserDetails.class))).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void refreshDeveRecusarAccessTokenUsadoComoRefresh() {
        RefreshTokenRequest request = new RefreshTokenRequest("access-token-como-refresh");
        User user = User.builder().id(1L).email("user@teste.com").role(Role.CUSTOMER).build();

        when(jwtUtil.extractUsername("access-token-como-refresh")).thenReturn("user@teste.com");
        when(userRepository.findByEmail("user@teste.com")).thenReturn(Optional.of(user));
        when(jwtUtil.isTokenValid(eq("access-token-como-refresh"), any(UserDetails.class))).thenReturn(true);
        when(jwtUtil.isTokenType("access-token-como-refresh", JwtUtil.TYPE_REFRESH)).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh(request))
                .isInstanceOf(BadCredentialsException.class);
    }
}
