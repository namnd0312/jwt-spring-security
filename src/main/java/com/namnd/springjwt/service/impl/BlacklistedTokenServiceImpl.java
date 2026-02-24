package com.namnd.springjwt.service.impl;

import com.namnd.springjwt.model.BlacklistedToken;
import com.namnd.springjwt.model.TokenType;
import com.namnd.springjwt.repository.BlacklistedTokenRepository;
import com.namnd.springjwt.service.BlacklistedTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
public class BlacklistedTokenServiceImpl implements BlacklistedTokenService {

    private static final Logger logger = LoggerFactory.getLogger(BlacklistedTokenServiceImpl.class);

    @Autowired
    private BlacklistedTokenRepository blacklistedTokenRepository;

    @Override
    @Transactional
    public void blacklistToken(String jti, Date expiryDate) {
        if (blacklistedTokenRepository.existsByJti(jti)) {
            return;
        }

        BlacklistedToken blacklistedToken = new BlacklistedToken();
        blacklistedToken.setJti(jti);
        blacklistedToken.setTokenType(TokenType.ACCESS);
        blacklistedToken.setExpiryDate(expiryDate);
        blacklistedTokenRepository.save(blacklistedToken);
        logger.debug("Token blacklisted (JTI), expires at: {}", expiryDate);
    }

    @Override
    public boolean isTokenBlacklisted(String jti) {
        return blacklistedTokenRepository.existsByJti(jti);
    }

    @Override
    @Transactional
    @Scheduled(fixedRate = 3600000) // Run every hour
    public void cleanupExpiredTokens() {
        blacklistedTokenRepository.deleteByExpiryDateBefore(new Date());
        logger.info("Expired blacklisted tokens cleaned up");
    }
}
