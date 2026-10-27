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
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.ofbiz.base.util.GeneralException;
import org.apache.ofbiz.base.util.StringUtil;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.content.content.ContentWorker;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.service.LocalDispatcher;

/**
 * ContentDocument Class
 */

public class ContentDocument implements LuceneDocument {

    private final Term documentIdentifier;
    private final LocalDispatcher dispatcher;
    private final GenericValue content;

    public ContentDocument(GenericValue content, LocalDispatcher dispatcher) {
        this.content = content;
        this.dispatcher = dispatcher;
        this.documentIdentifier = new Term("contentId", content.getString("contentId"));
    }

    @Override
    public String toString() {
        return getDocumentIdentifier().toString();
    }

    @Override
    public Term getDocumentIdentifier() {
        return documentIdentifier;
    }

    @Override
    public Document prepareDocument(Delegator delegator) throws GeneralException {
        Document doc;
        // make a new, empty document
        doc = new Document();
        String contentId = content.getString("contentId");
        doc.add(new StringField("contentId", contentId, Store.YES));
        // Add the last modified date of the file a field named "modified". Use a
        // Keyword field, so that it's searchable, but so that no attempt is
        // made to tokenize the field into words.
        Timestamp modDate = (Timestamp) content.get("lastModifiedDate");
        if (modDate == null) {
            modDate = (Timestamp) content.get("createdDate");
        }
        if (modDate != null) {
            doc.add(new StringField("modified", modDate.toString(), Store.YES));
        }
        String contentName = content.getString("contentName");
        if (UtilValidate.isNotEmpty(contentName)) {
            doc.add(new TextField("title", contentName, Store.YES));
        }
        String description = content.getString("description");
        if (UtilValidate.isNotEmpty(description)) {
            doc.add(new TextField("description", description, Store.YES));
        }
        List<String> ancestorList = new ArrayList<>();
        getContentAncestry(content.getDelegator(), contentId, "WEB_SITE_PUB_PT", ancestorList, new HashSet<>());
        String ancestorString = StringUtil.join(ancestorList, " ");
        if (UtilValidate.isNotEmpty(ancestorString)) {
            Field field = new StringField("site", ancestorString, Store.NO);
            doc.add(field);
        }
        boolean retVal = indexDataResource(doc);
        if (!retVal) {
            doc = null;
        }
        return doc;
    }

    /**
     * Adds the ancestors of a content having the given content type to the list, following the content associations from
     * contentId to contentIdTo. Unlike ContentWorker.getContentAncestryAll(), a database error is thrown instead of being
     * logged, so that the content is not indexed without its ancestors.
     */
    private static void getContentAncestry(Delegator delegator, String contentId, String contentTypeId, List<String> ancestorList,
            Set<String> visitedContentIds) throws GenericEntityException {
        if (!visitedContentIds.add(contentId)) {
            return;
        }
        List<GenericValue> contentAssocs = EntityQuery.use(delegator).from("ContentAssoc")
                .where("contentId", contentId)
                .cache().filterByDate().queryList();
        for (GenericValue contentAssoc : contentAssocs) {
            String contentIdTo = contentAssoc.getString("contentIdTo");
            getContentAncestry(delegator, contentIdTo, contentTypeId, ancestorList, visitedContentIds);
            if (!ancestorList.contains(contentIdTo)) {
                GenericValue contentTo = EntityQuery.use(delegator).from("Content").where("contentId", contentIdTo).cache().queryOne();
                if (contentTo != null && contentTypeId.equals(contentTo.getString("contentTypeId"))) {
                    ancestorList.add(contentIdTo);
                }
            }
        }
    }

    private boolean indexDataResource(Document doc) throws GeneralException {
        String contentId = content.getString("contentId");
        GenericValue dataResource = content.getRelatedOne("DataResource", true);
        if (dataResource == null) {
            return false;
        }
        String mimeTypeId = dataResource.getString("mimeTypeId");
        if (UtilValidate.isEmpty(mimeTypeId)) {
            mimeTypeId = "text/html";
        }
        Locale locale = Locale.getDefault();
        String currentLocaleString = dataResource.getString("localeString");
        if (UtilValidate.isNotEmpty(currentLocaleString)) {
            locale = UtilMisc.parseLocale(currentLocaleString);
        }
        String text;
        try {
            text = ContentWorker.renderContentAsText(dispatcher, contentId, null, locale, mimeTypeId, true);
        } catch (IOException e) {
            throw new GeneralException("Could not render content " + contentId + " as text", e);
        }
        if (UtilValidate.isNotEmpty(text)) {
            Field field = new TextField("content", text, Store.NO);
            doc.add(field);
        }
        List<GenericValue> featureDataResourceList = content.getRelated("ProductFeatureDataResource", null, null, true);
        List<String> featureList = new ArrayList<>();
        for (GenericValue productFeatureDataResource : featureDataResourceList) {
            String feature = productFeatureDataResource.getString("productFeatureId");
            featureList.add(feature);
        }
        String featureString = StringUtil.join(featureList, " ");
        if (UtilValidate.isNotEmpty(featureString)) {
            Field field = new TextField("feature", featureString, Store.NO);
            doc.add(field);
        }
        return true;
    }
}
