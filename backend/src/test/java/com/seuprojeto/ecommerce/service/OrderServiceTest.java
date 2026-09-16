package com.seuprojeto.ecommerce.service;

import com.seuprojeto.ecommerce.dto.order.CheckoutRequest;
import com.seuprojeto.ecommerce.dto.order.OrderResponse;
import com.seuprojeto.ecommerce.entity.*;
import com.seuprojeto.ecommerce.exception.EmptyCartException;
import com.seuprojeto.ecommerce.exception.InsufficientStockException;
import com.seuprojeto.ecommerce.exception.InvalidOrderStatusException;
import com.seuprojeto.ecommerce.exception.ResourceNotFoundException;
import com.seuprojeto.ecommerce.exception.ShippingOptionUnavailableException;
import com.seuprojeto.ecommerce.repository.CartItemRepository;
import com.seuprojeto.ecommerce.repository.OrderRepository;
import com.seuprojeto.ecommerce.repository.PaymentRepository;
import com.seuprojeto.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Cobre o fluxo mais crítico do sistema: o checkout. Ele mexe em estoque,
 * dinheiro (total do pedido) e é o ponto onde bugs de concorrência/consistência
 * doem mais caro. Também cobre a checagem de "dono do pedido" em findById,
 * que é a proteção contra IDOR.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ProductRepository productRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private CartService cartService;
    @Mock private EmailService emailService;
    @Mock private ShippingGateway shippingGateway;
    @Mock private PaymentRepository paymentRepository;
    @Mock private StripeGateway stripeGateway;

    private OrderService orderService;

    private User user;
    private Product product;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, productRepository, cartItemRepository, cartService,
                emailService, shippingGateway, paymentRepository, stripeGateway);

        user = User.builder().id(1L).name("Comprador").email("comprador@teste.com").role(Role.CUSTOMER).build();
        product = Product.builder()
                .id(10L).name("Produto X").price(new BigDecimal("50.00"))
                .stockQuantity(5).active(true)
                .weightKg(new BigDecimal("0.700")).heightCm(8).widthCm(15).lengthCm(20)
                .build();
    }

    private Cart cartWith(CartItem... items) {
        return Cart.builder().id(1L).user(user).items(new ArrayList<>(List.of(items))).build();
    }

    @Test
    void deveDebitarEstoqueCongelarPrecoEEsvaziarCarrinhoNoCheckout() {
        CartItem item = CartItem.builder().id(1L).product(product).quantity(2).build();
        Cart cart = cartWith(item);

        when(cartService.findOrCreateCart(user)).thenReturn(cart);
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order order = inv.getArgument(0);
            order.setId(100L);
            return order;
        });

        OrderResponse response = orderService.checkout(user, null);

        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.totalAmount()).isEqualByComparingTo("100.00");
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).unitPriceAtPurchase()).isEqualByComparingTo("50.00");
        assertThat(response.customerName()).isEqualTo("Comprador");
        assertThat(response.customerEmail()).isEqualTo("comprador@teste.com");

        // Estoque debitado na mesma instância gerenciada pela transação.
        assertThat(product.getStockQuantity()).isEqualTo(3);

        // Carrinho esvaziado só depois do pedido persistido.
        verify(cartItemRepository).deleteAll(anyList());
        assertThat(cart.getItems()).isEmpty();
    }

    @Test
    void devePreservarPrecoDoPedidoMesmoQueOProdutoMudeDePrecoDepois() {
        CartItem item = CartItem.builder().id(1L).product(product).quantity(1).build();
        Cart cart = cartWith(item);

        when(cartService.findOrCreateCart(user)).thenReturn(cart);
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.checkout(user, null);
        BigDecimal precoNoMomentoDaCompra = response.items().get(0).unitPriceAtPurchase();

        product.setPrice(new BigDecimal("999.99"));

        assertThat(precoNoMomentoDaCompra).isEqualByComparingTo("50.00");
    }

    @Test
    void deveSomarOFreteRecotadoAoTotalDoPedidoQuandoSelecionado() {
        CartItem item = CartItem.builder().id(1L).product(product).quantity(2).build();
        Cart cart = cartWith(item);

        when(cartService.findOrCreateCart(user)).thenReturn(cart);
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(shippingGateway.calculateShipping(eq("01310-100"), anyList()))
                .thenReturn(List.of(new ShippingGateway.ShippingQuoteResult("Correios", "PAC", new BigDecimal("15.50"), 9)));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        CheckoutRequest request = new CheckoutRequest("01310-100", "Correios", "PAC");
        OrderResponse response = orderService.checkout(user, request);

        assertThat(response.shippingCost()).isEqualByComparingTo("15.50");
        assertThat(response.shippingCarrierName()).isEqualTo("Correios");
        assertThat(response.shippingServiceName()).isEqualTo("PAC");
        // 2 x 50.00 (itens) + 15.50 (frete) = 115.50
        assertThat(response.totalAmount()).isEqualByComparingTo("115.50");
    }

    @Test
    void deveRecusarCheckoutQuandoOpcaoDeFreteSelecionadaNaoEstaMaisDisponivel() {
        CartItem item = CartItem.builder().id(1L).product(product).quantity(1).build();
        Cart cart = cartWith(item);

        when(cartService.findOrCreateCart(user)).thenReturn(cart);
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(shippingGateway.calculateShipping(eq("01310-100"), anyList())).thenReturn(List.of());

        CheckoutRequest request = new CheckoutRequest("01310-100", "Correios", "PAC");

        assertThatThrownBy(() -> orderService.checkout(user, request))
                .isInstanceOf(ShippingOptionUnavailableException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void deveRecusarCheckoutComCarrinhoVazio() {
        when(cartService.findOrCreateCart(user)).thenReturn(cartWith());

        assertThatThrownBy(() -> orderService.checkout(user, null))
                .isInstanceOf(EmptyCartException.class);

        verifyNoInteractions(orderRepository, productRepository, cartItemRepository);
    }

    @Test
    void deveRecusarCheckoutComEstoqueInsuficiente() {
        product.setStockQuantity(1);
        CartItem item = CartItem.builder().id(1L).product(product).quantity(2).build();
        Cart cart = cartWith(item);

        when(cartService.findOrCreateCart(user)).thenReturn(cart);
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> orderService.checkout(user, null))
                .isInstanceOf(InsufficientStockException.class);

        // Nada deve ser gravado se o estoque não fecha.
        verify(orderRepository, never()).save(any());
        verify(cartItemRepository, never()).deleteAll(anyList());
    }

    @Test
    void deveRecusarCheckoutDeProdutoDesativado() {
        product.setActive(false);
        CartItem item = CartItem.builder().id(1L).product(product).quantity(1).build();
        Cart cart = cartWith(item);

        when(cartService.findOrCreateCart(user)).thenReturn(cart);
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> orderService.checkout(user, null))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void donoDoPedidoConsegueVisualizarPeloId() {
        Order order = Order.builder().id(5L).user(user).status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.TEN).build();
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.findById(user, 5L);

        assertThat(response.id()).isEqualTo(5L);
    }

    @Test
    void adminConsegueVisualizarPedidoDeOutroUsuario() {
        User admin = User.builder().id(2L).role(Role.ADMIN).build();
        Order order = Order.builder().id(5L).user(user).status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.TEN).build();
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.findById(admin, 5L);

        assertThat(response.id()).isEqualTo(5L);
    }

    @Test
    void usuarioSemRelacaoComOPedidoRecebeNotFoundAoTentarVisualizar() {
        User estranho = User.builder().id(99L).role(Role.CUSTOMER).build();
        Order order = Order.builder().id(5L).user(user).status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.TEN).build();
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.findById(estranho, 5L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findByIdLancaExcecaoQuandoPedidoNaoExiste() {
        when(orderRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.findById(user, 404L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findByUserRetornaApenasOsPedidosDoUsuarioInformado() {
        Order order = Order.builder().id(5L).user(user).status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.TEN).build();
        Pageable pageable = PageRequest.of(0, 10);
        when(orderRepository.findByUserId(user.getId(), pageable))
                .thenReturn(new PageImpl<>(List.of(order)));

        Page<OrderResponse> page = orderService.findByUser(user, pageable);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).id()).isEqualTo(5L);
    }

    @Test
    void findAllRetornaPedidosDeTodosOsUsuarios() {
        Order order = Order.builder().id(5L).user(user).status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.TEN).build();
        Pageable pageable = PageRequest.of(0, 10);
        when(orderRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(order)));

        Page<OrderResponse> page = orderService.findAll(pageable);

        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    void updateStatusAlteraOStatusDoPedidoENotificaPorEmail() {
        Order order = Order.builder().id(5L).user(user).status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.TEN).build();
        when(orderRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.updateStatus(5L, OrderStatus.PAID);

        assertThat(response.status()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(emailService).sendOrderStatusChangedEmail(order, OrderStatus.PENDING);
    }

    @Test
    void updateStatusNaoEnviaEmailQuandoOStatusInformadoEhOMesmo() {
        Order order = Order.builder().id(5L).user(user).status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.TEN).build();
        when(orderRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(order));

        orderService.updateStatus(5L, OrderStatus.PENDING);

        verifyNoInteractions(emailService);
    }

    @Test
    void cancelarPedidoDevolveOEstoqueEExpiraASessaoDePagamentoAberta() {
        Order order = Order.builder().id(5L).user(user).status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("100.00")).build();
        order.getItems().add(OrderItem.builder().order(order).product(product).quantity(2)
                .unitPriceAtPurchase(product.getPrice()).build());
        Payment payment = Payment.builder().id(1L).order(order).status(PaymentStatus.PENDING)
                .stripeSessionId("cs_test_aberta").build();

        when(orderRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(order));
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(paymentRepository.findByOrderId(5L)).thenReturn(Optional.of(payment));

        orderService.updateStatus(5L, OrderStatus.CANCELED);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        // 5 em estoque + 2 devolvidos do pedido cancelado.
        assertThat(product.getStockQuantity()).isEqualTo(7);
        verify(stripeGateway).expireSession("cs_test_aberta");
        verify(emailService).sendOrderStatusChangedEmail(order, OrderStatus.PENDING);
    }

    @Test
    void naoPermiteVoltarPedidoPagoParaPendente() {
        Order order = Order.builder().id(5L).user(user).status(OrderStatus.PAID)
                .totalAmount(BigDecimal.TEN).build();
        when(orderRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateStatus(5L, OrderStatus.PENDING))
                .isInstanceOf(InvalidOrderStatusException.class);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verifyNoInteractions(emailService);
    }

    @Test
    void naoPermiteAlterarStatusDePedidoCancelado() {
        Order order = Order.builder().id(5L).user(user).status(OrderStatus.CANCELED)
                .totalAmount(BigDecimal.TEN).build();
        when(orderRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateStatus(5L, OrderStatus.PAID))
                .isInstanceOf(InvalidOrderStatusException.class);

        verifyNoInteractions(emailService, productRepository);
    }

    @Test
    void updateStatusLancaExcecaoQuandoPedidoNaoExiste() {
        when(orderRepository.findByIdForUpdate(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.updateStatus(404L, OrderStatus.SHIPPED))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
