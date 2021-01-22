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
package org.apache.ofbiz.content.search;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.GeneralException;
import org.apache.ofbiz.base.util.UtilDateTime;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.content.data.DataResourceWorker;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.ofbiz.entity.util.EntityUtilProperties;

public class ProductDocument implements LuceneDocument {
    private static final String MODULE = ProductDocument.class.getName();
    private static final String NULL_STRING = "NULL";
    private static final EntityCondition THRU_DATE_ONLY_CONDITION = EntityCondition.makeCondition(
            EntityCondition.makeCondition("thruDate", EntityOperator.EQUALS, null),
            EntityOperator.OR,
            EntityCondition.makeCondition("thruDate", EntityOperator.GREATER_THAN, UtilDateTime.nowTimestamp()));
    private final Term documentIdentifier;

    public ProductDocument(String productId) {
        this.documentIdentifier = new Term("productId", productId);
    }

    // An attempt to boost/weight values in a similar manner to what OFBiz product search does.
    private static void addTextField(Document doc, String fieldName, String value, boolean store, String fullTextFieldName, Delegator delegator) {
        if (fieldName == null) return;

        Field field = new TextField(fieldName, checkValue(value), (store ? Field.Store.YES : Field.Store.NO));
        doc.add(field);
        if (fullTextFieldName != null) {
            doc.add(new TextField(fullTextFieldName, checkValue(value), Field.Store.NO));
        }
    }

    private static String checkValue(String value) {
        if (UtilValidate.isEmpty(value)) {
            return NULL_STRING;
        }
        return value;
    }

    private static Timestamp checkSetNextReIndex(Timestamp nextValue, Timestamp currentValue) {
        // nextValue is null, stick with what we've got
        if (nextValue == null) return currentValue;
        // currentValue is null so use nextValue
        if (currentValue == null) return nextValue;
        // currentValue is after nextValue so use nextValue
        if (currentValue.after(nextValue)) return nextValue;
        // stick with current value
        return currentValue;
    }

    private static List<GenericValue> filterByThruDate(List<GenericValue> values) {
        return EntityUtil.filterByCondition(values, THRU_DATE_ONLY_CONDITION);
    }

    private static List<GenericValue> filterByThruDate(List<GenericValue> values, String thruDateName) {
        return EntityUtil.filterByCondition(values, EntityCondition.makeCondition(
                EntityCondition.makeCondition(thruDateName, EntityOperator.EQUALS, null),
                EntityOperator.OR,
                EntityCondition.makeCondition(thruDateName, EntityOperator.GREATER_THAN, UtilDateTime.nowTimestamp())));
    }

    private static Timestamp getCategoryCatalogs(Document doc, GenericValue productCategory, Set<String> indexedCatalogIds)
            throws GenericEntityException {
        Timestamp nextReIndex = null;
        List<GenericValue> prodCatalogCategories = productCategory.getRelated("ProdCatalogCategory", null, null, false);
        prodCatalogCategories = filterByThruDate(prodCatalogCategories);
        for (GenericValue prodCatalogCategory : prodCatalogCategories) {
            Timestamp fromDate = prodCatalogCategory.getTimestamp("fromDate");
            Timestamp thruDate = prodCatalogCategory.getTimestamp("thruDate");
            if (fromDate != null && fromDate.after(UtilDateTime.nowTimestamp())) {
                // fromDate is after now, update reindex date but don't index now
                nextReIndex = checkSetNextReIndex(fromDate, nextReIndex);
                continue;
            } else if (thruDate != null) {
                nextReIndex = checkSetNextReIndex(thruDate, nextReIndex);
            }
            // Skip if we've done this catalog already
            if (!indexedCatalogIds.add(prodCatalogCategory.getString("prodCatalogId"))) {
                continue;
            }
            doc.add(new StringField("prodCatalogId", prodCatalogCategory.getString("prodCatalogId"), Field.Store.NO));
        }
        return nextReIndex;
    }

    private static long quantizeTimestampToDays(Timestamp date) {
        long quantizedDate = 0;
        if (date != null) {
            quantizedDate = date.getTime() / 24 / 3600;
        }
        return quantizedDate;
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
    public Document prepareDocument(Delegator delegator) {
        String productId = getDocumentIdentifier().text();
        try {
            GenericValue product = EntityQuery.use(delegator).from("Product").where("productId", productId).queryOne();
            if (product == null) {
                // Return a null document (we will remove the document from the index)
                return null;
            } else {
                if ("Y".equals(product.getString("isVariant")) && "true".equals(EntityUtilProperties.getPropertyValue("prodsearch", "index.ignore"
                        + ".variants", delegator))) {
                    return null;
                }
                Document doc = new Document();
                Timestamp nextReIndex = null;

                // Product Fields
                doc.add(new StringField("productId", productId, Field.Store.YES));
                addTextField(doc, "productName", product.getString("productName"), false, "fullText", delegator);
                addTextField(doc, "internalName", product.getString("internalName"), false, "fullText", delegator);
                addTextField(doc, "brandName", product.getString("brandName"), false, "fullText", delegator);
                addTextField(doc, "description", product.getString("description"), false, "fullText", delegator);
                addTextField(doc, "longDescription", product.getString("longDescription"), false, "fullText", delegator);
                doc.add(new LongPoint("introductionDate", quantizeTimestampToDays(product.getTimestamp("introductionDate"))));
                nextReIndex = checkSetNextReIndex(product.getTimestamp("introductionDate"), nextReIndex);
                doc.add(new LongPoint("salesDiscontinuationDate", quantizeTimestampToDays(product.getTimestamp("salesDiscontinuationDate"))));
                nextReIndex = checkSetNextReIndex(product.getTimestamp("salesDiscontinuationDate"), nextReIndex);
                doc.add(new StringField("isVariant", product.get("isVariant") != null && product.getBoolean("isVariant") ? "true" : "false",
                        Field.Store.NO));

                // ProductFeature Fields, check that at least one of the fields is set to be indexed
                if (!"0".equals(EntityUtilProperties.getPropertyValue("prodsearch", "index.weight.ProductFeatureAndAppl.description", "0", delegator))
                        || !"0".equals(EntityUtilProperties.getPropertyValue("prodsearch", "index.weight.ProductFeatureAndAppl.abbrev", "0",
                        delegator))
                        || !"0".equals(EntityUtilProperties.getPropertyValue("prodsearch", "index.weight.ProductFeatureAndAppl.idCode", "0",
                        delegator))) {

                    List<GenericValue> productFeatureAndAppls = EntityQuery.use(delegator).from("ProductFeatureAndAppl").where("productId",
                            productId).queryList();
                    productFeatureAndAppls = filterByThruDate(productFeatureAndAppls);

                    for (GenericValue productFeatureAndAppl : productFeatureAndAppls) {
                        Timestamp fromDate = productFeatureAndAppl.getTimestamp("fromDate");
                        Timestamp thruDate = productFeatureAndAppl.getTimestamp("thruDate");
                        if (fromDate != null && fromDate.after(UtilDateTime.nowTimestamp())) {
                            // fromDate is after now, update reindex date but don't index the feature
                            nextReIndex = checkSetNextReIndex(fromDate, nextReIndex);
                            continue;
                        } else if (thruDate != null) {
                            nextReIndex = checkSetNextReIndex(thruDate, nextReIndex);
                        }
                        doc.add(new StringField("productFeatureId", productFeatureAndAppl.getString("productFeatureId"), Field.Store.NO));
                        doc.add(new StringField("productFeatureCategoryId", productFeatureAndAppl.getString("productFeatureCategoryId"),
                                Field.Store.NO));
                        doc.add(new StringField("productFeatureTypeId", productFeatureAndAppl.getString("productFeatureTypeId"), Field.Store.NO));
                        addTextField(doc, "featureDescription", productFeatureAndAppl.getString("description"), false, "fullText", delegator);
                        addTextField(doc, "featureAbbreviation", productFeatureAndAppl.getString("abbrev"), false, "fullText", delegator);
                        addTextField(doc, "featureCode", productFeatureAndAppl.getString("idCode"), false, "fullText", delegator);
                        // Get the ProductFeatureGroupIds
                        List<GenericValue> productFeatureGroupAppls = EntityQuery.use(delegator).from("ProductFeatureGroupAppl").where(
                                "productFeatureId", productFeatureAndAppl.get("productFeatureId")).queryList();
                        productFeatureGroupAppls = filterByThruDate(productFeatureGroupAppls);
                        for (GenericValue productFeatureGroupAppl : productFeatureGroupAppls) {
                            fromDate = productFeatureGroupAppl.getTimestamp("fromDate");
                            thruDate = productFeatureGroupAppl.getTimestamp("thruDate");
                            if (fromDate != null && fromDate.after(UtilDateTime.nowTimestamp())) {
                                // fromDate is after now, update reindex date but don't index the feature
                                nextReIndex = checkSetNextReIndex(fromDate, nextReIndex);
                                continue;
                            } else if (thruDate != null) {
                                nextReIndex = checkSetNextReIndex(thruDate, nextReIndex);
                            }
                            doc.add(new StringField("productFeatureGroupId", productFeatureGroupAppl.getString("productFeatureGroupId"),
                                    Field.Store.NO));
                        }
                    }
                }

                // ProductAttribute Fields
                if (!"0".equals(EntityUtilProperties.getPropertyValue("prodsearch", "index.weight.ProductAttribute.attrName", "0", delegator))
                        || !"0".equals(EntityUtilProperties.getPropertyValue("prodsearch", "index.weight.ProductAttribute.attrValue", "0",
                        delegator))) {

                    List<GenericValue> productAttributes =
                            EntityQuery.use(delegator).from("ProductAttribute").where("productId", productId).queryList();
                    for (GenericValue productAttribute : productAttributes) {
                        addTextField(doc, "attributeName", productAttribute.getString("attrName"), false, "fullText", delegator);
                        addTextField(doc, "attributeValue", productAttribute.getString("attrValue"), false, "fullText", delegator);
                    }
                }

                // GoodIdentification
                if (!"0".equals(EntityUtilProperties.getPropertyValue("prodsearch", "index.weight.GoodIdentification.idValue", "0", delegator))) {
                    List<GenericValue> goodIdentifications =
                            EntityQuery.use(delegator).from("GoodIdentification").where("productId", productId).queryList();
                    for (GenericValue goodIdentification : goodIdentifications) {
                        String goodIdentificationTypeId = goodIdentification.getString("goodIdentificationTypeId");
                        String idValue = goodIdentification.getString("idValue");
                        doc.add(new StringField("goodIdentificationTypeId", goodIdentificationTypeId, Field.Store.NO));
                        doc.add(new StringField(goodIdentificationTypeId + "_GoodIdentification", idValue, Field.Store.NO));
                        addTextField(doc, "identificationValue", idValue, false, "fullText", delegator);
                    }
                }

                // Virtual ProductIds
                if ("Y".equals(product.getString("isVirtual"))) {
                    if (!"0".equals(EntityUtilProperties.getPropertyValue("prodsearch", "index.weight.Variant.Product.productId", "0", delegator))) {
                        List<GenericValue> variantProductAssocs = EntityQuery.use(delegator).from("ProductAssoc").where("productId", productId,
                                "productAssocTypeId", "PRODUCT_VARIANT").queryList();
                        variantProductAssocs = filterByThruDate(variantProductAssocs);
                        for (GenericValue variantProductAssoc : variantProductAssocs) {
                            Timestamp fromDate = variantProductAssoc.getTimestamp("fromDate");
                            Timestamp thruDate = variantProductAssoc.getTimestamp("thruDate");
                            if (fromDate != null && fromDate.after(UtilDateTime.nowTimestamp())) {
                                // fromDate is after now, update reindex date but don't index the feature
                                nextReIndex = checkSetNextReIndex(fromDate, nextReIndex);
                                continue;
                            } else if (thruDate != null) {
                                nextReIndex = checkSetNextReIndex(thruDate, nextReIndex);
                            }
                            addTextField(doc, "variantProductId", variantProductAssoc.getString("productIdTo"), false, "fullText", delegator);
                        }
                    }
                }

                // Index product content
                String productContentTypes = EntityUtilProperties.getPropertyValue("prodsearch", "index.include.ProductContentTypes", delegator);
                for (String productContentTypeId : productContentTypes.split(",")) {
                    try {
                        EntityUtilProperties.getPropertyAsInteger("prodsearch", "index.weight.ProductContent." + productContentTypeId, 1);
                    } catch (Exception e) {
                        Debug.logWarning("Could not parse weight number: " + e.toString(), MODULE);
                    }

                    List<GenericValue> productContentAndInfos = EntityQuery.use(delegator).from("ProductContentAndInfo").where("productId",
                            productId, "productContentTypeId", productContentTypeId).queryList();
                    productContentAndInfos = filterByThruDate(productContentAndInfos);
                    for (GenericValue productContentAndInfo : productContentAndInfos) {
                        Timestamp fromDate = productContentAndInfo.getTimestamp("fromDate");
                        Timestamp thruDate = productContentAndInfo.getTimestamp("thruDate");
                        if (fromDate != null && fromDate.after(UtilDateTime.nowTimestamp())) {
                            // fromDate is after now, update reindex date but don't index the feature
                            nextReIndex = checkSetNextReIndex(fromDate, nextReIndex);
                            continue;
                        } else if (thruDate != null) {
                            nextReIndex = checkSetNextReIndex(thruDate, nextReIndex);
                        }
                        try {
                            Map<String, Object> drContext = UtilMisc.<String, Object>toMap("product", product);
                            String contentText = DataResourceWorker.renderDataResourceAsText(null, delegator, productContentAndInfo.getString(
                                    "dataResourceId"), drContext, null, null, false);
                            addTextField(doc, "content", contentText, false, "fullText", delegator);
                        } catch (IOException | GeneralException e1) {
                            Debug.logError(e1, "Error getting content text to index", MODULE);
                        }

                        // TODO: Not indexing alternate locales, needs special handling
                        /*
                        List<GenericValue> alternateViews = productContentAndInfo.getRelated("ContentAssocDataResourceViewTo", UtilMisc.toMap
                        ("caContentAssocTypeId", "ALTERNATE_LOCALE"), UtilMisc.toList("-caFromDate"));
                        alternateViews = EntityUtil.filterByDate(alternateViews, UtilDateTime.nowTimestamp(), "caFromDate", "caThruDate", true);
                        for (GenericValue thisView: alternateViews) {
                        }
                        */
                    }
                }

                // Index the product's directProductCategoryIds (direct parents), productCategoryIds (all ancestors) and prodCatalogIds
                this.populateCategoryData(doc, product);

                // Index ProductPrices, uses dynamic fields in the format
                // ${productPriceTypeId}_${productPricePurposeId}_${currencyUomId}_${productStoreGroupId}_price
                List<GenericValue> productPrices = product.getRelated("ProductPrice", null, null, false);
                productPrices = filterByThruDate(productPrices);
                for (GenericValue productPrice : productPrices) {
                    Timestamp fromDate = productPrice.getTimestamp("fromDate");
                    Timestamp thruDate = productPrice.getTimestamp("thruDate");
                    if (fromDate != null && fromDate.after(UtilDateTime.nowTimestamp())) {
                        // fromDate is after now, update reindex date but don't index the feature
                        nextReIndex = checkSetNextReIndex(fromDate, nextReIndex);
                        continue;
                    } else if (thruDate != null) {
                        nextReIndex = checkSetNextReIndex(thruDate, nextReIndex);
                    }
                    StringBuilder fieldNameSb = new StringBuilder();
                    fieldNameSb.append(productPrice.getString("productPriceTypeId"));
                    fieldNameSb.append('_');
                    fieldNameSb.append(productPrice.getString("productPricePurposeId"));
                    fieldNameSb.append('_');
                    fieldNameSb.append(productPrice.getString("currencyUomId"));
                    fieldNameSb.append('_');
                    fieldNameSb.append(productPrice.getString("productStoreGroupId"));
                    fieldNameSb.append("_price");
                    doc.add(new DoublePoint(fieldNameSb.toString(), productPrice.getDouble("price")));
                }

                // Index ProductSuppliers
                List<GenericValue> supplierProducts = product.getRelated("SupplierProduct", null, null, false);
                supplierProducts = filterByThruDate(supplierProducts, "availableThruDate");
                Set<String> supplierPartyIds = new TreeSet<>();
                for (GenericValue supplierProduct : supplierProducts) {
                    Timestamp fromDate = supplierProduct.getTimestamp("availableFromDate");
                    Timestamp thruDate = supplierProduct.getTimestamp("availableThruDate");
                    if (fromDate != null && fromDate.after(UtilDateTime.nowTimestamp())) {
                        // fromDate is after now, update reindex date but don't index the feature
                        nextReIndex = checkSetNextReIndex(fromDate, nextReIndex);
                        continue;
                    } else if (thruDate != null) {
                        nextReIndex = checkSetNextReIndex(thruDate, nextReIndex);
                    }
                    supplierPartyIds.add(supplierProduct.getString("partyId"));
                }
                for (String supplierPartyId : supplierPartyIds) {
                    doc.add(new StringField("supplierPartyId", supplierPartyId, Field.Store.NO));
                }

                // TODO: Add the nextReIndex timestamp to the document for when the product should be automatically re-indexed outside of any ECAs
                // based on the next known from/thru date whose passing will cause a change to the document.  Need to build a scheduled service to
                // look for these.
                return doc;
            }
        } catch (GenericEntityException e) {
            Debug.logError(e, MODULE);
        }
        return null;
    }

    private Timestamp populateCategoryData(Document doc, GenericValue product) throws GenericEntityException {
        Timestamp nextReIndex = null;
        Set<String> indexedCategoryIds = new TreeSet<>();
        List<GenericValue> productCategoryMembers = product.getRelated("ProductCategoryMember", null, null, false);
        productCategoryMembers = filterByThruDate(productCategoryMembers);

        for (GenericValue productCategoryMember : productCategoryMembers) {
            String productCategoryId = productCategoryMember.getString("productCategoryId");
            doc.add(new StringField("productCategoryId", productCategoryId, Field.Store.NO));
            doc.add(new StringField("directProductCategoryId", productCategoryId, Field.Store.NO));
            indexedCategoryIds.add(productCategoryId);
            Timestamp fromDate = productCategoryMember.getTimestamp("fromDate");
            Timestamp thruDate = productCategoryMember.getTimestamp("thruDate");
            if (fromDate != null && fromDate.after(UtilDateTime.nowTimestamp())) {
                // fromDate is after now, update reindex date but don't index the feature
                nextReIndex = checkSetNextReIndex(fromDate, nextReIndex);
                continue;
            } else if (thruDate != null) {
                nextReIndex = checkSetNextReIndex(thruDate, nextReIndex);
            }
            nextReIndex = checkSetNextReIndex(
                    this.getParentCategories(doc, productCategoryMember.getRelatedOne("ProductCategory", false), indexedCategoryIds),
                    nextReIndex);
        }
        return nextReIndex;
    }

    private Timestamp getParentCategories(Document doc, GenericValue productCategory, Set<String> indexedCategoryIds) throws GenericEntityException {
        return this.getParentCategories(doc, productCategory, indexedCategoryIds, new TreeSet<>());
    }

    private Timestamp getParentCategories(Document doc, GenericValue productCategory, Set<String> indexedCategoryIds,
                                          Set<String> indexedCatalogIds) throws GenericEntityException {
        Timestamp nextReIndex = null;
        nextReIndex = getCategoryCatalogs(doc, productCategory, indexedCatalogIds);
        List<GenericValue> productCategoryRollups = productCategory.getRelated("CurrentProductCategoryRollup", null, null, false);
        productCategoryRollups = filterByThruDate(productCategoryRollups);
        for (GenericValue productCategoryRollup : productCategoryRollups) {
            Timestamp fromDate = productCategoryRollup.getTimestamp("fromDate");
            Timestamp thruDate = productCategoryRollup.getTimestamp("thruDate");
            if (fromDate != null && fromDate.after(UtilDateTime.nowTimestamp())) {
                // fromDate is after now, update reindex date but don't index now
                nextReIndex = checkSetNextReIndex(fromDate, nextReIndex);
                continue;
            } else if (thruDate != null) {
                nextReIndex = checkSetNextReIndex(thruDate, nextReIndex);
            }
            // Skip if we've done this category already
            if (!indexedCategoryIds.add(productCategoryRollup.getString("parentProductCategoryId"))) {
                continue;
            }
            GenericValue parentProductCategory = productCategoryRollup.getRelatedOne("ParentProductCategory", false);
            doc.add(new StringField("productCategoryId", parentProductCategory.getString("productCategoryId"), Field.Store.NO));
            nextReIndex = checkSetNextReIndex(
                    this.getParentCategories(doc, parentProductCategory, indexedCategoryIds),
                    nextReIndex);
        }
        return nextReIndex;
    }

}
