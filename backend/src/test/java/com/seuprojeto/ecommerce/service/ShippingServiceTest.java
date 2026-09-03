package com.seuprojeto.ecommerce.service;

import com.seuprojeto.ecommerce.dto.shipping.ShippingOptionResponse;
import com.seuprojeto.ecommerce.dto.shipping.ShippingQuoteRequest;
import com.seuprojeto.ecommerce.entity.*;
import com.seuprojeto.ecommerce.exception.EmptyCartException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShippingServiceTest {

    @Mock private CartService cartService;
    @Mock private ShippingGateway shippingGateway;

    private ShippingService shippingService;

    private User user;
    private Product product;

    @BeforeEach
    void setUp() {
        shippingService = new ShippingService(cartService, shippingGateway);

        user = User.builder().id(1L).role(Role.CUSTOMER).build();
        product = Product.builder().id(9L)
                .price(new BigDecimal("49.90"))
                .weightKg(new BigDecimal("0.700"))
                .heightCm(8).widthCm(15).lengthCm(20)
                .build();
    }

    @Test
    void devolveAsOpcoesDeFreteParaOCarrinhoAtual() {
        CartItem item = CartItem.builder().id(1L).product(product).quantity(2).build();
        Cart cart = Cart.builder().id(3L).user(user).items(List.of(item)).build();
        when(cartService.findOrCreateCart(user)).thenReturn(cart);
        when(shippingGateway.calculateShipping(eq("01310-100"), anyList()))
                .thenReturn(List.of(new ShippingGateway.ShippingQuoteResult("Correios", "PAC", new BigDecimal("37.79"), 9)));

        List<ShippingOptionResponse> response = shippingService.quote(user, new ShippingQuoteRequest("01310-100"));

        assertThat(response).hasSize(1);
        assertThat(response.get(0).carrierName()).isEqualTo("Correios");
        assertThat(response.get(0).serviceName()).isEqualTo("PAC");
        assertThat(response.get(0).price()).isEqualByComparingTo("37.79");
        assertThat(response.get(0).deliveryTimeDays()).isEqualTo(9);
    }

    @Test
    void mapeiaOsItensDoCarrinhoParaOFormatoDoGateway() {
        CartItem item = CartItem.builder().id(1L).product(product).quantity(3).build();
        Cart cart = Cart.builder().id(3L).user(user).items(List.of(item)).build();
        when(cartService.findOrCreateCart(user)).thenReturn(cart);
        when(shippingGateway.calculateShipping(anyString(), anyList())).thenReturn(List.of());

        shippingService.quote(user, new ShippingQuoteRequest("01310-100"));

        var itemsCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(shippingGateway).calculateShipping(eq("01310-100"), itemsCaptor.capture());
        @SuppressWarnings("unchecked")
        List<ShippingGateway.ShippingItem> gatewayItems = itemsCaptor.getValue();

        assertThat(gatewayItems).hasSize(1);
        ShippingGateway.ShippingItem gatewayItem = gatewayItems.get(0);
        assertThat(gatewayItem.productId()).isEqualTo("9");
        assertThat(gatewayItem.weightKg()).isEqualByComparingTo("0.700");
        assertThat(gatewayItem.heightCm()).isEqualTo(8);
        assertThat(gatewayItem.widthCm()).isEqualTo(15);
        assertThat(gatewayItem.lengthCm()).isEqualTo(20);
        assertThat(gatewayItem.unitPrice()).isEqualByComparingTo("49.90");
        assertThat(gatewayItem.quantity()).isEqualTo(3);
    }

    @Test
    void lancaExcecaoQuandoOCarrinhoEstaVazio() {
        Cart cartVazio = Cart.builder().id(3L).user(user).items(List.of()).build();
        when(cartService.findOrCreateCart(user)).thenReturn(cartVazio);

        assertThatThrownBy(() -> shippingService.quote(user, new ShippingQuoteRequest("01310-100")))
                .isInstanceOf(EmptyCartException.class);

        verifyNoInteractions(shippingGateway);
    }
}
