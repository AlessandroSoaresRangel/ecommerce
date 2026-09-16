package com.seuprojeto.ecommerce.service;

import com.seuprojeto.ecommerce.dto.shipping.ShippingOptionResponse;
import com.seuprojeto.ecommerce.dto.shipping.ShippingQuoteRequest;
import com.seuprojeto.ecommerce.entity.Cart;
import com.seuprojeto.ecommerce.entity.User;
import com.seuprojeto.ecommerce.exception.EmptyCartException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ShippingService {

    private final CartService cartService;
    private final ShippingGateway shippingGateway;

    // Não pode ser readOnly: findOrCreateCart cria e persiste um carrinho
    // novo se o usuário ainda não tiver um, e o Postgres rejeita INSERT
    // dentro de uma transação somente-leitura.
    @Transactional
    public List<ShippingOptionResponse> quote(User user, ShippingQuoteRequest request) {
        Cart cart = cartService.findOrCreateCart(user);
        if (cart.getItems().isEmpty()) {
            throw new EmptyCartException();
        }

        List<ShippingGateway.ShippingItem> items = cart.getItems().stream()
                .map(i -> {
                    var p = i.getProduct();
                    return new ShippingGateway.ShippingItem(
                            p.getId().toString(),
                            p.getWeightKg() != null ? p.getWeightKg() : java.math.BigDecimal.ZERO,
                            p.getHeightCm() != null ? p.getHeightCm() : 1,
                            p.getWidthCm() != null ? p.getWidthCm() : 1,
                            p.getLengthCm() != null ? p.getLengthCm() : 1,
                            p.getPrice(),
                            i.getQuantity());
                })
                .toList();

        return shippingGateway.calculateShipping(request.destinationCep(), items).stream()
                .map(r -> new ShippingOptionResponse(r.carrierName(), r.serviceName(), r.price(), r.deliveryTimeDays()))
                .toList();
    }
}
