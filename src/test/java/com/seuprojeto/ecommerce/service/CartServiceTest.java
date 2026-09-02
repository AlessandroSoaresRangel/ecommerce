package com.seuprojeto.ecommerce.service;

import com.seuprojeto.ecommerce.dto.cart.CartItemRequest;
import com.seuprojeto.ecommerce.dto.cart.CartResponse;
import com.seuprojeto.ecommerce.entity.Cart;
import com.seuprojeto.ecommerce.entity.CartItem;
import com.seuprojeto.ecommerce.entity.Product;
import com.seuprojeto.ecommerce.entity.User;
import com.seuprojeto.ecommerce.exception.ResourceNotFoundException;
import com.seuprojeto.ecommerce.repository.CartItemRepository;
import com.seuprojeto.ecommerce.repository.CartRepository;
import com.seuprojeto.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Cobre o carrinho: criação automática do carrinho do usuário, soma de
 * quantidade quando o mesmo produto é adicionado duas vezes, e as operações
 * de atualizar/remover item.
 */
@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private ProductRepository productRepository;

    private CartService cartService;

    private User user;
    private Product product;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartRepository, cartItemRepository, productRepository);
        user = User.builder().id(1L).build();
        product = Product.builder().id(10L).name("Produto X").price(new BigDecimal("25.00")).active(true).build();
    }

    @Test
    void findOrCreateCartRetornaCarrinhoExistenteSemCriarNovo() {
        Cart existente = Cart.builder().id(1L).user(user).items(new ArrayList<>()).build();
        when(cartRepository.findByUserId(user.getId())).thenReturn(Optional.of(existente));

        Cart resultado = cartService.findOrCreateCart(user);

        assertThat(resultado).isEqualTo(existente);
        verify(cartRepository, never()).save(any());
    }

    @Test
    void findOrCreateCartCriaCarrinhoQuandoUsuarioAindaNaoTemUm() {
        when(cartRepository.findByUserId(user.getId())).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        Cart resultado = cartService.findOrCreateCart(user);

        assertThat(resultado.getUser()).isEqualTo(user);
        verify(cartRepository).save(any(Cart.class));
    }

    @Test
    void addItemCriaNovoItemQuandoProdutoAindaNaoEstaNoCarrinho() {
        Cart cart = Cart.builder().id(1L).user(user).items(new ArrayList<>()).build();
        when(cartRepository.findByUserId(user.getId())).thenReturn(Optional.of(cart));
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId())).thenReturn(Optional.empty());

        CartResponse response = cartService.addItem(user, new CartItemRequest(product.getId(), 3));

        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(3);
        assertThat(response.totalAmount()).isEqualByComparingTo("75.00");
        verify(cartItemRepository).save(any(CartItem.class));
    }

    @Test
    void addItemSomaQuantidadeQuandoProdutoJaEstaNoCarrinho() {
        CartItem itemExistente = CartItem.builder().id(1L).product(product).quantity(2).build();
        Cart cart = Cart.builder().id(1L).user(user).items(new ArrayList<>(List.of(itemExistente))).build();

        when(cartRepository.findByUserId(user.getId())).thenReturn(Optional.of(cart));
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId()))
                .thenReturn(Optional.of(itemExistente));

        cartService.addItem(user, new CartItemRequest(product.getId(), 3));

        assertThat(cart.getItems()).hasSize(1);
        assertThat(itemExistente.getQuantity()).isEqualTo(5);
    }

    @Test
    void addItemLancaExcecaoQuandoProdutoNaoExiste() {
        Cart cart = Cart.builder().id(1L).user(user).items(new ArrayList<>()).build();
        when(cartRepository.findByUserId(user.getId())).thenReturn(Optional.of(cart));
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.addItem(user, new CartItemRequest(999L, 1)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateItemQuantityAlteraAQuantidadeDoItem() {
        CartItem item = CartItem.builder().id(1L).product(product).quantity(1).build();
        Cart cart = Cart.builder().id(1L).user(user).items(new ArrayList<>(List.of(item))).build();
        when(cartRepository.findByUserId(user.getId())).thenReturn(Optional.of(cart));

        CartResponse response = cartService.updateItemQuantity(user, 1L, 7);

        assertThat(item.getQuantity()).isEqualTo(7);
        assertThat(response.items().get(0).quantity()).isEqualTo(7);
    }

    @Test
    void updateItemQuantityLancaExcecaoQuandoItemNaoPertenceAoCarrinho() {
        Cart cart = Cart.builder().id(1L).user(user).items(new ArrayList<>()).build();
        when(cartRepository.findByUserId(user.getId())).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> cartService.updateItemQuantity(user, 999L, 2))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void removeItemRetiraOItemDoCarrinhoEPersisteARemocao() {
        CartItem item = CartItem.builder().id(1L).product(product).quantity(2).build();
        Cart cart = Cart.builder().id(1L).user(user).items(new ArrayList<>(List.of(item))).build();
        when(cartRepository.findByUserId(user.getId())).thenReturn(Optional.of(cart));

        CartResponse response = cartService.removeItem(user, 1L);

        assertThat(cart.getItems()).isEmpty();
        assertThat(response.items()).isEmpty();
        verify(cartItemRepository).delete(item);
    }

    @Test
    void getCartSomaOSubtotalDeCadaItemNoTotal() {
        CartItem item1 = CartItem.builder().id(1L).product(product).quantity(2).build();
        Product outroProduto = Product.builder().id(11L).name("Produto Y").price(new BigDecimal("10.00")).build();
        CartItem item2 = CartItem.builder().id(2L).product(outroProduto).quantity(1).build();
        Cart cart = Cart.builder().id(1L).user(user).items(new ArrayList<>(List.of(item1, item2))).build();
        when(cartRepository.findByUserId(user.getId())).thenReturn(Optional.of(cart));

        CartResponse response = cartService.getCart(user);

        // 2 * 25.00 + 1 * 10.00
        assertThat(response.totalAmount()).isEqualByComparingTo("60.00");
    }
}
