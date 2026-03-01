package com.namnd.springjwt.service;

public interface EmailService {

    void sendPasswordResetEmail(String to, String token);

    void sendActivationEmail(String to, String token);
}
