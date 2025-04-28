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
package org.apache.ofbiz.ecommerce.content

import org.apache.ofbiz.base.util.collections.LifoSet

entityName = 'ContentDataResourceView'
lookupCaches = session.getAttribute('lookupCaches')

if (!lookupCaches) {
    lookupCaches = [:]
    session.setAttribute('lookupCaches', lookupCaches)
}
lifoSet = lookupCaches[entityName]

if (!lifoSet) {
    lifoSet = new LifoSet(10)
    lookupCaches.ContentAssocDataResourceViewFrom = lifoSet
}

sz = lifoSet.size()
contentIdKey = null
mrvList = []

lifoSet.each { pk0 ->
    pk = pk0.getPrimaryKey()
    gv = from(pk.getEntityName()).where(pk).cache().queryOne()
    if (gv) {
        arr = [gv.contentId, gv.contentName] as String[]
        mrvList.add(arr)
    }
}
context.put('mrvList', mrvList)
