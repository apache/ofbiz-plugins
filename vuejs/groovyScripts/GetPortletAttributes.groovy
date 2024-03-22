
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

// Call service getPortletAttributes and put all attributes in context

import org.apache.ofbiz.base.util.Debug
import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.util.EntityQuery

portalPageId = parameters.portalPageId
portalPortletId = parameters.portalPortletId
portletSeqId = parameters.portletSeqId

// read attributes as default values
portletAttributes = EntityQuery.use(delegator)
        .from("PortletAttribute")
        .where("portalPageId", "_NA_",
                "portalPortletId", portalPortletId,
                "portletSeqId", "00000")
        .queryList();
def attributeMap = [:]
if (portletAttributes.size()>0) {
    attributesIterator = portletAttributes.listIterator()
    while (attributesIterator.hasNext()) {
        attribute = attributesIterator.next()
        attributeMap.put(attribute.get("attrName"), attribute.get("attrValue"))
    }
}

// now read user and standard attributes
portalPortletInMap = [portalPageId : parameters.portalPageId, portalPortletId : parameters.portalPortletId , portletSeqId : parameters.portletSeqId , userLogin : userLogin]
resultOutMap = dispatcher.runSync("getPortletAttributes", portalPortletInMap)
if (resultOutMap.attributeMap)
    attributeMap.putAll((Map) resultOutMap.attributeMap)

// load context with all attributes
context.putAll(attributeMap)
