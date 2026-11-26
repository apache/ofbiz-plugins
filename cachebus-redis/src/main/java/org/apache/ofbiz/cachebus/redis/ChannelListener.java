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
 *******************************************************************************/
package org.apache.ofbiz.cachebus.redis;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.ofbiz.base.util.Debug;

import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;

/**
 * Receives the channel's payloads on Lettuce's I/O thread and only routes them: the receiver is called on the
 * connection's delivery thread, so the I/O thread is never held up. It also receives the gap channel, where another
 * server's announcement is reported as a gap and the bus's own announcement is ignored. A listener belongs to one
 * pub/sub connection, whose first subscription confirmation answers the initial subscribe; any later one is Lettuce
 * resubscribing after a reconnect, when messages published while the connection was down are lost.
 */
final class ChannelListener extends RedisPubSubAdapter<String, String> {

    private static final String MODULE = ChannelListener.class.getName();

    private final CacheBusRedisSettings settings;
    private final String busId;
    private final RedisChannelConnection owner;
    private final StatefulRedisPubSubConnection<String, String> connection;
    private final AtomicBoolean subscribedOnce = new AtomicBoolean();

    /**
     * Creates the listener of one pub/sub connection.
     * @param settings the bus's settings, for the channel names and the payload limit
     * @param busId the id the owning bus announces lost publishes with, ignored on the gap channel
     * @param owner the connection the messages are delivered through
     * @param connection the pub/sub connection this listener listens to
     */
    ChannelListener(CacheBusRedisSettings settings, String busId, RedisChannelConnection owner,
            StatefulRedisPubSubConnection<String, String> connection) {
        this.settings = settings;
        this.busId = busId;
        this.owner = owner;
        this.connection = connection;
    }

    @Override
    public void message(String channel, String payload) {
        try {
            if (settings.gapChannel().equals(channel)) {
                if (!busId.equals(payload)) {
                    String shownId = payload != null && payload.length() == 36 && payload.matches("[0-9a-f-]+")
                            ? payload : "(unrecognised id)";
                    Debug.logInfo("Redis cache invalidation bus: server " + shownId + " lost published invalidations; "
                            + "clearing distributed caches", MODULE);
                    owner.reportGap();
                }
                return;
            }
            if (!settings.channel().equals(channel)) {
                return;
            }
            int bytes = RedisInvalidationBus.byteLength(payload);
            if (bytes > settings.maxPayloadBytes()) {
                Debug.logWarning("Redis cache invalidation bus dropped a received payload of " + bytes
                        + " bytes, larger than max-payload-bytes (" + settings.maxPayloadBytes() + ")", MODULE);
                owner.reportGap();
                return;
            }
            owner.deliverPayload(payload, bytes);
        } catch (RuntimeException e) {
            Debug.logError(e, "Redis cache invalidation bus: error handling a received payload", MODULE);
        }
    }

    @Override
    public void subscribed(String channel, long count) {
        try {
            // a late confirmation from a connection already replaced, after a failed heartbeat, says nothing of the current one
            if (!owner.isSubscriber(connection)) {
                return;
            }
            if (settings.channel().equals(channel) && subscribedOnce.getAndSet(true)) {
                owner.subscriberState(true);
                Debug.logInfo("Redis cache invalidation bus resubscribed to channel " + channel
                        + " after a reconnect; distributed caches are cleared", MODULE);
                owner.reportGap();
            }
        } catch (RuntimeException e) {
            Debug.logError(e, "Redis cache invalidation bus: error handling a resubscription", MODULE);
        }
    }
}
