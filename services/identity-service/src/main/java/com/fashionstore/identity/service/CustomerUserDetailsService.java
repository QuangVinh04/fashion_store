package com.fashionstore.identity.service;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.identity.config.ErrorCode;
import com.fashionstore.identity.entity.CustomUserDetails;
import com.fashionstore.identity.entity.User;
import com.fashionstore.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class CustomerUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return new CustomUserDetails(user);
    }
}
