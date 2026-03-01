# Phase 3: Documentation Updates

## Context Links
- [Plan Overview](./plan.md)
- [Phase 2: Controller & DTO Changes](./phase-02-controller-dto-changes.md)
- Depends on: Phase 2 completion

## Overview
- **Priority:** P3
- **Status:** pending
- **Description:** Update README.md and project docs to reflect email-based login

## Key Insights
- README contains API examples with `username` field in login request -- must change to `email`
- Login response example needs `email` field added
- Registration example should note that duplicate usernames are allowed
- Docs in `./docs/` reference username-based auth flow

## Requirements

### Functional
- README API examples use email for login
- Login response example includes email
- Register section notes duplicate usernames allowed

## Related Code Files

### Files to Modify
- `README.md`
- `docs/project-overview-pdr.md` (API contracts section)
- `docs/system-architecture.md` (auth flow diagrams)
- `docs/codebase-summary.md` (UserService methods)

## Implementation Steps

### Step 1: Update `README.md`

1. **Login endpoint** (line 42-57): Change request body from `username` to `email`:
   ```json
   Request:
   {
     "email": "john@example.com",
     "password": "password123"
   }

   Response (200 OK):
   {
     "id": 1,
     "token": "eyJhbGc...",
     "refreshToken": "eyJhbGc...",
     "email": "john@example.com",
     "username": "john",
     "name": "John Doe",
     "roles": ["ROLE_USER"]
   }
   ```

2. **Register endpoint** (line 59-75): Add note about duplicate usernames:
   - Remove mention of "Username already taken" from error responses
   - Keep email uniqueness error

3. **Authentication Flow** (line 154-161): Update step 1:
   ```
   1. User submits email + password -> AuthenticationManager validates
   ```

4. **Database section** (line 218): Remove "(unique)" from username if mentioned, keep email unique:
   ```
   - **users** table: id, username, email (unique), password, full_name
   ```

5. **Troubleshooting** section: Update relevant entries

### Step 2: Update `docs/project-overview-pdr.md`

1. **FR-001 User Login** (line 26-27): Change "Accept username/password" to "Accept email/password"
2. **FR-001 Registration** (line 33): Remove "Validate username uniqueness"
3. **API Contracts Login** (line 146-168): Update request/response examples
4. **API Contracts Register** (line 196-198): Remove username uniqueness error
5. **Acceptance Criteria** (line 401-415): Update login/register stories

### Step 3: Update `docs/system-architecture.md`

1. **Auth flow diagram** (line 175-176): Change `{username, password}` to `{email, password}`
2. **JWT payload** (line 483): Change `"sub": "john"` to `"sub": "john@example.com"`
3. **Registration flow** (line 258): Remove username uniqueness check step
4. **Data model** (line 408): Remove UNIQUE from username column
5. **SecurityContext** (line 456): Show email as principal

### Step 4: Update `docs/codebase-summary.md`

1. **UserService** methods list: Remove `existsByUsername`, `findByUserName`
2. **UserServiceImpl** description: Note `loadUserByUsername` queries by email
3. **JwtService** methods: Rename references to `generateTokenFromEmail`, `getEmailFromJwtToken`
4. **AuthController login**: Note accepts `LoginRequestDto` instead of `User`
5. **JwtResponseDto fields**: Add email field

## Todo List

- [ ] Update README login example (request + response)
- [ ] Update README register section (remove username uniqueness note)
- [ ] Update README auth flow description
- [ ] Update project-overview-pdr.md API contracts
- [ ] Update system-architecture.md auth flow diagrams
- [ ] Update system-architecture.md JWT payload example
- [ ] Update codebase-summary.md service/controller descriptions
- [ ] Review all docs for stale "username" references in auth context

## Success Criteria

- All API examples show email-based login
- No stale references to username-based authentication in docs
- Login response examples include email field
- Registration docs note duplicate usernames allowed

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|-----------|
| Stale docs confuse developers | Low | Grep all docs for "username" after update |

## Security Considerations

- None specific to documentation changes

## Next Steps

- After all phases complete: run full `mvn clean compile`, manual API test
