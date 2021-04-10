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
package org.apache.ofbiz.pricat;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.ofbiz.base.location.ComponentLocationResolver;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.FileUtil;
import org.apache.ofbiz.base.util.UtilHttp;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;

public class PricatEvents {
    private static final String MODULE = PricatEvents.class.getName();
    public static final String PRICAT_LATEST_VERSION = UtilProperties.getPropertyValue("pricat", "pricat.latest.version", "V1.1");
    public static final String PRICAT_FILE_NAME = "PricatTemplate_" + PRICAT_LATEST_VERSION + ".xlsx";
    public static final String PRICAT_PATH = "component://pricat/webapp/pricat/downloads/";
    /**
     * Download excel template.
     * @param request
     * @param response
     */
    public static String downloadExcelTemplate(HttpServletRequest request, HttpServletResponse response) {
        String templateType = request.getParameter("templateType");
        if (UtilValidate.isEmpty(templateType)) {
            return "error";
        }
        try {
            String path = ComponentLocationResolver.getBaseLocation(PRICAT_PATH).toString();
            String fileName = null;
            if ("pricatExcelTemplate".equals(templateType)) {
                fileName = PRICAT_FILE_NAME;
            }
            if (UtilValidate.isEmpty(fileName)) {
                return "error";
            }
            Path file = Paths.get(path + fileName);
            byte[] bytes = Files.readAllBytes(file);
            UtilHttp.streamContentToBrowser(response, bytes, "application/octet-stream", URLEncoder.encode(fileName, "UTF-8"));
        } catch (IOException e) {
            Debug.logError(e.getMessage(), MODULE);
            return "error";
        }
        return "success";
    }
    /**
     * Upload a pricat.
     */
    public static String pricatUpload(HttpServletRequest request, HttpServletResponse response) {
        boolean isMultiPart = ServletFileUpload.isMultipartContent(request);
        if (isMultiPart) {
            return "parse_pricat";
        } else {
            String action = request.getParameter("action");
            if (UtilValidate.isNotEmpty(action) && "downloadPricat".equals(action)) {
                String sequenceNumString = request.getParameter("sequenceNum");
                long sequenceNum = -1;
                if (UtilValidate.isNotEmpty(sequenceNumString)) {
                    try {
                        sequenceNum = Long.valueOf(sequenceNumString);
                    } catch (NumberFormatException e) {
                        Debug.logError(e, MODULE);
                    }
                }
                String originalPricatFileName = (String) request.getSession().getAttribute(PricatParseExcelHtmlThread.PRICAT_FILE);
                String pricatFileName = originalPricatFileName;
                if (sequenceNum > 0 && AbstractPricatParser.isCommentedExcelExists(request, sequenceNum)) {
                    GenericValue userLogin = (GenericValue) request.getSession().getAttribute("userLogin");
                    String userLoginId = userLogin.getString("userLoginId");
                    pricatFileName = InterfacePricatParser.TEMP_FILES_FOLDER + userLoginId + "/" + sequenceNum + ".xlsx";
                }
                if (UtilValidate.isNotEmpty(pricatFileName) && UtilValidate.isNotEmpty(originalPricatFileName)) {
                    try {
                        Path path = Paths.get(pricatFileName);
                        byte[] bytes = Files.readAllBytes(path);
                        path = Paths.get(originalPricatFileName);
                        UtilHttp.streamContentToBrowser(response, bytes, "application/octet-stream",
                                URLEncoder.encode(path.getName(path.getNameCount() - 1).toString(), "UTF-8"));
                    } catch (IOException e) {
                        Debug.logError(e.getMessage(), MODULE);
                        return "error";
                    }
                    request.getSession().removeAttribute(PricatParseExcelHtmlThread.PRICAT_FILE);
                    return "download";
                }
            }
        }
        return "success";
    }

    /**
     * Download commented excel file after it's parsed.
     * @param request
     * @param response
     * @return
     */
    public static String downloadCommentedExcel(HttpServletRequest request, HttpServletResponse response) {
        String sequenceNum = request.getParameter("sequenceNum");
        GenericValue userLogin = (GenericValue) request.getSession().getAttribute("userLogin");
        if (UtilValidate.isEmpty(sequenceNum) || UtilValidate.isEmpty(userLogin)) {
            Debug.logError("sequenceNum[" + sequenceNum + "] or userLogin is empty", MODULE);
            return "error";
        }
        String userLoginId = userLogin.getString("userLoginId");
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        GenericValue historyValue = null;
        try {
            historyValue = EntityQuery.use(delegator).from("ExcelImportHistory").where("userLoginId", userLoginId, "sequenceNum",
                    Long.valueOf(sequenceNum)).queryOne();
        } catch (NumberFormatException | GenericEntityException e) {
            Debug.logError(e.getMessage(), MODULE);
            return "error";
        }
        if (UtilValidate.isEmpty(historyValue)) {
            Debug.logError("No ExcelImportHistory value found by sequenceNum[" + sequenceNum + "] and userLoginId[" + userLoginId + "].", MODULE);
            return "error";
        }
        String fileName = historyValue.getString("fileName");
        if (UtilValidate.isEmpty(fileName)) {
            fileName = sequenceNum + ".xlsx";
        }
        try {
            File file = FileUtil.getFile(InterfacePricatParser.TEMP_FILES_FOLDER + userLoginId + "/" + sequenceNum + ".xlsx");
            if (file.exists()) {
                Path path = Paths.get(file.getPath());
                byte[] bytes = Files.readAllBytes(path);
                UtilHttp.streamContentToBrowser(response, bytes, "application/octet-stream", URLEncoder.encode(fileName, "UTF-8"));
            }
        } catch (IOException e) {
            Debug.logError(e.getMessage(), MODULE);
            return "error";
        }
        return "success";
    }
}
