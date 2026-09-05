package com.fashionstore.catalog.mapper;

import com.fashionstore.catalog.dto.ProductAttributeCreateRequest;
import com.fashionstore.catalog.dto.ProductAttributeOptionRequest;
import com.fashionstore.catalog.dto.ProductAttributeResponse;
import com.fashionstore.catalog.dto.ProductAttributeUpdateRequest;
import com.fashionstore.catalog.dto.ProductAttributeValueResponse;
import com.fashionstore.catalog.model.attribute.ProductAttribute;
import com.fashionstore.catalog.model.attribute.ProductAttributeValue;
import com.fashionstore.catalog.model.enumeration.AttributeType;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import java.util.Comparator;
import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductAttributeMapper {

    default ProductAttribute toAttribute(ProductAttributeCreateRequest request) {
        if (request == null) {
            return null;
        }
        return ProductAttribute.builder()
                .name(cleanText(request.getName()))
                .code(normalizeCode(request.getName()))
                .type(defaultAttributeType())
                .displayName(cleanText(request.getDisplayName()))
                .published(request.getPublished() == null || request.getPublished())
                .build();
    }

    default void updateAttribute(@MappingTarget ProductAttribute attribute, ProductAttributeUpdateRequest request) {
        if (attribute == null || request == null) {
            return;
        }
        attribute.setName(cleanText(request.getName()));
        attribute.setCode(normalizeCode(request.getName()));
        attribute.setDisplayName(cleanText(request.getDisplayName()));
        if (request.getPublished() != null) {
            attribute.setPublished(request.getPublished());
        }
    }

    default ProductAttributeResponse toResponse(ProductAttribute attribute) {
        if (attribute == null) {
            return null;
        }
        return ProductAttributeResponse.builder()
                .id(attribute.getId())
                .name(attribute.getName())
                .code(attribute.getCode())
                .type(attribute.getType())
                .displayName(attribute.getDisplayName())
                .filterable(attribute.getFilterable())
                .searchable(attribute.getSearchable())
                .displayOrder(attribute.getDisplayOrder())
                .published(attribute.getPublished())
                .build();
    }

    default ProductAttributeResponse toResponse(ProductAttribute attribute, List<ProductAttributeValue> values) {
        ProductAttributeResponse response = toResponse(attribute);
        if (response == null) {
            return null;
        }
        response.setValues(mapValues(values));
        return response;
    }

    default ProductAttributeValueResponse toValueResponse(ProductAttributeValue value) {
        if (value == null) {
            return null;
        }
        ProductAttribute attribute = value.getAttribute();
        return ProductAttributeValueResponse.builder()
                .id(value.getId())
                .attributeId(attribute == null ? null : attribute.getId())
                .code(attribute == null ? null : attribute.getCode())
                .name(attribute == null ? null : attribute.getName())
                .value(value.getValue())
                .displayName(value.getValue())
                .published(value.getPublished())
                .position(value.getPosition())
                .build();
    }

    default List<ProductAttributeValueResponse> mapValues(List<ProductAttributeValue> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .sorted(Comparator.comparing(ProductAttributeValue::getPosition, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ProductAttributeValue::getValue, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(this::toValueResponse)
                .toList();
    }

    default ProductAttributeValue toValue(ProductAttribute attribute,
                                         ProductAttributeOptionRequest request,
                                         Integer position) {
        if (request == null) {
            return null;
        }
        String value = valueFromRequest(request);
        return ProductAttributeValue.builder()
                .attribute(attribute)
                .value(value)
                .normalizedValue(normalizeCode(value))
                .published(request.getPublished() != null && request.getPublished())
                .position(position == null ? 0 : position)
                .build();
    }

    default void updateValue(@MappingTarget ProductAttributeValue value,
                             ProductAttributeOptionRequest request) {
        if (value == null || request == null) {
            return;
        }
        String rawValue = valueFromRequest(request);
        value.setValue(rawValue);
        value.setNormalizedValue(normalizeCode(rawValue));
        if (request.getPublished() != null) {
            value.setPublished(request.getPublished());
        }
    }

    default AttributeType defaultAttributeType() {
        return AttributeType.MULTI_SELECT;
    }

    default String valueFromRequest(ProductAttributeOptionRequest request) {
        if (request == null) {
            return null;
        }
        String value = cleanText(request.getValue());
        if (value != null) {
            return value;
        }
        return cleanText(request.getDisplayName());
    }

    default String cleanText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    default String normalizeCode(String value) {
        String cleaned = cleanText(value);
        if (cleaned == null) {
            return null;
        }
        return cleaned.replaceAll("\\s+", "_").toUpperCase();
    }

}
