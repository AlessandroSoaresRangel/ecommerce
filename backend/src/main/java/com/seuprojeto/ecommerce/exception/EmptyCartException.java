package com.seuprojeto.ecommerce.exception;

public class EmptyCartException extends RuntimeException {
    public EmptyCartException() {
        super("Não é possível criar um pedido com o carrinho vazio");
    }
}
