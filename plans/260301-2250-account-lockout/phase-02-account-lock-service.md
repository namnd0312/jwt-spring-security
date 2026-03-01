# Phase 2: Account Lock Service

## Context Links

- [UserService.java](/src/main/java/com/namnd/springjwt/service/UserService.java)
- [UserServiceImpl.java](/src/main/java/com/namnd/springjwt/service/impl/UserServiceImpl.java)
- [UserRepository.java](/src/main/java/com/namnd/springjwt/repository/UserRepository.java)
- [Phase 1](./phase-01-entity-and-config.md)

## Overview

- **Priority:** P1
- **Status:** pending
- **Description:** Create AccountLockService interface + impl to manage failed attempts, locking, and auto-unlock logic

## Key Insights

- Service needs @Value injection for maxFailedAttempts and lockDurationMs
- Auto-unlock check must happen BEFORE authenticationManager.authenticate() (called from controller)
- Service modifies User entity directly via UserRepository (or UserService.save)
- Keep service focused: only lockout logic, no auth logic

## Requirements

### Functional
- `registerFailedAttempt(String email)`: increment failed attempts; lock if threshold reached
- `resetFailedAttempts(String email)`: reset count to 0 and clear lockTime on successful login
- `unlockIfExpired(User user)`: check if lock duration passed, unlock if so; return true if unlocked or was never locked
- `isLocked(User user)`: check if user currently locked (lockTime != null and not expired)
- `getRemainingLockTimeMs(User user)`: calculate remaining lock time for API response

### Non-functional
- Transactional operations for data consistency
- Logging on lock/unlock events

## Architecture

```
AccountLockService (interface)
  |
  v
AccountLockServiceImpl (@Service)
  |-- @Value maxFailedAttempts
  |-- @Value lockDurationMs
  |-- UserRepository (injected)
  |
  Methods:
  |-- registerFailedAttempt(email) -> increment or lock
  |-- resetFailedAttempts(email) -> clear count + lockTime
  |-- unlockIfExpired(user) -> auto-unlock if expired
  |-- isLocked(user) -> boolean
  |-- getRemainingLockTimeMs(user) -> long
```

## Related Code Files

### Create
- `src/main/java/com/namnd/springjwt/service/AccountLockService.java`
- `src/main/java/com/namnd/springjwt/service/impl/AccountLockServiceImpl.java`

## Implementation Steps

### Step 1: Create AccountLockService interface

```java
package com.namnd.springjwt.service;

import com.namnd.springjwt.model.User;

public interface AccountLockService {

    void registerFailedAttempt(String email);

    void resetFailedAttempts(String email);

    boolean unlockIfExpired(User user);

    boolean isLocked(User user);

    long getRemainingLockTimeMs(User user);
}
```

### Step 2: Create AccountLockServiceImpl

```java
package com.namnd.springjwt.service.impl;

import com.namnd.springjwt.model.User;
import com.namnd.springjwt.repository.UserRepository;
import com.namnd.springjwt.service.AccountLockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.Date;
import java.util.Optional;

@Service
public class AccountLockServiceImpl implements AccountLockService {

    private static final Logger logger = LoggerFactory.getLogger(AccountLockServiceImpl.class);

    @Autowired
    private UserRepository userRepository;

    @Value("${namnd.app.maxFailedAttempts}")
    private int maxFailedAttempts;

    @Value("${namnd.app.lockDurationMs}")
    private long lockDurationMs;

    @Override
    @Transactional
    public void registerFailedAttempt(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (!userOpt.isPresent()) {
            return; // user not found, nothing to do
        }

        User user = userOpt.get();
        int newFailedAttempts = user.getFailedAttempts() + 1;
        user.setFailedAttempts(newFailedAttempts);

        if (newFailedAttempts >= maxFailedAttempts) {
            user.setLockTime(new Date());
            logger.warn("Account locked for email: {} after {} failed attempts",
                    email, newFailedAttempts);
        }

        userRepository.save(user);
    }

    @Override
    @Transactional
    public void resetFailedAttempts(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (!userOpt.isPresent()) {
            return;
        }

        User user = userOpt.get();
        user.setFailedAttempts(0);
        user.setLockTime(null);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public boolean unlockIfExpired(User user) {
        if (user.getLockTime() == null) {
            return true; // not locked
        }

        long lockTimeMs = user.getLockTime().getTime();
        long now = System.currentTimeMillis();

        if (now - lockTimeMs >= lockDurationMs) {
            user.setFailedAttempts(0);
            user.setLockTime(null);
            userRepository.save(user);
            logger.info("Account auto-unlocked for email: {}", user.getEmail());
            return true; // was locked, now unlocked
        }

        return false; // still locked
    }

    @Override
    public boolean isLocked(User user) {
        if (user.getLockTime() == null) {
            return false;
        }
        long elapsed = System.currentTimeMillis() - user.getLockTime().getTime();
        return elapsed < lockDurationMs;
    }

    @Override
    public long getRemainingLockTimeMs(User user) {
        if (user.getLockTime() == null) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - user.getLockTime().getTime();
        long remaining = lockDurationMs - elapsed;
        return Math.max(remaining, 0);
    }
}
```

## Todo List

- [ ] Create AccountLockService interface
- [ ] Create AccountLockServiceImpl with all methods
- [ ] Verify @Value properties match application.yml keys
- [ ] Compile and verify no errors

## Success Criteria

- Service correctly increments failed attempts
- Locks account at threshold
- Auto-unlocks after duration expires
- Returns accurate remaining lock time
- All methods transactional where needed

## Risk Assessment

- **Race condition**: two concurrent failed logins could both read failedAttempts=4 and both increment to 5. Result: both trigger lock. Acceptable -- account still gets locked.
- **Clock skew**: System.currentTimeMillis() used for comparison. Single-server deployment, no issue.

## Security Considerations

- Failed attempt count persisted in DB (survives restarts)
- Lock time checked server-side only
- No lockout info exposed in error messages beyond "account locked" + remaining time
