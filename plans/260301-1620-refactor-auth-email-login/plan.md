---
title: "Refactor Auth to Email-Based Login"
description: "Switch authentication from username to email+password, allow duplicate usernames"
status: code-reviewed
priority: P2
effort: 2h
branch: master
tags: [auth, refactor, email-login, jwt]
created: 2026-03-01
---

# Refactor Auth to Email-Based Login

## Summary

Replace username-based authentication with email-based login. After this change:
- Login accepts `email` + `password` (not `username`)
- JWT `sub` claim contains email (not username)
- `loadUserByUsername()` internally queries by email
- Duplicate usernames are allowed; email remains unique
- Registration removes username uniqueness check

## Phases

| # | Phase | Status | Effort | File |
|---|-------|--------|--------|------|
| 1 | Core Auth Refactor | complete | 1h | [phase-01](./phase-01-core-auth-refactor.md) |
| 2 | Controller & DTO Changes | complete | 40m | [phase-02](./phase-02-controller-dto-changes.md) |
| 3 | Documentation Updates | pending | 20m | [phase-03](./phase-03-documentation-updates.md) |

## Key Dependencies

- Phase 2 depends on Phase 1 (controller uses updated services)
- Phase 3 depends on Phase 2 (docs reflect final API)

## Files Modified

| File | Phase | Change Summary |
|------|-------|---------------|
| `UserPrinciple.java` | 1 | Add `email` field, use as `getUsername()` return |
| `UserServiceImpl.java` | 1 | `loadUserByUsername()` queries by email |
| `UserService.java` | 1 | Add `findByEmail()` (already exists), keep interface clean |
| `JwtService.java` | 1 | `generateTokenFromUsername` renamed conceptually; sub=email |
| `JwtAuthenticationFilter.java` | 1 | No code change needed (calls same methods) |
| `AuthController.java` | 2 | Login uses `LoginRequestDto`, remove username uniqueness check |
| `JwtResponseDto.java` | 2 | Add `email` field |
| `LoginRequestDto.java` | 2 | **New file** - email + password fields |
| `AuthController.java` (logout) | 2 | Use `findByEmail()` instead of `findByUserName()` |
| `AuthController.java` (refresh) | 2 | `generateTokenFromUsername` now receives email |
| `README.md` | 3 | Update API examples |

## Files Created

- `src/main/java/com/namnd/springjwt/dto/LoginRequestDto.java`

## Risk Assessment

- **Breaking change**: Existing tokens have `sub=username`; after deploy all tokens invalidated (acceptable for auth refactor)
- **DB migration**: `ddl-auto: create-drop` in dev means schema auto-recreates; production needs manual migration to drop username unique constraint if it exists
- **Refresh token flow**: `generateTokenFromUsername()` currently takes username string; after refactor it takes email string. Method name becomes misleading -- rename to `generateTokenFromEmail()` for clarity
