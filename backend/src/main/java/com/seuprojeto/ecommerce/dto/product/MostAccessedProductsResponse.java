package com.seuprojeto.ecommerce.dto.product;

import java.util.List;

// DTO simples em vez de org.springframework.data.domain.Page: Page/PageImpl
// não tem um construtor "amigável" ao Jackson, o que quebra a
// deserialização ao ler de volta do cache Redis.
public record MostAccessedProductsResponse(
        List<ProductResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
