package com.namnd.springjwt.service;

public interface PasswordResetService {

    void createPasswordResetToken(String email);

    boolean validatePasswordResetToken(String token);

    void resetPassword(String token, String newPassword);
}
