package com.fashionstore.catalog.service;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.util.SlugUtils;
import com.fashionstore.catalog.dto.BrandRequest;
import com.fashionstore.catalog.dto.BrandResponse;
import com.fashionstore.catalog.exception.ProductErrorCode;
import com.fashionstore.catalog.model.Brand;
import com.fashionstore.catalog.repository.BrandRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class BrandService {

    BrandRepository brandRepository;

    @Transactional(readOnly = true)
    public List<BrandResponse> getAll() {
        return brandRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public BrandResponse getById(String id) {
        return toResponse(findById(id));
    }

    @Transactional
    public BrandResponse create(BrandRequest request) {
        String slug = normalizeSlug(request.getSlug(), request.getName());
        if (brandRepository.existsByName(request.getName()) || brandRepository.existsBySlug(slug)) {
            throw new AppException(ProductErrorCode.BRAND_ALREADY_EXIST);
        }
        Brand brand = Brand.builder()
                .name(request.getName())
                .slug(slug)
                .description(request.getDescription())
                .logoMediaId(request.getLogoMediaId())
                .active(request.getActive() == null || request.getActive())
                .build();
        return toResponse(brandRepository.save(brand));
    }

    @Transactional
    public BrandResponse update(String id, BrandRequest request) {
        Brand brand = findById(id);
        String slug = normalizeSlug(request.getSlug(), request.getName());
        if (brandRepository.existsByNameAndIdNot(request.getName(), id) || brandRepository.existsBySlugAndIdNot(slug, id)) {
            throw new AppException(ProductErrorCode.BRAND_ALREADY_EXIST);
        }
        brand.setName(request.getName());
        brand.setSlug(slug);
        brand.setDescription(request.getDescription());
        brand.setLogoMediaId(request.getLogoMediaId());
        brand.setActive(request.getActive() == null || request.getActive());
        return toResponse(brandRepository.save(brand));
    }

    @Transactional
    public void delete(String id) {
        brandRepository.delete(findById(id));
    }

    private Brand findById(String id) {
        return brandRepository.findById(id)
                .orElseThrow(() -> new AppException(ProductErrorCode.BRAND_NOT_FOUND));
    }

    private BrandResponse toResponse(Brand brand) {
        return BrandResponse.builder()
                .id(brand.getId())
                .name(brand.getName())
                .slug(brand.getSlug())
                .description(brand.getDescription())
                .logoMediaId(brand.getLogoMediaId())
                .active(brand.getActive())
                .build();
    }

    private String normalizeSlug(String slug, String fallbackName) {
        String value = slug == null || slug.isBlank() ? fallbackName : slug;
        return SlugUtils.makeSlug(value);
    }
}
