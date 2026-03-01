# Phase 03: Refactor BlacklistedTokenService to Use RedisService

## Context Links

- [plan.md](./plan.md)
- [Phase 01](./phase-01-redis-config-and-service-interface.md)
- [Phase 02](./phase-02-redis-service-implementation.md) (prerequisite)
- [BlacklistedTokenServiceImpl.java](../../src/main/java/com/namnd/springjwt/service/impl/BlacklistedTokenServiceImpl.java) (file to modify)

## Overview

- **Priority:** P2
- **Status:** pending
- **Description:** Replace direct `StringRedisTemplate` usage in `BlacklistedTokenServiceImpl` with the new `RedisService` abstraction.

## Key Insights

- `BlacklistedTokenServiceImpl` currently injects `StringRedisTemplate` directly
- After refactor, it injects `RedisService` instead
- `blacklistToken()` maps to `redisService.set(key, "1", ttlSeconds, TimeUnit.SECONDS)`
- `isTokenBlacklisted()` maps to `redisService.hasKey(key)` -- but must KEEP its own try-catch for fail-closed behavior
- The fail-closed pattern (return `true` on error) is a security requirement specific to this service, not a generic Redis behavior

## Requirements

**Functional:**
- `BlacklistedTokenServiceImpl` uses `RedisService` instead of `StringRedisTemplate`
- Fail-closed error handling preserved in `isTokenBlacklisted()`
- Identical external behavior (no API change)

**Non-functional:**
- Constructor injection of `RedisService`
- Maintain existing SLF4J logging

## Architecture

**Before:**
```
BlacklistedTokenServiceImpl --> StringRedisTemplate --> Redis
```

**After:**
```
BlacklistedTokenServiceImpl --> RedisService --> StringRedisTemplate --> Redis
```

## Related Code Files

**Modify:**
- `src/main/java/com/namnd/springjwt/service/impl/BlacklistedTokenServiceImpl.java`

**No new files in this phase.**

## Implementation Steps

### Step 1: Refactor `BlacklistedTokenServiceImpl.java`

**Path:** `src/main/java/com/namnd/springjwt/service/impl/BlacklistedTokenServiceImpl.java`

Replace the full file content with:

```java
package com.namnd.springjwt.service.impl;

import com.namnd.springjwt.config.RedisKeyPrefix;
import com.namnd.springjwt.service.BlacklistedTokenService;
import com.namnd.springjwt.service.RedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.concurrent.TimeUnit;

@Service
public class BlacklistedTokenServiceImpl implements BlacklistedTokenService {

    private static final Logger logger = LoggerFactory.getLogger(BlacklistedTokenServiceImpl.class);

    private final RedisService redisService;

    public BlacklistedTokenServiceImpl(RedisService redisService) {
        this.redisService = redisService;
    }

    @Override
    public void blacklistToken(String jti, Date expiryDate) {
        long ttlSeconds = (expiryDate.getTime() - System.currentTimeMillis()) / 1000;

        if (ttlSeconds <= 0) {
            logger.debug("Token already expired, skipping blacklist (JTI: {})", jti);
            return;
        }

        String key = RedisKeyPrefix.BLACKLIST + jti;
        redisService.set(key, "1", ttlSeconds, TimeUnit.SECONDS);
        logger.debug("Token blacklisted (JTI: {}), TTL: {}s", jti, ttlSeconds);
    }

    @Override
    public boolean isTokenBlacklisted(String jti) {
        try {
            String key = RedisKeyPrefix.BLACKLIST + jti;
            return Boolean.TRUE.equals(redisService.hasKey(key));
        } catch (Exception e) {
            // Fail-closed: deny token if Redis is unavailable (security requirement)
            logger.error("Redis unavailable during blacklist check (JTI: {}). Denying request.", jti);
            return true;
        }
    }
}
```

**Key changes:**
1. `StringRedisTemplate` replaced with `RedisService` in constructor
2. `blacklistToken()`: `redisTemplate.opsForValue().set(...)` replaced with `redisService.set(...)`
3. `isTokenBlacklisted()`: `redisTemplate.hasKey(...)` replaced with `redisService.hasKey(...)`
4. Try-catch in `isTokenBlacklisted()` kept -- `RedisService.hasKey()` returns `false` on error by default, but this service needs `true` (fail-closed) so the extra try-catch is essential

### Step 2: Verify compilation

```bash
mvn clean compile -q
```

### Step 3: Smoke test (manual)

1. Start application with Redis running
2. Login to get a JWT token
3. Logout (blacklists the token)
4. Try using the blacklisted token -- should be rejected
5. Verify Redis key exists: `redis-cli keys "blacklist:*"`

## Todo List

- [ ] Replace `StringRedisTemplate` with `RedisService` in `BlacklistedTokenServiceImpl`
- [ ] Verify fail-closed behavior preserved in `isTokenBlacklisted()`
- [ ] Run `mvn clean compile` to verify no errors
- [ ] Manual smoke test (login -> logout -> verify blacklist)

## Success Criteria

- `BlacklistedTokenServiceImpl` no longer imports or references `StringRedisTemplate`
- Fail-closed behavior preserved (returns `true` on Redis error in `isTokenBlacklisted`)
- No behavioral change from the caller's perspective
- Project compiles without errors

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Double error handling (RedisService + BlacklistedTokenService) | Low | Low | Acceptable -- RedisService logs generic error, BlacklistedTokenService applies business rule (fail-closed) |
| Regression in logout flow | Low | High | Manual smoke test in Step 3 |

## Security Considerations

- **Fail-closed preserved**: If `RedisService.hasKey()` throws (not just returns false), `isTokenBlacklisted()` catches and returns `true` -- rejecting the token. This is critical security behavior.
- No secrets exposed in refactor

## Next Steps

- Update `docs/codebase-summary.md` to reflect new `RedisService` and `RedisConfig` classes
- Update `docs/system-architecture.md` to show `RedisService` layer between services and Redis
