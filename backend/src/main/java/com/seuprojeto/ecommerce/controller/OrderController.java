package com.seuprojeto.ecommerce.controller;

import com.seuprojeto.ecommerce.dto.order.CheckoutRequest;
import com.seuprojeto.ecommerce.dto.order.OrderResponse;
import com.seuprojeto.ecommerce.dto.order.OrderStatusUpdateRequest;
import com.seuprojeto.ecommerce.entity.User;
import com.seuprojeto.ecommerce.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "Pedidos")
public class OrderController {

    private final OrderService orderService;

    @Operation(
            summary = "Fechar pedido (checkout)",
            description = "Cria um pedido a partir do carrinho do usuário autenticado: verifica e debita o " +
                    "estoque de cada produto, congela o preço de compra e esvazia o carrinho. Tudo em uma " +
                    "única transação. Se destinationCep/carrierName/serviceName forem informados, o frete é " +
                    "recotado no gateway e somado ao total do pedido. Falha com 400 se o carrinho estiver " +
                    "vazio ou a opção de frete não estiver mais disponível, ou 409 se não houver estoque " +
                    "suficiente ou houver conflito de concorrência em algum produto."
    )
    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> checkout(@AuthenticationPrincipal User user,
                                                   @Valid @RequestBody(required = false) CheckoutRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.checkout(user, request));
    }

    @Operation(
            summary = "Listar meus pedidos",
            description = "Retorna, de forma paginada, os pedidos do usuário autenticado. Requer autenticação."
    )
    @GetMapping("/orders")
    public Page<OrderResponse> myOrders(@AuthenticationPrincipal User user, Pageable pageable) {
        return orderService.findByUser(user, pageable);
    }

    @Operation(
            summary = "Buscar pedido por ID",
            description = "Retorna os detalhes de um pedido específico. Só o dono do pedido ou um usuário " +
                    "ADMIN podem visualizá-lo; caso contrário retorna 404 (para não revelar a existência do ID a terceiros)."
    )
    @GetMapping("/orders/{id}")
    public OrderResponse findById(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return orderService.findById(user, id);
    }

    @Operation(
            summary = "Listar todos os pedidos (admin)",
            description = "Retorna, de forma paginada, os pedidos de todos os usuários. Restrito a usuários com role ADMIN."
    )
    @GetMapping("/admin/orders")
    @PreAuthorize("hasRole('ADMIN')")
    public Page<OrderResponse> findAll(Pageable pageable) {
        return orderService.findAll(pageable);
    }

    @Operation(
            summary = "Atualizar status do pedido (admin)",
            description = "Altera o status de um pedido (PENDING, PAID, SHIPPED ou CANCELED). " +
                    "Restrito a usuários com role ADMIN."
    )
    @PatchMapping("/admin/orders/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public OrderResponse updateStatus(@PathVariable Long id, @Valid @RequestBody OrderStatusUpdateRequest request) {
        return orderService.updateStatus(id, request.status());
    }
}
