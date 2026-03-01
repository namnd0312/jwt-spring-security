package com.namnd.springjwt.service;

import com.namnd.springjwt.model.User;

public interface ActivationService {

    void createActivationToken(User user);

    void activateAccount(String token);

    void resendActivationToken(String email);
}
