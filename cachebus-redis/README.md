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

# cachebus-redis

Lets OFBiz servers that share a database clear each other's caches. The plugin carries the framework's cache
invalidation bus over Redis or Valkey publish/subscribe, so when one server changes data, the other servers drop the
cached copies instead of serving them until they expire.

What travels over the bus is small: after a transaction commits, the server publishes the entity name and the primary key
values of what changed, never entity values, plus the removals and clears of caches that declare `policy="invalidate"`.
Every server keeps its own local caches. Without this plugin, or with `cachebus.redis.uri` empty, a server behaves as
before.

## Requirements

* A Redis or Valkey server that every OFBiz server of the cluster can reach. It is tested with Valkey 8 and works with
  Redis 6 or later; the plugin needs the RESP3 protocol, so Redis 5 and earlier do not work. Nothing from Redis or Valkey
  ships with OFBiz; you run the server yourself. Only the [Lettuce](https://github.com/redis/lettuce) client (MIT
  License) is bundled with the plugin.
* The framework side, which is part of OFBiz: a delegator has to select `EntityCacheInvalidator`, see below.

## Enabling it

1. Set the server in `config/cachebus-redis.properties`; the tuning values and the delegator are `<property>` entries of
   the `cachebus-redis-container` element in `ofbiz-component.xml` and rarely need a change:

   ```properties
   cachebus.redis.uri=rediss://redis.example.com:6380
   cachebus.redis.cluster=prod-eu
   cachebus.redis.password-file=/run/secrets/redis_password
   ```

2. Select `EntityCacheInvalidator` on every delegator whose entity changes the servers should share, in
   `framework/entity/config/entityengine.xml`:

   ```xml
   <delegator name="default" entity-model-reader="main" entity-group-reader="main" entity-eca-reader="main"
              distributed-cache-clear-enabled="true"
              distributed-cache-clear-class-name="org.apache.ofbiz.entity.cache.EntityCacheInvalidator">
   ```

   JMS stays the default unless a delegator selects `EntityCacheInvalidator`; it is deprecated and will be removed after
   26.11.

3. Start the servers. Each one logs `cachebus-redis: cache invalidation bus installed, channel ...` and, once connected,
   `Redis cache invalidation bus connected to channel ...`. Check them on the status page described below.

Caches other than the entity caches are local unless their `<cache>` declaration says `policy="invalidate"`; the
framework does so for the caches of database data it ships. Declare it for your own caches that hold data read from the
database and that your code removes or clears when the data changes.

If `cachebus.redis.uri` is empty the plugin does nothing and logs that. A wrong setting (a malformed URI, a number out of
range, a password source that is named but missing) stops OFBiz from starting, with the property name in the message.

## Properties

The settings that ship with the plugin are `<property>` children of the `cachebus-redis-container` element in
`ofbiz-component.xml`:

| Property | Default | Meaning |
|---|---|---|
| `delegator-name` | `default` | The delegator used to read the `SystemProperty` overrides described below. |
| `heartbeat-ms` | `5000` | How often both connections are checked with a `PING`. 100 to 600000. |
| `heartbeat-timeout-ms` | `3000` | How long a `PING` may take before the connections are reopened. 10 to 5000, and less than `heartbeat-ms`; 5000 is the timeout of every command, which fails a slower `PING` first. |
| `max-payload-bytes` | `1048576` | A received message larger than this is dropped and counted as a gap. 1024 to 67108864. |
| `tombstone-ms` | `2000` | How long a received key invalidation blocks plain puts of that key. 0 to 60000. |

The deployment settings are in `config/cachebus-redis.properties`; every one is optional except the URI.

| Property | Default | Meaning |
|---|---|---|
| `cachebus.redis.uri` | empty | `redis://host:6379`, `rediss://host:6380` for TLS, or `redis-sentinel://host:26379/0#mymaster` for Sentinel (`rediss-sentinel://` with TLS); several Sentinels are `redis-sentinel://h1:26379,h2:26379/0#mymaster`. The scheme must be lowercase. Empty disables the plugin. |
| `cachebus.redis.username` | empty | ACL user name; empty uses the server's default user. |
| `cachebus.redis.password-env` | empty | Name of an environment variable that holds the password. |
| `cachebus.redis.password-file` | empty | File that holds the password; one trailing line break is ignored. |
| `cachebus.redis.password` | empty | The password itself, or `LOOKUP(key)` to read it from the configured secret provider. |
| `cachebus.redis.cluster` | `test` | Cluster name, see below. Letters, digits, `.`, `_` and `-`. |

Any key of the properties file except `password-env` and `password-file` (see below) can be overridden in the
database of the `delegator-name` delegator with a `SystemProperty` record, with `systemResourceId="cachebus-redis"` and
`systemPropertyId` set to the key. For example, to name the cluster in the database instead of the file:

```xml
<SystemProperty systemResourceId="cachebus-redis" systemPropertyId="cachebus.redis.cluster"
                systemPropertyValue="prod-eu"/>
```

The settings are read once when OFBiz starts, so a change needs a restart.

### Password

The password comes from the first of these that is set, in this order: `password-env`, then `password-file`, then
`password`. If `password-env` or `password-file` is set but the variable is unset or empty, or the file cannot be read or
is empty, OFBiz does not start; it does not fall back to the next source. Prefer the environment variable or the file
over writing the password into the properties file. The URI is not logged with its credentials. A password with leading
or trailing spaces must come from `password-env` or `password-file`, because the properties loader trims values.

`password-env` and `password-file` are read from the properties file only, never from a `SystemProperty` record: they
name a file or an environment variable of the server, and a record that could name any of them would let whoever can
edit records send its content to a server of their choice. Do not put the password itself in a `SystemProperty` record: it would be stored in the database in plain text and be
visible in Webtools. Use `password-env`, `password-file` or `LOOKUP(key)`. `LOOKUP(key)` is resolved only in the
properties file, not in a `SystemProperty` value; a `SystemProperty` record can instead name the secret's key in its
`systemPropertyLookup` field.

Docker, with a secret file:

```yaml
services:
  ofbiz:
    secrets:
      - redis_password
secrets:
  redis_password:
    file: ./redis_password.txt
```

with `cachebus.redis.password-file=/run/secrets/redis_password` in the properties file. Kubernetes, with a secret mounted
as a file (a fragment of the pod spec):

```yaml
volumes:
  - name: redis
    secret:
      secretName: ofbiz-redis
containers:
  - name: ofbiz
    volumeMounts:
      - name: redis
        mountPath: /run/secrets
        readOnly: true
```

with `cachebus.redis.password-file=/run/secrets/password`, where `password` is the key in the secret. Or as an
environment variable:

```yaml
env:
  - name: REDIS_PASSWORD
    valueFrom:
      secretKeyRef:
        name: ofbiz-redis
        key: password
```

with `cachebus.redis.password-env=REDIS_PASSWORD`. Mount or bake in a `cachebus-redis.properties` that names the
variable or file, not the password.

## Cluster name

Servers that use the same cluster name on the same Redis or Valkey server clear each other's caches; servers with
different names ignore each other. The default is `test`, and the name is logged at start-up. Staging and production
that share a Redis server must not share a name, so name every production cluster on purpose.

## Security

The bus has no authentication of its own beyond the Redis or Valkey login, and a forged message can clear any
distributed cache, which makes the server slow and loads the database. So:

* The Redis or Valkey server must not be reachable by untrusted clients. Keep it on a private network.
* Use TLS: a `rediss://` (or `rediss-sentinel://`) URI encrypts the traffic and checks the server certificate against
  the JVM's trust store. Mutual TLS (a client certificate) is not supported yet and may be added later.
* Use a dedicated ACL user that may do nothing but use the channel, instead of the default user:

  ```
  ACL SETUSER ofbiz-cachebus reset on >change-me resetchannels &ofbiz:cachebus:* +subscribe +publish +ping +client|setname +client|setinfo +hello
  ```

  and set `cachebus.redis.username=ofbiz-cachebus`. This is the complete set of commands the bus sends: `SUBSCRIBE`,
  `PUBLISH`, `PING`, `HELLO` (which carries the login), and `CLIENT SETNAME` and `CLIENT SETINFO` (client name and
  version, which the server may ignore if refused). The channel is `ofbiz:cachebus:` followed by the cluster name. With
  Sentinel, the Sentinel nodes need their own access for the lookup of the master. Pub/sub ignores the database
  number, so use database 0 in the URI; a non-zero database makes the client send `SELECT`, which would need `+select`.
  `reset` in the command makes it safe to run again, as it drops any permission granted earlier.

## When the bus loses messages

A server that cannot be sure it received everything clears every distributed cache and every entity cache once, and
counts a gap. That happens when:

* the bus connects, the first time included: messages published before the subscription is confirmed are gone, and
  the caches filled while OFBiz was starting, before the bus was installed, may hold data other servers changed
  meanwhile. At start-up the caches are nearly empty, so this clear costs little;
* the connection to Redis or Valkey is lost and restored (messages published meanwhile are gone);
* a heartbeat `PING` on either connection is not answered within `heartbeat-timeout-ms`, as in a network partition, a
  paused server or a connection the network silently dropped; both connections are then reopened, and the clear
  follows once the subscription is back;
* a received message is larger than `max-payload-bytes`;
* received messages arrive faster than they can be applied and more than 16 MB of them (or `max-payload-bytes`, if
  larger) are waiting; the waiting ones are dropped.

Reconnecting is automatic, starting at one second and doubling up to 30 seconds. The bus uses two connections, one to
subscribe and one to publish, a five second command timeout, TCP keepalive, and a queue of at most 10 000 waiting
commands, so a server that does not answer cannot exhaust the heap. Nothing waits for the network on the thread of
the application: publishing is asynchronous. Received messages are applied to the caches on a thread of their own, in
the order they arrive, so that a long clear does not hold up the connection's network thread and its heartbeat.

A server that could not publish an invalidation, for instance because it alone is cut off from Redis while the others
are not, announces it on a second channel, `ofbiz:cachebus:<cluster>:gap`, once it is connected again (at the latest on
the next heartbeat). The other servers then clear their distributed caches and count a gap; the ACL rule above
(`&ofbiz:cachebus:*`) already covers that channel. A server may also announce right after it starts, for invalidations
made between the installation of the bus and its first connection; the other servers then clear their caches once.
The failures are counted as "Publish failures" on the status page. The announcement cannot reach the others while the
publishing server is still cut off, so monitor that counter, and fix the connection of a server whose count grows. The
pending announcement is held only in memory, so if that server is restarted while cut off, clear the caches of the
other servers or restart them.

Changes made earlier in start-up, before the plugin's container starts, are not published at all, because no bus is
installed yet; the container starts after the web and service containers, so web requests and jobs may already run.
Other servers keep their cached copies of such data until it changes again or the cache entry expires, so avoid
scheduling jobs that change cached data to run in the first moments after a start.

The same holds at shutdown, in reverse: containers stop in the reverse order they started, so the plugin's container
stops first and removes the bus while the web and service containers are still finishing their requests and jobs.
Changes those make are not published, and publishes still in flight when the connections close may be lost. Before
stopping a server, stop sending it traffic and let its running jobs finish.

## Logging

Lettuce and Netty log every Redis command, the heartbeat's PING included, at debug level, and OFBiz's root logger accepts
every level. When the bus is installed, the plugin therefore logs `io.lettuce` and `io.netty` at warning level and above.
To see their debug output, declare a logger for either name in `framework/base/config/log4j2.xml`; a declared logger is
left as configured. A log4j2 reconfiguration (a `monitorInterval` reload) drops the plugin's loggers until OFBiz
restarts.

## Status page

`https://host:8443/cachebus`, for users with the `OFBTOOLS_VIEW` permission, shows whether the bus is installed and
connected, the cluster name and channel, the messages published and received, the publish failures, the gaps and the
time of the last message. It never shows the password or the URI.
