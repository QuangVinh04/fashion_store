package com.fashionstore.identity.service;

import com.fashionstore.identity.dto.user.UserAddressRequest;
import com.fashionstore.identity.dto.user.UserAddressResponse;

import java.util.List;

public interface UserAddressService {

    UserAddressResponse createAddress(UserAddressRequest request);
    List<UserAddressResponse> getMyAddresses();
    UserAddressResponse getAddressById(String id);
    UserAddressResponse updateAddress(String id, UserAddressRequest request);
    void deleteAddress(String id);
    UserAddressResponse setDefaultAddress(String id);
}
