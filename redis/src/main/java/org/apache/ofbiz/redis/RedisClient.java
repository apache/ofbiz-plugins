/*******************************************************************************
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
package org.apache.ofbiz.redis;

import java.io.IOException;
import java.net.URL;

import org.apache.ofbiz.base.location.FlexibleLocation;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilValidate;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonReactiveClient;
import org.redisson.config.Config;

public class RedisClient {
    
    public static final String module = RedisClient.class.getName();
    protected static final String REDIS_CONFIG_JSON = "component://redis/config/redis.json";
    private static RedissonClient redisSyncClient = null;
    private static RedissonReactiveClient redisASyncClient = null;
    
    public static RedissonClient getSyncInstance() {
        if (UtilValidate.isEmpty(redisSyncClient)) {
            try {
                URL fileUrl = FlexibleLocation.resolveLocation(REDIS_CONFIG_JSON);
                Debug.logInfo("Redis initializing configuration from: " + fileUrl, module);
                Config config = Config.fromJSON(fileUrl);
                redisSyncClient = Redisson.create(config);
            } catch (IOException e) {
                Debug.logError("Error while initializing configuration of Redis from " + REDIS_CONFIG_JSON, module);
                Debug.logError(e, module);
            }
        }
        return redisSyncClient;
    }
    
    public static RedissonReactiveClient getASyncInstance() {
        if (UtilValidate.isEmpty(redisASyncClient)) {
            try {
                URL fileUrl = FlexibleLocation.resolveLocation(REDIS_CONFIG_JSON);
                Debug.logInfo("Redis initializing configuration from: " + fileUrl, module);
                Config config = Config.fromJSON(fileUrl);
                redisASyncClient = Redisson.createReactive(config);
            } catch (IOException e) {
                Debug.logError("Error while initializing configuration of Redis from " + REDIS_CONFIG_JSON, module);
                Debug.logError(e, module);
            }
        }
        return redisASyncClient;
    }
}