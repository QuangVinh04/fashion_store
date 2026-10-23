package com.fashionstore.catalog.product.repository;

import com.fashionstore.catalog.product.entity.Product;
import com.fashionstore.catalog.review.entity.Review;
import com.fashionstore.catalog.product.entity.ProductStatus;
import com.fashionstore.catalog.product.entity.ProductType;
import com.fashionstore.catalog.review.entity.ReviewStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.UUID;
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
    void averageVisibleRatingCombinesWithTypePriceAndPagination() {
        Product boundary = product("boundary", ProductType.SHIRT, 200);
        review(boundary, 5, ReviewStatus.VISIBLE); review(boundary, 3, ReviewStatus.VISIBLE);
        Product hiddenIgnored = product("hidden-ignored", ProductType.SHIRT, 200);
        review(hiddenIgnored, 5, ReviewStatus.VISIBLE); review(hiddenIgnored, 1, ReviewStatus.HIDDEN);
        Product tooLow = product("too-low", ProductType.SHIRT, 200);
        review(tooLow, 4, ReviewStatus.VISIBLE); review(tooLow, 3, ReviewStatus.VISIBLE);
        Product wrongType = product("wrong-type", ProductType.JEANS, 200);
        review(wrongType, 5, ReviewStatus.VISIBLE);
        Product expensive = product("expensive", ProductType.SHIRT, 900);
        review(expensive, 5, ReviewStatus.VISIBLE);
        product("unreviewed", ProductType.SHIRT, 200);
        em.flush();

        var criteria = new ProductSpecificationsBuilder()
                .with("minRating", ":", 4, null, null)
                .with("productType", ":", ProductType.SHIRT, null, null)
                .with("maxPrice", "<", BigDecimal.valueOf(500), null, null).build();
        var first = products.findAll(criteria, PageRequest.of(0, 1));
        var second = products.findAll(criteria, PageRequest.of(1, 1));
        assertThat(first.getTotalElements()).isEqualTo(2);
        assertThat(java.util.stream.Stream.concat(first.stream(), second.stream()).map(Product::getId).toList())
                .containsExactlyInAnyOrder(boundary.getId(), hiddenIgnored.getId());
    }

    private Product product(String name, ProductType type, int price) {
        return products.saveAndFlush(Product.builder().name(name).slug(name).productType(type)
                .status(ProductStatus.PUBLISHED).basePrice(BigDecimal.valueOf(price)).build());
    }
    private void review(Product product, int rating, ReviewStatus status) {
        em.persist(Review.builder().product(product).userId(UUID.randomUUID().toString())
                .orderId(UUID.randomUUID().toString()).rating(rating).status(status).build());
    }
}
