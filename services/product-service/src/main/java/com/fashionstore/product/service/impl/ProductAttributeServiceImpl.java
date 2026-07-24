package com.fashionstore.product.service.impl;

import com.fashionstore.common.dto.PageResponse;
import com.fashionstore.common.exception.AppException;
import com.fashionstore.product.config.ProductErrorCode;
import com.fashionstore.product.dto.*;
import com.fashionstore.product.mapper.ProductAttributeMapper;
import com.fashionstore.product.mapper.ProductAttributeOptionMapper;
import com.fashionstore.product.model.attribute.ProductAttribute;
import com.fashionstore.product.model.attribute.ProductAttributeOption;
import com.fashionstore.product.model.attribute.ProductAttributeValue;
import com.fashionstore.product.repository.ProductAttributeOptionRepository;
import com.fashionstore.product.repository.ProductAttributeRepository;
import com.fashionstore.product.repository.ProductAttributeValueRepository;
import com.fashionstore.product.service.ProductAtrributeService;
import com.fashionstore.product.util.StringUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProductAttributeServiceImpl implements ProductAtrributeService {

    static final Set<String> RESERVED_CODES = Set.of("COLOR", "SIZE");

    ProductAttributeRepository productAttributeRepository;
    ProductAttributeValueRepository productAttributeValueRepository;
    ProductAttributeOptionRepository productAttributeOptionRepository;
    ProductAttributeMapper productAttributeMapper;
    ProductAttributeOptionMapper productAttributeOptionMapper;

    @Override
    @Transactional
    public ProductAttributeResponse createAttribute(ProductAttributeCreateRequest request) {
        String code = StringUtils.normalizeCode(request.getName());
        validateCodeAvailable(code, null);

        ProductAttribute attribute = productAttributeMapper.toAttribute(request);
        productAttributeRepository.save(attribute);

        Set<String> normalizedValues = new HashSet<>();

        List<ProductAttributeOption> options = request.getVariants().stream()
                .map(StringUtils::cleanText)
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .map(value -> {
                    String normalizedValue = StringUtils.normalizeCode(value);

                    if (!normalizedValues.add(normalizedValue)) {
                        return null;
                    }

                    return ProductAttributeOption.builder()
                            .attribute(attribute)
                            .value(value)
                            .normalizedValue(normalizedValue)
                            .published(true)
                            .build();
                })
                .filter(Objects::nonNull)
                .toList();
        productAttributeOptionRepository.saveAll(options);

        return productAttributeMapper.toResponse(attribute);
    }


    @Override
    @Transactional
    public ProductAttributeResponse updateAttribute(String id, ProductAttributeUpdateRequest request) {
        ProductAttribute attribute = getAttribute(id);
        String code = StringUtils.normalizeCode(request.getName());
        validateCodeAvailable(code, id);

        productAttributeMapper.updateAttribute(attribute, request);
        attribute = productAttributeRepository.save(attribute);

        return productAttributeMapper.toResponse(attribute);
    }

    @Override
    @Transactional
    public void deleteAttribute(String attributeId) {
        ProductAttribute attribute = productAttributeRepository.findById(attributeId)
                .orElseThrow(() ->
                        new AppException(ProductErrorCode.PRODUCT_ATTRIBUTE_NOT_FOUND)
                );

        if (productAttributeValueRepository.existsByAttributeId(attributeId)) {
            throw new AppException(
                    ProductErrorCode.PRODUCT_ATTRIBUTE_IN_USE
            );
        }
        productAttributeOptionRepository.deleteAllByAttributeId(attributeId);
        productAttributeRepository.delete(attribute);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductAttributeResponse getAttributeById(String id) {
        ProductAttribute attribute = getAttribute(id);
        return productAttributeMapper.toResponse(attribute, getValues(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<List<ProductAttributeResponse>> getAllAttributes(Pageable pageable, String keyword) {
        Page<ProductAttribute> attributePage;
        String cleanedKeyword = StringUtils.cleanText(keyword);
        if (cleanedKeyword == null) {
            attributePage = productAttributeRepository.findAll(pageable);
        } else {
            attributePage = productAttributeRepository
                    .findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(cleanedKeyword, cleanedKeyword, pageable);
        }

        List<ProductAttributeResponse> responses = attributePage.stream()
                .map(productAttributeMapper::toResponse)
                .toList();

        return PageResponse.<List<ProductAttributeResponse>>builder()
                .pageNo(pageable.getPageNumber())
                .pageSize(pageable.getPageSize())
                .totalPage(attributePage.getTotalPages())
                .items(responses)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductAttributeResponse> getAllPublishedAttributes() {
        return productAttributeRepository.findByPublishedTrueOrderByNameAsc().stream()
                .map(attribute -> productAttributeMapper.toResponse(attribute, getValues(attribute.getId())))
                .toList();
    }

    @Override
    @Transactional
    public ProductAttributeOptionResponse addAttributeOption(String attributeId, ProductAttributeOptionRequest request) {
        ProductAttribute attribute = getAttribute(attributeId);
        String normalizedValue = StringUtils.normalizeCode(request.getName());
        if (productAttributeOptionRepository.existsByAttributeIdAndNormalizedValue(attributeId, normalizedValue)) {
            throw new AppException(ProductErrorCode.PRODUCT_ATTRIBUTE_ALREADY_EXIST);
        }

        ProductAttributeOption option = productAttributeOptionMapper.toOption(request);
        option.setAttribute(attribute);

        return productAttributeOptionMapper.toOptionResponse(productAttributeOptionRepository.save(option));
    }

    @Override
    @Transactional
    public ProductAttributeOptionResponse updateAttributeOption(String attributeId,
                                                             String optionId,
                                                             ProductAttributeOptionRequest request) {
        getAttribute(attributeId);
        ProductAttributeOption option = productAttributeOptionRepository.findByIdAndAttributeId(optionId, attributeId)
                .orElseThrow(() -> new AppException(ProductErrorCode.PRODUCT_ATTRIBUTE_NOT_FOUND));

        String normalizedValue = StringUtils.normalizeCode(request.getName());
        if (productAttributeOptionRepository.existsByAttributeIdAndNormalizedValue(attributeId, normalizedValue)) {
            throw new AppException(ProductErrorCode.PRODUCT_ATTRIBUTE_ALREADY_EXIST);
        }

        option.setPublished(request.getPublished());
        option.setValue(request.getName());
        return productAttributeOptionMapper.toOptionResponse(productAttributeOptionRepository.save(option));
    }

    @Override
    @Transactional
    public void deleteAttributeOption(
            String attributeId,
            String optionId
    ) {
        ProductAttributeOption option = productAttributeOptionRepository
                .findByIdAndAttributeId(optionId, attributeId)
                .orElseThrow(() ->
                        new AppException(
                                ProductErrorCode.PRODUCT_ATTRIBUTE_OPTION_NOT_FOUND
                        )
                );

        if (productAttributeValueRepository
                .existsByAttributeOptionId(optionId)) {
            throw new AppException(
                    ProductErrorCode.PRODUCT_ATTRIBUTE_OPTION_IN_USE
            );
        }
        productAttributeOptionRepository.delete(option);
    }



    private ProductAttribute getAttribute(String id) {
        return productAttributeRepository.findById(id)
                .orElseThrow(() -> new AppException(ProductErrorCode.PRODUCT_ATTRIBUTE_NOT_FOUND));
    }

    private List<ProductAttributeValue> getValues(String attributeId) {
        return productAttributeValueRepository.findByAttributeIdOrderByPositionAscValueAsc(attributeId);
    }

    private void validateCodeAvailable(String code, String currentId) {
        if (RESERVED_CODES.contains(code)) {
            throw new AppException(ProductErrorCode.ATTRIBUTE_CODE_RESERVED,
                    "Color and Size are managed in the Variant tab, not in Attributes");
        }
        boolean duplicated = currentId == null
                ? productAttributeRepository.existsByCode(code)
                : productAttributeRepository.existsByCodeAndIdNot(code, currentId);
        if (duplicated) {
            throw new AppException(ProductErrorCode.PRODUCT_ATTRIBUTE_ALREADY_EXIST);
        }
    }

}

