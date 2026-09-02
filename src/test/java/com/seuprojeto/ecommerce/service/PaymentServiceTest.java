package com.seuprojeto.ecommerce.service;

import com.seuprojeto.ecommerce.dto.payment.PaymentRequest;
import com.seuprojeto.ecommerce.dto.payment.PaymentResponse;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Cobre a checagem de dono do pedido (proteção contra IDOR: um usuário não
 * pode pagar/ver o pagamento de um pedido que não é dele) e a validação de
 * status do pedido antes de aprovar um pagamento — os dois bugs de segurança
 * encontrados na revisão de código deste projeto.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderRepository orderRepository;

    private PaymentService paymentService;

    private User dono;
    private User estranho;
    private User admin;
    private Order pedidoPendente;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, orderRepository);

        dono = User.builder().id(1L).role(Role.CUSTOMER).build();
        estranho = User.builder().id(2L).role(Role.CUSTOMER).build();
        admin = User.builder().id(3L).role(Role.ADMIN).build();

        pedidoPendente = Order.builder().id(50L).user(dono).status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("100.00")).build();
    }

    @Test
    void donoDoPedidoConseguePagar() {
        when(orderRepository.findById(50L)).thenReturn(Optional.of(pedidoPendente));
        when(paymentRepository.findByOrderId(50L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        PaymentResponse response = paymentService.pay(dono, 50L, new PaymentRequest("PIX"));

        assertThat(response.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(pedidoPendente.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void adminConseguePagarPedidoDeOutroUsuario() {
        when(orderRepository.findById(50L)).thenReturn(Optional.of(pedidoPendente));
        when(paymentRepository.findByOrderId(50L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse response = paymentService.pay(admin, 50L, new PaymentRequest("PIX"));

        assertThat(response.status()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void usuarioSemRelacaoComOPedidoNaoConseguePagar() {
        when(orderRepository.findById(50L)).thenReturn(Optional.of(pedidoPendente));

        assertThatThrownBy(() -> paymentService.pay(estranho, 50L, new PaymentRequest("PIX")))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(paymentRepository, never()).save(any());
        assertThat(pedidoPendente.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void deveRecusarPagamentoDePedidoQueNaoEstaPendente() {
        pedidoPendente.setStatus(OrderStatus.PAID);
        when(orderRepository.findById(50L)).thenReturn(Optional.of(pedidoPendente));

        assertThatThrownBy(() -> paymentService.pay(dono, 50L, new PaymentRequest("PIX")))
                .isInstanceOf(InvalidOrderStatusException.class);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void donoConsegueVerODetalheDoProprioPagamento() {
        Payment payment = Payment.builder().id(7L).order(pedidoPendente)
                .method("PIX").status(PaymentStatus.APPROVED).build();
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.findById(dono, 7L);

        assertThat(response.id()).isEqualTo(7L);
    }

    @Test
    void usuarioSemRelacaoComOPedidoNaoConsegueVerOPagamento() {
        Payment payment = Payment.builder().id(7L).order(pedidoPendente)
                .method("PIX").status(PaymentStatus.APPROVED).build();
        when(paymentRepository.findById(7L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.findById(estranho, 7L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
