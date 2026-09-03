package com.seuprojeto.ecommerce.controller;

import com.seuprojeto.ecommerce.dto.user.UserResponse;
import com.seuprojeto.ecommerce.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@Tag(name = "Usuários")
public class UserController {

    @Operation(
            summary = "Usuário autenticado",
            description = "Retorna id, nome, e-mail e role do usuário dono do token JWT enviado. " +
                    "Usado pelo frontend para saber quem está logado e se deve mostrar as telas de admin. " +
                    "Requer autenticação."
    )
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}
