package com.seuprojeto.ecommerce.dto.order;

import jakarta.validation.constraints.AssertTrue;

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

    // Os três campos são um "tudo ou nada": aceitar só parte deles faria o
    // checkout ignorar o frete silenciosamente, sem avisar o cliente.
    @AssertTrue(message = "Para recotar o frete, informe destinationCep, carrierName e serviceName juntos")
    public boolean isShippingSelectionCompleteOrAbsent() {
        boolean anyFilled = (destinationCep != null && !destinationCep.isBlank())
                || (carrierName != null && !carrierName.isBlank())
                || (serviceName != null && !serviceName.isBlank());
        return !anyFilled || hasShippingSelection();
    }

    @AssertTrue(message = "CEP inválido")
    public boolean isDestinationCepValid() {
        return destinationCep == null || destinationCep.isBlank() || destinationCep.matches("\\d{5}-?\\d{3}");
    }
}
