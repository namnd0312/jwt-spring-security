package com.namnd.springjwt.service.impl;

import com.namnd.springjwt.model.ActivationToken;
import com.namnd.springjwt.model.User;
import com.namnd.springjwt.repository.ActivationTokenRepository;
import com.namnd.springjwt.repository.UserRepository;
import com.namnd.springjwt.service.ActivationService;
import com.namnd.springjwt.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class ActivationServiceImpl implements ActivationService {

    private static final Logger logger = LoggerFactory.getLogger(ActivationServiceImpl.class);
    // 24 hours in milliseconds
    private static final long TOKEN_EXPIRY_MS = 24 * 60 * 60 * 1000;

    @Autowired
    private ActivationTokenRepository activationTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    @Override
    @Transactional
    public void createActivationToken(User user) {
        // Delete any previous unused activation tokens for this user
        activationTokenRepository.deleteByUserAndUsedFalse(user);

        String tokenValue = UUID.randomUUID().toString();

        ActivationToken activationToken = new ActivationToken();
        activationToken.setToken(tokenValue);
        activationToken.setUser(user);
        activationToken.setExpiryDate(new Date(System.currentTimeMillis() + TOKEN_EXPIRY_MS));
        activationToken.setUsed(false);

        activationTokenRepository.save(activationToken);
        emailService.sendActivationEmail(user.getEmail(), tokenValue);
        logger.info("Activation token created for user: {}", user.getEmail());
    }

    @Override
    @Transactional
    public void activateAccount(String token) {
        // Generic error message to prevent token state enumeration
        ActivationToken activationToken = activationTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid or expired activation token."));

        if (activationToken.isUsed() || activationToken.getExpiryDate().before(new Date())) {
            throw new RuntimeException("Invalid or expired activation token.");
        }

        User user = activationToken.getUser();
        user.setActive(true);
        userRepository.save(user);

        activationToken.setUsed(true);
        activationTokenRepository.save(activationToken);
        logger.info("Account activated for user: {}", user.getEmail());
    }

    @Override
    @Transactional
    public void resendActivationToken(String email) {
        // Silent no-op if user not found or already active (prevent email enumeration)
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (!userOptional.isPresent() || userOptional.get().isActive()) {
            return;
        }

        createActivationToken(userOptional.get());
    }
}
