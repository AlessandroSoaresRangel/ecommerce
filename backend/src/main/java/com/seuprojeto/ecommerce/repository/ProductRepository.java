package com.seuprojeto.ecommerce.repository;

import com.seuprojeto.ecommerce.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // O CAST(:name AS string) é necessário porque, quando :name é null, o
    // driver do Postgres não consegue inferir o tipo do parâmetro dentro do
    // CONCAT (acaba tratando-o como bytea) e a query falha com
    // "function lower(bytea) does not exist". O cast força VARCHAR sempre,
    // com ou sem valor.
    @Query("""
        SELECT p FROM Product p
        WHERE (:includeInactive = true OR p.active = true)
          AND (:categoryId IS NULL OR p.category.id = :categoryId)
          AND (:name IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%')))
        """)
    Page<Product> search(@Param("categoryId") Long categoryId,
                          @Param("name") String name,
                          @Param("includeInactive") boolean includeInactive,
                          Pageable pageable);

    Page<Product> findByActiveTrueOrderByAccessCountDesc(Pageable pageable);

    // Incremento atômico em uma única instrução SQL: evita perder contagens
    // sob concorrência (duas visualizações simultâneas não se sobrescrevem
    // como aconteceria com um read-then-write via entidade gerenciada).
    @Modifying
    @Query("UPDATE Product p SET p.accessCount = p.accessCount + 1 WHERE p.id = :id")
    void incrementAccessCount(@Param("id") Long id);
}
