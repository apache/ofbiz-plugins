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
package org.apache.ofbiz.ecommerce.includes

import org.apache.ofbiz.base.util.UtilHttp
import org.apache.ofbiz.base.util.collections.LifoSet
import org.apache.ofbiz.content.ContentManagementWorker
import org.apache.ofbiz.content.content.ContentWorker

lookupCaches = session.getAttribute('lookupCaches')
//logInfo('entityName:' + entityName)
//logInfo('in MruAdd.groovy, lookupCaches:' + lookupCaches)

if (!lookupCaches) {
    lookupCaches = [:]
    session.setAttribute('lookupCaches', lookupCaches)
}

cacheEntityName = entityName
//logInfo('cacheEntityName:' + cacheEntityName)
lifoSet = lookupCaches[cacheEntityName]
//logInfo('lifoSet:' + lifoSet)
if (!lifoSet) {
    lifoSet = new LifoSet(10)
    lookupCaches[cacheEntityName] = lifoSet
}

paramMap = UtilHttp.getParameterMap(request)
contentId = paramMap.contentId
contentAssocDataResourceViewFrom = ContentWorker.getSubContentCache(delegator, null, null, contentId, null, null, null, null, null)
//logInfo('in mruadd, contentAssocDataResourceViewFrom :' + contentAssocDataResourceViewFrom )
if (contentAssocDataResourceViewFrom) {
    lookupCaches = session.getAttribute('lookupCaches')
    viewPK = contentAssocDataResourceViewFrom.getPrimaryKey()
    //logInfo('in mruadd, viewPK :' + viewPK )
    if (viewPK) {
        ContentManagementWorker.mruAdd(session, viewPK)
    }
}
