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

import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.entity.condition.EntityCondition
import org.apache.ofbiz.entity.condition.EntityOperator

// Sets publishedContentId to context.articleCheckContentId when that content is a forum thread or blog entry
// linked (PUBLISH_LINK) from a forum or blog associated to context.articlePublishRootId, or a response below
// one, and publishedArticle to that thread or entry. When context.articleRequirePublished is set, an entry
// that is not published is accepted only for a user who may update its blog.
context.publishedContentId = null
context.publishedArticle = null

String checkContentId = context.articleCheckContentId
if (!checkContentId || !context.articlePublishRootId) {
    return
}

// walk up the RESPONSE links to the article and its PUBLISH_LINK; the visited set stops on a link cycle
List<String> toVisit = [checkContentId]
Set<String> visited = [] as Set
while (toVisit) {
    String currentId = toVisit.remove(0)
    if (!visited.add(currentId)) {
        continue
    }
    List assocs = from('ContentAssoc')
            .where(EntityCondition.makeCondition([
                    EntityCondition.makeCondition('contentIdTo', currentId),
                    EntityCondition.makeCondition('contentAssocTypeId', EntityOperator.IN, ['PUBLISH_LINK', 'RESPONSE'])]))
            .cache().filterByDate().queryList()
    for (assoc in assocs) {
        if (assoc.contentAssocTypeId == 'RESPONSE') {
            toVisit << assoc.contentId
        } else if (from('ContentAssoc').where(contentId: context.articlePublishRootId, contentIdTo: assoc.contentId)
                .cache().filterByDate().queryFirst()) {
            GenericValue article = from('Content').where(contentId: currentId).cache().queryOne()
            if (context.articleRequirePublished && article.statusId != 'CTNT_PUBLISHED') {
                if (!context.userLogin) {
                    return
                }
                Map permission = dispatcher.runSync('genericContentPermission', [userLogin: context.userLogin, mainAction: 'UPDATE',
                        contentId: assoc.contentId, contentOperationId: 'CONTENT_UPDATE', contentPurposeTypeId: 'ARTICLE'], -1, true)
                if (!permission.hasPermission) {
                    return
                }
            }
            context.publishedArticle = article
            context.publishedContentId = checkContentId
            return
        }
    }
}
