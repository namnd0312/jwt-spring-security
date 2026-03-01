# Phase 4: Update Project Documentation

## Context Links

- [README](/README.md)
- [Codebase Summary](/docs/codebase-summary.md)
- [System Architecture](/docs/system-architecture.md)
- [Code Standards](/docs/code-standards.md)

## Overview

- **Priority:** P2
- **Status:** partial
- **Description:** Update project documentation to reflect email activation flow: new entities, services, endpoints, security behavior changes.

## Requirements

### Functional
- README: add activation endpoints to API Reference, update registration response, add activation flow to Architecture section
- Codebase Summary: add ActivationToken entity, ActivationTokenRepository, ActivationService, ActivationServiceImpl to relevant sections; update User entity description; update code metrics
- System Architecture: add activation flow diagram, update registration flow, update data model with activation_tokens table, update security boundaries

## Related Code Files

### Files to Modify
- `README.md`
- `docs/codebase-summary.md`
- `docs/system-architecture.md`

## Implementation Steps

### 1. Update README.md

Add to API Reference section after register endpoint:

```markdown
**POST /api/auth/register** - Create new user (inactive, requires email activation)
Response (200 OK):
"User registered successfully! Please check your email to activate your account."

**GET /api/auth/activate?token=xxx** - Activate user account
Response (200 OK):
"Account activated successfully! You can now login."

**POST /api/auth/resend-activation** - Resend activation email
Request:
{
  "email": "jane@example.com"
}
Response (200 OK):
"Activation email sent."
```

Update Architecture > Authentication Flow to note:
- New users are inactive until email activation
- Login returns 401 for inactive accounts

Update Database section:
- **activation_tokens** table: id, token, expiry_date, user_id (FK), used

Update Troubleshooting:
- **401 Account not activated** -- check email for activation link, or use resend-activation endpoint

### 2. Update docs/codebase-summary.md

Add to Data Models section:
```
**ActivationToken.java** (~30 lines)
- @Entity, @Data (Lombok)
- **Table:** activation_tokens
- **Columns:** id, token (unique), expiryDate, user (ManyToOne), used
```

Update User.java description:
```
- active (boolean, default false) -- requires email activation
```

Add to Services section:
```
**ActivationService.java** (interface)
- createActivationToken(User user)
- activateAccount(String token)
- resendActivationToken(String email)

**ActivationServiceImpl.java** (~80 lines)
- @Service, 24-hour token expiry
- Creates activation tokens, sends emails, activates accounts
```

Update EmailService description:
```
- sendActivationEmail(String email, String token) -- Sends activation link
```

Update AuthController endpoints:
```
- GET /activate -- activate account via token
- POST /resend-activation -- resend activation email
```

Update Code Metrics.

### 3. Update docs/system-architecture.md

Add activation_tokens to data model diagram.
Add Email Activation Flow sequence diagram (mirrors Password Reset Flow).
Update Registration Flow to include activation step.
Update Security Boundaries -- note that login rejects inactive users.
Remove "Email Verification" from Future Improvements (now implemented).

## Todo List

- [x] Update README.md with activation endpoints + troubleshooting
- [ ] Update docs/codebase-summary.md — ActivationToken entity added, but ActivationService/ActivationServiceImpl/new endpoints/metrics NOT added
- [ ] Update docs/system-architecture.md — activation flow diagram, updated registration flow, and removal of "Email Verification" from future work all missing

## Success Criteria

- All docs accurately reflect new activation flow
- New endpoints documented with request/response examples
- Data model diagrams include activation_tokens table
- No stale references to email verification as "future work"

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| Docs drift from code | Low | Update docs in same PR as code changes |

## Security Considerations

- Documentation should not expose internal implementation details (token entropy, expiry strategy)
- Activation endpoint examples use placeholder tokens

## Next Steps

- Feature complete after documentation update
