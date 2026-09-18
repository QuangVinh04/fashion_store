package com.fashionstore.catalog.repository;

import com.fashionstore.catalog.entity.Product;
import com.fashionstore.catalog.entity.ProductVariant;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductSpecificationTest {

    @Mock
    Root<Product> root;
    @Mock
    CriteriaQuery<?> query;
    @Mock
    CriteriaBuilder cb;
    @Mock
    Join<Product, ProductVariant> variantJoin;
    @Mock
    Path<String> optionPath;
    @Mock
    Path<Boolean> activePath;
    @Mock
    Predicate optionMatches;
    @Mock
    Predicate variantIsActive;
    @Mock
    Predicate combined;

    @Test
    void colorFilterOnlyMatchesActiveVariants() {
        stubActiveVariantJoin("color", "red");

        Predicate predicate = specification("color", "red").toPredicate(root, query, cb);

        assertThat(predicate).isSameAs(combined);
    }

    @Test
    void sizeFilterOnlyMatchesActiveVariants() {
        stubActiveVariantJoin("size", "xl");

        Predicate predicate = specification("size", "xl").toPredicate(root, query, cb);

        assertThat(predicate).isSameAs(combined);
    }

    @Test
    void minPriceFilter_generatesGreaterThanOrEqualToPredicate() {
        Path<java.math.BigDecimal> basePricePath = org.mockito.Mockito.mock(Path.class);
        Predicate gtePredicate = org.mockito.Mockito.mock(Predicate.class);
        when(root.<java.math.BigDecimal>get("basePrice")).thenReturn(basePricePath);
        when(cb.greaterThanOrEqualTo(basePricePath, new java.math.BigDecimal("100000"))).thenReturn(gtePredicate);

        Predicate predicate = specification("minPrice", "100000").toPredicate(root, query, cb);

        assertThat(predicate).isSameAs(gtePredicate);
    }

    @Test
    void maxPriceFilter_generatesLessThanOrEqualToPredicate() {
        Path<java.math.BigDecimal> basePricePath = org.mockito.Mockito.mock(Path.class);
        Predicate ltePredicate = org.mockito.Mockito.mock(Predicate.class);
        when(root.<java.math.BigDecimal>get("basePrice")).thenReturn(basePricePath);
        when(cb.lessThanOrEqualTo(basePricePath, new java.math.BigDecimal("500000"))).thenReturn(ltePredicate);

        Predicate predicate = specification("maxPrice", "500000").toPredicate(root, query, cb);

        assertThat(predicate).isSameAs(ltePredicate);
    }

    @Test
    void brandIdFilter_generatesEqualPredicate() {
        Path<Object> brandPath = org.mockito.Mockito.mock(Path.class);
        Path<Object> idPath = org.mockito.Mockito.mock(Path.class);
        Predicate eqPredicate = org.mockito.Mockito.mock(Predicate.class);
        when(root.get("brand")).thenReturn(brandPath);
        when(brandPath.get("id")).thenReturn(idPath);
        when(cb.equal(idPath, "brand-123")).thenReturn(eqPredicate);

        Predicate predicate = specification("brandId", "brand-123").toPredicate(root, query, cb);

        assertThat(predicate).isSameAs(eqPredicate);
    }

    @Test
    void genderFilter_generatesEqualPredicate() {
        Path<Object> genderPath = org.mockito.Mockito.mock(Path.class);
        Predicate eqPredicate = org.mockito.Mockito.mock(Predicate.class);
        when(root.get("gender")).thenReturn(genderPath);
        when(cb.equal(genderPath, com.fashionstore.catalog.entity.enumeration.Gender.MEN)).thenReturn(eqPredicate);

        Predicate predicate = specification("gender", "MEN").toPredicate(root, query, cb);

        assertThat(predicate).isSameAs(eqPredicate);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void categoryIdFilter_generatesEqualPredicate() {
        Join productCategoryJoin = org.mockito.Mockito.mock(Join.class);
        Join categoryJoin = org.mockito.Mockito.mock(Join.class);
        Path idPath = org.mockito.Mockito.mock(Path.class);
        Predicate eqPredicate = org.mockito.Mockito.mock(Predicate.class);

        when(root.join("productCategories")).thenReturn(productCategoryJoin);
        when(productCategoryJoin.join("category")).thenReturn(categoryJoin);
        when(categoryJoin.get("id")).thenReturn(idPath);
        when(cb.equal(idPath, "cat-123")).thenReturn(eqPredicate);

        Predicate predicate = specification("categoryId", "cat-123").toPredicate(root, query, cb);

        assertThat(predicate).isSameAs(eqPredicate);
    }

    /**
     * The variant join has to carry both restrictions: matching the option name is not
     * enough, otherwise a deactivated colour/size still surfaces the product in a public
     * faceted filter while the PDP refuses to sell it.
     */
    private void stubActiveVariantJoin(String key, String value) {
        String attributeName = "color".equals(key) ? "colorDisplay" : ("size".equals(key) ? "sizeDisplay" : key);
        when(root.<Product, ProductVariant>join("variants")).thenReturn(variantJoin);
        when(variantJoin.<String>get(attributeName)).thenReturn(optionPath);
        when(variantJoin.<Boolean>get("active")).thenReturn(activePath);
        when(cb.lower(optionPath)).thenReturn(optionPath);
        when(cb.equal(optionPath, value)).thenReturn(optionMatches);
        when(cb.isTrue(activePath)).thenReturn(variantIsActive);
        when(cb.and(optionMatches, variantIsActive)).thenReturn(combined);
    }

    private ProductSpecification specification(String key, String value) {
        return new ProductSpecification(new SpecSearchCriteria(key, SearchOperation.EQUALITY, value));
    }
}
