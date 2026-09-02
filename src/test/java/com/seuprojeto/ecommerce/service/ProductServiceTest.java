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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Cobre o CRUD de produtos, com foco em: a categoria precisa existir para
 * criar/atualizar um produto, novo produto sempre nasce ativo, e a exclusão
 * é sempre soft delete (nunca remove a linha do banco).
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ProductMapper productMapper;

    private ProductService productService;

    private Category category;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, categoryRepository, productMapper);
        category = Category.builder().id(1L).name("Eletrônicos").build();

        lenient().when(productMapper.toResponse(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            Long categoryId = p.getCategory() != null ? p.getCategory().getId() : null;
            String categoryName = p.getCategory() != null ? p.getCategory().getName() : null;
            return new ProductResponse(p.getId(), p.getName(), p.getDescription(), p.getPrice(),
                    p.getStockQuantity(), p.getImageUrl(), p.getActive(), categoryId, categoryName);
        });
    }

    @Test
    void findByIdRetornaProdutoERegistraOAcesso() {
        Product product = Product.builder().id(5L).name("Mouse").price(BigDecimal.TEN)
                .stockQuantity(10).active(true).category(category).build();
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));

        ProductResponse response = productService.findById(5L);

        assertThat(response.name()).isEqualTo("Mouse");
        assertThat(response.categoryId()).isEqualTo(1L);
        verify(productRepository).incrementAccessCount(5L);
    }

    @Test
    void findByIdLancaExcecaoQuandoProdutoNaoExiste() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findById(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createSalvaProdutoAtivoComACategoriaInformada() {
        ProductRequest request = new ProductRequest("Teclado", "Mecânico", new BigDecimal("199.90"), 20, null, 1L);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(100L);
            return p;
        });

        ProductResponse response = productService.create(request);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.active()).isTrue();
        assertThat(response.categoryId()).isEqualTo(1L);
    }

    @Test
    void createLancaExcecaoQuandoCategoriaNaoExiste() {
        ProductRequest request = new ProductRequest("Teclado", "Mecânico", new BigDecimal("199.90"), 20, null, 404L);
        when(categoryRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.create(request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    void updateSubstituiOsCamposDoProdutoExistente() {
        Product existente = Product.builder().id(5L).name("Antigo").description("velho")
                .price(BigDecimal.ONE).stockQuantity(1).active(true).category(category).build();
        Category novaCategoria = Category.builder().id(2L).name("Casa").build();
        ProductRequest request = new ProductRequest("Novo Nome", "novo", new BigDecimal("50.00"), 30, "img.png", 2L);

        when(productRepository.findById(5L)).thenReturn(Optional.of(existente));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(novaCategoria));

        ProductResponse response = productService.update(5L, request);

        assertThat(response.name()).isEqualTo("Novo Nome");
        assertThat(response.price()).isEqualByComparingTo("50.00");
        assertThat(response.stockQuantity()).isEqualTo(30);
        assertThat(response.categoryId()).isEqualTo(2L);
        // Dirty checking do JPA: não deve haver save() explícito.
        verify(productRepository, never()).save(any());
    }

    @Test
    void updateLancaExcecaoQuandoProdutoNaoExiste() {
        ProductRequest request = new ProductRequest("Novo Nome", "novo", new BigDecimal("50.00"), 30, null, 1L);
        when(productRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.update(404L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteDesativaOProdutoSemRemoverALinha() {
        Product existente = Product.builder().id(5L).name("Produto").active(true).build();
        when(productRepository.findById(5L)).thenReturn(Optional.of(existente));

        productService.delete(5L);

        assertThat(existente.getActive()).isFalse();
        verify(productRepository, never()).delete(any());
        verify(productRepository, never()).deleteById(any());
    }

    @Test
    void deleteLancaExcecaoQuandoProdutoNaoExiste() {
        when(productRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.delete(404L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findMostAccessedRetornaProdutosOrdenadosPorAcessoDeFormaPaginada() {
        Product maisAcessado = Product.builder().id(1L).name("Popular").accessCount(500L).category(category).build();
        Pageable pageable = PageRequest.of(0, 10);
        Page<Product> page = new PageImpl<>(List.of(maisAcessado), pageable, 1);
        when(productRepository.findByActiveTrueOrderByAccessCountDesc(pageable)).thenReturn(page);

        MostAccessedProductsResponse response = productService.findMostAccessed(pageable);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).name()).isEqualTo("Popular");
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(10);
    }

    @Test
    void findMostAccessedNaoIncluiProdutosInativos() {
        // A query do repositório já filtra active=true; aqui garantimos que
        // o service não faz nenhum filtro adicional que mascare esse contrato.
        Pageable pageable = PageRequest.of(0, 10);
        when(productRepository.findByActiveTrueOrderByAccessCountDesc(pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        MostAccessedProductsResponse response = productService.findMostAccessed(pageable);

        assertThat(response.content()).isEmpty();
        verify(productRepository).findByActiveTrueOrderByAccessCountDesc(pageable);
    }
}
