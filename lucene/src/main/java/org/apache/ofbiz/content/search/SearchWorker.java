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
package org.apache.ofbiz.content.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollectorManager;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilDateTime;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.content.content.ContentWorker;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.service.LocalDispatcher;

/**
 * SearchWorker Class
 */
public final class SearchWorker {

    private static final String MODULE = SearchWorker.class.getName();

    private SearchWorker() { }

    public static void indexContentTree(LocalDispatcher dispatcher, Delegator delegator, String siteId) throws Exception {
        GenericValue content = delegator.makeValue("Content", UtilMisc.toMap("contentId", siteId));
        List<GenericValue> siteList = ContentWorker.getAssociatedContent(content, "To", UtilMisc.toList("SUBSITE",
                "PUBLISH_LINK", "SUB_CONTENT"), null, UtilDateTime.nowTimestamp().toString(), null);

        if (siteList != null) {
            for (GenericValue siteContent : siteList) {
                String siteContentId = siteContent.getString("contentId");
                List<GenericValue> subContentList = ContentWorker.getAssociatedContent(siteContent, "To",
                        UtilMisc.toList("SUBSITE", "PUBLISH_LINK", "SUB_CONTENT"), null, UtilDateTime.nowTimestamp().toString(), null);

                if (subContentList != null) {
                    List<String> contentIdList = new ArrayList<>();
                    for (GenericValue subContent : subContentList) {
                        contentIdList.add(subContent.getString("contentId"));
                    }
                    indexContentList(dispatcher, delegator, contentIdList);
                    indexContentTree(dispatcher, delegator, siteContentId);
                }
            }
        }
    }

    public static String getIndexPath(String path) {
        String basePath = UtilProperties.getPropertyValue("lucene", "defaultIndex", "index");
        return (UtilValidate.isNotEmpty(path) ? basePath + "/" + path + "/index" : basePath);
    }

    public static void indexContentList(LocalDispatcher dispatcher, Delegator delegator, List<String> idList) throws Exception {
        DocumentIndexer indexer = DocumentIndexer.getInstance(delegator, "content");
        List<GenericValue> contentList = new ArrayList<>();
        for (String id : idList) {
            try {
                GenericValue content = EntityQuery.use(delegator).from("Content").where("contentId", id).cache().queryOne();
                if (content != null) {
                    contentList.add(content);
                }
            } catch (GenericEntityException e) {
                Debug.logError(e, MODULE);
                return;
            }
        }
        for (GenericValue content : contentList) {
            indexer.queue(new ContentDocument(content, dispatcher));
        }
    }

    /**
     * Runs a query and returns the stored fields of the best matching documents.
     * @param searcher the searcher of the index to query
     * @param query the query to run
     * @param maxHits the maximum number of documents to return
     * @return the matching documents, best match first
     * @throws IOException if the index cannot be read
     */
    public static List<Document> search(IndexSearcher searcher, Query query, int maxHits) throws IOException {
        TopDocs topDocs = searcher.search(query, new TopScoreDocCollectorManager(maxHits, maxHits));
        StoredFields storedFields = searcher.storedFields();
        List<Document> documents = new ArrayList<>(topDocs.scoreDocs.length);
        for (ScoreDoc hit : topDocs.scoreDocs) {
            documents.add(storedFields.document(hit.doc));
        }
        return documents;
    }

}
