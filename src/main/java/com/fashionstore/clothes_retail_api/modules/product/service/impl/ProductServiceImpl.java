package com.fashionstore.clothes_retail_api.modules.product.service.impl;

import com.fashionstore.clothes_retail_api.common.dto.PageResponse;
import com.fashionstore.clothes_retail_api.common.exception.AppException;
import com.fashionstore.clothes_retail_api.common.exception.ErrorCode;
import com.fashionstore.clothes_retail_api.modules.category.entity.Category;
import com.fashionstore.clothes_retail_api.modules.category.repository.CategoryRepository;
import com.fashionstore.clothes_retail_api.modules.product.dto.*;
import com.fashionstore.clothes_retail_api.modules.product.entity.Product;
import com.fashionstore.clothes_retail_api.modules.product.entity.ProductVariant;
import com.fashionstore.clothes_retail_api.modules.product.mapper.ProductMapper;
import com.fashionstore.clothes_retail_api.modules.product.mapper.ProductVariantMapper;
import com.fashionstore.clothes_retail_api.modules.product.repository.ProductRepository;
import com.fashionstore.clothes_retail_api.modules.product.repository.ProductVariantRepository;
import com.fashionstore.clothes_retail_api.modules.product.repository.specification.ProductSpecificationsBuilder;
import com.fashionstore.clothes_retail_api.modules.product.service.ProductService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProductServiceImpl implements ProductService {

    ProductRepository productRepository;
    CategoryRepository categoryRepository;
    ProductVariantRepository productVariantRepository;
    ProductMapper productMapper;
    ProductVariantMapper productVariantMapper;

    @Override
    public PageResponse<List<ProductSummaryResponse>> getAllProducts(Pageable pageable) {
        Page<Product> page = productRepository.findAll(pageable);

        return getPageResponse(pageable, page);
    }

    @Override
    public ProductResponse getProductById(String id) {
        Product product = productRepository.findDetailProductById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        return productMapper.toProductResponse(product);
    }

    @Override
    public PageResponse<List<ProductSummaryResponse>> searchProducts(Pageable pageable, String keyword) {
        Page<Product> page = productRepository.findAllByNameContaining(keyword, pageable);
        return getPageResponse(pageable, page);
    }

    @Override
    public PageResponse<List<ProductSummaryResponse>> getAllProductsByCategory(Pageable pageable, String categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));

        Page<Product> page = productRepository.findAllByCategory(category, pageable);
        return getPageResponse(pageable, page);
    }

    @Override
    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));

        Product product = productMapper.toProduct(request);
        product.setCategory(category);

        if(request.getVariants() != null){
            request.getVariants().forEach(productVariantMapper::toProductVariant);
        }

        productRepository.save(product);
        return productMapper.toProductResponse(product);
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(String productId, ProductRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        if(request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
            product.setCategory(category);
        }

        productMapper.updateProduct(product, request);
        productRepository.save(product);
        return productMapper.toProductResponse(product);
    }

    @Override
    @Transactional
    public void deleteProduct(String productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        productRepository.delete(product);
    }

    @Override
    @Transactional
    public ProductResponse addProductVariant(String productId, ProductVariantRequest request) {
        Product product = productRepository.findProductWithVariantsById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        ProductVariant pv = productMapper.toProductVariant(request);
        pv.setProduct(product);
        productVariantRepository.save(pv);

        product.getVariants().add(pv);
        productRepository.save(product);

        return productMapper.toProductResponse(product);
    }

    @Override
    @Transactional
    public ProductResponse updateProductVariant(String productId,
                                                String variantId,
                                                ProductVariantRequest request) {
        Product product = productRepository.findProductWithVariantsById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        // Đảm bảo tính sở hữu
        ProductVariant productVariant = product.getVariants().stream()
                .filter(v -> v.getId().equals(variantId))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_VARIANT_NOT_FOUND));

        productVariantMapper.updateProductVariant(productVariant, request);

        return productMapper.toProductResponse(product);
    }

    @Override
    @Transactional
    public ProductResponse deleteProductVariant(String productId, String variantId) {

        Product product = productRepository.findProductWithVariantsById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        ProductVariant variantToDelete = product.getVariants().stream()
                .filter(v -> v.getId().equals(variantId))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_VARIANT_NOT_FOUND));


        // Nhờ orphanRemoval = true, Hibernate sẽ tự động xóa bản ghi này trong DB
        product.getVariants().remove(variantToDelete);

        return productMapper.toProductResponse(product);
    }


    @Override
    public PageResponse<List<ProductSummaryResponse>> advanceSearchWithSpecifications (Pageable pageable, String[] product) {
        log.info("Search product by specifications");

        if (product != null) {

            ProductSpecificationsBuilder builder = new ProductSpecificationsBuilder();

            Pattern pattern = Pattern.compile("(\\w+?)([<:>~!])(\\p{Punct}?)(.*)(\\p{Punct}?)");
            for (String s : product) {
                Matcher matcher = pattern.matcher(s);
                if (matcher.find()) {
                    builder.with(matcher.group(1), matcher.group(2), matcher.group(4), matcher.group(3), matcher.group(5));
                }
            }
            // builder.build(): trả về Specification<User> dựa trên các điều kiện tìm kiếm đã được thêm vào builder trước đó.
            Page<Product> products = productRepository.findAll(Objects.requireNonNull(builder.build()), pageable);
            return getPageResponse(pageable, products);
        }

        Page<Product> products = productRepository.findAll(pageable);
        return getPageResponse(pageable, products);

    }

    private PageResponse<List<ProductSummaryResponse>> getPageResponse(Pageable pageable, Page<Product> products) {
        List<ProductSummaryResponse> responses = products.stream()
                .map(productMapper::toProductSummaryResponse)
                .toList();

        return PageResponse.<List<ProductSummaryResponse>>builder()
                .pageNo(pageable.getPageNumber())
                .pageSize(pageable.getPageSize())
                .totalPage(products.getTotalPages())
                .items(responses)
                .build();
    }


}
