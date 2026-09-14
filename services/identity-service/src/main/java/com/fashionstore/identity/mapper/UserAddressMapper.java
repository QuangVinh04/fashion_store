package com.fashionstore.identity.mapper;


import com.fashionstore.identity.dto.user.UserAddressRequest;
import com.fashionstore.identity.dto.user.UserAddressResponse;
import com.fashionstore.identity.entity.UserAddress;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface UserAddressMapper {
    @Mapping(target = "fullAddress", expression = "java(buildFullAddress(entity))")
    UserAddressResponse toResponse(UserAddress entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    UserAddress toEntity(UserAddressRequest request);


    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, unmappedTargetPolicy = ReportingPolicy.IGNORE)
    void updateEntityFromRequest(UserAddressRequest request, @MappingTarget UserAddress entity);


    default String buildFullAddress(UserAddress entity) {
        if (entity == null) return null;
        return String.format("%s, %s, %s, %s",
                entity.getDetailAddress(),
                entity.getWard(),
                entity.getDistrict(),
                entity.getProvince());
    }
}
