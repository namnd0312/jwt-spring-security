---
title: "Email Activation Flow"
description: "Inactive users on register, email activation required before login"
status: in-review
priority: P1
effort: 3h
branch: master
tags: [auth, email, activation, registration]
created: 2026-03-01
---

# Email Activation Flow

## Summary

After registration, users are inactive by default. System sends activation email with unique token/link. User clicks link to activate account. Only active users can login. Mirrors existing PasswordResetToken pattern.

## Phases

| # | Phase | Effort | Status | File |
|---|-------|--------|--------|------|
| 1 | Entity & Repository | 30m | complete | [phase-01](./phase-01-activation-token-entity-and-repository.md) |
| 2 | Activation Service | 1h | complete | [phase-02](./phase-02-activation-service-interface-and-implementation.md) |
| 3 | Auth Flow Integration | 1h | complete | [phase-03](./phase-03-auth-controller-and-security-integration.md) |
| 4 | Documentation | 30m | partial | [phase-04](./phase-04-update-project-documentation.md) |

## Code Review Findings (2026-03-01)

**Report:** `plans/260301-1647-email-activation-flow/reports/code-reviewer-260301-2203-email-activation-flow.md`

### Must Fix Before Merge

| # | Severity | Issue | File |
|---|----------|-------|------|
| 1 | Critical | SMTP fallback logs full token URL — token leak | `EmailServiceImpl.java` lines 47, 69 |
| 2 | Critical | `resendActivationToken` leaks email existence + activation state | `ActivationServiceImpl.java` lines 76, 79 |
| 3 | Critical | No DB migration for existing users — all locked out on deploy | `application.yml` (need migration SQL) |
| 4 | High | Multiple valid tokens per user — old tokens not invalidated on resend | `ActivationServiceImpl.java` line 82 |

### Next Steps

1. Remove token URL from SMTP fallback log lines
2. Make `resendActivationToken` a silent no-op (mirror `forgotPassword` pattern); controller always returns generic 200
3. Add `UPDATE users SET active = true WHERE active = false` migration script
4. Delete/invalidate existing unused tokens before creating new one in `createActivationToken`
5. Complete Phase 4: add `ActivationService`/impl to `codebase-summary.md`; add activation flow to `system-architecture.md`

## Key Design Decisions

1. **`UserPrinciple.isEnabled()` returns `user.active`** -- Spring Security's `AbstractUserDetailsAuthenticationProvider` automatically throws `DisabledException` for disabled users. No manual check needed in login endpoint.
2. **Mirror PasswordResetToken pattern** -- Same entity structure (id, token UUID, expiryDate, used, user FK), same email sending approach via EmailService.
3. **Activation token expiry: 24 hours** (configurable via `application.yml`).
4. **GET endpoint for activation** (`/api/auth/activate?token=xxx`) -- user clicks link from email, GET is appropriate.
5. **Resend activation endpoint** (`POST /api/auth/resend-activation`) -- allows user to request new activation email if original expired.

## Files to Modify

- `model/User.java` -- add `active` boolean field (default false)
- `model/UserPrinciple.java` -- `isEnabled()` returns `user.active`, add `active` to constructor + `build()`
- `controller/AuthController.java` -- register calls activation service, add activate + resend endpoints
- `service/EmailService.java` -- add `sendActivationEmail()` method
- `service/impl/EmailServiceImpl.java` -- implement activation email sending
- `application.yml` -- add `namnd.app.activationBaseUrl` config

## Files to Create

- `model/ActivationToken.java`
- `repository/ActivationTokenRepository.java`
- `service/ActivationService.java`
- `service/impl/ActivationServiceImpl.java`

## Dependencies

- Spring Mail (already configured)
- PostgreSQL (already configured)
- Existing EmailService pattern
- Existing PasswordResetToken pattern
