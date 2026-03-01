# Phase 1: Activation Token Entity & Repository

## Context Links

- [PasswordResetToken pattern](/src/main/java/com/namnd/springjwt/model/PasswordResetToken.java)
- [PasswordResetTokenRepository](/src/main/java/com/namnd/springjwt/repository/PasswordResetTokenRepository.java)
- [User entity](/src/main/java/com/namnd/springjwt/model/User.java)

## Overview

- **Priority:** P1
- **Status:** complete
- **Description:** Add `active` field to User entity; create ActivationToken entity and repository mirroring PasswordResetToken pattern.

## Key Insights

- PasswordResetToken has: id, token (unique), expiryDate, user (ManyToOne LAZY), used boolean -- reuse exact same structure
- User entity uses `@Data` (Lombok) + `@Entity` with `GenerationType.IDENTITY`
- Hibernate ddl-auto is `update` so column addition is automatic
- New users must default to `active = false`

## Requirements

### Functional
- User entity has `active` boolean field, default `false`
- ActivationToken entity stores: id, token (UUID), expiryDate, user FK, used flag
- Repository provides `findByToken(String)` query method

### Non-Functional
- Entity follows existing naming conventions (snake_case columns, plural table name)
- Under 35 lines per entity file (matching existing pattern)

## Architecture

```
User.java
  + private boolean active = false;  // new field

ActivationToken.java  (mirrors PasswordResetToken.java exactly)
  - id (BIGSERIAL, PK)
  - token (VARCHAR, UNIQUE, NOT NULL)
  - expiryDate (TIMESTAMP, NOT NULL)
  - user (ManyToOne LAZY, FK to users)
  - used (boolean, default false)

ActivationTokenRepository.java  (mirrors PasswordResetTokenRepository.java)
  - findByToken(String): Optional<ActivationToken>
```

## Related Code Files

### Files to Modify
- `src/main/java/com/namnd/springjwt/model/User.java` -- add `active` field

### Files to Create
- `src/main/java/com/namnd/springjwt/model/ActivationToken.java`
- `src/main/java/com/namnd/springjwt/repository/ActivationTokenRepository.java`

## Implementation Steps

### 1. Add `active` field to User.java

```java
// Add after existing fields in User.java
@Column(nullable = false)
private boolean active = false;
```

### 2. Create ActivationToken.java

Mirror `PasswordResetToken.java` exactly:

```java
package com.namnd.springjwt.model;

import lombok.Data;
import javax.persistence.*;
import java.util.Date;

@Data
@Entity
@Table(name = "activation_tokens")
public class ActivationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "expiry_date", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date expiryDate;

    @Column(nullable = false)
    private boolean used = false;
}
```

### 3. Create ActivationTokenRepository.java

```java
package com.namnd.springjwt.repository;

import com.namnd.springjwt.model.ActivationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ActivationTokenRepository extends JpaRepository<ActivationToken, Long> {
    Optional<ActivationToken> findByToken(String token);
}
```

## Todo List

- [x] Add `active` boolean field to `User.java` (default false)
- [x] Create `ActivationToken.java` entity mirroring PasswordResetToken
- [x] Create `ActivationTokenRepository.java` interface
- [x] Run `mvn compile` to verify no errors
- [ ] **BLOCKED:** Add SQL migration script for existing users (`UPDATE users SET active = true WHERE active = false`)

## Success Criteria

- `mvn compile` passes
- User entity has `active` field defaulting to `false`
- ActivationToken entity mirrors PasswordResetToken structure
- Repository interface compiles with `findByToken` method

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| Existing users in DB get `active=false` after migration | High -- all existing users locked out | Run SQL: `UPDATE users SET active = true WHERE active = false` after deployment |
| Hibernate auto-DDL adds nullable column | Low | `@Column(nullable = false)` + default value handles it |

## Security Considerations

- Token stored as UUID string (sufficient entropy for activation)
- `used` flag prevents token reuse
- Expiry date prevents stale tokens

## Next Steps

- Phase 2: ActivationService implementation uses these entities
