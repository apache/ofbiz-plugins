<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

---
name: manage-cache-and-performance
description: Manage OFBiz caching strategies using UtilCache and Entity Engine caching to optimize application performance.
---

# Skill: Manage Cache and Performance

## Goal
To properly implement and configure caching in OFBiz using `UtilCache` and Entity Engine caching, optimizing performance and reducing database load without introducing memory leaks or stale data.

## Triggers
- When the user asks to improve database query latency.
- When configuring service `<service ... use-cache="true">`.
- When designing entity lookups that are frequently read but rarely updated.
- When configuring `cache.properties` for OFBiz system performance.

## Rules & Procedures

### 1. Caching with `UtilCache`
`UtilCache` is the primary caching utility in OFBiz. It supports LRU (Least Recently Used) eviction, expiration based on time, and soft references.

**Pattern: Programmatic Caching**
```java
// Get or create a cache instance
UtilCache<String, GenericValue> myCache = UtilCache.getOrCreateUtilCache("my.custom.cache", 1000, 1000, 3600000, false);

// Putting a value in cache
myCache.put("myKey", myValue);

// Retrieving from cache
GenericValue cachedValue = myCache.get("myKey");

// Removing from cache
myCache.remove("myKey");
```

### 2. Cache Configuration (`cache.properties`)
Caches are configured in `framework/base/config/cache.properties`. *(See Anti-Pattern 7: Soft References as Primary Strategy)*
```properties
# Set max entries (0 for unlimited)
my.custom.cache.maxSize=5000

# Set expire time in milliseconds (e.g., 1 hour = 3600000)
my.custom.cache.expireTime=3600000

# Use soft references (allows GC to reclaim memory if needed)
my.custom.cache.useSoftReference=true
```

### 3. Entity Engine Caching
OFBiz automatically caches entity lookups if configured.
- **Declarative Caching** in `entitymodel.xml`:
  ```xml
  <entity entity-name="MyEntity" package-name="..." use-cache="true">
  ```
- **Programmatic Caching (Delegator)**:
  ```java
  GenericValue val = EntityQuery.use(delegator).from("Product").where("productId", "10000").cache(true).queryOne();
  List<GenericValue> list = EntityQuery.use(delegator).from("ProductCategory").cache(true).queryList();
  ```
*Note: `delegator.findOne(..., true)` hits the shared Entity Engine cache, whereas `UtilCache` is a general-purpose application-level cache.*

### 4. Service Results Caching
Services can cache their results automatically based on input parameters.
In `services.xml`:
```xml
<service name="calculateComplexPrice" engine="java" use-cache="true">
```

### 5. Cache Key Design Rules
- **Good cache keys**: Have bounded cardinality, are deterministic and repeatable, reflect real reuse patterns.
- **Bad cache keys**: Include timestamps, random values, or high-cardinality identifiers without limits. *(See Anti-Pattern 3: Cache Explosion)*

### 6. Cache Invalidation Strategy
When external systems or direct SQL modify the database, or when you bypass OFBiz entity triggers, the cache must be cleared manually.
- **Key-based removal** (`myCache.remove(cacheKey);`) is the absolute preferred invalidation approach for granular performance control.
- Use `delegator.clearCacheLine("EntityName")` to clear the entity cache programmatically.
- Use `UtilCache.clearCache("my.custom.cache")` for application caches.

**Cache invalidation must be triggered by data writes (store/commit), not data reads (find)**. Use EECA (`store`) or SECA (`commit`) to invalidate caches after data is committed. *(See the [manage-eca](../manage-eca/SKILL.md) skill, Good Pattern 1, Anti-Pattern 2, and Anti-Pattern 5).*

## Guardrails

### 1. When NOT to Cache
- Frequently updated entities (high write / low read ratio).
- User-specific or session-specific data. *(See Anti-Pattern 4: User/Session Data in Global Cache)*
- Security-sensitive data (permissions, auth state).
- Transaction-dependent data that must reflect real-time DB state. *(See Anti-Pattern 6: Treating Cache as Primary Storage)*
- Data used for financial posting or accounting correctness.

### 1b. Query Efficiency Before Caching
- Before adding cache, first remove avoidable query overhead:
- Use `.queryCount()` for existence checks instead of `queryList()`/`queryOne()`.
- Push date-effective filtering into the database with `.filterByDate()` or `.filterByDate("fromField", "thruField")` instead of filtering fetched rows in memory.
- Use `.select(...)` to avoid loading wide rows when only a few fields are needed.

### 2. Transaction and Async Stale Data Risks
- **Uncommitted Data**: The Entity Engine cache may return uncommitted data within an active transaction. Relying on cache inside a transaction can produce confusing behavior and phantom reads.
- **Async Services**: Async services run concurrently in new transactions. If an async service depends on cached data from the main transaction before it commits, it will see stale data.

### 3. Distributed Data Services (DDS)
For multi-node setups, ensure cache clearing is synchronized (usually via RMI or JMS in OFBiz). *Assume a single-node setup unless explicitly requested, and do not overly engineer distributed event clearing otherwise.*

## Examples: Good Patterns vs Anti-Patterns

### 1. Write-Triggered Invalidation (✅ GOOD)
```xml
<!-- ✅ GOOD PATTERN: Invalidating cache on a data write -->
<eca entity="Product" operation="store" event="return">
  <action service="clearProductCache" mode="sync"/>
</eca>
```

### 2. Read-Triggered Invalidation (🚨 ANTI-PATTERN)
```xml
<!-- 🚨 ANTI-PATTERN: Invalidating cache on a find operation -->
<eca entity="Product" operation="find" event="return">
  <action service="clearProductCache" mode="sync"/>
</eca>
```

### 3. Cache Explosion (🚨 ANTI-PATTERN)
**NEVER** construct cache keys using high-cardinality unbound variables like timestamps or pure UUIDs.
```java
// 🚨 ANTI-PATTERN: Unbounded key cardinality
myCache.put(userId + "_" + orderId + "_" + System.currentTimeMillis(), value);
```
**Why this is dangerous**: This creates a theoretically infinite number of unique keys. It is a memory leak disguised as a cache. The cache immediately fills up to its `maxSize`, aggressively thrashes via LRU eviction, and consumes massive JVM memory overhead.

### 4. User/Session Data in Global Cache (🚨 ANTI-PATTERN)
```java
// 🚨 ANTI-PATTERN: Storing user-specific data in a global cache
UtilCache<String, Object> cache = UtilCache.getUtilCache("pricing.cache");
cache.put(userLoginId, calculatedPrice);
```
**Why this is dangerous**: 
- Data leaks between users.
- Wrong prices / permissions served to the wrong session.
- Memory grows infinitely with the number of users.
- No automatic session cleanup.
**Correct Alternative**: Use `request` scope, `session` attributes, or service context.

### 5. Caching without Invalidation (🚨 ANTI-PATTERN)
```java
// 🚨 ANTI-PATTERN: Caching without an invalidation strategy
GenericValue product = productCache.get(productId);
if (product == null) {
    product = EntityQuery.use(delegator).from("Product").where(key).cache(true).queryOne();
    productCache.put(productId, product);
}
// No invalidation anywhere
```
**Why this is dangerous**: 
- The cache becomes permanently stale.
- The UI shows old values indefinitely.
- Fixes or data updates don't show up until a full system restart.
**Correct Alternative**: Always write an EECA on `store` or a SECA on `commit` to clear the cache when the underlying data changes.

### 6. Treating Cache as Primary Storage (🚨 ANTI-PATTERN)
```java
// 🚨 ANTI-PATTERN: Treating cache as primary storage
orderCache.put(orderId, orderData);
Order order = orderCache.get(orderId); // DB never consulted
```
**Why this is dangerous**: 
- Data is volatile and will be lost if the server/JVM restarts or memory clears.
- It bypasses database constraints and transactions entirely.
**Correct Alternative**: Always write to the DB first. Use cache strictly to avoid repeated reads. Cache misses MUST fall back to the DB.

### 7. Soft References as Primary Strategy (🚨 ANTI-PATTERN)
```properties
# 🚨 ANTI-PATTERN: Using soft references to hide memory pressure
my.custom.cache.maxSize=0
my.custom.cache.useSoftReference=true
```
**Why this is dangerous**: 
- **Unpredictable eviction**: Soft references are cleared only under GC pressure. The cache may work fine for days, then get wiped suddenly during GC.
- **Performance cliffs**: Cache hit ratio collapses instantly. DB load spikes. System appears to “randomly slow down”.
- **Hides real problems**: It masks memory leaks, oversized caches, bad cache keys, or missing invalidation. It delays failures, it doesn’t fix them.
**Correct Alternative ✅**: Always define `maxSize` and `expireTime`. Use `useSoftReference=true` only as a secondary safety net after sizing correctly.
```properties
# ✅ Correct approach
my.custom.cache.maxSize=5000
my.custom.cache.expireTime=3600000
my.custom.cache.useSoftReference=true