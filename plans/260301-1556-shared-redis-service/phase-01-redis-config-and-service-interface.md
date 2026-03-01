# Phase 01: Redis Configuration and Service Interface

## Context Links

- [plan.md](./plan.md)
- [BlacklistedTokenServiceImpl.java](../../src/main/java/com/namnd/springjwt/service/impl/BlacklistedTokenServiceImpl.java) (current Redis consumer)
- [application.yml](../../src/main/resources/application.yml) (Redis connection config)
- [Code Standards](../../docs/code-standards.md)

## Overview

- **Priority:** P2
- **Status:** pending
- **Description:** Create `RedisConfig` bean configuration and `RedisService` interface defining all Redis operations.

## Key Insights

- Spring Boot auto-configures `StringRedisTemplate` already (used by `BlacklistedTokenServiceImpl`)
- We need an additional `RedisTemplate<String, Object>` bean with Jackson serializer for hash/object operations
- The interface groups methods by Redis data structure for clarity
- Java 8 compatibility required (no var, no stream improvements from 9+)

## Requirements

**Functional:**
- `RedisConfig` provides a `RedisTemplate<String, Object>` bean with Jackson2JsonRedisSerializer
- `RedisService` interface declares all operations: key-value, hash, list, set, pub/sub, distributed lock

**Non-functional:**
- Constructor injection pattern
- Under 200 lines per file
- Follow existing package conventions (`config/`, `service/`)

## Architecture

```
config/
  RedisConfig.java          <-- NEW: @Configuration, RedisTemplate bean
  RedisKeyPrefix.java       <-- NEW: centralized Redis key prefix constants

service/
  RedisService.java         <-- NEW: interface with all Redis operations
  impl/
    RedisServiceImpl.java   <-- Phase 2
```

Spring Boot auto-configures `RedisConnectionFactory` (Lettuce) from `application.yml` `spring.redis.*` properties. `RedisConfig` uses that factory to build a custom `RedisTemplate<String, Object>`.

## Related Code Files

**Create:**
- `src/main/java/com/namnd/springjwt/config/RedisConfig.java`
- `src/main/java/com/namnd/springjwt/config/RedisKeyPrefix.java`
- `src/main/java/com/namnd/springjwt/service/RedisService.java`

**No modifications to existing files in this phase.**

## Implementation Steps

### Step 1: Create `RedisConfig.java`

**Path:** `src/main/java/com/namnd/springjwt/config/RedisConfig.java`

```java
package com.namnd.springjwt.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(Object.class));
        template.setHashValueSerializer(new Jackson2JsonRedisSerializer<>(Object.class));
        template.afterPropertiesSet();
        return template;
    }
}
```

**Notes:**
- `RedisConnectionFactory` auto-configured by Spring Boot from `spring.redis.*` in `application.yml`
- `StringRedisSerializer` for keys ensures human-readable keys in Redis
- `Jackson2JsonRedisSerializer` for values enables JSON serialization of objects
- The auto-configured `StringRedisTemplate` remains untouched (Spring Boot handles it)

### Step 2: Create `RedisKeyPrefix.java` constants

**Path:** `src/main/java/com/namnd/springjwt/config/RedisKeyPrefix.java`

```java
package com.namnd.springjwt.config;

/**
 * Centralized Redis key prefix constants.
 * All Redis keys must use a prefix from this class to avoid collisions
 * and keep key naming consistent across the project.
 */
public final class RedisKeyPrefix {

    private RedisKeyPrefix() {} // prevent instantiation

    public static final String BLACKLIST = "blacklist:";
    public static final String LOCK = "lock:";
    // Add future prefixes here (e.g., CACHE, SESSION, RATE_LIMIT)
}
```

### Step 3: Create `RedisService.java` interface

**Path:** `src/main/java/com/namnd/springjwt/service/RedisService.java`

```java
package com.namnd.springjwt.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public interface RedisService {

    // Key-Value operations
    void set(String key, String value);
    void set(String key, String value, long timeout, TimeUnit unit);
    String get(String key);
    Boolean delete(String key);
    Long delete(Collection<String> keys);
    Boolean hasKey(String key);
    Boolean expire(String key, long timeout, TimeUnit unit);
    Long getExpire(String key);

    // Hash operations
    void hSet(String key, String hashKey, Object value);
    Object hGet(String key, String hashKey);
    Map<Object, Object> hGetAll(String key);
    Long hDelete(String key, Object... hashKeys);
    Boolean hHasKey(String key, String hashKey);

    // List operations
    Long lPush(String key, String value);
    Long rPush(String key, String value);
    List<String> lRange(String key, long start, long end);
    Long lLen(String key);

    // Set operations
    Long sAdd(String key, String... values);
    Set<String> sMembers(String key);
    Boolean sIsMember(String key, String value);
    Long sRemove(String key, String... values);

    // Pub/Sub
    void publish(String channel, String message);

    // Distributed Lock
    boolean tryLock(String key, long timeout, TimeUnit unit);
    void unlock(String key);
}
```

### Step 4: Verify compilation

```bash
mvn clean compile -q
```

## Todo List

- [ ] Create `RedisConfig.java` in `config/` package
- [ ] Create `RedisService.java` interface in `service/` package
- [ ] Run `mvn clean compile` to verify no errors

## Success Criteria

- `RedisConfig` registered as `@Configuration` with `RedisTemplate<String, Object>` bean
- `RedisService` interface contains all 26 method signatures grouped by data structure
- Project compiles without errors

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Jackson serializer conflicts with existing beans | Low | Medium | Custom bean name or `@Primary` if needed |
| `RedisConnectionFactory` not available | Low | High | Already configured in `application.yml` and working for `StringRedisTemplate` |

## Security Considerations

- No secrets introduced in this phase
- Redis connection credentials managed via existing `application.yml` env vars (`REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`)

## Next Steps

- Phase 02: Implement `RedisServiceImpl` with error handling and logging
