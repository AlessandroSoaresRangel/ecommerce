package com.seuprojeto.ecommerce.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
        @NotBlank(message = "O refresh token é obrigatório")
        String refreshToken
) {}
