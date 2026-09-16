package com.seuprojeto.ecommerce.exception;

public class CategoryInUseException extends RuntimeException {
    public CategoryInUseException(Long categoryId) {
        super("A categoria id " + categoryId + " possui produtos vinculados e não pode ser removida");
    }
}
