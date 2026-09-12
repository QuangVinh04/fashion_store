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

    /**
     * The variant join has to carry both restrictions: matching the option name is not
     * enough, otherwise a deactivated colour/size still surfaces the product in a public
     * faceted filter while the PDP refuses to sell it.
     */
    private void stubActiveVariantJoin(String key, String value) {
        when(root.<Product, ProductVariant>join("variants")).thenReturn(variantJoin);
        when(variantJoin.<String>get(key)).thenReturn(optionPath);
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
