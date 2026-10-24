package com.fashionstore.catalog.product.repository;

import com.fashionstore.catalog.product.entity.Brand;
import com.fashionstore.catalog.product.entity.Category;
import com.fashionstore.catalog.product.entity.Product;
import com.fashionstore.catalog.product.entity.ProductCategory;
import com.fashionstore.catalog.product.entity.ProductVariant;
import com.fashionstore.catalog.product.entity.Gender;
import com.fashionstore.catalog.product.entity.ProductStatus;
import com.fashionstore.catalog.product.entity.ProductType;
import com.fashionstore.catalog.product.dto.AdminProductFilter;
import com.fashionstore.catalog.inventory.entity.Inventory;
import com.fashionstore.catalog.inventory.entity.InventoryStatus;
import com.fashionstore.catalog.inventory.repository.InventoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.database=h2",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.datasource.url=jdbc:h2:mem:adminfiltertest;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BackofficeProductFiltersTest {
    @Autowired ProductRepository products;
    @Autowired BrandRepository brands;
    @Autowired CategoryRepository categories;
    @Autowired InventoryRepository inventories;

    @Test void summarizesStockOverActiveVariantsOnly() {
        Product product = Product.builder().name("Stocked Tee").slug("stocked-tee").build();
        ProductVariant healthy = ProductVariant.builder().product(product).sku("ST-1").active(true)
                .displayName("Healthy").optionSignature("one").build();
        ProductVariant low = ProductVariant.builder().product(product).sku("ST-2").active(true)
                .displayName("Low").optionSignature("two").build();
        ProductVariant inactive = ProductVariant.builder().product(product).sku("ST-3").active(false)
                .displayName("Inactive").optionSignature("three").build();
        product.getVariants().add(healthy);
        product.getVariants().add(low);
        product.getVariants().add(inactive);
        products.saveAndFlush(product);

        inventories.saveAndFlush(Inventory.builder().productId(product.getId()).variantId(healthy.getId())
                .quantity(10).reservedQuantity(2).minThreshold(5).status(InventoryStatus.ACTIVE).build());
        inventories.saveAndFlush(Inventory.builder().productId(product.getId()).variantId(low.getId())
                .quantity(3).reservedQuantity(0).minThreshold(5).status(InventoryStatus.ACTIVE).build());
        inventories.saveAndFlush(Inventory.builder().productId(product.getId()).variantId(inactive.getId())
                .quantity(100).reservedQuantity(0).minThreshold(5).status(InventoryStatus.ACTIVE).build());

        var aggregate = inventories.summarizeByProductIds(List.of(product.getId())).getFirst();

        assertThat(aggregate.getActiveVariantCount()).isEqualTo(2);
        assertThat(aggregate.getTotalQuantity()).isEqualTo(13);
        assertThat(aggregate.getTotalReserved()).isEqualTo(2);
        assertThat(aggregate.getTotalAvailable()).isEqualTo(11);
        assertThat(aggregate.getLowStockVariantCount()).isEqualTo(1);
        assertThat(aggregate.getOutOfStockVariantCount()).isZero();
    }

    @Test void combinesFiltersAndCountsProductsOnceAcrossCategories() {
        Brand brand = brands.saveAndFlush(Brand.builder().name("Brand").slug("brand").build());
        Category first = categories.saveAndFlush(Category.builder().name("Tops").slug("tops").build());
        Category second = categories.saveAndFlush(Category.builder().name("Summer").slug("summer").build());
        Product matching = Product.builder().name("Cotton Shirt").slug("cotton-shirt").brand(brand)
            .gender(Gender.MEN).productType(ProductType.SHIRT).status(ProductStatus.DRAFT).basePrice(new BigDecimal("200")).build();
        matching.getProductCategories().add(ProductCategory.builder().product(matching).category(first).build());
        matching.getProductCategories().add(ProductCategory.builder().product(matching).category(second).build());
        products.saveAndFlush(matching);
        products.saveAndFlush(Product.builder().name("Cotton Tee").slug("cotton-tee").status(ProductStatus.PUBLISHED).basePrice(new BigDecimal("50")).build());
        AdminProductFilter filters = AdminProductFilter.builder().keyword("COTTON").status(ProductStatus.DRAFT)
            .brandId(brand.getId()).categoryId(second.getId()).gender(Gender.MEN).productType(ProductType.SHIRT)
            .minPrice(new BigDecimal("100")).maxPrice(new BigDecimal("300")).build();
        var result = products.findAll(AdminProductSpecifications.from(filters), PageRequest.of(0, 1));
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).extracting(Product::getSlug).containsExactly("cotton-shirt");
        filters.setStatus(ProductStatus.PUBLISHED);
        assertThat(products.findAll(AdminProductSpecifications.from(filters), PageRequest.of(0, 1)).getTotalElements()).isZero();
    }

    @Test void keywordIsLiteralAndCanMatchVariantSku() {
        Product shirt = Product.builder().name("Shirt").slug("shirt").build();
        shirt.getVariants().add(ProductVariant.builder().product(shirt).sku("SHIRT-100%").displayName("Default").optionSignature("one").build());
        products.saveAndFlush(shirt);
        products.saveAndFlush(Product.builder().name("Other").slug("other").build());
        assertThat(products.findAll(AdminProductSpecifications.from(AdminProductFilter.builder().keyword("100%").build()), PageRequest.of(0, 20)).getTotalElements()).isEqualTo(1);
        assertThat(products.findAll(AdminProductSpecifications.from(AdminProductFilter.builder().keyword("_").build()), PageRequest.of(0, 20)).getTotalElements()).isZero();
    }
}
