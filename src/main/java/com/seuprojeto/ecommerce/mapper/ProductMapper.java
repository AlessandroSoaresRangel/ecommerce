package com.seuprojeto.ecommerce.mapper;

import com.seuprojeto.ecommerce.dto.product.ProductResponse;
import com.seuprojeto.ecommerce.entity.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryName", source = "category.name")
    ProductResponse toResponse(Product product);
}
