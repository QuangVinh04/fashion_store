package com.fashionstore.identity.mapper;


import com.fashionstore.identity.dto.user.UserAddressRequest;
import com.fashionstore.identity.dto.user.UserAddressResponse;
import com.fashionstore.identity.entity.UserAddress;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserAddressMapper {
    @Mapping(target = "fullAddress", expression = "java(buildFullAddress(entity))")
    UserAddressResponse toResponse(UserAddress entity);

    @Mapping(target = "userId", ignore = true)
    UserAddress toEntity(UserAddressRequest request);


    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, unmappedTargetPolicy = ReportingPolicy.IGNORE)
    // Default changes are handled by the service; false must preserve the current default.
    @Mapping(target = "isDefault", ignore = true)
    void updateEntityFromRequest(UserAddressRequest request, @MappingTarget UserAddress entity);


    default String buildFullAddress(UserAddress entity) {
        if (entity == null) return null;
        return Stream.of(entity.getDetailAddress(), entity.getWard(), entity.getDistrict(), entity.getProvince())
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(", "));
    }
}
