# Planner Report: Redis Token Blacklist Migration

**Date:** 2026-03-01
**Plan:** `/Users/admin/Desktop/DEV/BACK_END/jwt-spring-security/plans/260301-1516-redis-token-blacklist/`

## Summary

Created a 4-phase implementation plan to replace PostgreSQL-based token blacklisting with Redis. The migration is surgical: only 4 files modified, 3 files deleted, 2 files unchanged (filter + controller). Redis TTL eliminates the `@Scheduled` cleanup job entirely.

## Phases

| # | Phase | Effort | Key Action |
|---|-------|--------|------------|
| 1 | Add Redis dependency + config | 30m | `spring-boot-starter-data-redis` in pom.xml, `spring.redis.*` in application.yml |
| 2 | Rewrite BlacklistedTokenServiceImpl | 1h | Replace JPA ops with `StringRedisTemplate`, key pattern `blacklist:{jti}`, TTL = expiry - now |
| 3 | Remove PostgreSQL artifacts | 30m | Delete `BlacklistedToken.java`, `BlacklistedTokenRepository.java`, `TokenType.java` |
| 4 | Verify and test | 1h | Compile, context load test, manual login/logout/blacklist flow, Redis key inspection |

**Total estimated effort: 3h**

## Key Design Decisions

1. **Interface preserved** -- `BlacklistedTokenService` keeps `blacklistToken()` + `isTokenBlacklisted()`. Consumers (filter, controller) untouched.
2. **`cleanupExpiredTokens()` removed from interface** -- Redis TTL auto-expires keys, making scheduled cleanup unnecessary.
3. **Constructor injection** -- New impl uses constructor injection per code standards (replaces `@Autowired` field injection).
4. **Key pattern: `blacklist:{jti}`** -- Simple, namespaced. Value is `"1"` (presence-only check via `hasKey()`).
5. **TTL guard** -- If token already expired (TTL <= 0), skip blacklisting entirely.
6. **`TokenType` enum deleted** -- Only used by `BlacklistedToken` entity. YAGNI.

## Files Impact Matrix

| File | Action | Reason |
|------|--------|--------|
| `pom.xml` | modify | Add redis starter dependency |
| `application.yml` | modify | Add `spring.redis.*` config |
| `BlacklistedTokenService.java` | modify | Remove `cleanupExpiredTokens()` |
| `BlacklistedTokenServiceImpl.java` | rewrite | StringRedisTemplate replaces JPA |
| `BlacklistedToken.java` | delete | JPA entity no longer needed |
| `BlacklistedTokenRepository.java` | delete | JPA repository no longer needed |
| `TokenType.java` | delete | Enum only used by deleted entity |
| `JwtAuthenticationFilter.java` | unchanged | Uses interface only |
| `AuthController.java` | unchanged | Uses interface only |
| `docker-compose.yml` | optional modify | Add redis-service container |

## Unresolved Questions

1. **Redis availability in test profile** -- `SpringJwtApplicationTests` loads full context. Should a test profile disable Redis or use embedded Redis (`testcontainers`)?
2. **Docker Compose** -- Should Redis be added to `docker-compose.yml` now or deferred? Plan marks it optional.
3. **`blacklisted_tokens` table** -- Manual DROP needed? `ddl-auto: update` won't auto-drop. Harmless but could confuse future developers.
