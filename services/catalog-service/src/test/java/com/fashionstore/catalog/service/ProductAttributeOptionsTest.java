package com.fashionstore.catalog.service;

import com.fashionstore.catalog.dto.ProductAttributeOptionRequest;
import com.fashionstore.catalog.entity.attribute.ProductAttribute;
import com.fashionstore.catalog.entity.attribute.ProductAttributeOption;
import com.fashionstore.catalog.mapper.ProductAttributeMapper;
import com.fashionstore.catalog.mapper.ProductAttributeOptionMapper;
import com.fashionstore.catalog.repository.ProductAttributeOptionRepository;
import com.fashionstore.catalog.repository.ProductAttributeRepository;
import com.fashionstore.catalog.repository.ProductAttributeValueRepository;
import com.fashionstore.catalog.service.impl.ProductAttributeServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductAttributeOptionsTest {
    @Mock ProductAttributeRepository attributes;
    @Mock ProductAttributeValueRepository values;
    @Mock ProductAttributeOptionRepository options;
    @Mock ProductAttributeMapper attributeMapper;
    @Mock ProductAttributeOptionMapper optionMapper;
    @InjectMocks ProductAttributeServiceImpl service;

    @Test void listsOptionsRatherThanAssignedProductValues() {
        when(attributes.findById("a1")).thenReturn(Optional.of(new ProductAttribute()));
        when(options.findByAttributeIdOrderByValueAsc("a1")).thenReturn(List.of(new ProductAttributeOption()));
        assertThat(service.getAttributeOptions("a1")).hasSize(1);
        verifyNoInteractions(values);
    }

    @Test void canToggleAnExistingOptionWithoutTreatingItAsDuplicate() {
        ProductAttributeOption option = ProductAttributeOption.builder().value("Cotton").normalizedValue("COTTON").published(true).build();
        when(attributes.findById("a1")).thenReturn(Optional.of(new ProductAttribute()));
        when(options.findByIdAndAttributeId("o1", "a1")).thenReturn(Optional.of(option));
        service.updateAttributeOption("a1", "o1", ProductAttributeOptionRequest.builder().name("Cotton").published(false).build());
        assertThat(option.getPublished()).isFalse();
        assertThat(option.getNormalizedValue()).isEqualTo("COTTON");
        verify(options).existsByAttributeIdAndNormalizedValueAndIdNot("a1", "COTTON", "o1");
        verify(options).save(option);
    }

    @Test void renameUpdatesTheUniquenessKey() {
        ProductAttributeOption option = ProductAttributeOption.builder().value("Cotton").normalizedValue("COTTON").published(true).build();
        when(attributes.findById("a1")).thenReturn(Optional.of(new ProductAttribute()));
        when(options.findByIdAndAttributeId("o1", "a1")).thenReturn(Optional.of(option));
        service.updateAttributeOption("a1", "o1", ProductAttributeOptionRequest.builder().name("Linen").published(true).build());
        assertThat(option.getValue()).isEqualTo("Linen");
        assertThat(option.getNormalizedValue()).isEqualTo("LINEN");
    }

    @Test void rejectsChangingTypeWhenProductsAlreadyUseTheAttribute() {
        var attribute = ProductAttribute.builder().type(com.fashionstore.catalog.entity.enumeration.AttributeType.TEXT).build();
        when(attributes.findById("a1")).thenReturn(Optional.of(attribute));
        when(values.existsByAttributeId("a1")).thenReturn(true);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.updateAttribute("a1",
                com.fashionstore.catalog.dto.ProductAttributeUpdateRequest.builder().name("Material")
                .type(com.fashionstore.catalog.entity.enumeration.AttributeType.NUMBER).build()))
                .isInstanceOf(com.fashionstore.common.exception.AppException.class);
        verify(attributes, never()).save(any());
    }

    @Test void cannotDeleteAnOptionReferencedByValueEvenWithoutOptionForeignKey() {
        var option = ProductAttributeOption.builder().value("Cotton").normalizedValue("COTTON").build();
        option.setId("o1");
        when(options.findByIdAndAttributeId("o1", "a1")).thenReturn(Optional.of(option));
        when(values.existsByAttributeIdAndNormalizedValue("a1", "COTTON")).thenReturn(true);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.deleteAttributeOption("a1", "o1"))
                .isInstanceOf(com.fashionstore.common.exception.AppException.class);
        verify(options, never()).delete(any());
    }
}
