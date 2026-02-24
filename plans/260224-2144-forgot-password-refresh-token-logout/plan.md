---
title: "Forgot Password, Refresh Token & Logout"
description: "Add forgot password (SMTP), refresh token rotation (DB), and logout with token blacklisting"
status: completed
priority: P1
effort: 6h
branch: master
tags: [security, jwt, authentication, email, refresh-token, logout]
created: 2026-02-24
---

# Forgot Password, Refresh Token & Logout

## Summary

Three security features for the JWT Spring Security project:
1. **Forgot Password** - Email-based password reset via SMTP with single-use tokens (30m expiry)
2. **Refresh Token** - DB-stored refresh tokens with rotation on each use (7d expiry)
3. **Logout** - Token blacklisting in DB with scheduled cleanup

## Architecture Impact

- 3 new entities: `PasswordResetToken`, `RefreshToken`, `BlacklistedToken`
- 1 entity modified: `User` (add `email` field)
- 4 new services (interface + impl each): `EmailService`, `PasswordResetService`, `RefreshTokenService`, `BlacklistedTokenService`
- 2 existing services modified: `JwtService`, `UserService`/`UserServiceImpl`
- 4 new DTOs: `ForgotPasswordDto`, `ResetPasswordDto`, `RefreshTokenRequestDto`, `TokenRefreshResponseDto`
- 2 existing DTOs modified: `JwtResponseDto`, `RegisterDto`
- 1 new dependency: `spring-boot-starter-mail`
- Files modified: `AuthController`, `JwtAuthenticationFilter`, `SecurityConfig`, `application.yml`, `pom.xml`

## Phases

| # | Phase | Status | Effort | File |
|---|-------|--------|--------|------|
| 1 | Database & Entity Layer | completed | 1.5h | [phase-01](./phase-01-database-entity-layer.md) |
| 2 | Service Layer | completed | 2h | [phase-02](./phase-02-service-layer.md) |
| 3 | Controller & Security Layer | completed | 1.5h | [phase-03](./phase-03-controller-security-layer.md) |
| 4 | Testing & Verification | completed | 1h | [phase-04](./phase-04-testing-and-verification.md) |

## Dependencies

- Phase 2 depends on Phase 1 (entities/repos must exist)
- Phase 3 depends on Phase 2 (services must exist)
- Phase 4 depends on Phase 3 (all code must be in place)

## Key Constraints

- Java 8 (no `java.time.Instant` in entities, use `Date` or configure converter)
- JJWT 0.9.0 (older API, no builder-based key handling)
- Spring Boot 2.6.4 (WebSecurityConfigurerAdapter, not SecurityFilterChain)
- Hibernate DDL: `create-drop` (tables auto-created from entities)
- Existing style: `@Autowired` field injection, `@Data` Lombok, service interface + impl

## New Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | /api/auth/forgot-password | No | Request password reset email |
| POST | /api/auth/reset-password | No | Reset password with token |
| POST | /api/auth/refresh-token | No | Get new access + refresh token |
| POST | /api/auth/logout | Yes | Blacklist token + delete refresh token |

## New DB Tables

| Table | Key Columns |
|-------|------------|
| password_reset_tokens | id, token, user_id (FK), expiry_date, used |
| refresh_tokens | id, token, user_id (FK unique), expiry_date |
| blacklisted_tokens | id, jti, token_type, expiry_date |

## Validation Summary

**Validated:** 2026-02-24
**Questions asked:** 6

### Confirmed Decisions
- **Access token expiry**: Shorten from 24h to **15 minutes** (900000ms). Refresh tokens handle renewal
- **Blacklist storage**: Use **JTI (JWT ID) claim** instead of full JWT string. Store UUID-sized JTI in blacklist table. Requires adding `jti` claim to `generateTokenLogin` and `generateTokenFromUsername`
- **Email dev mode**: **Log-only fallback** when SMTP fails. Log reset link to console, don't crash
- **Email on registration**: **Required for new users**. Add non-null validation in register endpoint. Existing users (nullable column) unaffected
- **Email uniqueness**: **Reject duplicate emails** in register flow. Add `existsByEmail` check before saving
- **Logout scope**: **Current session only**. Blacklist presented access token + delete its refresh token. Other sessions stay active

### Action Items (Plan Revisions Needed)
- [ ] Phase 1: Change `blacklisted_tokens.token` column to `jti` (VARCHAR, UUID-sized ~36 chars)
- [ ] Phase 1: Change `namnd.app.jwtExpiration` from 86400000 to 900000 (15 min) in application.yml
- [ ] Phase 2: Modify `JwtService.generateTokenLogin` and `generateTokenFromUsername` to include `.setId(UUID.randomUUID().toString())` JTI claim
- [ ] Phase 2: Add `getJtiFromToken(String token)` method to JwtService
- [ ] Phase 2: Update `BlacklistedTokenService.blacklistToken()` to accept JTI string instead of full JWT
- [ ] Phase 2: Update `BlacklistedTokenService.isTokenBlacklisted()` to check by JTI
- [ ] Phase 2: Update `BlacklistedToken` entity field from `token` to `jti`
- [ ] Phase 2: EmailServiceImpl should catch SMTP failures gracefully and log the reset link as fallback
- [ ] Phase 3: Add email validation in register endpoint (reject null/empty email + duplicate email check via `existsByEmail`)
- [ ] Phase 3: Update `JwtAuthenticationFilter` to extract JTI from token and check blacklist by JTI
- [ ] Phase 3: Logout endpoint extracts JTI from JWT (not full JWT string) for blacklisting
- [ ] Phase 3: Add `existsByEmail` to UserRepository and UserService
