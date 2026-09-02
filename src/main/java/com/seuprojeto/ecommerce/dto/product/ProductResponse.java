package com.seuprojeto.ecommerce.dto.product;

import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        Integer stockQuantity,
        String imageUrl,
        Boolean active,
        Long categoryId,
        String categoryName
) {}
