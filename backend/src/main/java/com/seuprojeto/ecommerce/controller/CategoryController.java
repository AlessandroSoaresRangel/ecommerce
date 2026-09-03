package com.seuprojeto.ecommerce.controller;

import com.seuprojeto.ecommerce.entity.Category;
import com.seuprojeto.ecommerce.exception.ResourceNotFoundException;
import com.seuprojeto.ecommerce.repository.CategoryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
@Tag(name = "Categorias")
public class CategoryController {

    private final CategoryRepository categoryRepository;

    public record CategoryRequest(@NotBlank String name, String description) {}

    @Operation(
            summary = "Listar categorias",
            description = "Retorna todas as categorias de produtos cadastradas. Endpoint público."
    )
    @GetMapping
    public ResponseEntity<List<Category>> findAll() {
        return ResponseEntity.ok(categoryRepository.findAll());
    }

    @Operation(
            summary = "Criar categoria",
            description = "Cadastra uma nova categoria de produtos. Restrito a usuários com role ADMIN."
    )
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Category> create(@RequestBody CategoryRequest request) {
        Category category = Category.builder()
                .name(request.name())
                .description(request.description())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryRepository.save(category));
    }

    @Operation(
            summary = "Remover categoria",
            description = "Remove uma categoria existente. Restrito a usuários com role ADMIN. " +
                    "Falha com 404 se a categoria não existir."
    )
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!categoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Categoria não encontrada: id " + id);
        }
        categoryRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
