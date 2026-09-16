package com.seuprojeto.ecommerce.controller;

import com.seuprojeto.ecommerce.dto.cart.CartItemRequest;
import com.seuprojeto.ecommerce.dto.cart.CartResponse;
import com.seuprojeto.ecommerce.entity.User;
import com.seuprojeto.ecommerce.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
@Validated
@Tag(name = "Carrinho")
public class CartController {

    private final CartService cartService;

    @Operation(summary = "Ver carrinho", description = "Retorna o carrinho do usuário autenticado, com os itens atuais e o total. "
            +
            "Requer autenticação.")
    @GetMapping
    public CartResponse getCart(@AuthenticationPrincipal User user) {
        return cartService.getCart(user);
    }

    @Operation(summary = "Adicionar item ao carrinho", description = "Adiciona um produto ao carrinho do usuário autenticado. Se o produto já estiver "
            +
            "no carrinho, a quantidade é somada. Requer autenticação.")
    @PostMapping("/items")
    public CartResponse addItem(@AuthenticationPrincipal User user, @Valid @RequestBody CartItemRequest request) {
        return cartService.addItem(user, request);
    }

    @Operation(summary = "Atualizar quantidade de um item", description = "Altera a quantidade de um item já existente no carrinho do usuário autenticado. "
            +
            "Requer autenticação.")
    @PutMapping("/items/{itemId}")
    public CartResponse updateItem(@AuthenticationPrincipal User user,
            @PathVariable Long itemId,
            @RequestParam @Min(1) int quantity) {
        return cartService.updateItemQuantity(user, itemId, quantity);
    }

    @Operation(summary = "Remover item do carrinho", description = "Remove um item do carrinho do usuário autenticado. Requer autenticação.")
    @DeleteMapping("/items/{itemId}")
    public CartResponse removeItem(@AuthenticationPrincipal User user, @PathVariable Long itemId) {
        return cartService.removeItem(user, itemId);
    }
}
