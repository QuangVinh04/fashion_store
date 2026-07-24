package com.fashionstore.identity.service;


public interface EmailService {
    void sendVerificationEmail(String to, String fullName, String token);

}
