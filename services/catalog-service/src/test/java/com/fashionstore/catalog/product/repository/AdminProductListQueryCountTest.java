package com.fashionstore.catalog.product.repository;

import com.fashionstore.catalog.product.dto.AdminProductFilter;
import com.fashionstore.catalog.product.entity.Brand;
import com.fashionstore.catalog.product.entity.Category;
import com.fashionstore.catalog.product.entity.Product;
import com.fashionstore.catalog.product.entity.ProductCategory;
import com.fashionstore.catalog.product.mapper.ProductMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/** Danh sách sản phẩm admin phải chạy số câu SQL cố định, không tăng theo số dòng trong trang (chặn N+1). */
@DataJpaTest(properties = {
    "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.database=h2",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.datasource.url=jdbc:h2:mem:productquerycount;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.show-sql=false",
    "spring.jpa.properties.hibernate.generate_statistics=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AdminProductListQueryCountTest {
    private static final int PRODUCT_COUNT = 12;
    private static final int PAGE_SIZE = 10;
    // count + page + productCategories batch + category batch + brand
    private static final long MAX_STATEMENTS = 5;

    private final ProductMapper mapper = Mappers.getMapper(ProductMapper.class);

    @Autowired ProductRepository products;
    @Autowired BrandRepository brands;
    @Autowired CategoryRepository categories;
    @Autowired EntityManager entityManager;
    @Autowired EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void seedProducts() {
        for (int index = 0; index < PRODUCT_COUNT; index++) {
            Brand brand = brands.save(Brand.builder().name("Brand " + index).slug("brand-" + index).build());
            Category first = categories.save(Category.builder().name("First " + index).slug("first-" + index).build());
            Category second = categories.save(Category.builder().name("Second " + index).slug("second-" + index).build());
            Product product = Product.builder().name("Product " + index).slug("product-" + index).brand(brand).build();
            product.getProductCategories().add(ProductCategory.builder().product(product).category(first).build());
            product.getProductCategories().add(ProductCategory.builder().product(product).category(second).build());
            products.save(product);
        }
        entityManager.flush();
    }

    @Test
    void filteredListDoesNotLoadRelationsPerRow() {
        AdminProductFilter filter = AdminProductFilter.builder().keyword("product").build();

        long statements = countStatements(() -> products.findAll(AdminProductSpecifications.from(filter), firstPage()));

        assertThat(statements).isLessThanOrEqualTo(MAX_STATEMENTS);
    }

    @Test
    void unfilteredListDoesNotLoadRelationsPerRow() {
        long statements = countStatements(() -> products.findAll(firstPage()));

        assertThat(statements).isLessThanOrEqualTo(MAX_STATEMENTS);
    }

    private Pageable firstPage() {
        return PageRequest.of(0, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private long countStatements(Supplier<Page<Product>> query) {
        entityManager.clear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        Page<Product> page = query.get();
        page.forEach(mapper::toProductSummaryResponse);

        assertThat(page.getContent()).hasSize(PAGE_SIZE).allSatisfy(product ->
            assertThat(mapper.toProductSummaryResponse(product).getCategoryName()).isNotNull());
        return statistics.getPrepareStatementCount();
    }
}
