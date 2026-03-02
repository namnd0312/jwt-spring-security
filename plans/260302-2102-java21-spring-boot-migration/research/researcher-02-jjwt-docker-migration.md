# Research: JJWT Migration & Docker Updates for Java 21

**Date:** 2026-03-02
**Scope:** JJWT 0.9.0 → 0.12.6, Docker base images, Lombok Java 21 compat

---

## 1. JJWT: Latest Version

**Latest stable: `0.12.6`** (released June 2024 on Maven Central)

Artifacts split into three:
```xml
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-api</artifactId>
  <version>0.12.6</version>
</dependency>
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-impl</artifactId>
  <version>0.12.6</version>
  <scope>runtime</scope>
</dependency>
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-jackson</artifactId>
  <version>0.12.6</version>
  <scope>runtime</scope>
</dependency>
```
Old single artifact `io.jsonwebtoken:jjwt:0.9.0` is removed — must replace with all three above.

---

## 2. JJWT API Breaking Changes (0.9.0 → 0.12.6)

### 2a. Token Building

```java
// OLD (0.9.0) — String key, setXxx setters, SignatureAlgorithm enum
Jwts.builder()
    .setSubject(email)
    .setId(UUID.randomUUID().toString())
    .setIssuedAt(new Date())
    .setExpiration(new Date(System.currentTimeMillis() + EXPIRE_TIME))
    .signWith(SignatureAlgorithm.HS512, SECRET_KEY)  // String key
    .compact();

// NEW (0.12.x) — SecretKey, fluent setters without "set" prefix
private SecretKey getSigningKey() {
    byte[] keyBytes = Decoders.BASE64.decode(SECRET_KEY);
    return Keys.hmacShaKeyFor(keyBytes);
}

Jwts.builder()
    .subject(email)
    .id(UUID.randomUUID().toString())
    .issuedAt(new Date())
    .expiration(new Date(System.currentTimeMillis() + EXPIRE_TIME))
    .signWith(getSigningKey())   // SecretKey — algorithm inferred from key length
    .compact();
```

**Key change:** `signWith(SignatureAlgorithm, String)` removed. Must pass `SecretKey`.
The `SECRET_KEY` in `application.yml` must be Base64-encoded and ≥64 bytes for HS512.

### 2b. Token Parsing

```java
// OLD (0.9.0)
Jwts.parser()
    .setSigningKey(SECRET_KEY)     // String key
    .parseClaimsJws(token)
    .getBody()
    .getSubject();

// NEW (0.12.x) — parserBuilder() gone, Jwts.parser() now returns JwtParserBuilder
Jwts.parser()
    .verifyWith(getSigningKey())   // SecretKey
    .build()
    .parseSignedClaims(token)
    .getPayload()
    .getSubject();
```

Changes summary:
| Old | New |
|-----|-----|
| `Jwts.parser().setSigningKey(str)` | `Jwts.parser().verifyWith(SecretKey)` |
| `parseClaimsJws(token)` | `parseSignedClaims(token)` |
| `.getBody()` | `.getPayload()` |
| `Jwts.parserBuilder()` | `Jwts.parser()` (returns builder directly now) |

### 2c. Claims Setters Renamed

| Old (0.9.0) | New (0.12.x) |
|-------------|--------------|
| `.setSubject(s)` | `.subject(s)` |
| `.setId(s)` | `.id(s)` |
| `.setIssuedAt(d)` | `.issuedAt(d)` |
| `.setExpiration(d)` | `.expiration(d)` |
| `.setIssuer(s)` | `.issuer(s)` |

### 2d. SignatureException Import Change

`io.jsonwebtoken.SignatureException` is removed in 0.12.x.
Replace with `io.jsonwebtoken.security.SecurityException` or catch `JwtException` (parent).

Current `JwtService.java` catches `SignatureException` — must update:
```java
// OLD
import io.jsonwebtoken.SignatureException;
catch (SignatureException e) { ... }

// NEW
import io.jsonwebtoken.security.SecurityException;
catch (SecurityException e) { ... }
```

### 2e. Key Handling Pattern for Existing String Secret

```java
// application.yml: namnd.app.jwtSecret must be Base64-encoded, ≥88 chars for HS512
// (512 bits = 64 bytes → Base64 = ~88 chars)

@Value("${namnd.app.jwtSecret}")
private String SECRET_KEY;  // Base64-encoded string

private SecretKey getSigningKey() {
    byte[] keyBytes = Decoders.BASE64.decode(SECRET_KEY);
    return Keys.hmacShaKeyFor(keyBytes);  // from io.jsonwebtoken.security.Keys
}
```

---

## 3. Docker Base Image — Java 21

**Current Dockerfile:** `FROM openjdk:11` (deprecated image)

**Recommended replacements:**

| Tag | Size | Use case |
|-----|------|----------|
| `eclipse-temurin:21-jre-alpine` | ~80MB | Production (smallest) |
| `eclipse-temurin:21-jdk-alpine` | ~200MB | Build stage |
| `eclipse-temurin:21-jre` (Ubuntu) | ~200MB | If Alpine glibc issues arise |

Multi-stage build pattern (recommended):
```dockerfile
# Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build
COPY target/spring-jwt.jar app.jar

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /opt/app
COPY --from=builder /build/app.jar spring-jwt.jar
ENTRYPOINT ["java", "-jar", "spring-jwt.jar"]
```

Single-stage (simpler, acceptable):
```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /opt/app
COPY target/spring-jwt.jar spring-jwt.jar
ENTRYPOINT ["java", "-jar", "spring-jwt.jar"]
```

`openjdk:11` is deprecated on Docker Hub — `eclipse-temurin` is the official successor (Adoptium/Eclipse Foundation).

---

## 4. PostgreSQL Docker Image

**Current:** `postgres:13.1-alpine`
**Target:** `postgres:16-alpine`

- `postgres:16-alpine` is stable and widely used in production
- Alpine variant based on Alpine Linux; significantly smaller than default Debian variant
- No breaking API changes between Pg13 and Pg16 for standard JPA/JDBC usage
- Data volume path in current `docker-compose.yml` uses a non-standard host path — should use named volume instead:

```yaml
# OLD (host path, fragile)
volumes:
  - /Users/admin/Desktop/DEV/DOCKER/docker-volumes:/var/lib/docker/volumes/postgres/_data

# NEW (named volume, portable)
volumes:
  - postgres-data:/var/lib/postgresql/data

volumes:
  postgres-data:
```

- `scram-sha-256` auth is default from Pg14+; existing `POSTGRES_USER/PASSWORD` env vars work unchanged.
- `version: "3.7"` top-level key in docker-compose is deprecated — can be removed (Compose V2 ignores it).

---

## 5. Lombok 1.18.30 + Java 21 Compatibility

- Lombok `1.18.30` introduced Java 21 support — **compatible**
- No issues with standard annotations (`@Data`, `@Builder`, `@Slf4j`, `@RequiredArgsConstructor`, etc.)
- Known edge case: `onX` syntax with STS4 IDE (not a runtime issue)
- Spring Boot 3.4.x BOM manages Lombok; explicit version `1.18.30` is fine but can be omitted to let BOM manage it (Spring Boot 3.4.x uses `1.18.34`)
- Annotation processor must be in `maven-compiler-plugin` `annotationProcessorPaths` if `lombok` is not on compile classpath — current pom.xml uses `<optional>true</optional>` which is correct

**Recommendation:** Remove explicit `<version>1.18.30</version>` and let Spring Boot 3.4.x BOM pick `1.18.34` or latest managed.

---

## 6. Affected Files Summary

| File | Change needed |
|------|--------------|
| `pom.xml` | Replace `jjwt:0.9.0` with 3 artifacts; update `java.version`; update Spring Boot parent |
| `JwtService.java` | Full API rewrite (see sections 2a–2d) |
| `Dockerfile` | Replace `openjdk:11` with `eclipse-temurin:21-jre-alpine` |
| `docker-compose.yml` | `postgres:13.1-alpine` → `16-alpine`; fix volume; remove `version:` |
| `application.yml` | Ensure `jwtSecret` is Base64-encoded and ≥88 chars |

---

## Unresolved Questions

1. Is `namnd.app.jwtSecret` in `application.yml` already Base64-encoded with ≥88 chars? If not, a new secret must be generated — this invalidates all existing tokens.
2. Should multi-stage Docker build be used (requires Maven in container) or pre-built JAR copy (current pattern)? Current pattern copies pre-built JAR — single-stage JRE image is sufficient.
3. Any custom Spring Security config (`SecurityConfig.java`) needing Spring Security 6.x migration alongside Spring Boot 3.4.x?

---

## Sources

- [JJWT GitHub Releases](https://github.com/jwtk/jjwt/releases)
- [Maven Central: jjwt-api 0.12.6](https://repo1.maven.org/maven2/io/jsonwebtoken/jjwt-api/0.12.6/)
- [Maven Central: jjwt-impl 0.12.6](https://mvnrepository.com/artifact/io.jsonwebtoken/jjwt-impl/0.12.6)
- [eclipse-temurin Docker Hub](https://hub.docker.com/_/eclipse-temurin)
- [eclipse-temurin:21-jre-alpine](https://hub.docker.com/layers/library/eclipse-temurin/21-jre-alpine/images/sha256-068047c992cc82c7443792937328dc9b5d7261477b8141900425b1d3538f312a)
- [postgres:16-alpine Docker Hub](https://hub.docker.com/layers/library/postgres/16-alpine/images/sha256-b89a4e92591810eac1fbce6107485d7c6b9449df51c1ccfcfed514a7fdd69955)
- [Lombok Changelog](https://projectlombok.org/changelog)
- [Baeldung: JWT Deprecated setSigningKey](https://www.baeldung.com/jwt-deprecated-setsigningkey)
- [JJWT 0.12 Discussion #955](https://github.com/jwtk/jjwt/discussions/955)
