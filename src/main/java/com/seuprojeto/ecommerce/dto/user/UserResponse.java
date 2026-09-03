package com.seuprojeto.ecommerce.dto.user;

import com.seuprojeto.ecommerce.entity.Role;

public record UserResponse(
        Long id,
        String name,
        String email,
        Role role
) {}
