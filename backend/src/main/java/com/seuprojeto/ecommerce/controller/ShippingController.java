package com.seuprojeto.ecommerce.controller;

import com.seuprojeto.ecommerce.dto.shipping.ShippingOptionResponse;
import com.seuprojeto.ecommerce.dto.shipping.ShippingQuoteRequest;
import com.seuprojeto.ecommerce.entity.User;
import com.seuprojeto.ecommerce.service.ShippingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Frete")
public class ShippingController {

    private final ShippingService shippingService;

    @Operation(
            summary = "Cotar frete do carrinho",
            description = "Calcula o custo de frete via Melhor Envio (sandbox) para os itens atuais do carrinho " +
                    "do usuário autenticado, dado um CEP de destino. Requer autenticação."
    )
    @PostMapping("/cart/shipping-quote")
    public List<ShippingOptionResponse> quote(@AuthenticationPrincipal User user,
                                               @Valid @RequestBody ShippingQuoteRequest request) {
        return shippingService.quote(user, request);
    }
}
