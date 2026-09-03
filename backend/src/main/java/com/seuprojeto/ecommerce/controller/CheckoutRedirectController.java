package com.seuprojeto.ecommerce.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Páginas puramente cosméticas para o navegador ter para onde ir depois do
 * redirect da Stripe Checkout — este projeto não tem frontend próprio.
 */
@RestController
@Tag(name = "Webhooks")
public class CheckoutRedirectController {

    @GetMapping("/checkout/success")
    public Map<String, String> success(@RequestParam(required = false) String session_id) {
        return Map.of(
                "message", "Pagamento recebido pela Stripe. A confirmação final chega assim que o webhook processar.",
                "sessionId", session_id == null ? "" : session_id);
    }

    @GetMapping("/checkout/cancel")
    public Map<String, String> cancel() {
        return Map.of("message", "Pagamento cancelado. Você pode tentar novamente.");
    }
}
