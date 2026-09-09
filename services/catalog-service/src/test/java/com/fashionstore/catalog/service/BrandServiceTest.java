package com.fashionstore.catalog.service;

import com.fashionstore.catalog.dto.BrandResponse;
import com.fashionstore.catalog.model.Brand;
import com.fashionstore.catalog.repository.BrandRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrandServiceTest {

    @Mock
    BrandRepository brandRepository;

    @InjectMocks
    BrandService brandService;

    /**
     * product.brand_id carries a real FK (fk_product_brand) with no ON DELETE rule, so a hard
     * delete of a brand still referenced by products fails at flush time and surfaces as a 500.
     * Deactivating keeps the row and the reference intact.
     */
    @Test
    void deleteDeactivatesTheBrandInsteadOfRemovingTheRow() {
        Brand brand = Brand.builder().name("Nike").slug("nike").active(true).build();
        when(brandRepository.findById("b1")).thenReturn(Optional.of(brand));

        brandService.delete("b1");

        assertThat(brand.getActive()).isFalse();
        verify(brandRepository).save(brand);
        verify(brandRepository, never()).delete(any(Brand.class));
    }

    @Test
    void deletingAnAlreadyInactiveBrandStaysInactive() {
        Brand brand = Brand.builder().name("Nike").slug("nike").active(false).build();
        when(brandRepository.findById("b1")).thenReturn(Optional.of(brand));

        brandService.delete("b1");

        assertThat(brand.getActive()).isFalse();
        verify(brandRepository, never()).delete(any(Brand.class));
    }

    /** /api/v1/brands is permitAll, so the storefront listing must not show deactivated brands. */
    @Test
    void publicListOnlyReturnsActiveBrands() {
        Brand active = Brand.builder().name("Nike").slug("nike").active(true).build();
        when(brandRepository.findAllByActiveTrueOrderByNameAsc()).thenReturn(List.of(active));

        List<BrandResponse> brands = brandService.getAll();

        assertThat(brands).singleElement().satisfies(brand -> {
            assertThat(brand.getName()).isEqualTo("Nike");
            assertThat(brand.getActive()).isTrue();
        });
        verify(brandRepository, never()).findAll();
    }

    /** The backoffice still has to see deactivated brands, otherwise delete is irreversible. */
    @Test
    void adminListIncludesInactiveBrands() {
        Brand inactive = Brand.builder().name("Puma").slug("puma").active(false).build();
        when(brandRepository.findAll()).thenReturn(List.of(inactive));

        List<BrandResponse> brands = brandService.getAllForAdmin();

        assertThat(brands).singleElement().satisfies(brand -> {
            assertThat(brand.getName()).isEqualTo("Puma");
            assertThat(brand.getActive()).isFalse();
        });
    }
}
