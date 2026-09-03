package com.seuprojeto.ecommerce.service;

import com.seuprojeto.ecommerce.dto.product.MostAccessedProductsResponse;
import com.seuprojeto.ecommerce.dto.product.ProductRequest;
import com.seuprojeto.ecommerce.dto.product.ProductResponse;
import com.seuprojeto.ecommerce.entity.Category;
import com.seuprojeto.ecommerce.entity.Product;
import com.seuprojeto.ecommerce.exception.ResourceNotFoundException;
import com.seuprojeto.ecommerce.mapper.ProductMapper;
import com.seuprojeto.ecommerce.repository.CategoryRepository;
import com.seuprojeto.ecommerce.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;

    @Transactional(readOnly = true)
    public Page<ProductResponse> search(Long categoryId, String name, Pageable pageable) {
        return productRepository.search(categoryId, name, pageable)
                .map(productMapper::toResponse);
    }

    @Transactional
    public ProductResponse findById(Long id) {
        Product product = findEntityById(id);
        productRepository.incrementAccessCount(id);
        return productMapper.toResponse(product);
    }

    // Resultado cacheado no Redis por instância de (página, tamanho) — ver
    // CacheConfig para o TTL. Como o cache não é invalidado a cada
    // visualização de produto (isso anularia o propósito de cachear), o
    // ranking pode ficar até 5 minutos desatualizado; é a troca aceita para
    // não recalcular esse ranking a cada requisição.
    @Cacheable(cacheNames = "mostAccessedProducts", key = "#pageable.pageNumber + '-' + #pageable.pageSize")
    @Transactional(readOnly = true)
    public MostAccessedProductsResponse findMostAccessed(Pageable pageable) {
        Page<Product> page = productRepository.findByActiveTrueOrderByAccessCountDesc(pageable);
        return new MostAccessedProductsResponse(
                page.getContent().stream().map(productMapper::toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Category category = findCategoryById(request.categoryId());

        Product product = Product.builder()
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .stockQuantity(request.stockQuantity())
                .imageUrl(request.imageUrl())
                .weightKg(request.weightKg())
                .heightCm(request.heightCm())
                .widthCm(request.widthCm())
                .lengthCm(request.lengthCm())
                .category(category)
                .active(true)
                .build();

        return productMapper.toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = findEntityById(id);
        Category category = findCategoryById(request.categoryId());

        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStockQuantity(request.stockQuantity());
        product.setImageUrl(request.imageUrl());
        product.setWeightKg(request.weightKg());
        product.setHeightCm(request.heightCm());
        product.setWidthCm(request.widthCm());
        product.setLengthCm(request.lengthCm());
        product.setCategory(category);

        // Não precisa de save() explícito: dentro de uma transação, o JPA
        // detecta a mudança no objeto gerenciado e sincroniza com o banco
        // (dirty checking).
        return productMapper.toResponse(product);
    }

    @Transactional
    public void delete(Long id) {
        Product product = findEntityById(id);
        // Soft delete: mantém histórico de pedidos íntegro, só some das buscas públicas.
        product.setActive(false);
    }

    private Product findEntityById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: id " + id));
    }

    private Category findCategoryById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada: id " + id));
    }
}
