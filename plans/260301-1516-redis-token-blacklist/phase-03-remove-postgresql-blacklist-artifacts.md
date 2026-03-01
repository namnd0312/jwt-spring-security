# Phase 3: Remove PostgreSQL Blacklist Artifacts

## Context Links

- [plan.md](./plan.md)
- [Phase 2](./phase-02-rewrite-blacklisted-token-service.md)
- [BlacklistedToken.java](/Users/admin/Desktop/DEV/BACK_END/jwt-spring-security/src/main/java/com/namnd/springjwt/model/BlacklistedToken.java)
- [BlacklistedTokenRepository.java](/Users/admin/Desktop/DEV/BACK_END/jwt-spring-security/src/main/java/com/namnd/springjwt/repository/BlacklistedTokenRepository.java)
- [TokenType.java](/Users/admin/Desktop/DEV/BACK_END/jwt-spring-security/src/main/java/com/namnd/springjwt/model/TokenType.java)

## Overview

- **Priority:** P2
- **Status:** pending
- **Description:** Delete JPA entity, repository, and enum files that are no longer needed after Redis migration. Verify no remaining references in codebase.

## Key Insights

- `BlacklistedToken.java` entity only used by `BlacklistedTokenRepository` and old `BlacklistedTokenServiceImpl`
- `BlacklistedTokenRepository.java` only used by old `BlacklistedTokenServiceImpl`
- `TokenType.java` enum only referenced by `BlacklistedToken.java` and old `BlacklistedTokenServiceImpl`
- No other files import these three classes (verified via grep)
- `blacklisted_tokens` PostgreSQL table: Hibernate `ddl-auto: update` won't drop it automatically; manual drop needed if desired

## Requirements

**Functional:**
- Remove dead code from codebase
- Project compiles without deleted files

**Non-functional:**
- Clean codebase, no orphan files
- Database table `blacklisted_tokens` documented for manual cleanup

## Architecture

No architectural changes. Simply removing unused data layer artifacts.

## Related Code Files

**Deleted:**
- `src/main/java/com/namnd/springjwt/model/BlacklistedToken.java`
- `src/main/java/com/namnd/springjwt/repository/BlacklistedTokenRepository.java`
- `src/main/java/com/namnd/springjwt/model/TokenType.java`

**Verified unchanged (no references to deleted files):**
- `JwtAuthenticationFilter.java` -- uses `BlacklistedTokenService` interface only
- `AuthController.java` -- uses `BlacklistedTokenService` interface only
- `JwtService.java` -- no blacklist references
- `SecurityConfig.java` -- no blacklist references

## Implementation Steps

### Step 1: Delete entity file

```bash
rm /Users/admin/Desktop/DEV/BACK_END/jwt-spring-security/src/main/java/com/namnd/springjwt/model/BlacklistedToken.java
```

### Step 2: Delete repository file

```bash
rm /Users/admin/Desktop/DEV/BACK_END/jwt-spring-security/src/main/java/com/namnd/springjwt/repository/BlacklistedTokenRepository.java
```

### Step 3: Delete TokenType enum

```bash
rm /Users/admin/Desktop/DEV/BACK_END/jwt-spring-security/src/main/java/com/namnd/springjwt/model/TokenType.java
```

### Step 4: Verify no remaining references

```bash
cd /Users/admin/Desktop/DEV/BACK_END/jwt-spring-security
grep -r "BlacklistedToken\|BlacklistedTokenRepository\|TokenType" src/main/java/ --include="*.java"
```

Expected: only `BlacklistedTokenService.java` and `BlacklistedTokenServiceImpl.java` (interface + implementation, no imports of deleted classes).

### Step 5: Compile check

```bash
mvn clean compile -q
```

### Step 6: (Optional) Drop PostgreSQL table

If cleaning up the database manually:

```sql
DROP TABLE IF EXISTS blacklisted_tokens;
```

Note: With `ddl-auto: update`, Hibernate won't auto-drop the table. It simply won't manage it anymore. The table will remain but unused. Safe to leave or drop manually.

## Todo List

- [ ] Delete `BlacklistedToken.java`
- [ ] Delete `BlacklistedTokenRepository.java`
- [ ] Delete `TokenType.java`
- [ ] Verify no compile errors referencing deleted files
- [ ] Run `mvn clean compile`
- [ ] (Optional) Drop `blacklisted_tokens` table from PostgreSQL

## Success Criteria

- All three files deleted from codebase
- `grep` for deleted class names returns zero results in source (except interface/impl references to `BlacklistedTokenService`)
- `mvn clean compile` passes
- No import statements referencing deleted classes remain

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| Missed reference to deleted class | Compile error | Grep verification in Step 4 catches this |
| Orphan DB table `blacklisted_tokens` | Wastes disk space | Document manual DROP; harmless if left |
| `TokenType` used elsewhere in future | Need to recreate | Currently only used by `BlacklistedToken`; YAGNI |

## Security Considerations

- No security impact from deleting unused files
- `blacklisted_tokens` table data becomes stale but harmless (tokens already expired)

## Next Steps

Proceed to [Phase 4: Verify and Test](./phase-04-verify-and-test.md)
