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
package org.apache.ofbiz.birt;

import java.io.File;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.GeneralException;
import org.apache.ofbiz.base.util.UtilGenerics;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.base.util.string.FlexibleStringExpander;
import org.apache.ofbiz.birt.flexible.BirtUtil;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceUtil;
import org.apache.ofbiz.webapp.WebAppUtil;
import org.eclipse.birt.report.engine.api.EXCELRenderOption;
import org.eclipse.birt.report.engine.api.EngineConfig;
import org.eclipse.birt.report.engine.api.EngineException;
import org.eclipse.birt.report.engine.api.HTMLRenderOption;
import org.eclipse.birt.report.engine.api.HTMLServerImageHandler;
import org.eclipse.birt.report.engine.api.IPDFRenderOption;
import org.eclipse.birt.report.engine.api.IReportEngine;
import org.eclipse.birt.report.engine.api.IReportRunnable;
import org.eclipse.birt.report.engine.api.IRunAndRenderTask;
import org.eclipse.birt.report.engine.api.PDFRenderOption;
import org.eclipse.birt.report.engine.api.RenderOption;

public final class BirtWorker {

    private static final String MODULE = BirtWorker.class.getName();

    private static final String BIRT_PARAMETERS = "birtParameters";
    private static final String BIRT_LOCALE = "birtLocale";
    private static final String BIRT_IMAGE_DIRECTORY = "birtImageDirectory";
    private static final String BIRT_CONTENT_TYPE = "birtContentType";
    private static final String BIRT_OUTPUT_FILE_NAME = "birtOutputFileName";
    private static final String RES_ERROR = "BirtErrorUiLabels";

    private static final HTMLServerImageHandler IMAGE_HANDLER = new HTMLServerImageHandler();

    private BirtWorker() { }

    private static final Map<Integer, Level> LEVEL_INT_MAP = new HashMap<>();
    static {
        LEVEL_INT_MAP.put(Debug.ERROR, Level.SEVERE);
        LEVEL_INT_MAP.put(Debug.TIMING, Level.FINE);
        LEVEL_INT_MAP.put(Debug.INFO, Level.INFO);
        LEVEL_INT_MAP.put(Debug.IMPORTANT, Level.INFO);
        LEVEL_INT_MAP.put(Debug.WARNING, Level.WARNING);
        LEVEL_INT_MAP.put(Debug.ERROR, Level.SEVERE);
        LEVEL_INT_MAP.put(Debug.FATAL, Level.ALL);
        LEVEL_INT_MAP.put(Debug.ALWAYS, Level.ALL);
    }

    /**
     * export report
     * @param design
     * @param context
     * @param contentType
     * @param output
     * @throws EngineException
     * @throws GeneralException
     * @throws SQLException
     */
    public static void exportReport(IReportRunnable design, Map<String, ? extends Object> context, String contentType, OutputStream output)
            throws EngineException, GeneralException, SQLException {

        Locale birtLocale = (Locale) context.get(BIRT_LOCALE);
        String birtImageDirectory = (String) context.get(BIRT_IMAGE_DIRECTORY);

        if (contentType == null) {
            contentType = "text/html";
        } else {
            contentType = contentType.toLowerCase(Locale.getDefault());
        }
        if (birtImageDirectory == null) {
            birtImageDirectory = "/";
        }
        Debug.logInfo("Get report engine", MODULE);
        IReportEngine engine = BirtFactory.getReportEngine();

        IRunAndRenderTask task = engine.createRunAndRenderTask(design);
        if (birtLocale != null) {
            Debug.logInfo("Set BIRT locale:" + birtLocale, MODULE);
            task.setLocale(birtLocale);
        }

        // set parameters if exists
        Map<String, Object> parameters = UtilGenerics.cast(context.get(BirtWorker.getBirtParameters()));
        if (parameters != null) {
            //Debug.logInfo("Set BIRT parameters:" + parameters, MODULE);
            task.setParameterValues(parameters);
        }

        // set output options
        if (!BirtUtil.isSupportedMimeType(contentType)) {
            throw new GeneralException("Unknown content type : " + contentType);
        }
        RenderOption options = new RenderOption();
        options.setOutputFormat(BirtUtil.getMimeTypeOutputFormat(contentType));

        //specific process for mimetype
        if ("text/html".equals(contentType)) { // HTML
            HTMLRenderOption htmlOptions = new HTMLRenderOption(options);
            htmlOptions.setImageDirectory(birtImageDirectory);
            htmlOptions.setBaseImageURL(birtImageDirectory);
            options.setImageHandler(IMAGE_HANDLER);
        } else if ("application/pdf".equals(contentType)) { // PDF
            PDFRenderOption pdfOptions = new PDFRenderOption(options);
            pdfOptions.setOption(IPDFRenderOption.PAGE_OVERFLOW, Boolean.TRUE);
        } else if ("application/vnd.ms-excel".equals(contentType)) { // MS Excel
            new EXCELRenderOption(options);
        }

        options.setOutputStream(output);
        task.setRenderOption(options);

        // run report
        if (Debug.infoOn()) {
            Debug.logInfo("BIRT's locale is: " + task.getLocale(), MODULE);
            Debug.logInfo("Run report's task", MODULE);
        }
        task.run();
        task.close();
    }

    /**
     * set web context objects
     * @param appContext
     * @param request
     * @param response
     */
    public static void setWebContextObjects(Map<String, Object> appContext, HttpServletRequest request, HttpServletResponse response)
            throws GeneralException {
        HttpSession session = request.getSession();
        ServletContext servletContext = session.getServletContext();

        if (appContext == null || servletContext == null) {
            throw new GeneralException("The context reporting is empty, check your configuration");
        }

        // initialize the delegator
        appContext.put("delegator", WebAppUtil.getDelegator(servletContext));
        // initialize security
        appContext.put("security", WebAppUtil.getSecurity(servletContext));
        // initialize the services dispatcher
        appContext.put("dispatcher", WebAppUtil.getDispatcher(servletContext));
    }

    public static String getBirtParameters() {
        return BIRT_PARAMETERS;
    }

    public static String getBirtLocale() {
        return BIRT_LOCALE;
    }

    public static String getBirtImageDirectory() {
        return BIRT_IMAGE_DIRECTORY;
    }

    public static String getBirtContentType() {
        return BIRT_CONTENT_TYPE;
    }

    public static String getBirtOutputFileName() {
        return BIRT_OUTPUT_FILE_NAME;
    }

    //TODO documentation
    public static String recordReportContent(Delegator delegator, LocalDispatcher dispatcher, Map<String, Object> context) throws GeneralException {
        Locale locale = (Locale) context.get("locale");
        String description = (String) context.get("description");
        String reportName = (String) context.get("reportName");
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        String entityViewName = (String) context.get("entityViewName");
        String serviceName = (String) context.get("serviceName");
        String masterContentId = (String) context.get("masterContentId");
        String dataResourceId = delegator.getNextSeqId("DataResource");
        String contentId = delegator.getNextSeqId("Content");
        context.put("contentId", contentId);

        if (UtilValidate.isEmpty(serviceName) && UtilValidate.isEmpty(entityViewName)) {
            throw new GenericServiceException("Service and entity name cannot be both empty");
        }

        String modelElementName = null;
        String workflowType = null;
        if (UtilValidate.isEmpty(serviceName)) {
            modelElementName = entityViewName;
            workflowType = "Entity";
        } else {
            modelElementName = serviceName;
            workflowType = "Service";
        }

        EntityCondition entityConditionRpt = EntityCondition.makeCondition("contentTypeId", "RPTDESIGN");
        String templatePathLocation = BirtUtil.resolveTemplatePathLocation();
        File templatePathLocationDir = new File(templatePathLocation);
        if (!templatePathLocationDir.exists()) {
            boolean created = templatePathLocationDir.mkdirs();
            if (!created) {
                throw new GeneralException(UtilProperties.getMessage(RES_ERROR, "BirtErrorCannotLocateReportFolder", locale));
            }
        }
        int i = 0;
        String templateFileLocation = null;
        EntityCondition ecl = null;
        do {
            StringBuffer rptDesignNameSb = new StringBuffer(templatePathLocation);
            rptDesignNameSb.append(BirtUtil.encodeReportName(reportName));
            rptDesignNameSb.append("_").append(i);
            rptDesignNameSb.append(".rptdesign");
            templateFileLocation = rptDesignNameSb.toString();
            EntityCondition entityConditionOnName = EntityCondition.makeCondition("drObjectInfo", templateFileLocation);
            ecl = EntityCondition.makeCondition(UtilMisc.toList(entityConditionRpt, entityConditionOnName));
            i++;
        } while (EntityQuery.use(delegator).from("ContentDataResourceView").where(ecl).queryCount() > 0);

        //resolve the initial form structure from master content
        Map<String, Object> resultElectronicText = dispatcher.runSync("getElectronicText", UtilMisc.toMap("contentId", masterContentId, "locale",
                locale, "userLogin", userLogin));
        if (ServiceUtil.isError(resultElectronicText)) {
            throw new GeneralException(ServiceUtil.getErrorMessage(resultElectronicText));
        }
        String reportForm = (String) resultElectronicText.get("textData");
        if (!reportForm.startsWith("<?xml")) {
            StringBuffer xmlHeaderForm = new StringBuffer("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            xmlHeaderForm.append("<forms xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                    + "xmlns=\"http://ofbiz.apache.org/Widget-Form\" "
                    + "xsi:schemaLocation=\"http://ofbiz.apache.org/Widget-Form "
                    + "http://ofbiz.apache.org/dtds/widget-form.xsd\">");
            xmlHeaderForm.append(reportForm);
            xmlHeaderForm.append("</forms>");
            reportForm = xmlHeaderForm.toString();
        }
        FlexibleStringExpander reportFormExpd = FlexibleStringExpander.getInstance(reportForm);
        reportForm = reportFormExpd.expandString(context);

        //create content and dataressource strucutre
        Map<String, Object> result = null;
        result = dispatcher.runSync("createDataResource", UtilMisc.toMap("dataResourceId", dataResourceId, "dataResourceTypeId", "ELECTRONIC_TEXT",
                "dataTemplateTypeId", "FORM_COMBINED", "userLogin", userLogin));
        if (ServiceUtil.isError(result)) {
            throw new GeneralException(ServiceUtil.getErrorMessage(result));
        }
        result = dispatcher.runSync("createElectronicTextForm", UtilMisc.toMap("dataResourceId", dataResourceId, "textData", reportForm, "userLogin",
                userLogin));
        if (ServiceUtil.isError(result)) {
            throw new GeneralException(ServiceUtil.getErrorMessage(result));
        }
        result = dispatcher.runSync("createContent", UtilMisc.toMap("contentId", contentId, "contentTypeId", "FLEXIBLE_REPORT", "dataResourceId",
                dataResourceId, "statusId", "CTNT_IN_PROGRESS", "contentName", reportName, "description", description, "userLogin", userLogin));
        if (ServiceUtil.isError(result)) {
            throw new GeneralException(ServiceUtil.getErrorMessage(result));
        }
        String dataResourceIdRpt = delegator.getNextSeqId("DataResource");
        String contentIdRpt = delegator.getNextSeqId("Content");
        String rptDesignName = BirtUtil.encodeReportName(reportName);
        if (!rptDesignName.endsWith(".rptdesign")) {
            rptDesignName = rptDesignName.concat(".rptdesign");
        }
        result = dispatcher.runSync("createDataResource", UtilMisc.toMap("dataResourceId", dataResourceIdRpt, "dataResourceTypeId", "LOCAL_FILE",
                "mimeTypeId", "text/rptdesign", "dataResourceName", rptDesignName, "objectInfo", templateFileLocation, "userLogin", userLogin));
        if (ServiceUtil.isError(result)) {
            throw new GeneralException(ServiceUtil.getErrorMessage(result));
        }
        result = dispatcher.runSync("createContent", UtilMisc.toMap("contentId", contentIdRpt, "contentTypeId", "RPTDESIGN", "dataResourceId",
                dataResourceIdRpt, "statusId", "CTNT_PUBLISHED", "contentName", reportName, "description", description + " (.rptDesign file)",
                "userLogin", userLogin));
        if (ServiceUtil.isError(result)) {
            throw new GeneralException(ServiceUtil.getErrorMessage(result));
        }
        result = dispatcher.runSync("createContentAssoc", UtilMisc.toMap("contentId", masterContentId, "contentIdTo", contentId,
                "contentAssocTypeId", "SUB_CONTENT", "userLogin", userLogin));
        if (ServiceUtil.isError(result)) {
            throw new GeneralException(ServiceUtil.getErrorMessage(result));
        }
        result = dispatcher.runSync("createContentAssoc", UtilMisc.toMap("contentId", contentId, "contentIdTo", contentIdRpt, "contentAssocTypeId",
                "SUB_CONTENT", "userLogin", userLogin));
        if (ServiceUtil.isError(result)) {
            throw new GeneralException(ServiceUtil.getErrorMessage(result));
        }
        result = dispatcher.runSync("createContentAttribute", UtilMisc.toMap("contentId", contentId, "attrName", workflowType, "attrValue",
                modelElementName, "userLogin", userLogin));
        if (ServiceUtil.isError(result)) {
            throw new GeneralException(ServiceUtil.getErrorMessage(result));
        }
        return contentId;
    }

    /**
     * initialize configuration log with the low level present on debug.properties
     * @param config
     */
    public static void setLogConfig(EngineConfig config) {
        String ofbizHome = System.getProperty("ofbiz.home");
        int lowerLevel = 0;
        //resolve the lower level open on debug.properties, maybe it's better to implement correctly log4j here
        for (int i = 1; i < 7; i++) {
            if (Debug.isOn(i)) {
                lowerLevel = i;
                break;
            }
        }
        config.setLogConfig(UtilProperties.getPropertyValue("debug", "log4j.appender.css.dir", ofbizHome + "/runtime/logs/"),
                LEVEL_INT_MAP.get(lowerLevel));
    }
}
