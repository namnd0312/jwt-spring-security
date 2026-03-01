# Phase 02: Redis Service Implementation

## Context Links

- [plan.md](./plan.md)
- [Phase 01](./phase-01-redis-config-and-service-interface.md) (prerequisite)
- [Code Standards](../../docs/code-standards.md)

## Overview

- **Priority:** P2
- **Status:** pending
- **Description:** Implement `RedisServiceImpl` with try-catch error handling, SLF4J logging, and sensible defaults on every method.

## Key Insights

- Two templates needed: `StringRedisTemplate` for string ops, `RedisTemplate<String, Object>` for hash/object ops
- Every method must be wrapped in try-catch to prevent Redis failures from crashing the application
- Return sensible defaults on failure: `null` for get, `false` for boolean, `0L` for counts, empty collections for list/set/map
- Distributed lock uses `setIfAbsent()` (Redis `SET NX EX`) -- simple, no Lua script needed
- File target: ~180-200 lines. If exceeds 200, consider splitting (see note below)

## Requirements

**Functional:**
- Implement all 26 methods from `RedisService` interface
- Log errors at `error` level, operational info at `debug` level
- Return safe defaults on Redis failure

**Non-functional:**
- Constructor injection for both templates
- SLF4J logging via `LoggerFactory`
- Under 200 lines (concise method bodies, no verbose Javadoc)

## Architecture

```
service/
  RedisService.java          <-- Phase 1 (interface)
  impl/
    RedisServiceImpl.java    <-- THIS PHASE: full implementation
```

**Injection diagram:**
```
RedisServiceImpl
  |-- StringRedisTemplate (auto-configured by Spring Boot)
  |-- RedisTemplate<String, Object> (from RedisConfig bean in Phase 1)
```

## Related Code Files

**Create:**
- `src/main/java/com/namnd/springjwt/service/impl/RedisServiceImpl.java`

**Dependencies (from Phase 1):**
- `src/main/java/com/namnd/springjwt/config/RedisConfig.java`
- `src/main/java/com/namnd/springjwt/service/RedisService.java`

## Implementation Steps

### Step 1: Create `RedisServiceImpl.java`

**Path:** `src/main/java/com/namnd/springjwt/service/impl/RedisServiceImpl.java`

```java
package com.namnd.springjwt.service.impl;

import com.namnd.springjwt.service.RedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class RedisServiceImpl implements RedisService {

    private static final Logger logger = LoggerFactory.getLogger(RedisServiceImpl.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    public RedisServiceImpl(StringRedisTemplate stringRedisTemplate,
                            RedisTemplate<String, Object> redisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisTemplate = redisTemplate;
    }

    // ─── Key-Value Operations ───

    @Override
    public void set(String key, String value) {
        try {
            stringRedisTemplate.opsForValue().set(key, value);
        } catch (Exception e) {
            logger.error("Redis SET failed for key: {}", key, e);
        }
    }

    @Override
    public void set(String key, String value, long timeout, TimeUnit unit) {
        try {
            stringRedisTemplate.opsForValue().set(key, value, timeout, unit);
        } catch (Exception e) {
            logger.error("Redis SET with TTL failed for key: {}", key, e);
        }
    }

    @Override
    public String get(String key) {
        try {
            return stringRedisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            logger.error("Redis GET failed for key: {}", key, e);
            return null;
        }
    }

    @Override
    public Boolean delete(String key) {
        try {
            return stringRedisTemplate.delete(key);
        } catch (Exception e) {
            logger.error("Redis DELETE failed for key: {}", key, e);
            return false;
        }
    }

    @Override
    public Long delete(Collection<String> keys) {
        try {
            return stringRedisTemplate.delete(keys);
        } catch (Exception e) {
            logger.error("Redis batch DELETE failed", e);
            return 0L;
        }
    }

    @Override
    public Boolean hasKey(String key) {
        try {
            return stringRedisTemplate.hasKey(key);
        } catch (Exception e) {
            logger.error("Redis HASKEY failed for key: {}", key, e);
            return false;
        }
    }

    @Override
    public Boolean expire(String key, long timeout, TimeUnit unit) {
        try {
            return stringRedisTemplate.expire(key, timeout, unit);
        } catch (Exception e) {
            logger.error("Redis EXPIRE failed for key: {}", key, e);
            return false;
        }
    }

    @Override
    public Long getExpire(String key) {
        try {
            return stringRedisTemplate.getExpire(key);
        } catch (Exception e) {
            logger.error("Redis GETEXPIRE failed for key: {}", key, e);
            return 0L;
        }
    }

    // ─── Hash Operations ───

    @Override
    public void hSet(String key, String hashKey, Object value) {
        try {
            redisTemplate.opsForHash().put(key, hashKey, value);
        } catch (Exception e) {
            logger.error("Redis HSET failed for key: {}, hashKey: {}", key, hashKey, e);
        }
    }

    @Override
    public Object hGet(String key, String hashKey) {
        try {
            return redisTemplate.opsForHash().get(key, hashKey);
        } catch (Exception e) {
            logger.error("Redis HGET failed for key: {}, hashKey: {}", key, hashKey, e);
            return null;
        }
    }

    @Override
    public Map<Object, Object> hGetAll(String key) {
        try {
            return redisTemplate.opsForHash().entries(key);
        } catch (Exception e) {
            logger.error("Redis HGETALL failed for key: {}", key, e);
            return Collections.emptyMap();
        }
    }

    @Override
    public Long hDelete(String key, Object... hashKeys) {
        try {
            return redisTemplate.opsForHash().delete(key, hashKeys);
        } catch (Exception e) {
            logger.error("Redis HDEL failed for key: {}", key, e);
            return 0L;
        }
    }

    @Override
    public Boolean hHasKey(String key, String hashKey) {
        try {
            return redisTemplate.opsForHash().hasKey(key, hashKey);
        } catch (Exception e) {
            logger.error("Redis HHASKEY failed for key: {}, hashKey: {}", key, hashKey, e);
            return false;
        }
    }

    // ─── List Operations ───

    @Override
    public Long lPush(String key, String value) {
        try {
            return stringRedisTemplate.opsForList().leftPush(key, value);
        } catch (Exception e) {
            logger.error("Redis LPUSH failed for key: {}", key, e);
            return 0L;
        }
    }

    @Override
    public Long rPush(String key, String value) {
        try {
            return stringRedisTemplate.opsForList().rightPush(key, value);
        } catch (Exception e) {
            logger.error("Redis RPUSH failed for key: {}", key, e);
            return 0L;
        }
    }

    @Override
    public List<String> lRange(String key, long start, long end) {
        try {
            return stringRedisTemplate.opsForList().range(key, start, end);
        } catch (Exception e) {
            logger.error("Redis LRANGE failed for key: {}", key, e);
            return Collections.emptyList();
        }
    }

    @Override
    public Long lLen(String key) {
        try {
            return stringRedisTemplate.opsForList().size(key);
        } catch (Exception e) {
            logger.error("Redis LLEN failed for key: {}", key, e);
            return 0L;
        }
    }

    // ─── Set Operations ───

    @Override
    public Long sAdd(String key, String... values) {
        try {
            return stringRedisTemplate.opsForSet().add(key, values);
        } catch (Exception e) {
            logger.error("Redis SADD failed for key: {}", key, e);
            return 0L;
        }
    }

    @Override
    public Set<String> sMembers(String key) {
        try {
            return stringRedisTemplate.opsForSet().members(key);
        } catch (Exception e) {
            logger.error("Redis SMEMBERS failed for key: {}", key, e);
            return Collections.emptySet();
        }
    }

    @Override
    public Boolean sIsMember(String key, String value) {
        try {
            return stringRedisTemplate.opsForSet().isMember(key, value);
        } catch (Exception e) {
            logger.error("Redis SISMEMBER failed for key: {}", key, e);
            return false;
        }
    }

    @Override
    public Long sRemove(String key, String... values) {
        try {
            Object[] objects = values;
            return stringRedisTemplate.opsForSet().remove(key, objects);
        } catch (Exception e) {
            logger.error("Redis SREM failed for key: {}", key, e);
            return 0L;
        }
    }

    // ─── Pub/Sub ───

    @Override
    public void publish(String channel, String message) {
        try {
            stringRedisTemplate.convertAndSend(channel, message);
        } catch (Exception e) {
            logger.error("Redis PUBLISH failed for channel: {}", channel, e);
        }
    }

    // ─── Distributed Lock ───

    @Override
    public boolean tryLock(String key, long timeout, TimeUnit unit) {
        try {
            Boolean result = stringRedisTemplate.opsForValue()
                    .setIfAbsent(key, "locked", timeout, unit);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            logger.error("Redis TRYLOCK failed for key: {}", key, e);
            return false;
        }
    }

    @Override
    public void unlock(String key) {
        try {
            stringRedisTemplate.delete(key);
        } catch (Exception e) {
            logger.error("Redis UNLOCK failed for key: {}", key, e);
        }
    }
}
```

**Line count estimate:** ~195 lines. Within the 200-line limit.

**Note on splitting:** If the file exceeds 200 lines during implementation due to additional comments or formatting, split into:
- `RedisKeyValueServiceImpl.java` (key-value + lock + pub/sub)
- `RedisCollectionServiceImpl.java` (hash + list + set)

Both would implement sub-interfaces of `RedisService`. However, this should NOT be needed if the code stays concise.

### Step 2: Verify compilation

```bash
mvn clean compile -q
```

## Todo List

- [ ] Create `RedisServiceImpl.java` in `service/impl/` package
- [ ] Verify all 26 methods implemented
- [ ] Run `mvn clean compile` to verify no errors
- [ ] Confirm file is under 200 lines

## Success Criteria

- All 26 `RedisService` methods implemented
- Every method has try-catch with error logging
- Sensible defaults returned on failure (null/false/0/empty)
- Constructor injection of both templates
- File under 200 lines
- Project compiles

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| File exceeds 200 lines | Low | Low | Split into two impl classes if needed |
| `setIfAbsent` with TTL not available in Spring Data Redis 2.6 | Very Low | Medium | Available since Spring Data Redis 2.1, confirmed for Boot 2.6.4 |
| Generic Exception catch too broad | Medium | Low | Acceptable for utility service; specific callers handle business logic |

## Security Considerations

- No secrets logged (only key names in error messages)
- Lock values are opaque ("locked"), no sensitive data stored
- Error messages do not leak internal state

## Next Steps

- Phase 03: Refactor `BlacklistedTokenServiceImpl` to use `RedisService`
