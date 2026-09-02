package com.seuprojeto.ecommerce.dto.cart;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(
        Long id,
        List<CartItemResponse> items,
        BigDecimal totalAmount
) {
    public record CartItemResponse(
            Long id,
            Long productId,
            String productName,
            BigDecimal unitPrice,
            Integer quantity,
            BigDecimal subtotal
    ) {}
}
