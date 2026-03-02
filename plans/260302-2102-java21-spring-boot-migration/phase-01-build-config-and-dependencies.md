# Phase 1: Build Config & Dependencies

## Context Links
- [Research: Spring Boot Migration](./research/researcher-01-spring-boot-2.6-to-3.4-java21-jakarta-security6-jjwt-migration.md)
- [Research: JJWT & Docker](./research/researcher-02-jjwt-docker-migration.md)
- [Plan Overview](./plan.md)

## Overview
- **Priority:** P1 (must be first — all other phases depend on correct dependency resolution)
- **Status:** pending
- **Effort:** 1h
- **Description:** Update pom.xml to Spring Boot 3.4.3, Java 21, JJWT 0.12.6 (3 artifacts), remove explicit Lombok version

## Key Insights
- Spring Boot 3.4.3 parent BOM manages Spring Security 6.4.x, Hibernate 6.x, Lombok 1.18.34
- JJWT single artifact `jjwt:0.9.0` must be replaced with 3 artifacts: `jjwt-api`, `jjwt-impl`, `jjwt-jackson`
- Lombok 1.18.30 explicit version can be removed; BOM picks 1.18.34 (Java 21 compatible)
- **VALIDATED**: Switch from WAR to JAR packaging — remove `spring-boot-starter-tomcat` (provided) and `<packaging>war</packaging>`

## Requirements
- pom.xml compiles with `mvn clean compile` after changes (will fail on javax imports — expected, fixed in Phase 2)
- No new dependencies beyond JJWT split and optional properties-migrator
- Switch packaging from WAR to JAR

## Architecture
Switch from WAR to JAR packaging. Remove `ServletInitializer.java` (no longer needed with embedded Tomcat).

## Related Code Files
| File | Action |
|------|--------|
| `pom.xml` | Modify |
| `src/main/java/com/namnd/springjwt/ServletInitializer.java` | **Delete** (WAR→JAR, no longer needed) |

## Implementation Steps

### 1. Update Spring Boot parent version
```xml
<!-- OLD -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>2.6.4</version>
    <relativePath/>
</parent>

<!-- NEW -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.4.3</version>
    <relativePath/>
</parent>
```

### 2. Update Java version property
```xml
<!-- OLD -->
<properties>
    <java.version>1.8</java.version>
</properties>

<!-- NEW -->
<properties>
    <java.version>21</java.version>
</properties>
```

### 3. Replace JJWT single artifact with 3 artifacts
```xml
<!-- REMOVE this -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt</artifactId>
    <version>0.9.0</version>
</dependency>

<!-- ADD these 3 -->
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

### 4. Remove explicit Lombok version (let BOM manage)
```xml
<!-- OLD -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>1.18.30</version>
    <optional>true</optional>
</dependency>

<!-- NEW -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>
```

### 5. Switch from WAR to JAR packaging
```xml
<!-- REMOVE packaging line (defaults to jar) or change to: -->
<packaging>jar</packaging>

<!-- REMOVE this dependency (no external Tomcat needed with JAR) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-tomcat</artifactId>
    <scope>provided</scope>
</dependency>
```

### 6. Delete ServletInitializer.java
Delete `src/main/java/com/namnd/springjwt/ServletInitializer.java` — WAR deployment initializer not needed for JAR packaging.

### 7. (Optional) Add properties-migrator temporarily
```xml
<!-- Add temporarily to detect deprecated properties at startup — remove after Phase 5 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-properties-migrator</artifactId>
    <scope>runtime</scope>
</dependency>
```

## Todo List
- [ ] Update Spring Boot parent to 3.4.3
- [ ] Update java.version to 21
- [ ] Replace JJWT 0.9.0 with 3 JJWT 0.12.6 artifacts
- [ ] Remove explicit Lombok version
- [ ] Switch packaging from WAR to JAR
- [ ] Remove spring-boot-starter-tomcat (provided scope)
- [ ] Delete ServletInitializer.java
- [ ] (Optional) Add spring-boot-properties-migrator
- [ ] Verify `mvn dependency:resolve` succeeds

## Success Criteria
- `mvn dependency:resolve` downloads all dependencies without errors
- `mvn dependency:tree` shows Spring Boot 3.4.3 artifacts, JJWT 0.12.6 artifacts
- No version conflicts in dependency tree

## Risk Assessment
- **Low risk**: straightforward version bumps in single file
- BOM conflict: unlikely since we removed explicit Lombok version
- JJWT version mismatch if only some artifacts updated — ensure all 3 are 0.12.6

## Security Considerations
- JJWT 0.12.6 enforces minimum key sizes (HS512 requires 64+ bytes) — actual key update in Phase 4
- Spring Boot 3.4.3 includes latest security patches

## Next Steps
Proceed to Phase 2 (javax -> jakarta namespace migration) immediately after.
