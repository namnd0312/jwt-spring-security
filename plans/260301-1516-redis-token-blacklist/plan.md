---
title: "Replace PostgreSQL Token Blacklist with Redis"
description: "Migrate token blacklisting from JPA/PostgreSQL to Redis with auto-expiring keys"
status: completed
priority: P2
effort: 3h
branch: master
tags: [redis, security, token-blacklist, performance]
created: 2026-03-01
---

# Replace PostgreSQL Token Blacklist with Redis

## Overview

Replace the JPA-based `BlacklistedToken` entity and `BlacklistedTokenRepository` with Redis `StringRedisTemplate` operations. Redis TTL auto-expires entries, eliminating the `@Scheduled` cleanup job. The `BlacklistedTokenService` interface stays unchanged so `JwtAuthenticationFilter` and `AuthController` require zero modifications.

## Phases

| # | Phase | Status | Effort | File |
|---|-------|--------|--------|------|
| 1 | Add Redis dependency and config | complete | 30m | [phase-01](./phase-01-add-redis-dependency-and-config.md) |
| 2 | Rewrite BlacklistedTokenServiceImpl | complete | 1h | [phase-02](./phase-02-rewrite-blacklisted-token-service.md) |
| 3 | Remove PostgreSQL blacklist artifacts | complete | 30m | [phase-03](./phase-03-remove-postgresql-blacklist-artifacts.md) |
| 4 | Verify and test | compile-pass | 1h | [phase-04](./phase-04-verify-and-test.md) |

## Key Dependencies

- Redis server running locally (default `localhost:6379`)
- Spring Boot 2.6.4 compatible with `spring-boot-starter-data-redis`
- `BlacklistedTokenService` interface contract preserved (no consumers change)

## Files Modified

- `pom.xml` -- add redis dependency
- `application.yml` -- add redis connection config
- `BlacklistedTokenServiceImpl.java` -- rewrite to use `StringRedisTemplate`
- `BlacklistedTokenService.java` -- remove `cleanupExpiredTokens()` method

## Files Deleted

- `BlacklistedToken.java` (JPA entity)
- `BlacklistedTokenRepository.java` (JPA repository)
- `TokenType.java` (enum, only used by BlacklistedToken)

## Files Unchanged

- `JwtAuthenticationFilter.java` -- uses `BlacklistedTokenService` interface only
- `AuthController.java` -- uses `BlacklistedTokenService` interface only
- `JwtService.java` -- no blacklist references
- `docker-compose.yml` -- optionally add Redis service (documented in phase-01) ✓ done

## Review Notes (2026-03-01)

Code review report: `reports/code-reviewer-260301-1538-redis-token-blacklist.md`

**Blocking before production:**
- Parameterize `datasource.password`, `mail.username`, `mail.password` (hardcoded in application.yml)
- Rotate exposed Google mail app password; use strong `jwtSecret` via env var
- ~~Fail-closed Redis error handling in `isTokenBlacklisted()` — FIXED~~
- Parameterize `datasource.url` for Docker Compose compatibility

**Pending tasks:**
- Integration test (Phase 4 manual steps) — requires running Redis + Postgres
- ~~Update `docs/codebase-summary.md` and `docs/system-architecture.md`~~ — in progress
