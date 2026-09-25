package com.fashionstore.identity.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.identity.exception.IdentityErrorCode;
import com.fashionstore.identity.dto.user.UpdateProfileRequest;
import com.fashionstore.identity.dto.user.UserAddressRequest;
import com.fashionstore.identity.dto.user.UserAddressResponse;
import com.fashionstore.identity.dto.user.UserProfileResponse;
import com.fashionstore.identity.entity.User;
import com.fashionstore.identity.entity.UserAddress;
import com.fashionstore.identity.mapper.UserAddressMapper;
import com.fashionstore.identity.mapper.UserMapper;
import com.fashionstore.identity.repository.UserAddressRepository;
import com.fashionstore.identity.repository.UserRepository;
import com.fashionstore.identity.service.CurrentUserProvider;
import com.fashionstore.identity.service.UserAddressService;
import com.fashionstore.identity.service.UserService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserAddressServiceImpl implements UserAddressService {

    UserAddressRepository userAddressRepository;
    CurrentUserProvider currentUserProvider;
    UserAddressMapper userAddressMapper;


    @Override
    @Transactional
    public UserAddressResponse createAddress(UserAddressRequest request) {
        String userId = currentUserProvider.getCurrentUserId();
        boolean hasNoAddress = !userAddressRepository.existsByUserId(userId);

        UserAddress address = userAddressMapper.toEntity(request);
        address.setUserId(userId);
        // Nếu là địa chỉ đầu tiên hoặc request yêu cầu default -> đặt là default
        if (hasNoAddress || Boolean.TRUE.equals(request.isDefault())) {
            address.setIsDefault(true);
            if (!hasNoAddress) {
                userAddressRepository.resetDefaultAddress(userId);
            }
        } else {
            address.setIsDefault(false);
        }
        return userAddressMapper.toResponse(userAddressRepository.save(address));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserAddressResponse> getMyAddresses() {
        String userId = currentUserProvider.getCurrentUserId();
        return userAddressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId)
                .stream()
                .map(userAddressMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UserAddressResponse getAddressById(String id) {
        UserAddress address = userAddressRepository.findById(id)
                .orElseThrow(() -> new AppException(IdentityErrorCode.ADDRESS_NOT_FOUND));
        return userAddressMapper.toResponse(address);
    }

    @Override
    @Transactional
    public UserAddressResponse updateAddress(String id, UserAddressRequest request) {
        String userId = currentUserProvider.getCurrentUserId();
        UserAddress address = userAddressRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new AppException(IdentityErrorCode.ADDRESS_NOT_FOUND));

        userAddressMapper.updateEntityFromRequest(request, address);

        if (Boolean.TRUE.equals(request.isDefault())) {
            userAddressRepository.resetOtherDefaultAddresses(userId, id);
            address.setIsDefault(true);
        }
        return userAddressMapper.toResponse(userAddressRepository.save(address));
    }

    @Override
    @Transactional
    public void deleteAddress(String id) {
        String userId = currentUserProvider.getCurrentUserId();
        UserAddress address = userAddressRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new AppException(IdentityErrorCode.ADDRESS_NOT_FOUND));

        if (Boolean.TRUE.equals(address.getIsDefault())) {
            // Nếu xóa địa chỉ default nhưng user vẫn còn địa chỉ khác, chọn địa chỉ cũ nhất tiếp theo làm default
            userAddressRepository.findFirstByUserIdAndIdNotOrderByCreatedAtAsc(userId, id)
                    .ifPresent(nextDefault -> {
                        nextDefault.setIsDefault(true);
                        userAddressRepository.save(nextDefault);
                    });
        }
        userAddressRepository.delete(address);
    }

    @Override
    @Transactional
    public UserAddressResponse setDefaultAddress(String id) {
        String userId = currentUserProvider.getCurrentUserId();
        UserAddress address = userAddressRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new AppException(IdentityErrorCode.ADDRESS_NOT_FOUND));

        userAddressRepository.resetOtherDefaultAddresses(userId, id);

        address.setIsDefault(true);

        return userAddressMapper.toResponse(userAddressRepository.save(address));
    }
}
