package com.seuprojeto.ecommerce.controller;

import com.seuprojeto.ecommerce.dto.product.MostAccessedProductsResponse;
import com.seuprojeto.ecommerce.dto.product.ProductRequest;
import com.seuprojeto.ecommerce.dto.product.ProductResponse;
import com.seuprojeto.ecommerce.entity.Role;
import com.seuprojeto.ecommerce.entity.User;
import com.seuprojeto.ecommerce.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
@Tag(name = "Produtos")
public class ProductController {

    private final ProductService productService;

    @Operation(
            summary = "Listar/buscar produtos",
            description = "Retorna o catálogo de produtos de forma paginada, com filtros opcionais por " +
                    "categoria (categoryId) e por nome (busca parcial). Endpoint público. " +
                    "O parâmetro includeInactive só tem efeito para quem estiver autenticado com role ADMIN " +
                    "(ignorado para todo o resto, que sempre só vê produtos ativos)."
    )
    @GetMapping
    public ResponseEntity<Page<ProductResponse>> search(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @AuthenticationPrincipal User requester,
            Pageable pageable) {
        boolean effectiveIncludeInactive = includeInactive && requester != null && requester.getRole() == Role.ADMIN;
        return ResponseEntity.ok(productService.search(categoryId, name, effectiveIncludeInactive, pageable));
    }

    @Operation(
            summary = "Produtos mais acessados",
            description = "Retorna os produtos mais visualizados (via GET /products/{id}), ordenados por número " +
                    "de acessos decrescente, de forma paginada. Endpoint público. O resultado fica em cache " +
                    "(Redis) por 5 minutos, então uma visualização recente pode levar até esse tempo para " +
                    "refletir no ranking."
    )
    @GetMapping("/most-accessed")
    public ResponseEntity<MostAccessedProductsResponse> mostAccessed(Pageable pageable) {
        return ResponseEntity.ok(productService.findMostAccessed(pageable));
    }

    @Operation(
            summary = "Buscar produto por ID",
            description = "Retorna os detalhes de um produto específico. Endpoint público. " +
                    "Falha com 404 se o produto não existir."
    )
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.findById(id));
    }

    @Operation(
            summary = "Criar produto",
            description = "Cadastra um novo produto no catálogo. Restrito a usuários com role ADMIN."
    )
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(request));
    }

    @Operation(
            summary = "Atualizar produto",
            description = "Atualiza os dados de um produto existente (nome, preço, estoque, etc.). " +
                    "Restrito a usuários com role ADMIN."
    )
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(productService.update(id, request));
    }

    @Operation(
            summary = "Remover produto",
            description = "Remove um produto do catálogo. Restrito a usuários com role ADMIN."
    )
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
