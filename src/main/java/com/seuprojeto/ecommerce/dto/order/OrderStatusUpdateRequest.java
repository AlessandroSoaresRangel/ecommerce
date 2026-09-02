package com.seuprojeto.ecommerce.dto.order;

import com.seuprojeto.ecommerce.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record OrderStatusUpdateRequest(
        @NotNull(message = "O status é obrigatório")
        OrderStatus status
) {}
