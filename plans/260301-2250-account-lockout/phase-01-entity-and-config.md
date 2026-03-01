# Phase 1: Entity & Config

## Context Links

- [User.java](/src/main/java/com/namnd/springjwt/model/User.java)
- [UserPrinciple.java](/src/main/java/com/namnd/springjwt/model/UserPrinciple.java)
- [application.yml](/src/main/resources/application.yml)

## Overview

- **Priority:** P1
- **Status:** pending
- **Description:** Add lockout fields to User entity, locked state to UserPrinciple, and config properties to application.yml

## Key Insights

- User entity uses Lombok @Data, so getters/setters auto-generated
- UserPrinciple.isAccountNonLocked() currently hardcoded to `true` -- needs to return actual lock state
- UserPrinciple.build() must compute lock state from User fields
- JPA ddl-auto=update will auto-add columns (nullable by default, which is correct)

## Requirements

### Functional
- User entity stores failed attempt count and lock timestamp
- UserPrinciple reflects whether account is currently locked
- Lock duration and max attempts configurable via application.yml

### Non-functional
- Existing users unaffected (failedAttempts defaults to 0, lockTime null = not locked)
- No breaking changes to existing API responses

## Related Code Files

### Modify
- `src/main/java/com/namnd/springjwt/model/User.java`
- `src/main/java/com/namnd/springjwt/model/UserPrinciple.java`
- `src/main/resources/application.yml`

## Implementation Steps

### Step 1: Add fields to User.java

Add after the `active` field:

```java
@Column(nullable = false)
private int failedAttempts = 0;

@Column(name = "lock_time")
private Date lockTime;
```

Import `java.util.Date` (already imported in other files in this project).

### Step 2: Update UserPrinciple.java

Add `accountNonLocked` field to constructor and static build method:

```java
private boolean accountNonLocked;

public UserPrinciple(Long id, String displayName, String email, String password,
                     boolean active, boolean accountNonLocked,
                     Collection<? extends GrantedAuthority> roles) {
    this.id = id;
    this.displayName = displayName;
    this.email = email;
    this.password = password;
    this.active = active;
    this.accountNonLocked = accountNonLocked;
    this.roles = roles;
}
```

Update `build()` method -- compute locked state:

```java
public static UserPrinciple build(User user) {
    List<GrantedAuthority> authorities = user.getRoles().stream()
            .map(role -> new SimpleGrantedAuthority(role.getName()))
            .collect(Collectors.toList());

    boolean accountNonLocked = user.getLockTime() == null;

    return new UserPrinciple(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getPassword(),
            user.isActive(),
            accountNonLocked,
            authorities);
}
```

Update `isAccountNonLocked()`:

```java
@Override
public boolean isAccountNonLocked() {
    return accountNonLocked;
}
```

### Step 3: Add config to application.yml

Under `namnd.app`:

```yaml
namnd:
  app:
    # ... existing props ...
    maxFailedAttempts: 5
    lockDurationMs: 900000  # 15 minutes
```

## Todo List

- [ ] Add failedAttempts and lockTime to User.java
- [ ] Add accountNonLocked field to UserPrinciple
- [ ] Update UserPrinciple constructor and build()
- [ ] Update isAccountNonLocked() to return field value
- [ ] Add config properties to application.yml
- [ ] Compile and verify no errors

## Success Criteria

- User entity has new columns after app startup
- UserPrinciple correctly reports lock state
- Config values readable via @Value injection
- All existing code still compiles

## Risk Assessment

- **Low risk**: additive changes only, no breaking modifications
- Existing users get failedAttempts=0, lockTime=null (not locked)

## Security Considerations

- Lock state derived from DB fields, not in-memory (survives restarts)
- No sensitive data exposed in new fields
