package com.seuprojeto.ecommerce.dto.shipping;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ShippingQuoteRequest(
        @NotBlank(message = "O CEP de destino é obrigatório")
        @Pattern(regexp = "\\d{5}-?\\d{3}", message = "CEP inválido")
        String destinationCep
) {}
