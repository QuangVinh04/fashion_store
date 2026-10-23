package com.fashionstore.catalog.product.service;

import com.fashionstore.catalog.product.dto.ProductAttributeCreateRequest;
import com.fashionstore.catalog.product.dto.ProductAttributeUpdateRequest;
import com.fashionstore.catalog.product.entity.AttributeType;
import com.fashionstore.catalog.product.mapper.ProductAttributeMapper;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import static org.assertj.core.api.Assertions.assertThat;

class AttributeMetadataMappingTest {
    private final ProductAttributeMapper mapper = Mappers.getMapper(ProductAttributeMapper.class);

    @Test void configurableTypeAndFlagsSurviveCreationAndEditing() {
        var attribute = mapper.toAttribute(ProductAttributeCreateRequest.builder().name("Material")
                .type(AttributeType.SINGLE_SELECT).filterable(true).searchable(true).displayOrder(5).build());
        assertThat(attribute.getType()).isEqualTo(AttributeType.SINGLE_SELECT);
        assertThat(attribute.getFilterable()).isTrue();
        assertThat(attribute.getDisplayOrder()).isEqualTo(5);
        mapper.updateAttribute(attribute, ProductAttributeUpdateRequest.builder().name("Material")
                .filterable(false).searchable(false).displayOrder(2).build());
        assertThat(attribute.getType()).isEqualTo(AttributeType.SINGLE_SELECT);
        assertThat(attribute.getFilterable()).isFalse();
        assertThat(attribute.getSearchable()).isFalse();
        assertThat(attribute.getDisplayOrder()).isEqualTo(2);
    }

    @Test void omittedFieldsRetainLegacyDefaults() {
        var attribute = mapper.toAttribute(ProductAttributeCreateRequest.builder().name("Material").build());
        assertThat(attribute.getType()).isEqualTo(AttributeType.MULTI_SELECT);
        assertThat(attribute.getFilterable()).isFalse();
        assertThat(attribute.getDisplayOrder()).isZero();
    }
}
