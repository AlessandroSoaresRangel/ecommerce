package com.seuprojeto.ecommerce.service;

import com.seuprojeto.ecommerce.dto.payment.PaymentResponse;
import com.seuprojeto.ecommerce.dto.payment.StripeCheckoutResponse;
import com.seuprojeto.ecommerce.entity.*;
import com.seuprojeto.ecommerce.exception.InvalidOrderStatusException;
import com.seuprojeto.ecommerce.exception.ResourceNotFoundException;
import com.seuprojeto.ecommerce.repository.OrderRepository;
import com.seuprojeto.ecommerce.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Integra com o Stripe Checkout (modo teste): cria uma Checkout Session
 * hospedada pela Stripe e devolve a URL para o cliente pagar. A aprovação
 * definitiva só acontece quando o webhook do Stripe confirma o pagamento
 * (handleWebhookEvent) — este serviço nunca aprova um pagamento sozinho.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final String METHOD_STRIPE = "STRIPE";

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final EmailService emailService;
    private final StripeGateway stripeGateway;

    @Transactional
    public StripeCheckoutResponse createCheckoutSession(User requester, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado: id " + orderId));

        requireOwnerOrAdmin(requester, order);

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidOrderStatusException(
                    "Pedido id " + orderId + " não pode ser pago: status atual é " + order.getStatus());
        }

        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseGet(() -> Payment.builder().order(order).build());
        payment.setMethod(METHOD_STRIPE);
        payment.setStatus(PaymentStatus.PENDING);
        Payment saved = paymentRepository.save(payment);

        StripeGateway.CheckoutSessionResult session = stripeGateway.createCheckoutSession(order, saved.getId());
        saved.setStripeSessionId(session.sessionId());
        Payment updated = paymentRepository.save(saved);

        return new StripeCheckoutResponse(updated.getId(), order.getId(), session.checkoutUrl(), session.sessionId());
    }

    /**
     * Chamado pelo StripeWebhookController depois que a assinatura já foi
     * verificada. Idempotente: eventos repetidos para um Payment já
     * APPROVED/REJECTED são ignorados.
     */
    @Transactional
    public void handleWebhookEvent(StripeGateway.WebhookEventResult event) {
        switch (event.eventType()) {
            case "checkout.session.completed", "checkout.session.async_payment_succeeded" ->
                    approvePayment(event.sessionId(), event.paymentIntentId());
            case "checkout.session.expired", "checkout.session.async_payment_failed" ->
                    rejectPayment(event.sessionId());
            default -> log.debug("Evento Stripe ignorado: {}", event.eventType());
        }
    }

    private void approvePayment(String sessionId, String paymentIntentId) {
        if (sessionId == null) return;
        paymentRepository.findByStripeSessionId(sessionId).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.APPROVED) return;

            payment.setStatus(PaymentStatus.APPROVED);
            payment.setTransactionId(paymentIntentId);
            payment.setPaidAt(LocalDateTime.now());
            paymentRepository.save(payment);

            Order order = payment.getOrder();
            OrderStatus previousStatus = order.getStatus();
            order.setStatus(OrderStatus.PAID);
            emailService.sendOrderStatusChangedEmail(order, previousStatus);
        });
    }

    private void rejectPayment(String sessionId) {
        if (sessionId == null) return;
        paymentRepository.findByStripeSessionId(sessionId).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.APPROVED) return;
            payment.setStatus(PaymentStatus.REJECTED);
            paymentRepository.save(payment);
        });
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
