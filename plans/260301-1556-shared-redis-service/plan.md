---
title: "Shared Redis Utility Service"
description: "Create a reusable RedisService abstraction to centralize all Redis operations and refactor BlacklistedTokenService to use it"
status: in-review
priority: P2
effort: 3h
branch: master
tags: [redis, utility, shared-service, refactor]
created: 2026-03-01
---

# Shared Redis Utility Service

## Overview

Extract a shared `RedisService` interface + implementation that wraps all common Redis operations (key-value, hash, list, set, pub/sub, distributed lock) behind a single injectable service. Then refactor `BlacklistedTokenServiceImpl` to consume it instead of raw `StringRedisTemplate`.

## Motivation

- Current codebase uses `StringRedisTemplate` directly in `BlacklistedTokenServiceImpl`
- Future features (rate limiting, caching, session store) will also need Redis
- Centralizing Redis access enables consistent error handling, logging, and testability

## Phases

| Phase | File | Status | Effort |
|-------|------|--------|--------|
| 1 | [phase-01-redis-config-and-service-interface.md](./phase-01-redis-config-and-service-interface.md) | complete | 1h |
| 2 | [phase-02-redis-service-implementation.md](./phase-02-redis-service-implementation.md) | needs-fix (277 lines > 200 limit; see M1 in review) | 1.5h |
| 3 | [phase-03-refactor-blacklisted-token-service.md](./phase-03-refactor-blacklisted-token-service.md) | needs-fix (fail-closed bypass; see H1 in review) | 0.5h |

## Dependencies

- `spring-boot-starter-data-redis` already in `pom.xml`
- Redis config (`spring.redis.*`) already in `application.yml`
- No new Maven dependencies required

## Key Decisions

- Constructor injection, SLF4J logging throughout
- `StringRedisTemplate` for string ops, `RedisTemplate<String, Object>` (with Jackson serializer) for hash/object ops
- Try-catch with sensible defaults on every Redis call
- Simple distributed lock (no Lua scripts) -- sufficient for this project scope

## Files Created

- `src/main/java/com/namnd/springjwt/config/RedisConfig.java`
- `src/main/java/com/namnd/springjwt/service/RedisService.java`
- `src/main/java/com/namnd/springjwt/service/impl/RedisServiceImpl.java`

## Files Modified

- `src/main/java/com/namnd/springjwt/service/impl/BlacklistedTokenServiceImpl.java`

## Success Criteria

- [x] `RedisConfig` provides `RedisTemplate<String, Object>` bean
- [x] `RedisService` interface covers all listed operations
- [x] `RedisServiceImpl` handles errors gracefully with logging
- [x] `BlacklistedTokenServiceImpl` uses `RedisService` (no raw template)
- [x] Project compiles: `mvn clean compile`

## Review Findings (2026-03-01)

Report: `reports/code-reviewer-260301-1611-shared-redis-service.md`

**Must fix before done:**
- [H1] Fail-closed bypass in `isTokenBlacklisted` — exception swallowed by `RedisServiceImpl.hasKey()`, catch block in `BlacklistedTokenServiceImpl` is dead code; token incorrectly allowed when Redis is down
- [M1] `RedisServiceImpl.java` is 277 lines — exceeds 200-line project rule; split into `RedisKeyValueServiceImpl` + `RedisCollectionServiceImpl`
- [M2] `unlock()` has no ownership check — can unlock another holder's lock after TTL expiry

**Defer/document:**
- [L1] `sRemove` cast needs explanatory comment
- [L4] `getExpire` returns `0L` on error — ambiguous vs. persist key; document
- [L5] No null-guard on `expiryDate` in `blacklistToken`

**Docs owed:**
- `docs/codebase-summary.md` — update to reflect `RedisService`/`RedisConfig`
- `docs/system-architecture.md` — update to show `RedisService` layer
