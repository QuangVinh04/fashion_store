package com.fashionstore.catalog.product.repository;

import com.fashionstore.catalog.product.entity.Product;
import com.fashionstore.catalog.product.entity.ProductVariant;
import com.fashionstore.catalog.product.entity.ProductStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.database=h2",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:variant-filters;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductVariantFilterIntegrationTest {
    @Autowired ProductRepository products;

    @Test
    void colorAndSizeMustMatchTheSameActiveVariant() {
        Product crossed = product("crossed");
        variant(crossed, "red", "S", true);
        variant(crossed, "blue", "L", true);
        products.saveAndFlush(crossed);
        Product inactive = product("inactive");
        variant(inactive, "red", "L", false);
        products.saveAndFlush(inactive);
        Product matching = product("matching");
        variant(matching, "red", "L", true);
        products.saveAndFlush(matching);

        for (boolean sizeFirst : new boolean[]{false, true}) {
            var builder = new ProductSpecificationsBuilder();
            if (sizeFirst) builder.with("size", ":", "l", null, null);
            builder.with("color", ":", "ReD", null, null);
            if (!sizeFirst) builder.with("size", ":", "l", null, null);
            var page = products.findAll(builder.build(), PageRequest.of(0, 1));

            assertThat(page.getContent()).extracting(Product::getId).containsExactly(matching.getId());
            assertThat(page.getTotalElements()).isEqualTo(1);
        }
    }

    @Test
    void singleOptionStillMatchesAnyActiveVariant() {
        Product crossed = product("single-options");
        variant(crossed, "red", "S", true);
        variant(crossed, "blue", "L", true);
        products.saveAndFlush(crossed);
        Product inactive = product("inactive-options");
        variant(inactive, "red", "L", false);
        products.saveAndFlush(inactive);

        for (String key : new String[]{"color", "size"}) {
            var criteria = new ProductSpecificationsBuilder()
                    .with(key, ":", key.equals("color") ? "red" : "L", null, null).build();
            assertThat(products.findAll(criteria, PageRequest.of(0, 10)).getContent())
                    .extracting(Product::getId).containsExactly(crossed.getId());
        }
    }

    private Product product(String name) {
        return Product.builder().name(name).slug(name).status(ProductStatus.PUBLISHED).build();
    }

    private void variant(Product product, String color, String size, boolean active) {
        product.getVariants().add(ProductVariant.builder().product(product)
                .sku(UUID.randomUUID().toString()).optionSignature(color + ":" + size)
                .displayName(color + " / " + size).color(color).size(size).active(active).build());
    }
}
