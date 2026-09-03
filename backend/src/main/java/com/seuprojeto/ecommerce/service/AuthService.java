package com.seuprojeto.ecommerce.service;

import io.jsonwebtoken.JwtException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyInUseException(request.email());
        }

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(Role.CUSTOMER)
                .build();

        user = userRepository.save(user);

        // Todo usuário novo já sai com um carrinho vazio associado.
        Cart cart = Cart.builder().user(user).build();
        cartRepository.save(cart);

        return buildTokens(user);
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalStateException("Usuário autenticado não encontrado"));

        return buildTokens(user);
    }

    public AuthResponse refresh(RefreshTokenRequest request) {
        // Um refresh token malformado/adulterado/expirado não é um erro de
        // servidor: assim como no JwtAuthFilter, tratamos como credencial
        // inválida (401) em vez de deixar a exceção do jjwt virar um 500.
        try {
            String username = jwtUtil.extractUsername(request.refreshToken());
            User user = userRepository.findByEmail(username)
                    .orElseThrow(() -> new IllegalStateException("Usuário não encontrado"));

            if (!jwtUtil.isTokenValid(request.refreshToken(), user)) {
                throw new BadCredentialsException("Refresh token inválido ou expirado");
            }

            return buildTokens(user);
        } catch (JwtException ex) {
            throw new BadCredentialsException("Refresh token inválido ou expirado");
        }
    }

    private AuthResponse buildTokens(UserDetails user) {
        String accessToken = jwtUtil.generateToken(user);
        String refreshToken = jwtUtil.generateRefreshToken(user);
        return AuthResponse.of(accessToken, refreshToken);
    }
}
