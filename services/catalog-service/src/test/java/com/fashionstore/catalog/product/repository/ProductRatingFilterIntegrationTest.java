package com.fashionstore.catalog.product.repository;

import com.fashionstore.catalog.product.entity.Product;
import com.fashionstore.catalog.product.entity.ProductStatus;
import com.fashionstore.catalog.product.entity.ProductType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.database=h2",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:rating-filters;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductRatingFilterIntegrationTest {
    @Autowired ProductRepository products;
    @Autowired EntityManager em;

    @Test
    void storedAverageRatingCombinesWithTypePriceAndPagination() {
        Product boundary = product("boundary", ProductType.SHIRT, 200, "4.0");
        Product high = product("high", ProductType.SHIRT, 200, "4.8");
        product("too-low", ProductType.SHIRT, 200, "3.9");
        product("wrong-type", ProductType.JEANS, 200, "5.0");
        product("expensive", ProductType.SHIRT, 900, "5.0");
        product("unreviewed", ProductType.SHIRT, 200, "0.0");
        em.flush();

        var criteria = new ProductSpecificationsBuilder()
                .with("minRating", ":", 4, null, null)
                .with("productType", ":", ProductType.SHIRT, null, null)
                .with("maxPrice", "<", BigDecimal.valueOf(500), null, null).build();
        var first = products.findAll(criteria, PageRequest.of(0, 1));
        var second = products.findAll(criteria, PageRequest.of(1, 1));
        assertThat(first.getTotalElements()).isEqualTo(2);
        assertThat(java.util.stream.Stream.concat(first.stream(), second.stream()).map(Product::getId).toList())
                .containsExactlyInAnyOrder(boundary.getId(), high.getId());
    }

    @Test
    void updateRatingStoresStatsWithoutBumpingVersion() {
        Product product = product("rated", ProductType.SHIRT, 200, "0.0");
        Long version = product.getVersion();

        products.updateRating(product.getId(), new BigDecimal("4.5"), 2);
        em.clear();

        Product reloaded = products.findById(product.getId()).orElseThrow();
        assertThat(reloaded.getRatingAverage()).isEqualByComparingTo("4.5");
        assertThat(reloaded.getReviewCount()).isEqualTo(2);
        assertThat(reloaded.getVersion()).isEqualTo(version);
    }

    private Product product(String name, ProductType type, int price, String ratingAverage) {
        return products.saveAndFlush(Product.builder().name(name).slug(name).productType(type)
                .status(ProductStatus.PUBLISHED).basePrice(BigDecimal.valueOf(price))
                .ratingAverage(new BigDecimal(ratingAverage)).build());
    }
}
