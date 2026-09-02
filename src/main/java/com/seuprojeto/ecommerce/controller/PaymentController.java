package com.seuprojeto.ecommerce.controller;

import com.seuprojeto.ecommerce.dto.payment.PaymentRequest;
import com.seuprojeto.ecommerce.dto.payment.PaymentResponse;
import com.seuprojeto.ecommerce.entity.User;
import com.seuprojeto.ecommerce.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "Pagamentos")
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(
            summary = "Pagar pedido",
            description = "Simula o pagamento de um pedido existente: registra o método de pagamento e, " +
                    "se aprovado, marca o pedido como PAID. Requer autenticação; só o dono do pedido ou um " +
                    "ADMIN podem pagá-lo, e o pedido precisa estar com status PENDING."
    )
    @PostMapping("/orders/{orderId}/payment")
    public PaymentResponse pay(@AuthenticationPrincipal User user,
                                @PathVariable Long orderId,
                                @Valid @RequestBody PaymentRequest request) {
        return paymentService.pay(user, orderId, request);
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
