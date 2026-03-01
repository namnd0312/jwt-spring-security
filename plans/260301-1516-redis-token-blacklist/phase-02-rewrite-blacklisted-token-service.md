# Phase 2: Rewrite BlacklistedTokenServiceImpl to Use Redis

## Context Links

- [plan.md](./plan.md)
- [Phase 1](./phase-01-add-redis-dependency-and-config.md)
- [BlacklistedTokenService.java](/Users/admin/Desktop/DEV/BACK_END/jwt-spring-security/src/main/java/com/namnd/springjwt/service/BlacklistedTokenService.java)
- [BlacklistedTokenServiceImpl.java](/Users/admin/Desktop/DEV/BACK_END/jwt-spring-security/src/main/java/com/namnd/springjwt/service/impl/BlacklistedTokenServiceImpl.java)

## Overview

- **Priority:** P2
- **Status:** pending
- **Description:** Replace JPA-based blacklist implementation with `StringRedisTemplate`. Use Redis key pattern `blacklist:{jti}` with TTL = token expiry - now. Remove `@Scheduled` cleanup (Redis TTL handles it). Update interface to remove `cleanupExpiredTokens()`.

## Key Insights

- Redis TTL auto-expires keys, eliminating the need for scheduled cleanup jobs
- `StringRedisTemplate.opsForValue().set(key, value, timeout, unit)` sets key + TTL atomically
- `hasKey(key)` is O(1) lookup vs database query
- Access token max TTL: 15 min (900s). Refresh token: 7 days (604800s). Both well within Redis limits.
- `TokenType` enum only used in `BlacklistedToken` entity -- can be removed in Phase 3

## Requirements

**Functional:**
- `blacklistToken(jti, expiryDate)` stores JTI in Redis with auto-expiring TTL
- `isTokenBlacklisted(jti)` returns true if key exists in Redis
- No manual cleanup needed

**Non-functional:**
- O(1) lookup performance (vs database query)
- Automatic memory reclamation via TTL
- Thread-safe (Redis is single-threaded)

## Architecture

```
AuthController.logout()
    --> BlacklistedTokenService.blacklistToken(jti, expiryDate)
        --> StringRedisTemplate.opsForValue().set("blacklist:{jti}", "1", ttlSeconds, SECONDS)

JwtAuthenticationFilter.doFilterInternal()
    --> BlacklistedTokenService.isTokenBlacklisted(jti)
        --> StringRedisTemplate.hasKey("blacklist:{jti}")
```

## Related Code Files

**Modified:**
- `src/main/java/com/namnd/springjwt/service/BlacklistedTokenService.java` -- remove `cleanupExpiredTokens()` method
- `src/main/java/com/namnd/springjwt/service/impl/BlacklistedTokenServiceImpl.java` -- full rewrite

**Impacted (verify no breakage):**
- `src/main/java/com/namnd/springjwt/SpringJwtApplication.java` -- has `@EnableScheduling`, keep it (other services may use it)

## Implementation Steps

### Step 1: Update `BlacklistedTokenService` interface

Remove `cleanupExpiredTokens()` method. Redis TTL handles cleanup automatically.

**File:** `src/main/java/com/namnd/springjwt/service/BlacklistedTokenService.java`

```java
package com.namnd.springjwt.service;

import java.util.Date;

public interface BlacklistedTokenService {

    void blacklistToken(String jti, Date expiryDate);

    boolean isTokenBlacklisted(String jti);
}
```

### Step 2: Rewrite `BlacklistedTokenServiceImpl`

**File:** `src/main/java/com/namnd/springjwt/service/impl/BlacklistedTokenServiceImpl.java`

```java
package com.namnd.springjwt.service.impl;

import com.namnd.springjwt.service.BlacklistedTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.concurrent.TimeUnit;

@Service
public class BlacklistedTokenServiceImpl implements BlacklistedTokenService {

    private static final Logger logger = LoggerFactory.getLogger(BlacklistedTokenServiceImpl.class);
    private static final String BLACKLIST_PREFIX = "blacklist:";

    private final StringRedisTemplate redisTemplate;

    public BlacklistedTokenServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void blacklistToken(String jti, Date expiryDate) {
        long ttlSeconds = (expiryDate.getTime() - System.currentTimeMillis()) / 1000;

        if (ttlSeconds <= 0) {
            // Token already expired, no need to blacklist
            logger.debug("Token already expired, skipping blacklist (JTI: {})", jti);
            return;
        }

        String key = BLACKLIST_PREFIX + jti;
        redisTemplate.opsForValue().set(key, "1", ttlSeconds, TimeUnit.SECONDS);
        logger.debug("Token blacklisted (JTI: {}), TTL: {}s", jti, ttlSeconds);
    }

    @Override
    public boolean isTokenBlacklisted(String jti) {
        String key = BLACKLIST_PREFIX + jti;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
```

**Key changes:**
- Constructor injection of `StringRedisTemplate` (follows code standards, replaces `@Autowired` field injection)
- `blacklistToken()`: calculates TTL = expiryDate - now in seconds, sets Redis key with auto-expiry
- `isTokenBlacklisted()`: simple `hasKey()` O(1) check
- No `@Scheduled` cleanup -- Redis TTL handles it
- No `@Transactional` -- not a database operation
- Removed all JPA imports (`BlacklistedTokenRepository`, `BlacklistedToken`, `TokenType`)

### Step 3: Verify `@EnableScheduling` stays on `SpringJwtApplication`

`@EnableScheduling` on `SpringJwtApplication.java` should remain -- other scheduled tasks may exist or be added later. Removing the `@Scheduled` method from `BlacklistedTokenServiceImpl` is sufficient.

## Todo List

- [ ] Remove `cleanupExpiredTokens()` from `BlacklistedTokenService` interface
- [ ] Rewrite `BlacklistedTokenServiceImpl` with `StringRedisTemplate`
- [ ] Use constructor injection (per code standards)
- [ ] Verify `SpringJwtApplication.java` `@EnableScheduling` not removed
- [ ] Verify no compile errors after changes

## Success Criteria

- `BlacklistedTokenServiceImpl` uses only `StringRedisTemplate`, no JPA references
- Interface contract preserved: `blacklistToken()` + `isTokenBlacklisted()`
- No `@Scheduled` cleanup method
- `mvn clean compile` passes
- `JwtAuthenticationFilter` and `AuthController` compile without changes

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| Redis unavailable at runtime | `blacklistToken` / `isTokenBlacklisted` throw exception | Wrap in try-catch or use circuit breaker (future enhancement) |
| TTL calculation negative (clock skew) | Key set with 0 or negative TTL | Guard: skip blacklist if TTL <= 0 |
| Key collision across apps sharing Redis | False blacklist match | Prefix `blacklist:` scopes keys; could add app namespace if needed |

## Security Considerations

- Redis keys contain only JTI (UUID), no sensitive data
- Key value is `"1"` (presence-only, no token content stored)
- TTL ensures keys don't persist beyond token lifetime
- Redis access should be restricted to app network only

## Next Steps

Proceed to [Phase 3: Remove PostgreSQL Blacklist Artifacts](./phase-03-remove-postgresql-blacklist-artifacts.md)
