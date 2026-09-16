package com.seuprojeto.ecommerce.dto.order;

/**
 * Seleção de frete opcional para o checkout. Quando os três campos vêm
 * preenchidos, o backend recota o frete no gateway (nunca confia no preço
 * vindo do cliente) e soma o valor retornado ao total do pedido.
 */
public record CheckoutRequest(
        String destinationCep,
        String carrierName,
        String serviceName
) {
    public boolean hasShippingSelection() {
        return destinationCep != null && !destinationCep.isBlank()
                && carrierName != null && !carrierName.isBlank()
                && serviceName != null && !serviceName.isBlank();
    }
}
