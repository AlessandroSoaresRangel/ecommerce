package com.seuprojeto.ecommerce.service;

import com.seuprojeto.ecommerce.dto.payment.PaymentRequest;
import com.seuprojeto.ecommerce.dto.payment.PaymentResponse;
import com.seuprojeto.ecommerce.entity.*;
import com.seuprojeto.ecommerce.exception.InvalidOrderStatusException;
import com.seuprojeto.ecommerce.exception.ResourceNotFoundException;
import com.seuprojeto.ecommerce.repository.OrderRepository;
import com.seuprojeto.ecommerce.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Simula um gateway de pagamento externo (Stripe, PagSeguro, etc).
 * Em vez de chamar uma API real, "aprova" o pagamento de forma
 * determinística para manter o projeto de portfólio autocontido e
 * sem dependência de credenciais externas.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    @Transactional
    public PaymentResponse pay(User requester, Long orderId, PaymentRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado: id " + orderId));

        requireOwnerOrAdmin(requester, order);

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidOrderStatusException(
                    "Pedido id " + orderId + " não pode ser pago: status atual é " + order.getStatus());
        }

        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElse(Payment.builder().order(order).build());

        // Gateway simulado: sempre aprova e gera um id de transação fake.
        payment.setMethod(request.method());
        payment.setStatus(PaymentStatus.APPROVED);
        payment.setTransactionId("SIMULATED-" + UUID.randomUUID());
        payment.setPaidAt(LocalDateTime.now());

        Payment saved = paymentRepository.save(payment);

        order.setStatus(OrderStatus.PAID);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PaymentResponse findById(User requester, Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento não encontrado: id " + id));

        requireOwnerOrAdmin(requester, payment.getOrder());

        return toResponse(payment);
    }

    // Mesma regra de OrderService.findById: só o dono do pedido ou um ADMIN
    // podem ver/pagar; qualquer outro recebe 404 em vez de 403, para não
    // revelar a existência do pedido/pagamento a terceiros.
    private void requireOwnerOrAdmin(User requester, Order order) {
        boolean isOwner = order.getUser().getId().equals(requester.getId());
        boolean isAdmin = requester.getRole() == Role.ADMIN;

        if (!isOwner && !isAdmin) {
            throw new ResourceNotFoundException("Pedido não encontrado: id " + order.getId());
        }
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getMethod(),
                payment.getStatus(),
                payment.getTransactionId(),
                payment.getPaidAt()
        );
    }
}
