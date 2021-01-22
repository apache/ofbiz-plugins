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
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.ofbiz.htmlreport.AbstractReportThread;
import org.apache.ofbiz.htmlreport.InterfaceReport;
import org.apache.ofbiz.pricat.sample.SamplePricatParser;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.FileUtil;
import org.apache.ofbiz.base.util.UtilDateTime;
import org.apache.ofbiz.base.util.UtilGenerics;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.service.LocalDispatcher;

/**
 * Thread for running pricat import excel html report.
 */
public class PricatParseExcelHtmlThread extends AbstractReportThread {

    public static final String PARSE_EXCEL = "parse_excel";
    public static final String CONFIRM = "confirm_action";
    public static final String[] MESSAGE_LABELS = new String[]{"FORMAT_DEFAULT", "FORMAT_WARNING", "FORMAT_HEADLINE", "FORMAT_NOTE", "FORMAT_OK",
            "FORMAT_ERROR", "FORMAT_THROWABLE"};
    public static final List<String> MESSAGES = Collections.unmodifiableList(Arrays.asList(MESSAGE_LABELS));
    public static final String FILE_DATE_TIME_PATTERN = "yyyyMMddHHmmss";
    public static final String DEF_COLOR_NAME = "DefaultColor";
    public static final String DEF_DIM_NAME = "DefaultDimension";
    public static final String DEF_CAT_NAME = "DefaultCategory";
    public static final String EXCEL_TEMPLATE_TYPE = "excelTemplateType";
    public static final String FACILITY_ID = "facilityId";
    public static final String PRICAT_FILE = "__PRICAT_FILE__";
    public static final String DEFAULT_PRICAT_TYPE = "sample_pricat";
    public static final Map<String, String> TYPE_LABELS = UtilMisc.toMap("sample_pricat", "SamplePricatTemplate",
            "ofbiz_pricat", "OFBizPricatTemplate");
    private static final String MODULE = PricatParseExcelHtmlThread.class.getName();
    private static final String RESOURCE = "PricatUiLabels";
    private LocalDispatcher dispatcher;
    private Delegator delegator;
    private List<FileItem> fileItems;
    private File pricatFile;
    private String userLoginId;
    private GenericValue userLogin;
    private Map<String, String[]> facilities = new HashMap<>();
    private HttpSession session;
    private String selectedPricatType = DEFAULT_PRICAT_TYPE;
    private InterfacePricatParser pricatParser;
    private String thruReasonId = "EXCEL_IMPORT_SUCCESS";

    /**
     * Constructor, creates a new html thread.
     * @param request
     * @param response
     * @param name
     */
    public PricatParseExcelHtmlThread(HttpServletRequest request, HttpServletResponse response, String name) {

        super(request, response, name);
        dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        setDelegator(dispatcher.getDelegator());
        userLogin = (GenericValue) request.getSession().getAttribute("userLogin");
        if (UtilValidate.isEmpty(userLogin)) {
            initHtmlReport(request, response, true, true);
            getReport().println(UtilProperties.getMessage(RESOURCE, "PricatRequireUserLogin", getLocale()), InterfaceReport.FORMAT_ERROR);
            return;
        } else {
            userLoginId = userLogin.getString("userLoginId");
            session = request.getSession();
        }
        long sequenceNum = addExcelImportHistory();
        File userFolder = FileUtil.getFile(InterfacePricatParser.TEMP_FILES_FOLDER + userLoginId + "/");
        if (!userFolder.exists()) {
            userFolder.mkdirs();
        }
        String logFileName = InterfacePricatParser.TEMP_FILES_FOLDER + userLoginId + "/" + sequenceNum + ".log";
        initHtmlReport(request, response, true, true, logFileName);
        if (sequenceNum > 0) {
            getReport().setSequenceNum(sequenceNum);
            getReport().addLogFile(logFileName);
        }
        try {
            getReport().print(UtilProperties.getMessage(RESOURCE, "StartStoreExcel", getLocale()), InterfaceReport.FORMAT_HEADLINE);
            ServletFileUpload dfu = new ServletFileUpload(new DiskFileItemFactory(10240, userFolder));
            fileItems = UtilGenerics.cast(dfu.parseRequest(request));
        } catch (FileUploadException e) {
            getReport().addError(e);
        }
        if (UtilValidate.isEmpty(fileItems)) {
            getReport().println(UtilProperties.getMessage(RESOURCE, "NoFileUploaded", getLocale()), InterfaceReport.FORMAT_ERROR);
        } else {
            getReport().println(UtilProperties.getMessage(RESOURCE, "ok", getLocale()), InterfaceReport.FORMAT_OK);
        }
    }

    @Override
    public String getReportUpdate() {
        return getReport().getReportUpdate();
    }

    @Override
    public void run() {
        try {
            if (getName().startsWith(PARSE_EXCEL) && UtilValidate.isNotEmpty(fileItems)) {
                getReport().println();
                getReport().println(UtilProperties.getMessage(RESOURCE, "StartParsePricat", getLocale()), InterfaceReport.FORMAT_HEADLINE);
                if (prepareParse()) {
                    if (selectedPricatType.equals(DEFAULT_PRICAT_TYPE)) {
                        pricatParser = new SamplePricatParser(dispatcher, delegator, getLocale(), getReport(), facilities, pricatFile, userLogin);
                    }
                    if (UtilValidate.isEmpty(pricatParser)) {
                        getReport().println(UtilProperties.getMessage(RESOURCE, "NoPricatParserFor", getLocale()), InterfaceReport.FORMAT_ERROR);
                    } else {
                        pricatParser.parsePricatExcel();
                        getReport().println(UtilProperties.getMessage(RESOURCE, "PricatParseCompleted", getLocale()),
                                InterfaceReport.FORMAT_HEADLINE);
                    }
                }
            } else {
                getReport().println(getName(), InterfaceReport.FORMAT_ERROR);
                Debug.logError(getName(), MODULE);
                thruReasonId = "EXCEL_IMPORT_ERROR";
            }
        } catch (Exception e) {
            getReport().println(e);
            Debug.logError(e, MODULE);
            thruReasonId = "EXCEL_IMPORT_ERROR";
        } finally {
            // wait 5 seconds to wait page output
            try {
                sleep(5000);
            } catch (InterruptedException e) {
            }
            // call report update to make sure all messages are output to file
            getReport().getReportUpdate();
            String logFileName = getReport().closeLogFile();
            if (UtilValidate.isNotEmpty(pricatParser)) {
                if ("EXCEL_IMPORT_SUCCESS".equals(thruReasonId) && pricatParser.hasErrorMessages()) {
                    thruReasonId = "EXCEL_IMPORT_QUEST";
                }
                pricatParser.endExcelImportHistory(logFileName, thruReasonId);
            }
        }
    }

    private boolean prepareParse() throws IOException {
        // 1 get facilities belong to current userLogin
        facilities = getCurrentUserLoginFacilities();
        if (UtilValidate.isEmpty(facilities)) {
            getReport().println(UtilProperties.getMessage(RESOURCE, "CurrentUserLoginNoFacility", new Object[]{userLoginId}, getLocale()),
                    InterfaceReport.FORMAT_ERROR);
            return false;
        } else {
            getReport().println(" ... " + UtilProperties.getMessage(RESOURCE, "ok", getLocale()), InterfaceReport.FORMAT_OK);
            getReport().println();
        }
        // 2. store the pricat excel file
        if (!storePricatFile()) {
            return false;
        }
        return true;
    }

    private boolean storePricatFile() throws IOException {
        FileItem fi = null;
        FileItem pricatFi = null;
        byte[] pricatBytes = {};
        // check excelTemplateType
        for (int i = 0; i < fileItems.size(); i++) {
            fi = fileItems.get(i);
            String fieldName = fi.getFieldName();
            if (fi.isFormField() && UtilValidate.isNotEmpty(fieldName)) {
                if (fieldName.equals(EXCEL_TEMPLATE_TYPE)) {
                    selectedPricatType = fi.getString();
                }
            }
        }
        getReport().print(UtilProperties.getMessage(RESOURCE, "ExcelTemplateTypeSelected", getLocale()), InterfaceReport.FORMAT_DEFAULT);
        if (TYPE_LABELS.containsKey(selectedPricatType)) {
            getReport().print(UtilProperties.getMessage(RESOURCE, TYPE_LABELS.get(selectedPricatType), getLocale()), InterfaceReport.FORMAT_DEFAULT);
            getReport().println(" ... " + UtilProperties.getMessage(RESOURCE, "ok", getLocale()), InterfaceReport.FORMAT_OK);
        } else {
            getReport().println(UtilProperties.getMessage(RESOURCE, TYPE_LABELS.get(selectedPricatType), getLocale()), InterfaceReport.FORMAT_ERROR);
            return false;
        }

        // store the file
        for (int i = 0; i < fileItems.size(); i++) {
            fi = fileItems.get(i);
            String fieldName = fi.getFieldName();
            if ("filename".equals(fieldName)) {
                pricatFi = fi;
                pricatBytes = pricatFi.get();
                Path path = Paths.get(fi.getName());
                pricatFile = new File(InterfacePricatParser.TEMP_FILES_FOLDER + userLoginId + "/" + path.getFileName().toString());
                FileOutputStream fos = new FileOutputStream(pricatFile);
                fos.write(pricatBytes);
                fos.flush();
                fos.close();
                session.setAttribute(PRICAT_FILE, pricatFile.getAbsolutePath());
            }
        }
        return true;
    }

    private Map<String, String[]> getCurrentUserLoginFacilities() {
        getReport().println();
        getReport().println(UtilProperties.getMessage(RESOURCE, "GetCurrentUserLoginFacility", getLocale()), InterfaceReport.FORMAT_DEFAULT);
        Map<String, Object> context = new HashMap<>();
        context.put("userLogin", userLogin);
        context.put("locale", getLocale());
        try {
            List<EntityCondition> orgConditions = new LinkedList<>();
            orgConditions.add(EntityCondition.makeCondition("onePartyIdFrom", EntityOperator.EQUALS, userLogin.getString("partyId")));
            orgConditions.add(EntityCondition.makeCondition("twoRoleTypeIdFrom", EntityOperator.EQUALS, "INTERNAL_ORGANIZATIO"));
            orgConditions.add(EntityCondition.makeCondition("twoRoleTypeIdTo", EntityOperator.EQUALS, "EMPLOYEE"));
            orgConditions.add(EntityCondition.makeCondition("twoRoleTypeIdTo", EntityOperator.EQUALS, "EMPLOYEE"));
            List<GenericValue> organizations = delegator.findList("PartyRelationshipToFrom", EntityCondition.makeCondition(orgConditions), null,
                    null, null, false);
            Timestamp now = UtilDateTime.nowTimestamp();
            organizations = EntityUtil.filterByDate(organizations, now, "twoFromDate", "twoThruDate", true);
            organizations = EntityUtil.filterByDate(organizations, now, "oneFromDate", "oneThruDate", true);
            List<EntityCondition> ownerPartyConditions = new LinkedList<>();
            Set<String> orgPartyIds = new HashSet<>();
            for (GenericValue organization : organizations) {
                String orgPartyId = organization.getString("onePartyIdTo");
                if (!orgPartyIds.contains(orgPartyId)) {
                    ownerPartyConditions.add(EntityCondition.makeCondition("ownerPartyId", EntityOperator.EQUALS, orgPartyId));
                    orgPartyIds.add(orgPartyId);
                }
            }
            if (UtilValidate.isEmpty(ownerPartyConditions)) {
                return facilities;
            }
            List<GenericValue> facilityValues = delegator.findList("Facility", EntityCondition.makeCondition(ownerPartyConditions,
                    EntityOperator.OR), null, null, null, false);
            if (UtilValidate.isNotEmpty(facilityValues)) {
                int i = 1;
                for (GenericValue facilityValue : facilityValues) {
                    if (UtilValidate.isNotEmpty(facilityValue)) {
                        String facilityId = facilityValue.getString("facilityId");
                        if (!facilities.containsKey(facilityId)) {
                            String facilityName = facilityValue.getString("facilityName");
                            facilities.put(facilityId, new String[]{facilityName, facilityValue.getString("ownerPartyId")});
                            getReport().println(UtilProperties.getMessage(RESOURCE, "FacilityFoundForCurrentUserLogin",
                                    new Object[]{String.valueOf(i), facilityName, facilityId}, getLocale()), InterfaceReport.FORMAT_NOTE);
                            i++;
                        }
                    }
                }
            }
        } catch (GenericEntityException e) {
            Debug.logError(e.getMessage(), MODULE);
        }
        return facilities;
    }
    /** getDelegator */
    public Delegator getDelegator() {
        return delegator;
    }
    /** setDelegator */
    public void setDelegator(Delegator delegator) {
        this.delegator = delegator;
    }
    /** addExcelImportHistory */
    public synchronized long addExcelImportHistory() {
        long latestId = 1;
        try {
            GenericValue latestHistoryValue = EntityQuery.use(delegator).from("ExcelImportHistory").where("userLoginId", userLoginId).orderBy(
                    "sequenceNum DESC").queryFirst();
            if (UtilValidate.isNotEmpty(latestHistoryValue)) {
                latestId = latestHistoryValue.getLong("sequenceNum") + 1;
            }
            GenericValue newHistoryValue = delegator.makeValue("ExcelImportHistory", UtilMisc.toMap("sequenceNum", latestId, "userLoginId",
                    userLoginId,
                    "fileName", pricatFile == null ? "" : pricatFile.getName(), "statusId", isAlive() ? "EXCEL_IMPORTING" : "EXCEL_IMPORTED",
                    "fromDate", UtilDateTime.nowTimestamp(), "threadName", getName(), "logFileName",
                    InterfacePricatParser.TEMP_FILES_FOLDER + userLoginId + "/" + latestId + ".log"));
            newHistoryValue.create();
        } catch (GenericEntityException e) {
            Debug.logError(e, MODULE);
            return -1;
        }
        return latestId;
    }
}
