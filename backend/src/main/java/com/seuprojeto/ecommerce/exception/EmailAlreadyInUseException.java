package com.seuprojeto.ecommerce.exception;

public class EmailAlreadyInUseException extends RuntimeException {
    public EmailAlreadyInUseException(String email) {
        super("O e-mail já está em uso: " + email);
    }
}
