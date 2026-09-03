package com.seuprojeto.ecommerce.service;

import com.seuprojeto.ecommerce.dto.payment.PaymentResponse;
import com.seuprojeto.ecommerce.dto.payment.StripeCheckoutResponse;
import com.seuprojeto.ecommerce.entity.*;
import com.seuprojeto.ecommerce.exception.InvalidOrderStatusException;
import com.seuprojeto.ecommerce.exception.ResourceNotFoundException;
import com.seuprojeto.ecommerce.repository.OrderRepository;
import com.seuprojeto.ecommerce.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Cobre a checagem de dono do pedido (proteção contra IDOR: um usuário não
 * pode pagar/ver o pagamento de um pedido que não é dele), a validação de
 * status do pedido antes de iniciar um pagamento, e o processamento do
 * webhook do Stripe (única via que aprova/rejeita um pagamento de fato).
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private EmailService emailService;
    @Mock private StripeGateway stripeGateway;

    private PaymentService paymentService;

    private User dono;
    private User estranho;
    private User admin;
    private Order pedidoPendente;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, orderRepository, emailService, stripeGateway);

        dono = User.builder().id(1L).role(Role.CUSTOMER).build();
        estranho = User.builder().id(2L).role(Role.CUSTOMER).build();
        admin = User.builder().id(3L).role(Role.ADMIN).build();

        pedidoPendente = Order.builder().id(50L).user(dono).status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("100.00")).build();
    }

    @Test
    void donoDoPedidoConsegueIniciarOCheckout() {
        when(orderRepository.findById(50L)).thenReturn(Optional.of(pedidoPendente));
        when(paymentRepository.findByOrderId(50L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            if (p.getId() == null) p.setId(1L);
            return p;
        });
        when(stripeGateway.createCheckoutSession(eq(pedidoPendente), anyLong()))
                .thenReturn(new StripeGateway.CheckoutSessionResult("cs_test_123", "https://checkout.stripe.com/c/cs_test_123"));

        StripeCheckoutResponse response = paymentService.createCheckoutSession(dono, 50L);

        assertThat(response.checkoutUrl()).isEqualTo("https://checkout.stripe.com/c/cs_test_123");
        assertThat(response.sessionId()).isEqualTo("cs_test_123");
        // A aprovação não é mais síncrona: o pedido continua PENDING até o webhook confirmar.
        assertThat(pedidoPendente.getStatus()).isEqualTo(OrderStatus.PENDING);
        verifyNoInteractions(emailService);
    }

    @Test
    void adminConsegueIniciarOCheckoutDePedidoDeOutroUsuario() {
        when(orderRepository.findById(50L)).thenReturn(Optional.of(pedidoPendente));
        when(paymentRepository.findByOrderId(50L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            if (p.getId() == null) p.setId(2L);
            return p;
        });
        when(stripeGateway.createCheckoutSession(any(Order.class), anyLong()))
                .thenReturn(new StripeGateway.CheckoutSessionResult("cs_test_456", "https://checkout.stripe.com/c/cs_test_456"));

        StripeCheckoutResponse response = paymentService.createCheckoutSession(admin, 50L);

        assertThat(response.sessionId()).isEqualTo("cs_test_456");
    }

    @Test
    void usuarioSemRelacaoComOPedidoNaoConsegueIniciarOCheckout() {
        when(orderRepository.findById(50L)).thenReturn(Optional.of(pedidoPendente));

        assertThatThrownBy(() -> paymentService.createCheckoutSession(estranho, 50L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(paymentRepository, never()).save(any());
        assertThat(pedidoPendente.getStatus()).isEqualTo(OrderStatus.PENDING);
        verifyNoInteractions(emailService, stripeGateway);
    }

    @Test
    void deveRecusarCheckoutDePedidoQueNaoEstaPendente() {
        pedidoPendente.setStatus(OrderStatus.PAID);
        when(orderRepository.findById(50L)).thenReturn(Optional.of(pedidoPendente));

        assertThatThrownBy(() -> paymentService.createCheckoutSession(dono, 50L))
                .isInstanceOf(InvalidOrderStatusException.class);

        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(emailService, stripeGateway);
    }

    @Test
    void webhookCheckoutSessionCompletedAprovaPagamentoEMudaPedidoParaPago() {
        Payment payment = Payment.builder().id(1L).order(pedidoPendente)
                .method("STRIPE").status(PaymentStatus.PENDING).stripeSessionId("cs_test_123").build();
        when(paymentRepository.findByStripeSessionId("cs_test_123")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentService.handleWebhookEvent(
                new StripeGateway.WebhookEventResult("checkout.session.completed", "cs_test_123", "pi_test_456"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getTransactionId()).isEqualTo("pi_test_456");
        assertThat(payment.getPaidAt()).isNotNull();
        assertThat(pedidoPendente.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(emailService).sendOrderStatusChangedEmail(pedidoPendente, OrderStatus.PENDING);
    }

    @Test
    void webhookSessaoExpiradaRejeitaPagamento() {
        Payment payment = Payment.builder().id(1L).order(pedidoPendente)
                .method("STRIPE").status(PaymentStatus.PENDING).stripeSessionId("cs_test_123").build();
        when(paymentRepository.findByStripeSessionId("cs_test_123")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentService.handleWebhookEvent(
                new StripeGateway.WebhookEventResult("checkout.session.expired", "cs_test_123", null));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(pedidoPendente.getStatus()).isEqualTo(OrderStatus.PENDING);
        verifyNoInteractions(emailService);
    }

    @Test
    void webhookEventoDesconhecidoENoOp() {
        paymentService.handleWebhookEvent(
                new StripeGateway.WebhookEventResult("payment_intent.created", "cs_test_123", null));

        verifyNoInteractions(paymentRepository, emailService);
    }

    @Test
    void webhookNaoReprocessaPagamentoJaAprovado() {
        Payment payment = Payment.builder().id(1L).order(pedidoPendente)
                .method("STRIPE").status(PaymentStatus.APPROVED).stripeSessionId("cs_test_123")
                .transactionId("pi_test_original").build();
        when(paymentRepository.findByStripeSessionId("cs_test_123")).thenReturn(Optional.of(payment));

        paymentService.handleWebhookEvent(
                new StripeGateway.WebhookEventResult("checkout.session.completed", "cs_test_123", "pi_test_outro"));

        assertThat(payment.getTransactionId()).isEqualTo("pi_test_original");
        verify(paymentRepository, never()).save(any());
        verify(emailService, never()).sendOrderStatusChangedEmail(any(), any());
    }

    @Test
    void donoConsegueVerODetalheDoProprioPagamento() {
        Payment payment = Payment.builder().id(7L).order(pedidoPendente)
                .method("STRIPE").status(PaymentStatus.APPROVED).build();
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.findById(dono, 7L);

        assertThat(response.id()).isEqualTo(7L);
    }

    @Test
    void usuarioSemRelacaoComOPedidoNaoConsegueVerOPagamento() {
        Payment payment = Payment.builder().id(7L).order(pedidoPendente)
                .method("STRIPE").status(PaymentStatus.APPROVED).build();
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.findById(estranho, 7L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
