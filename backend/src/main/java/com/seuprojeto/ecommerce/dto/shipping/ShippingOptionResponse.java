package com.seuprojeto.ecommerce.dto.shipping;

import java.math.BigDecimal;

public record ShippingOptionResponse(
        String carrierName,
        String serviceName,
        BigDecimal price,
        Integer deliveryTimeDays
) {}
