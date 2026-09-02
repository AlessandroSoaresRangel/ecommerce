package com.seuprojeto.ecommerce.controller;

import com.seuprojeto.ecommerce.dto.auth.*;
import com.seuprojeto.ecommerce.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticação")
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "Registrar novo usuário",
            description = "Cria uma conta com role CUSTOMER, já associada a um carrinho vazio, " +
                    "e retorna o par de tokens JWT (access + refresh). Falha com 409 se o e-mail já estiver em uso."
    )
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @Operation(
            summary = "Autenticar usuário",
            description = "Valida e-mail e senha e retorna um novo par de tokens JWT. " +
                    "Falha com 401 se as credenciais forem inválidas."
    )
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(
            summary = "Renovar tokens",
            description = "Recebe um refresh token válido e retorna um novo par de tokens JWT, " +
                    "sem exigir login novamente. Falha com 401 se o refresh token estiver inválido ou expirado."
    )
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }
}
