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
package org.apache.ofbiz.birt.flexible;

import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.MapUtils;
import org.apache.ofbiz.base.util.GeneralException;
import org.apache.ofbiz.base.util.StringUtil;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityConditionList;
import org.apache.ofbiz.entity.condition.EntityExpr;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.eclipse.birt.report.engine.api.RenderOption;
import org.eclipse.birt.report.model.api.elements.DesignChoiceConstants;

public final class BirtUtil {

    private static final String MODULE = BirtUtil.class.getName();

    private static final Map<String, String> ENTITY_FIELD_TYPE_BIRT_TYPE_MAP = MapUtils.unmodifiableMap(UtilMisc.toMap(
            "id", DesignChoiceConstants.COLUMN_DATA_TYPE_STRING,
            "url", DesignChoiceConstants.COLUMN_DATA_TYPE_STRING,
            "name", DesignChoiceConstants.COLUMN_DATA_TYPE_STRING,
            "value", DesignChoiceConstants.COLUMN_DATA_TYPE_STRING,
            "email", DesignChoiceConstants.COLUMN_DATA_TYPE_STRING,
            "comment", DesignChoiceConstants.COLUMN_DATA_TYPE_STRING,
            "id-long", DesignChoiceConstants.COLUMN_DATA_TYPE_STRING,
            "id-vlong", DesignChoiceConstants.COLUMN_DATA_TYPE_STRING,
            "very-long", DesignChoiceConstants.COLUMN_DATA_TYPE_STRING,
            "indicator", DesignChoiceConstants.COLUMN_DATA_TYPE_STRING,
            "very-short", DesignChoiceConstants.COLUMN_DATA_TYPE_STRING,
            "tel-number", DesignChoiceConstants.COLUMN_DATA_TYPE_STRING,
            "description", DesignChoiceConstants.COLUMN_DATA_TYPE_STRING,
            "long-varchar", DesignChoiceConstants.COLUMN_DATA_TYPE_STRING,
            "short-varchar", DesignChoiceConstants.COLUMN_DATA_TYPE_STRING,
            "credit-card-date", DesignChoiceConstants.COLUMN_DATA_TYPE_STRING,
            "credit-card-number", DesignChoiceConstants.COLUMN_DATA_TYPE_STRING,
            "date-time", DesignChoiceConstants.COLUMN_DATA_TYPE_DATETIME,
            "date", DesignChoiceConstants.COLUMN_DATA_TYPE_DATE,
            "time", DesignChoiceConstants.COLUMN_DATA_TYPE_TIME,
            "currency-amount", DesignChoiceConstants.COLUMN_DATA_TYPE_DECIMAL,
            "currency-precise", DesignChoiceConstants.COLUMN_DATA_TYPE_DECIMAL,
            "fixed-point", DesignChoiceConstants.COLUMN_DATA_TYPE_DECIMAL,
            "floating-point", DesignChoiceConstants.COLUMN_DATA_TYPE_DECIMAL,
            "numeric", DesignChoiceConstants.COLUMN_DATA_TYPE_INTEGER,
            "object", DesignChoiceConstants.COLUMN_DATA_TYPE_JAVA_OBJECT,
            "blob", DesignChoiceConstants.COLUMN_DATA_TYPE_BLOB));

    private static final Map<String, String> ENTITY_FIELD_TYPE_BIRT_PARAM_TYPE_MAP = MapUtils.unmodifiableMap(UtilMisc.toMap(
            "id", DesignChoiceConstants.PARAM_TYPE_STRING,
            "url", DesignChoiceConstants.PARAM_TYPE_STRING,
            "name", DesignChoiceConstants.PARAM_TYPE_STRING,
            "value", DesignChoiceConstants.PARAM_TYPE_STRING,
            "email", DesignChoiceConstants.PARAM_TYPE_STRING,
            "comment", DesignChoiceConstants.PARAM_TYPE_STRING,
            "id-long", DesignChoiceConstants.PARAM_TYPE_STRING,
            "id-vlong", DesignChoiceConstants.PARAM_TYPE_STRING,
            "very-long", DesignChoiceConstants.PARAM_TYPE_STRING,
            "indicator", DesignChoiceConstants.PARAM_TYPE_STRING,
            "very-short", DesignChoiceConstants.PARAM_TYPE_STRING,
            "tel-number", DesignChoiceConstants.PARAM_TYPE_STRING,
            "description", DesignChoiceConstants.PARAM_TYPE_STRING,
            "long-varchar", DesignChoiceConstants.PARAM_TYPE_STRING,
            "short-varchar", DesignChoiceConstants.PARAM_TYPE_STRING,
            "credit-card-date", DesignChoiceConstants.PARAM_TYPE_STRING,
            "credit-card-number", DesignChoiceConstants.PARAM_TYPE_STRING,
            "date-time", DesignChoiceConstants.PARAM_TYPE_DATETIME,
            "date", DesignChoiceConstants.PARAM_TYPE_DATE,
            "time", DesignChoiceConstants.PARAM_TYPE_TIME,
            "currency-amount", DesignChoiceConstants.PARAM_TYPE_DECIMAL,
            "currency-precise", DesignChoiceConstants.PARAM_TYPE_DECIMAL,
            "fixed-point", DesignChoiceConstants.PARAM_TYPE_DECIMAL,
            "floating-point", DesignChoiceConstants.PARAM_TYPE_DECIMAL,
            "numeric", DesignChoiceConstants.PARAM_TYPE_INTEGER,
            "object", DesignChoiceConstants.PARAM_TYPE_JAVA_OBJECT,
            "blob", DesignChoiceConstants.PARAM_TYPE_JAVA_OBJECT));

    private static final Map<String, String> MIME_TYPE_OUTPUT_FORMAT_MAP = UtilMisc.toMap(
            "text/html", RenderOption.OUTPUT_FORMAT_HTML,
            "application/pdf", RenderOption.OUTPUT_FORMAT_PDF,
            "application/postscript", "postscript",
            "application/vnd.ms-word", "doc",
            "application/vnd.ms-excel", "xls",
            "application/vnd.ms-powerpoint", "ppt",
            "application/vnd.oasis.opendocument.text", "odt",
            "application/vnd.oasis.opendocument.spreadsheet", "ods",
            "application/vnd.oasis.opendocument.presentation", "odp",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx");

    private BirtUtil() { }

    /**
     * Return birt field type corresponding to given entity field type
     * @param entityFieldType
     * @return birt field type corresponding to given entity field type
     */
    public static String convertFieldTypeToBirtType(String entityFieldType) {
        if (UtilValidate.isEmpty(entityFieldType)) {
            return null;
        }
        return ENTITY_FIELD_TYPE_BIRT_TYPE_MAP.get(entityFieldType.toLowerCase());
    }

    /**
     * Return birt parameter type corresponding to given entity field type
     * @param entityFieldType
     * @return birt parameter type corresponding to given entity field type
     */
    public static String convertFieldTypeToBirtParameterType(String entityFieldType) {
        if (UtilValidate.isEmpty(entityFieldType)) {
            return null;
        }
        return ENTITY_FIELD_TYPE_BIRT_PARAM_TYPE_MAP.get(entityFieldType.toLowerCase());
    }

    /**
     * Return true if mime type related to a contentType is supported by Birt
     * @param contentType
     * @return true if mime type related to a contentType is supported by Birt
     */
    public static boolean isSupportedMimeType(String contentType) {
        return MIME_TYPE_OUTPUT_FORMAT_MAP.containsKey(contentType);
    }

    /**
     * Return mime type related to a contentType supported by Birt
     * @param contentType
     * @return mime type related to a contentType supported by Birt
     * @throws GeneralException
     */
    public static String getMimeTypeOutputFormat(String contentType) throws GeneralException {
        if (isSupportedMimeType(contentType)) {
            return MIME_TYPE_OUTPUT_FORMAT_MAP.get(contentType);
        }
        throw new GeneralException("Unknown content type : " + contentType);
    }

    /**
     * @param contentType
     * return extension file related to a contentType supported by Birt
     * @throws GeneralException
     */
    public static String getMimeTypeFileExtension(String contentType) throws GeneralException {
        return ".".concat(getMimeTypeOutputFormat(contentType));
    }

    /**
     * Resolve the template path location where rptDesign file are stored,
     * first try the resolution from content.properties content.upload.path.prefix
     * second from birt.properties with rptDesign.output.path
     * and add birtReptDesign directory
     * default OFBIZ_HOME/runtime/uploads/birtRptDesign/
     * Note: the Birt.properties file does not exist OOTB but can be added for convenience.
     *       like using the location where you edit with the Birt Report Designer...
     * @return template path location where rptDesign file is stored
     */
    public static String resolveTemplatePathLocation() {
        String templatePathLocation = UtilProperties.getPropertyValue("birt", "rptDesign.output.path");
        if (UtilValidate.isEmpty(templatePathLocation)) {
            templatePathLocation = UtilProperties.getPropertyValue("content", "content.upload.path.prefix", "runtime/uploads");
        }
        if (!templatePathLocation.endsWith("/")) {
            templatePathLocation = templatePathLocation.concat("/");
        }
        if (!templatePathLocation.endsWith("/birtRptDesign/")) {
            templatePathLocation = templatePathLocation.concat("birtRptDesign/");
        }
        if (!templatePathLocation.startsWith("/")) templatePathLocation = System.getProperty("ofbiz.home").concat("/").concat(templatePathLocation);
        return templatePathLocation;
    }

    /**
     * With the reporting contentId element resolve the path to rptDesign linked
     * @param delegator
     * @param contentId
     * @return path to rptDesign file
     * @throws GenericEntityException
     */
    public static String resolveRptDesignFilePathFromContent(Delegator delegator, String contentId) throws GenericEntityException {
        GenericValue contentRpt = EntityQuery.use(delegator)
                .from("ContentAssoc")
                .where("contentId", contentId)
                .select("contentIdTo")
                .cache()
                .queryFirst();
        if (contentRpt != null) {
            String contentIdRpt = contentRpt.getString("contentIdTo");
            List<EntityExpr> listConditions = UtilMisc.toList(
                    EntityCondition.makeCondition("contentTypeId", "RPTDESIGN"),
                    EntityCondition.makeCondition("contentId", contentIdRpt));
            EntityConditionList<EntityExpr> ecl = EntityCondition.makeCondition(listConditions);
            GenericValue dataRessouceRptDesignFile = EntityQuery.use(delegator)
                    .from("ContentDataResourceView")
                    .where(ecl)
                    .select("drObjectInfo")
                    .cache()
                    .queryFirst();
            if (dataRessouceRptDesignFile != null) {
                return dataRessouceRptDesignFile.getString("drObjectInfo");
            }
        }
        return "";
    }

    /**
     * remove all non unicode alphanumeric and replace space by _
     * @param reportName
     * @return spaces replaced by underscore
     */
    public static String encodeReportName(String reportName) {
        if (UtilValidate.isEmpty(reportName)) return "";
        return StringUtil.replaceString(StringUtil.removeRegex(reportName.trim(), "[^\\p{L}\\s]"), " ", "_");
    }
}
