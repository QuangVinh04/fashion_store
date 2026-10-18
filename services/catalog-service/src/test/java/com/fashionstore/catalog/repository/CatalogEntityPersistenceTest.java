package com.fashionstore.catalog.repository;

import com.fashionstore.catalog.entity.*;
import com.fashionstore.catalog.entity.enumeration.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database=h2", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:catalog-final-model;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CatalogEntityPersistenceTest {
    @Autowired EntityManager em;

    @Test void maximumApiProductNameFitsTheDatabase() {
        var product = Product.builder().name("A".repeat(255)).slug("maximum-name").build();
        em.persist(product); em.flush(); em.clear();
        assertThat(em.find(Product.class, product.getId()).getName()).hasSize(255);
    }

    @Test void supportedProductAttributesAndRealDatesSurviveReload() {
        var publishedAt = LocalDateTime.of(2026, 10, 1, 10, 0);
        var product = Product.builder().name("Shirt").slug("persisted-shirt")
                .productType(ProductType.SHIRT).shortDescription("Cotton shirt")
                .sizeChartId("chart-id").thumbnailMediaId("image-id")
                .metaTitle("Title").metaKeyword("cotton").metaDescription("Description")
                .publishedAt(publishedAt).status(ProductStatus.PUBLISHED).build();
        em.persist(product); em.flush(); em.clear();
        var actual = em.find(Product.class, product.getId());
        assertThat(actual.getProductType()).isEqualTo(ProductType.SHIRT);
        assertThat(actual.getSizeChartId()).isEqualTo("chart-id");
        assertThat(actual.getShortDescription()).isEqualTo("Cotton shirt");
        assertThat(actual.getThumbnailMediaId()).isEqualTo("image-id");
        assertThat(actual.getMetaTitle()).isEqualTo("Title");
        assertThat(actual.getPublishedAt()).isEqualTo(publishedAt);
        assertThat(actual.getPublished()).isTrue();
    }

    @Test void unknownHistoricalDatesRemainUnknown() {
        assertThat(Product.builder().status(ProductStatus.PUBLISHED).build().getPublishedAt()).isNull();
        assertThat(Product.builder().status(ProductStatus.ARCHIVED).build().getDeletedAt()).isNull();
    }

    @Test void skuPackagingOverridesAndImagesSurviveReload() {
        var product = Product.builder().name("Polo").slug("persisted-polo").weight(300).length(20).width(15).height(2).build();
        em.persist(product);
        var variant = ProductVariant.builder().product(product).sku("POLO-M")
                .displayName("M").optionSignature("M").price(java.math.BigDecimal.valueOf(80000)).weightGram(400).lengthMm(19)
                .barcode("barcode-m").thumbnailMediaId("sku-photo").build();
        var image = ProductImage.builder().product(product).mediaId("gallery-photo")
                .url("https://example.test/image.jpg").altText("Polo front").build();
        em.persist(variant); em.persist(image); em.flush(); em.clear();
        var actual = em.find(ProductVariant.class, variant.getId());
        assertThat(actual.getWeightGram()).isEqualTo(400);
        assertThat(actual.getLengthMm()).isEqualTo(19);
        assertThat(actual.getLength()).isEqualTo(2);
        assertThat(actual.getWidth()).isEqualTo(15);
        assertThat(actual.getBarcode()).isEqualTo("barcode-m");
        assertThat(actual.getThumbnailMediaId()).isEqualTo("sku-photo");
        assertThat(em.find(ProductImage.class, image.getId()).getMediaId()).isEqualTo("gallery-photo");
        assertThat(em.find(ProductImage.class, image.getId()).getAltText()).isEqualTo("Polo front");
    }

    @Test void editingInheritedSkuDoesNotFreezeTheOldProductDefault() {
        var product = Product.builder().name("Inherited packaging").slug("inherited-packaging")
                .weight(300).length(20).build();
        em.persist(product);
        var variant = ProductVariant.builder().product(product).sku("INHERITED-M")
                .price(java.math.BigDecimal.valueOf(80000)).displayName("M").optionSignature("M").build();
        em.persist(variant); em.flush(); em.clear();

        var actual = em.find(ProductVariant.class, variant.getId());
        var mapper = org.mapstruct.factory.Mappers.getMapper(com.fashionstore.catalog.mapper.ProductMapper.class);
        var editable = mapper.toProductVariantResponse(actual);
        actual.getProduct().setWeight(400);
        actual.setWeightGram(editable.getWeightGram());
        actual.setLengthMm(editable.getLengthMm());
        em.flush(); em.clear();

        var reloaded = em.find(ProductVariant.class, variant.getId());
        assertThat(reloaded.getWeightGramOverride()).isNull();
        assertThat(reloaded.getLengthMmOverride()).isNull();
        assertThat(reloaded.getWeightGram()).isEqualTo(400);
        assertThat(reloaded.getLengthMm()).isEqualTo(200);
    }

    @Test void mediaMetadataAndImageIdentitiesRemainDistinctFromUrls() {
        var media = MediaFile.builder().ownerId("admin").originalFilename("shirt.jpg").displayName("Front")
                .storedFilename("stored.jpg").storageKey("images/stored.jpg").contentType("image/jpeg")
                .sizeBytes(100L).mediaType(MediaType.IMAGE).altText("Front view").folder("catalog")
                .tags(new LinkedHashSet<>(Set.of("shirt"))).build();
        var brand = Brand.builder().name("Brand").slug("brand").logoMediaId("brand-media").logoUrl("https://example.test/brand.jpg").build();
        var category = Category.builder().name("Tops").slug("tops").thumbnailUrl("https://example.test/tops.jpg").build();
        category.setThumbnailMediaId("category-media");
        em.persist(media); em.persist(brand); em.persist(category); em.flush(); em.clear();
        var actual = em.find(MediaFile.class, media.getId());
        assertThat(actual.getDisplayName()).isEqualTo("Front");
        assertThat(actual.getAltText()).isEqualTo("Front view");
        assertThat(actual.getFolder()).isEqualTo("catalog");
        assertThat(actual.getTags()).containsExactly("shirt");
        assertThat(em.find(Brand.class, brand.getId()).getLogoMediaId()).isEqualTo("brand-media");
        assertThat(em.find(Brand.class, brand.getId()).getLogoUrl()).isEqualTo("https://example.test/brand.jpg");
        assertThat(em.find(Category.class, category.getId()).getThumbnailMediaId()).isEqualTo("category-media");
        assertThat(em.find(Category.class, category.getId()).getThumbnailUrl()).isEqualTo("https://example.test/tops.jpg");
    }
}
