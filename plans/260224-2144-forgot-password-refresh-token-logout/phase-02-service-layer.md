# Phase 2: Service Layer

## Context Links

- [Plan Overview](./plan.md)
- [Phase 1 - Database & Entity Layer](./phase-01-database-entity-layer.md)
- [JwtService.java](../src/main/java/com/namnd/springjwt/service/JwtService.java)
- [UserService.java](../src/main/java/com/namnd/springjwt/service/UserService.java)
- [UserServiceImpl.java](../src/main/java/com/namnd/springjwt/service/impl/UserServiceImpl.java)
- [Code Standards](../docs/code-standards.md)

## Overview

- **Date:** 2026-02-24
- **Priority:** P1
- **Status:** pending
- **Effort:** 2h
- **Description:** Create 4 new service interfaces with implementations (EmailService, PasswordResetService, RefreshTokenService, BlacklistedTokenService). Modify JwtService to add `generateTokenFromUsername`. Modify UserService/UserServiceImpl to add `findByEmail`.

## Key Insights

- Existing pattern: interface in `service/` + impl in `service/impl/` with `@Autowired` field injection
- JwtService is `@Component` (not interface+impl), keep it that way -- just add a method
- `RefreshTokenRepository.deleteByUser()` is a derived delete query; must be called within `@Transactional`
- JJWT 0.9.0 API: `Jwts.builder().setSubject()...signWith(SignatureAlgorithm.HS512, SECRET_KEY)`
- Spring Mail: use `JavaMailSender` (auto-configured by spring-boot-starter-mail)
- `UUID.randomUUID().toString()` provides 122 bits of entropy for tokens

## Requirements

### Functional
- **EmailService**: send password reset emails with a link containing the reset token
- **PasswordResetService**: create token, validate token (exists + not expired + not used), reset password (validate + update + mark used)
- **RefreshTokenService**: create refresh token for user, verify expiration, delete by user (for logout/rotation)
- **BlacklistedTokenService**: add token to blacklist, check if blacklisted, scheduled cleanup of expired entries
- **JwtService**: new method `generateTokenFromUsername(String username)` for refresh flow (no Authentication object needed)
- **UserService**: new method `findByEmail(String email)` for forgot-password lookup

### Non-Functional
- Each file under 200 lines
- `@Transactional` on methods that perform writes or deletes
- Log important events (token creation, validation failures, email sent) at appropriate levels
- Never log full tokens or passwords

## Architecture

```
service/
├── JwtService.java                  (MODIFY - add generateTokenFromUsername)
├── UserService.java                 (MODIFY - add findByEmail)
├── EmailService.java                (NEW - interface)
├── PasswordResetService.java        (NEW - interface)
├── RefreshTokenService.java         (NEW - interface)
├── BlacklistedTokenService.java     (NEW - interface)
└── impl/
    ├── UserServiceImpl.java         (MODIFY - add findByEmail impl)
    ├── EmailServiceImpl.java        (NEW)
    ├── PasswordResetServiceImpl.java (NEW)
    ├── RefreshTokenServiceImpl.java  (NEW)
    └── BlacklistedTokenServiceImpl.java (NEW)
```

### Service Dependencies

```
PasswordResetServiceImpl
├── PasswordResetTokenRepository
├── UserRepository
├── EmailService
└── PasswordEncoder

RefreshTokenServiceImpl
├── RefreshTokenRepository
└── UserRepository

BlacklistedTokenServiceImpl
└── BlacklistedTokenRepository

EmailServiceImpl
└── JavaMailSender (Spring auto-configured)
```

## Related Code Files

### Files to Modify
- `src/main/java/com/namnd/springjwt/service/JwtService.java` - add generateTokenFromUsername
- `src/main/java/com/namnd/springjwt/service/UserService.java` - add findByEmail signature
- `src/main/java/com/namnd/springjwt/service/impl/UserServiceImpl.java` - add findByEmail impl

### Files to Create
- `src/main/java/com/namnd/springjwt/service/EmailService.java`
- `src/main/java/com/namnd/springjwt/service/PasswordResetService.java`
- `src/main/java/com/namnd/springjwt/service/RefreshTokenService.java`
- `src/main/java/com/namnd/springjwt/service/BlacklistedTokenService.java`
- `src/main/java/com/namnd/springjwt/service/impl/EmailServiceImpl.java`
- `src/main/java/com/namnd/springjwt/service/impl/PasswordResetServiceImpl.java`
- `src/main/java/com/namnd/springjwt/service/impl/RefreshTokenServiceImpl.java`
- `src/main/java/com/namnd/springjwt/service/impl/BlacklistedTokenServiceImpl.java`

## Implementation Steps

### Step 1: Modify JwtService.java - add generateTokenFromUsername

Add a new method below the existing `generateTokenLogin` method. This method builds a JWT from a username string directly (used by the refresh token flow where we don't have an Authentication object):

```java
public String generateTokenFromUsername(String username) {
    return Jwts.builder()
            .setSubject(username)
            .setIssuedAt(new Date())
            .setExpiration(new Date(new Date().getTime() + EXPIRE_TIME * 1000))
            .signWith(SignatureAlgorithm.HS512, SECRET_KEY)
            .compact();
}
```

Also add a getter for EXPIRE_TIME so the controller can compute token expiry for blacklisting:

```java
public long getExpireTime() {
    return EXPIRE_TIME;
}
```

File will be ~75 lines after changes.

### Step 2: Modify UserService.java - add findByEmail

Add to the interface:

```java
Optional<User> findByEmail(String email);
```

### Step 3: Modify UserServiceImpl.java - implement findByEmail

Add the implementation:

```java
@Override
public Optional<User> findByEmail(String email) {
    return userRepository.findByEmail(email);
}
```

### Step 4: Create EmailService.java interface

Create `src/main/java/com/namnd/springjwt/service/EmailService.java`:

```java
package com.namnd.springjwt.service;

public interface EmailService {

    void sendPasswordResetEmail(String to, String token);
}
```

### Step 5: Create EmailServiceImpl.java

Create `src/main/java/com/namnd/springjwt/service/impl/EmailServiceImpl.java`:

```java
package com.namnd.springjwt.service.impl;

import com.namnd.springjwt.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailServiceImpl.class);

    @Autowired
    private JavaMailSender mailSender;

    @Value("${namnd.app.passwordResetBaseUrl}")
    private String passwordResetBaseUrl;

    @Override
    public void sendPasswordResetEmail(String to, String token) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject("Password Reset Request");
            message.setText(
                "You have requested to reset your password.\n\n"
                + "Click the link below to reset your password:\n"
                + passwordResetBaseUrl + "?token=" + token + "\n\n"
                + "This link will expire in 30 minutes.\n"
                + "If you did not request this, please ignore this email."
            );

            mailSender.send(message);
            logger.info("Password reset email sent to: {}", maskEmail(to));
        } catch (Exception e) {
            logger.error("Failed to send password reset email: {}", e.getMessage());
        }
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 2) {
            return "***" + email.substring(atIndex);
        }
        return email.substring(0, 2) + "***" + email.substring(atIndex);
    }
}
```

**Note:** `sendPasswordResetEmail` catches exceptions silently. The forgot-password endpoint always returns 200 regardless, so email failure should not leak info to the caller.

### Step 6: Create PasswordResetService.java interface

Create `src/main/java/com/namnd/springjwt/service/PasswordResetService.java`:

```java
package com.namnd.springjwt.service;

public interface PasswordResetService {

    void createPasswordResetToken(String email);

    boolean validatePasswordResetToken(String token);

    void resetPassword(String token, String newPassword);
}
```

### Step 7: Create PasswordResetServiceImpl.java

Create `src/main/java/com/namnd/springjwt/service/impl/PasswordResetServiceImpl.java`:

```java
package com.namnd.springjwt.service.impl;

import com.namnd.springjwt.model.PasswordResetToken;
import com.namnd.springjwt.model.User;
import com.namnd.springjwt.repository.PasswordResetTokenRepository;
import com.namnd.springjwt.repository.UserRepository;
import com.namnd.springjwt.service.EmailService;
import com.namnd.springjwt.service.PasswordResetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final Logger logger = LoggerFactory.getLogger(PasswordResetServiceImpl.class);
    private static final long TOKEN_EXPIRY_MS = 30 * 60 * 1000; // 30 minutes

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void createPasswordResetToken(String email) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (!userOptional.isPresent()) {
            // Don't reveal whether email exists
            logger.debug("Password reset requested for non-existent email");
            return;
        }

        User user = userOptional.get();
        String tokenValue = UUID.randomUUID().toString();

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken(tokenValue);
        resetToken.setUser(user);
        resetToken.setExpiryDate(new Date(System.currentTimeMillis() + TOKEN_EXPIRY_MS));
        resetToken.setUsed(false);

        passwordResetTokenRepository.save(resetToken);
        emailService.sendPasswordResetEmail(email, tokenValue);
        logger.info("Password reset token created for user: {}", user.getUsername());
    }

    @Override
    public boolean validatePasswordResetToken(String token) {
        Optional<PasswordResetToken> tokenOptional = passwordResetTokenRepository.findByToken(token);
        if (!tokenOptional.isPresent()) {
            return false;
        }

        PasswordResetToken resetToken = tokenOptional.get();
        return !resetToken.isUsed() && resetToken.getExpiryDate().after(new Date());
    }

    @Override
    @Transactional
    public void resetPassword(String token, String newPassword) {
        Optional<PasswordResetToken> tokenOptional = passwordResetTokenRepository.findByToken(token);
        if (!tokenOptional.isPresent()) {
            throw new RuntimeException("Invalid password reset token");
        }

        PasswordResetToken resetToken = tokenOptional.get();
        if (resetToken.isUsed()) {
            throw new RuntimeException("Password reset token already used");
        }
        if (resetToken.getExpiryDate().before(new Date())) {
            throw new RuntimeException("Password reset token expired");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);
        logger.info("Password reset successful for user: {}", user.getUsername());
    }
}
```

### Step 8: Create RefreshTokenService.java interface

Create `src/main/java/com/namnd/springjwt/service/RefreshTokenService.java`:

```java
package com.namnd.springjwt.service;

import com.namnd.springjwt.model.RefreshToken;

public interface RefreshTokenService {

    RefreshToken createRefreshToken(Long userId);

    RefreshToken verifyExpiration(RefreshToken token);

    void deleteByUserId(Long userId);
}
```

### Step 9: Create RefreshTokenServiceImpl.java

Create `src/main/java/com/namnd/springjwt/service/impl/RefreshTokenServiceImpl.java`:

```java
package com.namnd.springjwt.service.impl;

import com.namnd.springjwt.model.RefreshToken;
import com.namnd.springjwt.model.User;
import com.namnd.springjwt.repository.RefreshTokenRepository;
import com.namnd.springjwt.repository.UserRepository;
import com.namnd.springjwt.service.RefreshTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final Logger logger = LoggerFactory.getLogger(RefreshTokenServiceImpl.class);

    @Value("${namnd.app.jwtRefreshExpiration}")
    private long refreshTokenExpiration;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Override
    @Transactional
    public RefreshToken createRefreshToken(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        // Delete existing refresh token for this user (rotation)
        Optional<RefreshToken> existing = refreshTokenRepository.findByUser(user);
        existing.ifPresent(token -> refreshTokenRepository.delete(token));

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiryDate(new Date(System.currentTimeMillis() + refreshTokenExpiration));

        refreshToken = refreshTokenRepository.save(refreshToken);
        logger.debug("Refresh token created for user: {}", user.getUsername());
        return refreshToken;
    }

    @Override
    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().before(new Date())) {
            refreshTokenRepository.delete(token);
            throw new RuntimeException("Refresh token expired. Please login again.");
        }
        return token;
    }

    @Override
    @Transactional
    public void deleteByUserId(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
        refreshTokenRepository.deleteByUser(user);
        logger.debug("Refresh token deleted for user: {}", user.getUsername());
    }
}
```

### Step 10: Create BlacklistedTokenService.java interface

Create `src/main/java/com/namnd/springjwt/service/BlacklistedTokenService.java`:

```java
package com.namnd.springjwt.service;

import java.util.Date;

public interface BlacklistedTokenService {

    void blacklistToken(String token, Date expiryDate);

    boolean isTokenBlacklisted(String token);

    void cleanupExpiredTokens();
}
```

### Step 11: Create BlacklistedTokenServiceImpl.java

Create `src/main/java/com/namnd/springjwt/service/impl/BlacklistedTokenServiceImpl.java`:

```java
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
    public void blacklistToken(String token, Date expiryDate) {
        if (blacklistedTokenRepository.existsByToken(token)) {
            return;
        }

        BlacklistedToken blacklistedToken = new BlacklistedToken();
        blacklistedToken.setToken(token);
        blacklistedToken.setTokenType(TokenType.ACCESS);
        blacklistedToken.setExpiryDate(expiryDate);
        blacklistedTokenRepository.save(blacklistedToken);
        logger.debug("Token blacklisted, expires at: {}", expiryDate);
    }

    @Override
    public boolean isTokenBlacklisted(String token) {
        return blacklistedTokenRepository.existsByToken(token);
    }

    @Override
    @Transactional
    @Scheduled(fixedRate = 3600000) // Run every hour
    public void cleanupExpiredTokens() {
        blacklistedTokenRepository.deleteByExpiryDateBefore(new Date());
        logger.info("Expired blacklisted tokens cleaned up");
    }
}
```

### Step 12: Run compile check

```bash
mvn clean compile
```

Fix any compilation errors before proceeding to Phase 3.

## Todo List

- [ ] Add generateTokenFromUsername and getExpireTime to JwtService.java
- [ ] Add findByEmail to UserService.java interface
- [ ] Add findByEmail implementation to UserServiceImpl.java
- [ ] Create EmailService.java interface
- [ ] Create EmailServiceImpl.java
- [ ] Create PasswordResetService.java interface
- [ ] Create PasswordResetServiceImpl.java
- [ ] Create RefreshTokenService.java interface
- [ ] Create RefreshTokenServiceImpl.java
- [ ] Create BlacklistedTokenService.java interface
- [ ] Create BlacklistedTokenServiceImpl.java
- [ ] Run mvn clean compile and fix errors

## Success Criteria

- `mvn clean compile` passes with zero errors
- All 4 service interfaces exist with correct method signatures
- All 4 service implementations exist with proper `@Service`, `@Transactional` annotations
- JwtService has `generateTokenFromUsername` method
- UserService/Impl have `findByEmail` method
- EmailServiceImpl uses `JavaMailSender` for sending emails
- BlacklistedTokenServiceImpl has `@Scheduled` cleanup method
- PasswordResetServiceImpl silently returns when email not found (no info leakage)

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| JavaMailSender not auto-configured if mail props missing | High - app fails to start | Use `@Value` with defaults; document required env vars |
| `deleteByUser` without `@Transactional` throws exception | High - runtime error | All delete operations wrapped in `@Transactional` |
| RuntimeException for invalid tokens is generic | Low | Sufficient for MVP; can introduce custom exceptions later |
| `@Scheduled` requires `@EnableScheduling` on app | Medium | Phase 3 adds `@EnableScheduling` to SpringJwtApplication |
| Email sending is synchronous (blocks request) | Low | Forgot-password always returns 200 immediately; email failure logged but swallowed |

## Security Considerations

- PasswordResetServiceImpl never reveals whether an email exists in the system
- Email addresses are masked in logs (`jo***@gmail.com`)
- Tokens are never logged in full
- Password reset tokens are single-use (marked `used = true` after consumption)
- Expired tokens are cleaned up by scheduled task (blacklisted) and checked at validation time (reset/refresh)
- BCrypt encoding used for new passwords (same as registration)

## Next Steps

- Phase 3 creates DTOs and controller endpoints that call these services
- Phase 3 adds `@EnableScheduling` annotation for blacklist cleanup
- Phase 3 modifies JwtAuthenticationFilter to check blacklist
