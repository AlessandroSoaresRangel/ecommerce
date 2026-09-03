package com.seuprojeto.ecommerce.controller;

import com.seuprojeto.ecommerce.dto.payment.PaymentResponse;
import com.seuprojeto.ecommerce.dto.payment.StripeCheckoutResponse;
import com.seuprojeto.ecommerce.entity.User;
import com.seuprojeto.ecommerce.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "Pagamentos")
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(
            summary = "Iniciar pagamento do pedido (Stripe Checkout)",
            description = "Cria uma Stripe Checkout Session (modo teste) para um pedido existente e devolve a URL " +
                    "de pagamento hospedada pela Stripe. O pedido só é marcado como PAID quando o Stripe confirma " +
                    "o pagamento via webhook. Requer autenticação; só o dono do pedido ou um ADMIN podem iniciar o " +
                    "pagamento, e o pedido precisa estar com status PENDING."
    )
    @PostMapping("/orders/{orderId}/payment")
    public StripeCheckoutResponse createCheckout(@AuthenticationPrincipal User user, @PathVariable Long orderId) {
        return paymentService.createCheckoutSession(user, orderId);
    }

    @Operation(
            summary = "Buscar pagamento por ID",
            description = "Retorna os detalhes de um pagamento específico (status, método, ID da transação, " +
                    "data de confirmação). Só o dono do pedido associado ou um ADMIN podem visualizá-lo."
    )
    @GetMapping("/payments/{id}")
    public PaymentResponse findById(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return paymentService.findById(user, id);
    }
}
