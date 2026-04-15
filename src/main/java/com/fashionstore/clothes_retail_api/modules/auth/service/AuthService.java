package com.fashionstore.clothes_retail_api.modules.auth.service;

import com.fashionstore.clothes_retail_api.modules.auth.dto.AuthResponse;
import com.fashionstore.clothes_retail_api.modules.auth.dto.LoginResquest;
import com.fashionstore.clothes_retail_api.modules.auth.dto.RegisterRequest;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginResquest request);


}
