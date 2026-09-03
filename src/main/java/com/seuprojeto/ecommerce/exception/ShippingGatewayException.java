package com.seuprojeto.ecommerce.exception;

public class ShippingGatewayException extends RuntimeException {
    public ShippingGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
