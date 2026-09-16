package com.seuprojeto.ecommerce.service;

import com.seuprojeto.ecommerce.dto.cart.CartItemRequest;
import com.seuprojeto.ecommerce.dto.cart.CartResponse;
import com.seuprojeto.ecommerce.entity.Cart;
import com.seuprojeto.ecommerce.entity.CartItem;
import com.seuprojeto.ecommerce.entity.Product;
import com.seuprojeto.ecommerce.entity.User;
import com.seuprojeto.ecommerce.exception.InsufficientStockException;
import com.seuprojeto.ecommerce.exception.ResourceNotFoundException;
import com.seuprojeto.ecommerce.repository.CartItemRepository;
import com.seuprojeto.ecommerce.repository.CartRepository;
import com.seuprojeto.ecommerce.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    // Usuários legados podem ainda não ter carrinho; findOrCreateCart pode
    // persistir um, portanto esta transação não pode ser read-only.
    @Transactional
    public CartResponse getCart(User user) {
        return toResponse(findOrCreateCart(user));
    }

    @Transactional
    public CartResponse addItem(User user, CartItemRequest request) {
        Cart cart = findOrCreateCart(user);
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: id " + request.productId()));

        if (!Boolean.TRUE.equals(product.getActive())) {
            throw new ResourceNotFoundException("Produto não está disponível: id " + product.getId());
        }

        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId())
                .orElse(null);

        int targetQuantity = (item == null ? 0 : item.getQuantity()) + request.quantity();
        if (product.getStockQuantity() != null && product.getStockQuantity() < targetQuantity) {
            throw new InsufficientStockException(product.getName(), product.getStockQuantity(), targetQuantity);
        }

        if (item == null) {
            item = CartItem.builder().cart(cart).product(product).quantity(request.quantity()).build();
            cart.getItems().add(item);
        } else {
            item.setQuantity(targetQuantity);
        }

        cartItemRepository.save(item);
        return toResponse(cart);
    }

    @Transactional
    public CartResponse updateItemQuantity(User user, Long itemId, int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("A quantidade deve ser no mínimo 1");
        }
        Cart cart = findOrCreateCart(user);
        CartItem item = findItemInCart(cart, itemId);
        if (item.getProduct().getStockQuantity() != null && item.getProduct().getStockQuantity() < quantity) {
            throw new InsufficientStockException(item.getProduct().getName(), item.getProduct().getStockQuantity(),
                    quantity);
        }
        item.setQuantity(quantity);
        return toResponse(cart);
    }

    @Transactional
    public CartResponse removeItem(User user, Long itemId) {
        Cart cart = findOrCreateCart(user);
        CartItem item = findItemInCart(cart, itemId);
        cart.getItems().remove(item);
        cartItemRepository.delete(item);
        return toResponse(cart);
    }

    private CartItem findItemInCart(Cart cart, Long itemId) {
        return cart.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Item não encontrado no carrinho: id " + itemId));
    }

    Cart findOrCreateCart(User user) {
        return cartRepository.findByUserId(user.getId())
                .orElseGet(() -> cartRepository.save(Cart.builder().user(user).build()));
    }

    private CartResponse toResponse(Cart cart) {
        List<CartResponse.CartItemResponse> items = cart.getItems().stream()
                .map(i -> new CartResponse.CartItemResponse(
                        i.getId(),
                        i.getProduct().getId(),
                        i.getProduct().getName(),
                        i.getProduct().getPrice(),
                        i.getQuantity(),
                        i.getProduct().getPrice().multiply(BigDecimal.valueOf(i.getQuantity()))))
                .toList();

        BigDecimal total = items.stream()
                .map(CartResponse.CartItemResponse::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CartResponse(cart.getId(), items, total);
    }
}
