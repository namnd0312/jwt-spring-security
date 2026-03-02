# Phase 2: javax to jakarta Namespace Migration

## Context Links
- [Research: Spring Boot Migration](./research/researcher-01-spring-boot-2.6-to-3.4-java21-jakarta-security6-jjwt-migration.md)
- [Plan Overview](./plan.md)

## Overview
- **Priority:** P1 (blocks compilation)
- **Status:** pending
- **Effort:** 30min
- **Description:** Mechanical find-and-replace of all `javax.*` Jakarta EE imports to `jakarta.*` across 12 source files

## Key Insights
- Only Jakarta EE packages are renamed. JDK packages (`javax.sql.*`, `javax.crypto.*`) stay as-is
- This project has NO `javax.annotation.*` usage (no `@PostConstruct`)
- 4 categories of imports to change: persistence, servlet, validation, transaction
- Total: 12 files, ~20 import lines

## Requirements
- All `javax.persistence.*` -> `jakarta.persistence.*`
- All `javax.servlet.*` -> `jakarta.servlet.*`
- All `javax.validation.*` -> `jakarta.validation.*`
- All `javax.transaction.*` -> `jakarta.transaction.*`
- No `javax.*` Jakarta EE imports remain after this phase

## Architecture
No architectural changes. Pure import renaming.

## Related Code Files

### javax.persistence.* -> jakarta.persistence.* (5 files)
| File | Current Import |
|------|---------------|
| `src/main/java/com/namnd/springjwt/model/User.java` | `import javax.persistence.*;` |
| `src/main/java/com/namnd/springjwt/model/Role.java` | `import javax.persistence.*;` |
| `src/main/java/com/namnd/springjwt/model/RefreshToken.java` | `import javax.persistence.*;` |
| `src/main/java/com/namnd/springjwt/model/PasswordResetToken.java` | `import javax.persistence.*;` |
| `src/main/java/com/namnd/springjwt/model/ActivationToken.java` | `import javax.persistence.*;` |

### javax.servlet.* -> jakarta.servlet.* (4 files)
| File | Current Imports |
|------|----------------|
| `src/main/java/com/namnd/springjwt/config/filter/JwtAuthenticationFilter.java` | `javax.servlet.FilterChain`, `javax.servlet.ServletException`, `javax.servlet.http.HttpServletRequest`, `javax.servlet.http.HttpServletResponse` |
| `src/main/java/com/namnd/springjwt/config/custom/CustomAccesDeniedHandler.java` | `javax.servlet.ServletException`, `javax.servlet.http.HttpServletRequest`, `javax.servlet.http.HttpServletResponse` |
| `src/main/java/com/namnd/springjwt/util/CookieUtils.java` | `javax.servlet.http.Cookie`, `javax.servlet.http.HttpServletRequest`, `javax.servlet.http.HttpServletResponse` |
| `src/main/java/com/namnd/springjwt/controller/AuthController.java` | `javax.servlet.http.HttpServletRequest` |

### javax.validation.* -> jakarta.validation.* (2 files)
| File | Current Imports |
|------|----------------|
| `src/main/java/com/namnd/springjwt/controller/AuthController.java` | `javax.validation.Valid` |
| `src/main/java/com/namnd/springjwt/dto/LoginRequestDto.java` | `javax.validation.constraints.Email`, `javax.validation.constraints.NotBlank` |

### javax.transaction.* -> jakarta.transaction.* (1 file)
| File | Current Import |
|------|---------------|
| `src/main/java/com/namnd/springjwt/service/impl/AccountLockServiceImpl.java` | `javax.transaction.Transactional` |

## Implementation Steps

### 1. Entity models (5 files) — change `javax.persistence` to `jakarta.persistence`

**User.java** (line 6):
```java
// OLD
import javax.persistence.*;
// NEW
import jakarta.persistence.*;
```

**Role.java** (line 5):
```java
// OLD
import javax.persistence.*;
// NEW
import jakarta.persistence.*;
```

**RefreshToken.java** (line 5):
```java
// OLD
import javax.persistence.*;
// NEW
import jakarta.persistence.*;
```

**PasswordResetToken.java** (line 5):
```java
// OLD
import javax.persistence.*;
// NEW
import jakarta.persistence.*;
```

**ActivationToken.java** (line 5):
```java
// OLD
import javax.persistence.*;
// NEW
import jakarta.persistence.*;
```

### 2. Servlet files (4 files) — change `javax.servlet` to `jakarta.servlet`

**JwtAuthenticationFilter.java** (lines 13-16):
```java
// OLD
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

// NEW
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
```

**CustomAccesDeniedHandler.java** (lines 6-8):
```java
// OLD
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

// NEW
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
```

**CookieUtils.java** (lines 3-5):
```java
// OLD
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

// NEW
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
```

**AuthController.java** (lines 23, 25):
```java
// OLD
import javax.validation.Valid;
...
import javax.servlet.http.HttpServletRequest;

// NEW
import jakarta.validation.Valid;
...
import jakarta.servlet.http.HttpServletRequest;
```

### 3. Validation files (1 additional file beyond AuthController)

**LoginRequestDto.java** (lines 5-6):
```java
// OLD
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;

// NEW
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
```

### 4. Transaction file (1 file)

**AccountLockServiceImpl.java** (line 11):
```java
// OLD
import javax.transaction.Transactional;

// NEW
import jakarta.transaction.Transactional;
```

## Todo List
- [ ] Update 5 entity models: User, Role, RefreshToken, PasswordResetToken, ActivationToken
- [ ] Update JwtAuthenticationFilter.java (4 imports)
- [ ] Update CustomAccesDeniedHandler.java (3 imports)
- [ ] Update CookieUtils.java (3 imports)
- [ ] Update AuthController.java (2 imports: validation + servlet)
- [ ] Update LoginRequestDto.java (2 imports)
- [ ] Update AccountLockServiceImpl.java (1 import)
- [ ] Verify: `grep -r "import javax\." src/main/` returns zero Jakarta EE results

## Success Criteria
- Zero `javax.persistence`, `javax.servlet`, `javax.validation`, `javax.transaction` imports in `src/main/`
- Only `javax.crypto.*`, `javax.sql.*`, or other JDK packages remain (if any)

## Risk Assessment
- **Very low risk**: mechanical text replacement, no logic changes
- Typo risk: use find-and-replace, not manual editing

## Security Considerations
None. No behavioral changes.

## Next Steps
Proceed to Phase 3 (Spring Security 6.x migration).
