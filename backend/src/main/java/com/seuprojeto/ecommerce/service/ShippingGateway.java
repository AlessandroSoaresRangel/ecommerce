package com.seuprojeto.ecommerce.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Isola a chamada à API do Melhor Envio (sandbox) atrás de uma interface
 * fina, no mesmo espírito de StripeGateway: devolve records simples, para
 * que ShippingService seja testável sem tocar a rede real.
 */
public interface ShippingGateway {

    List<ShippingQuoteResult> calculateShipping(String destinationCep, List<ShippingItem> items);

    record ShippingItem(String productId, BigDecimal weightKg, int heightCm, int widthCm,
                         int lengthCm, BigDecimal unitPrice, int quantity) {}

    record ShippingQuoteResult(String carrierName, String serviceName, BigDecimal price,
                                Integer deliveryTimeDays) {}
}
