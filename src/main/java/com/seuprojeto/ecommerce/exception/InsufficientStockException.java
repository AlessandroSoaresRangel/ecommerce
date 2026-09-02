package com.seuprojeto.ecommerce.exception;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String productName, int available, int requested) {
        super("Estoque insuficiente para '%s': disponível %d, solicitado %d"
                .formatted(productName, available, requested));
    }
}
