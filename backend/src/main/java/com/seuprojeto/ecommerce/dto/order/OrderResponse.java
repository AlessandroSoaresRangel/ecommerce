package com.seuprojeto.ecommerce.dto.order;

import com.seuprojeto.ecommerce.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        OrderStatus status,
        BigDecimal totalAmount,
        List<Item> items,
        LocalDateTime createdAt,
        String customerName,
        String customerEmail
) {
    public record Item(
            Long productId,
            String productName,
            Integer quantity,
            BigDecimal unitPriceAtPurchase
    ) {}
}
