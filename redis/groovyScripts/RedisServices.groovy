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

import java.security.Key

import org.apache.ofbiz.base.crypto.DesCrypt
import org.apache.ofbiz.base.lang.JSON
import org.apache.ofbiz.base.util.Debug
import org.apache.ofbiz.base.util.UtilValidate
import org.apache.ofbiz.entity.util.EntityUtilProperties
import org.apache.ofbiz.redis.RedisClient
import org.apache.ofbiz.service.ServiceUtil
import org.redisson.api.RBinaryStream
import org.redisson.api.RedissonClient

public Map<String,Object> getRedisInfo(){
  Map<String, Object> result = ServiceUtil.returnSuccess();
  def redisKey = (String) context.get("redisKey");
  def redisValue = [:];
  RedissonClient syncClient = RedisClient.getSyncInstance();
  RBinaryStream stream = syncClient.getBinaryStream(redisKey);
  String password = EntityUtilProperties.getPropertyValue("redis", "redis.encrypt.password", delegator);
  try {
    if(UtilValidate.isNotEmpty(stream)){
      def bytes = stream.get();
      if(bytes != null){
        Key decryptKey = DesCrypt.getDesKey(password.getBytes("UTF-8"));
        byte[] decrypts = DesCrypt.decrypt(decryptKey, bytes);
        String content = new String(decrypts, "UTF-8");
        if(UtilValidate.isNotEmpty(content)){
          redisValue = JSON.from(content).toObject(Map.class);
        }
      }
    }
  } catch (Exception e) {
    Debug.logError(e, module);
    return ServiceUtil.returnError(e.getMessage());
  }
  result.put("redisValue", redisValue);
  return result;
}

public Map<String,Object> setRedisInfo(){
  Map<String, Object> result = ServiceUtil.returnSuccess();
  def redisKey = (String) context.get("redisKey");
  def redisValue = (Map) context.get("redisValue");
  if(UtilValidate.isNotEmpty(redisValue)){
    Iterator iterator = redisValue.keySet().iterator();
    while(iterator.hasNext()){
      String k = iterator.next();
      if(UtilValidate.isEmpty(redisValue[k])){
        iterator.remove();
      }
    }
  }
  RedissonClient syncClient = RedisClient.getSyncInstance();
  RBinaryStream stream = syncClient.getBinaryStream(redisKey);
  String password = EntityUtilProperties.getPropertyValue("redis", "redis.encrypt.password", delegator);
  try {
    if(UtilValidate.isNotEmpty(stream)){
      def content = JSON.from(redisValue ?: "").toString();
      Key encryptKey = DesCrypt.getDesKey(password.getBytes("UTF-8"));
      byte[] encryptContent = DesCrypt.encrypt(encryptKey, content.getBytes("UTF-8"));
      stream.set(encryptContent);
    }
  } catch (Exception e) {
    Debug.logError(e, module);
    return ServiceUtil.returnError(e.getMessage());
  }
  return result;
}

public Map<String,Object> deleteRedisInfo(){
  Map<String, Object> result = ServiceUtil.returnSuccess();
  def redisKey = (String) context.get("redisKey");
  RedissonClient syncClient = RedisClient.getSyncInstance();
  RBinaryStream stream = syncClient.getBinaryStream(redisKey);
  try {
    if(UtilValidate.isNotEmpty(stream)){
      stream.delete();
    }
  } catch (Exception e) {
    Debug.logError(e, module);
    return ServiceUtil.returnError(e.getMessage());
  }
  return result;
}