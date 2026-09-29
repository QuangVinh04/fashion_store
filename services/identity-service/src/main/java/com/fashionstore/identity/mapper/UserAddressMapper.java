package com.fashionstore.identity.mapper;


import com.fashionstore.identity.dto.user.UserAddressRequest;
import com.fashionstore.identity.dto.user.UserAddressResponse;
import com.fashionstore.identity.entity.UserAddress;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserAddressMapper {
    @Mapping(target = "fullAddress", expression = "java(buildFullAddress(entity))")
    UserAddressResponse toResponse(UserAddress entity);

    @Mapping(target = "userId", ignore = true)
    UserAddress toEntity(UserAddressRequest request);


    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, unmappedTargetPolicy = ReportingPolicy.IGNORE)
    void updateEntityFromRequest(UserAddressRequest request, @MappingTarget UserAddress entity);


    default String buildFullAddress(UserAddress entity) {
        if (entity == null) return null;
        return java.util.stream.Stream.of(entity.getDetailAddress(), entity.getWard(), entity.getDistrict(), entity.getProvince())
                .filter(part -> part != null && !part.isBlank())
                .collect(java.util.stream.Collectors.joining(", "));
    }
}
