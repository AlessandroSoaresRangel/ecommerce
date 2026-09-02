package com.seuprojeto.ecommerce.mapper;

import com.seuprojeto.ecommerce.dto.product.ProductResponse;
import com.seuprojeto.ecommerce.entity.Category;
import com.seuprojeto.ecommerce.entity.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O mapeamento entity->DTO é gerado pelo MapStruct em tempo de compilação;
 * esse teste garante que a configuração declarada em ProductMapper (achatar
 * category.id/category.name em categoryId/categoryName) realmente produz o
 * resultado esperado, inclusive quando o produto não tem categoria.
 */
class ProductMapperTest {

    private final ProductMapper mapper = new ProductMapperImpl();

    @Test
    void mapeiaTodosOsCamposIncluindoOsDaCategoria() {
        Category category = Category.builder().id(1L).name("Eletrônicos").build();
        Product product = Product.builder()
                .id(10L)
                .name("Mouse gamer")
                .description("16000 DPI")
                .price(new BigDecimal("199.90"))
                .stockQuantity(25)
                .imageUrl("mouse.png")
                .active(true)
                .category(category)
                .build();

        ProductResponse response = mapper.toResponse(product);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("Mouse gamer");
        assertThat(response.price()).isEqualByComparingTo("199.90");
        assertThat(response.stockQuantity()).isEqualTo(25);
        assertThat(response.active()).isTrue();
        assertThat(response.categoryId()).isEqualTo(1L);
        assertThat(response.categoryName()).isEqualTo("Eletrônicos");
    }

    @Test
    void naoQuebraQuandoOProdutoNaoTemCategoria() {
        Product product = Product.builder()
                .id(11L).name("Produto órfão").price(BigDecimal.TEN).stockQuantity(1).active(true)
                .category(null)
                .build();

        ProductResponse response = mapper.toResponse(product);

        assertThat(response.categoryId()).isNull();
        assertThat(response.categoryName()).isNull();
    }
}
