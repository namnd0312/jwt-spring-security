# Phase 4: JJWT 0.12.6 Migration

## Context Links
- [Research: JJWT & Docker](./research/researcher-02-jjwt-docker-migration.md)
- [Plan Overview](./plan.md)
- Current file: `src/main/java/com/namnd/springjwt/service/JwtService.java` (86 lines)

## Overview
- **Priority:** P1 (JwtService will not compile with JJWT 0.12.6)
- **Status:** pending
- **Effort:** 1h
- **Description:** Full rewrite of JwtService.java for JJWT 0.12.6 API. Update signing key in application.yml.

## Key Insights
- JJWT 0.12.x removed all `setXxx()` claim setters — use `subject()`, `id()`, `issuedAt()`, `expiration()`
- `signWith(SignatureAlgorithm.HS512, String)` removed — use `signWith(SecretKey)`
- `Jwts.parser()` now returns `JwtParserBuilder` directly (no separate `parserBuilder()`)
- `parseClaimsJws()` -> `parseSignedClaims()`; `.getBody()` -> `.getPayload()`
- `io.jsonwebtoken.SignatureException` removed — use `io.jsonwebtoken.security.SecurityException`
- Current secret `bezKoderSecretKey` is only 17 chars — HS512 requires >= 64 bytes (88+ chars Base64). JJWT 0.12.x throws `WeakKeyException` at runtime
- Must use `Decoders.BASE64.decode()` + `Keys.hmacShaKeyFor()` pattern

## Requirements
- All token generation uses new JJWT 0.12.6 builder API
- All token parsing uses new JJWT 0.12.6 parser API
- Signing key decoded from Base64 into `SecretKey` object
- `application.yml` jwtSecret updated to Base64-encoded 64-byte key
- No `SignatureException` import — use `SecurityException`
- Existing method signatures preserved (no changes to callers)

## Architecture
No architectural changes. Same service interface, same method signatures. Internal implementation updated.

## Related Code Files
| File | Action |
|------|--------|
| `src/main/java/com/namnd/springjwt/service/JwtService.java` | **Full rewrite** |
| `src/main/resources/application.yml` | Update `namnd.app.jwtSecret` value |

## Implementation Steps

### 1. Replace JwtService.java entirely

**Current code (OLD — delete entirely):**
```java
package com.namnd.springjwt.service;

import com.namnd.springjwt.model.UserPrinciple;
import io.jsonwebtoken.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;

@Component
public class JwtService {
    @Value("${namnd.app.jwtSecret}")
    private String SECRET_KEY;
    @Value("${namnd.app.jwtExpiration}")
    private long EXPIRE_TIME;
    // ... all methods use deprecated API ...
}
```

**New code (replace entire file):**
```java
package com.namnd.springjwt.service;

import com.namnd.springjwt.model.UserPrinciple;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtService {

    @Value("${namnd.app.jwtSecret}")
    private String SECRET_KEY;

    @Value("${namnd.app.jwtExpiration}")
    private long EXPIRE_TIME;

    private static final Logger logger = LoggerFactory.getLogger(JwtService.class);

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(SECRET_KEY);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateTokenLogin(Authentication authentication) {
        UserPrinciple userPrinciple = (UserPrinciple) authentication.getPrincipal();

        return Jwts.builder()
                .subject(userPrinciple.getUsername())
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRE_TIME))
                .signWith(getSigningKey())
                .compact();
    }

    public String generateTokenFromEmail(String email) {
        return Jwts.builder()
                .subject(email)
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRE_TIME))
                .signWith(getSigningKey())
                .compact();
    }

    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(authToken);
            return true;
        } catch (SecurityException e) {
            logger.error("Invalid JWT signature -> Message: {} ", e.getMessage());
        } catch (MalformedJwtException e) {
            logger.error("Invalid JWT token -> Message: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            logger.error("Expired JWT token -> Message: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            logger.error("Unsupported JWT token -> Message: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.error("JWT claims string is empty -> Message: {}", e.getMessage());
        }
        return false;
    }

    public String getEmailFromJwtToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public String getJtiFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getId();
    }

    public Date getExpirationFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration();
    }
}
```

### 2. Update jwtSecret in application.yml

```yaml
# OLD — 17 chars, too short for HS512, not Base64-encoded
namnd:
  app:
    jwtSecret: bezKoderSecretKey

# NEW — 64 bytes Base64-encoded (88 chars), valid for HS512
namnd:
  app:
    jwtSecret: ${JWT_SECRET:kBJb8FEOvTCWEcfZB6RLMM5BLoI8p0FWOWEu7FSZBYn+ItVi7mHRePYCvum5Ic6l4M2nFw+kdl8du99Bxnb7zg==}
```

**Note:** Wrapped in env var override `${JWT_SECRET:...}` so production can inject its own key.

### Key API changes summary

| Old (0.9.0) | New (0.12.6) |
|-------------|--------------|
| `Jwts.builder().setSubject(s)` | `Jwts.builder().subject(s)` |
| `.setId(s)` | `.id(s)` |
| `.setIssuedAt(d)` | `.issuedAt(d)` |
| `.setExpiration(d)` | `.expiration(d)` |
| `.signWith(SignatureAlgorithm.HS512, stringKey)` | `.signWith(SecretKey)` |
| `Jwts.parser().setSigningKey(str)` | `Jwts.parser().verifyWith(SecretKey).build()` |
| `.parseClaimsJws(token)` | `.parseSignedClaims(token)` |
| `.getBody()` | `.getPayload()` |
| `catch (SignatureException e)` | `catch (SecurityException e)` |
| `import io.jsonwebtoken.SignatureException` | `import io.jsonwebtoken.security.SecurityException` |

### 3. Note on `javax.crypto.SecretKey`

The import `import javax.crypto.SecretKey;` is a **JDK** package, NOT Jakarta EE. It stays as `javax.crypto.*` — do NOT change it to `jakarta.crypto.*`.

## Todo List
- [ ] Replace entire JwtService.java with new code
- [ ] Update `namnd.app.jwtSecret` in application.yml with Base64-encoded 64-byte key
- [ ] Wrap jwtSecret in env var override: `${JWT_SECRET:defaultKey}`
- [ ] Verify `javax.crypto.SecretKey` import (JDK, not Jakarta — stays as javax)
- [ ] Verify no `io.jsonwebtoken.SignatureException` import remains

## Success Criteria
- JwtService.java compiles without errors
- No reference to deprecated JJWT APIs (`setSubject`, `setSigningKey`, `parseClaimsJws`, `getBody`)
- No `io.jsonwebtoken.SignatureException` import
- jwtSecret in application.yml is Base64-encoded and >= 88 chars

## Risk Assessment
- **High risk**: incorrect key encoding causes all JWT operations to fail at runtime
- **Token invalidation**: changing the secret key invalidates all existing JWTs — acceptable for dev, production should plan for token rotation
- Algorithm inference: JJWT 0.12.x infers HS512 from 64-byte key — verified correct

## Security Considerations
- HS512 key now enforced to be minimum 64 bytes (512 bits) — old 17-char key was cryptographically weak
- Secret wrapped in env var for production override — never hardcode production secrets
- `e.getMessage()` logged instead of full exception object `e` to avoid leaking stack traces

## Next Steps
Proceed to Phase 5 (Application Config & Properties).
