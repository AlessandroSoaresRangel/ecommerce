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

        @NotNull(message = "O peso é obrigatório")
        @DecimalMin(value = "0.0", inclusive = false, message = "O peso deve ser maior que zero")
        BigDecimal weightKg,

        @NotNull(message = "A altura é obrigatória")
        @Min(value = 1, message = "A altura deve ser no mínimo 1 cm")
        Integer heightCm,

        @NotNull(message = "A largura é obrigatória")
        @Min(value = 1, message = "A largura deve ser no mínimo 1 cm")
        Integer widthCm,

        @NotNull(message = "O comprimento é obrigatório")
        @Min(value = 1, message = "O comprimento deve ser no mínimo 1 cm")
        Integer lengthCm,

        @NotNull(message = "A categoria é obrigatória")
        Long categoryId
) {}
