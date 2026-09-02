package com.seuprojeto.ecommerce.service;

import com.seuprojeto.ecommerce.dto.order.OrderResponse;
import com.seuprojeto.ecommerce.entity.*;
import com.seuprojeto.ecommerce.exception.EmptyCartException;
import com.seuprojeto.ecommerce.exception.InsufficientStockException;
import com.seuprojeto.ecommerce.exception.ResourceNotFoundException;
import com.seuprojeto.ecommerce.repository.CartItemRepository;
import com.seuprojeto.ecommerce.repository.OrderRepository;
import com.seuprojeto.ecommerce.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CartItemRepository cartItemRepository;
    private final CartService cartService;

    /**
     * Cria o pedido a partir do carrinho do usuário.
     *
     * A operação toda roda em uma única transação: verifica estoque,
     * debita a quantidade de cada produto, grava o pedido com o preço
     * "congelado" no momento da compra, e esvazia o carrinho. Se
     * qualquer passo falhar (estoque insuficiente, ou um conflito de
     * @Version porque outro pedido mexeu no mesmo produto ao mesmo
     * tempo), a transação inteira é revertida — nunca fica um pedido
     * "pela metade" ou estoque debitado sem pedido correspondente.
     */
    @Transactional
    public OrderResponse checkout(User user) {
        Cart cart = cartService.findOrCreateCart(user);

        if (cart.getItems().isEmpty()) {
            throw new EmptyCartException();
        }

        Order order = Order.builder()
                .user(user)
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal total = BigDecimal.ZERO;

        for (CartItem cartItem : cart.getItems()) {
            // Recarrega o produto dentro da transação para que o @Version
            // seja verificado no commit — é isso que impede overselling
            // quando dois checkouts concorrentes disputam o mesmo item.
            Product product = productRepository.findById(cartItem.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Produto não encontrado: id " + cartItem.getProduct().getId()));

            // Um produto pode ter sido desativado (soft delete) depois de já
            // estar no carrinho de alguém — não deve ser possível concluir a
            // compra de algo que não está mais disponível no catálogo.
            if (!Boolean.TRUE.equals(product.getActive())) {
                throw new ResourceNotFoundException(
                        "Produto não está mais disponível: id " + product.getId());
            }

            int requested = cartItem.getQuantity();
            if (product.getStockQuantity() < requested) {
                throw new InsufficientStockException(product.getName(), product.getStockQuantity(), requested);
            }

            product.setStockQuantity(product.getStockQuantity() - requested);

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantity(requested)
                    .unitPriceAtPurchase(product.getPrice())
                    .build();

            order.getItems().add(orderItem);
            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(requested)));
        }

        order.setTotalAmount(total);
        Order saved = orderRepository.save(order);

        // Esvazia o carrinho só depois que o pedido foi persistido com sucesso.
        cartItemRepository.deleteAll(cart.getItems());
        cart.getItems().clear();

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> findByUser(User user, Pageable pageable) {
        return orderRepository.findByUserId(user.getId(), pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(User requester, Long id) {
        Order order = findEntity(id);

        boolean isOwner = order.getUser().getId().equals(requester.getId());
        boolean isAdmin = requester.getRole() == Role.ADMIN;

        if (!isOwner && !isAdmin) {
            throw new ResourceNotFoundException("Pedido não encontrado: id " + id);
        }

        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> findAll(Pageable pageable) {
        return orderRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional
    public OrderResponse updateStatus(Long id, OrderStatus newStatus) {
        Order order = findEntity(id);
        order.setStatus(newStatus);
        return toResponse(order);
    }

    private Order findEntity(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado: id " + id));
    }

    private OrderResponse toResponse(Order order) {
        List<OrderResponse.Item> items = order.getItems().stream()
                .map(i -> new OrderResponse.Item(
                        i.getProduct().getId(),
                        i.getProduct().getName(),
                        i.getQuantity(),
                        i.getUnitPriceAtPurchase()))
                .toList();

        return new OrderResponse(order.getId(), order.getStatus(), order.getTotalAmount(), items, order.getCreatedAt());
    }
}
