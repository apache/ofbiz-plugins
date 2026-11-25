/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ofbiz.cachebus.redis

import java.text.DateFormat

// Status page of the Redis/Valkey cache invalidation bus. Never shows the password or the URI.
Map<String, Object> busStatus = [:]
String yes = context.uiLabelMap.CacheBusYes
String no = context.uiLabelMap.CacheBusNo

RedisInvalidationBus bus = RedisInvalidationBus.current()
busStatus.installed = bus ? yes : context.uiLabelMap.CacheBusNotInstalled

CacheBusRedisSettings settings = bus?.settings()
busStatus.cluster = settings?.cluster() ?: ''
busStatus.channel = settings?.channel() ?: ''

Map<String, Object> snapshot = bus?.statistics()?.snapshot() ?: [:]
busStatus.connected = snapshot.connected ? yes : no
busStatus.published = snapshot.published ?: 0
busStatus.publishFailures = snapshot.publishFailures ?: 0
busStatus.received = snapshot.received ?: 0
busStatus.receivedBytes = snapshot.receivedBytes ?: 0
busStatus.gaps = snapshot.gaps ?: 0
long lastMessageMillis = snapshot.lastMessageMillis ?: 0L
busStatus.lastMessage = lastMessageMillis > 0 ?
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, locale).format(new Date(lastMessageMillis)) :
        context.uiLabelMap.CacheBusNever
context.busStatus = busStatus
