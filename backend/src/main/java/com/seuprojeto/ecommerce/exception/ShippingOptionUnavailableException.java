package com.seuprojeto.ecommerce.exception;

public class ShippingOptionUnavailableException extends RuntimeException {
    public ShippingOptionUnavailableException(String carrierName, String serviceName) {
        super("A opção de frete selecionada (" + carrierName + " · " + serviceName
                + ") não está mais disponível. Calcule o frete novamente.");
    }
}
