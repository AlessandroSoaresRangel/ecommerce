package com.seuprojeto.ecommerce.dto.product;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ProductRequest(

        @NotBlank(message = "O nome é obrigatório")
        String name,

        String description,

        @NotNull(message = "O preço é obrigatório")
        @DecimalMin(value = "0.0", inclusive = true, message = "O preço não pode ser negativo")
        BigDecimal price,

        @NotNull(message = "A quantidade em estoque é obrigatória")
        @Min(value = 0, message = "O estoque não pode ser negativo")
        Integer stockQuantity,

        String imageUrl,

        @NotNull(message = "A categoria é obrigatória")
        Long categoryId
) {}
