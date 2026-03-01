# Phase 2: Activation Service Interface & Implementation

## Context Links

- [PasswordResetService interface](/src/main/java/com/namnd/springjwt/service/PasswordResetService.java)
- [PasswordResetServiceImpl](/src/main/java/com/namnd/springjwt/service/impl/PasswordResetServiceImpl.java)
- [EmailService interface](/src/main/java/com/namnd/springjwt/service/EmailService.java)
- [EmailServiceImpl](/src/main/java/com/namnd/springjwt/service/impl/EmailServiceImpl.java)

## Overview

- **Priority:** P1
- **Status:** complete (with review findings)
- **Description:** Create ActivationService (interface + impl) that generates activation tokens, sends activation emails, and activates user accounts. Also extend EmailService with activation email method.

## Key Insights

- PasswordResetServiceImpl pattern: inject repos + EmailService, generate UUID token, save with expiry, send email
- EmailServiceImpl uses `SimpleMailMessage` + `JavaMailSender`, has SMTP fallback logging
- Token expiry for password reset is 30 minutes; activation should be 24 hours (longer window)
- EmailService currently only has `sendPasswordResetEmail` -- add `sendActivationEmail`

## Requirements

### Functional
- `createActivationToken(User user)` -- generate UUID, save token with 24h expiry, send activation email
- `activateAccount(String token)` -- validate token (not expired, not used), set `user.active = true`, mark token used
- `resendActivationToken(String email)` -- generate new token for inactive user, send new email
- Activation email contains clickable link: `{activationBaseUrl}?token={uuid}`

### Non-Functional
- Service methods annotated `@Transactional` where DB writes occur
- Error logging for email send failures (SMTP fallback pattern)
- Under 100 lines per implementation file

## Architecture

```
EmailService.java (interface)
  + sendActivationEmail(String to, String token)

EmailServiceImpl.java
  + @Value activationBaseUrl
  + sendActivationEmail() -- same pattern as sendPasswordResetEmail()

ActivationService.java (interface)
  - createActivationToken(User user)
  - activateAccount(String token)
  - resendActivationToken(String email)

ActivationServiceImpl.java
  - @Autowired ActivationTokenRepository
  - @Autowired UserRepository
  - @Autowired EmailService
  - TOKEN_EXPIRY_MS = 24 * 60 * 60 * 1000 (24 hours)
```

## Related Code Files

### Files to Modify
- `src/main/java/com/namnd/springjwt/service/EmailService.java` -- add `sendActivationEmail` method
- `src/main/java/com/namnd/springjwt/service/impl/EmailServiceImpl.java` -- implement activation email
- `src/main/resources/application.yml` -- add `namnd.app.activationBaseUrl`

### Files to Create
- `src/main/java/com/namnd/springjwt/service/ActivationService.java`
- `src/main/java/com/namnd/springjwt/service/impl/ActivationServiceImpl.java`

## Implementation Steps

### 1. Add activation email method to EmailService interface

```java
// Add to EmailService.java
void sendActivationEmail(String to, String token);
```

### 2. Add activationBaseUrl config to application.yml

```yaml
namnd:
  app:
    # ... existing config ...
    activationBaseUrl: ${ACTIVATION_BASE_URL:http://localhost:8080/api/auth/activate}
```

### 3. Implement sendActivationEmail in EmailServiceImpl

```java
@Value("${namnd.app.activationBaseUrl}")
private String activationBaseUrl;

@Override
public void sendActivationEmail(String to, String token) {
    String activationLink = activationBaseUrl + "?token=" + token;

    try {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Activate Your Account");
        message.setText(
            "Welcome! Please activate your account.\n\n"
            + "Click the link below to activate:\n"
            + activationLink + "\n\n"
            + "This link will expire in 24 hours.\n"
            + "If you did not register, please ignore this email."
        );

        mailSender.send(message);
        logger.info("Activation email sent to: {}", maskEmail(to));
    } catch (Exception e) {
        logger.error("Failed to send activation email: {}", e.getMessage());
        logger.info("Activation link (SMTP fallback): {}", activationLink);
    }
}
```

### 4. Create ActivationService interface

```java
package com.namnd.springjwt.service;

public interface ActivationService {
    void createActivationToken(User user);
    void activateAccount(String token);
    void resendActivationToken(String email);
}
```

### 5. Create ActivationServiceImpl

```java
package com.namnd.springjwt.service.impl;

@Service
public class ActivationServiceImpl implements ActivationService {

    private static final Logger logger = LoggerFactory.getLogger(ActivationServiceImpl.class);
    private static final long TOKEN_EXPIRY_MS = 24 * 60 * 60 * 1000; // 24 hours

    @Autowired
    private ActivationTokenRepository activationTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    @Override
    @Transactional
    public void createActivationToken(User user) {
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
        ActivationToken activationToken = activationTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid activation token"));

        if (activationToken.isUsed()) {
            throw new RuntimeException("Activation token already used");
        }
        if (activationToken.getExpiryDate().before(new Date())) {
            throw new RuntimeException("Activation token expired");
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
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.isActive()) {
            throw new RuntimeException("Account is already active");
        }

        createActivationToken(user);
    }
}
```

## Todo List

- [x] Add `sendActivationEmail(String to, String token)` to `EmailService.java`
- [x] Add `namnd.app.activationBaseUrl` to `application.yml`
- [x] Implement `sendActivationEmail` in `EmailServiceImpl.java`
- [x] Create `ActivationService.java` interface
- [x] Create `ActivationServiceImpl.java` implementation
- [x] Run `mvn compile` to verify
- [ ] **FIX REQUIRED:** Remove token URL from SMTP fallback log in `EmailServiceImpl` (security — token leak)
- [ ] **FIX REQUIRED:** Make `resendActivationToken` silent no-op for unknown/active emails (security — info leak)
- [ ] **FIX REQUIRED:** Invalidate existing unused tokens before inserting new one in `createActivationToken`

## Success Criteria

- `mvn compile` passes
- ActivationService creates tokens, sends emails, activates accounts
- EmailService sends activation emails with correct link format
- Resend flow validates user is inactive before sending

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| Email delivery failure | Medium -- user can't activate | SMTP fallback logging + resend endpoint |
| Multiple tokens per user | Low -- only latest matters | All valid tokens work; `used` flag prevents reuse |
| Token enumeration attack | Low | UUID tokens have sufficient entropy |

## Security Considerations

- UUID tokens (128-bit entropy) prevent guessing
- Token marked `used` after activation prevents replay
- 24-hour expiry limits attack window
- `resendActivationToken` requires valid email in DB

## Next Steps

- Phase 3: Integrate with AuthController (register, activate, resend endpoints)
