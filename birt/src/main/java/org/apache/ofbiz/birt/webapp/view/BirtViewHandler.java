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
package org.apache.ofbiz.birt.webapp.view;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.GeneralException;
import org.apache.ofbiz.base.util.UtilGenerics;
import org.apache.ofbiz.base.util.UtilHttp;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.birt.BirtFactory;
import org.apache.ofbiz.birt.BirtWorker;
import org.apache.ofbiz.birt.flexible.BirtUtil;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.util.EntityUtilProperties;
import org.apache.ofbiz.webapp.view.ViewHandler;
import org.apache.ofbiz.webapp.view.ViewHandlerException;
import org.eclipse.birt.core.exception.BirtException;
import org.eclipse.birt.report.engine.api.IReportEngine;
import org.eclipse.birt.report.engine.api.IReportRunnable;
import org.xml.sax.SAXException;

public class BirtViewHandler implements ViewHandler {

    private static final String MODULE = BirtViewHandler.class.getName();
    private static final String RES_ERROR = "BirtErrorUiLabels";
    private ServletContext servletContext = null;

    private String name = "birt";

    @Override
    public void init(ServletContext context) throws ViewHandlerException {
        this.servletContext = context;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public void render(String name, String page, String info,
            String contentType, String encoding, HttpServletRequest request,
            HttpServletResponse response) throws ViewHandlerException {

        try {
            IReportEngine engine = org.apache.ofbiz.birt.BirtFactory.getReportEngine();
            // open report design
            IReportRunnable design = null;

            // add dynamic parameter for page
            if (UtilValidate.isEmpty(page) || "ExecuteFlexibleReport".equals(page)) {
                page = request.getParameter("rptDesignFile");
            }
            if (UtilValidate.isEmpty(page)) {
                Locale locale = request.getLocale();
                throw new ViewHandlerException(UtilProperties.getMessage(RES_ERROR, "BirtErrorNotPublishedReport", locale));
            }
            if (page.startsWith("component://")) {
                InputStream reportInputStream = BirtFactory.getReportInputStreamFromLocation(page);
                design = engine.openReportDesign(reportInputStream);
            } else {
                design = engine.openReportDesign(page);
            }

            Map<String, Object> appContext = UtilGenerics.cast(engine.getConfig().getAppContext());
            BirtWorker.setWebContextObjects(appContext, request, response);

            Map<String, Object> context = new HashMap<>();
            // set parameters from request
            Map<String, Object> parameters = UtilGenerics.cast(request.getAttribute(BirtWorker.getBirtParameters()));
            if (parameters != null) {
                context.put(BirtWorker.getBirtParameters(), parameters);
            } else {
                context.put(BirtWorker.getBirtParameters(), UtilHttp.getParameterMap(request));
            }
            // set locale from request
            Locale locale = (Locale) request.getAttribute(BirtWorker.getBirtLocale());
            if (locale == null) {
                locale = UtilHttp.getLocale(request);
            }

            // set override content type
            String overrideContentType = request.getParameter(BirtWorker.getBirtContentType());
            if (UtilValidate.isNotEmpty(overrideContentType)) {
                contentType = overrideContentType;
            }

            // set output file name to get also file extension
            String outputFileName = request.getParameter(BirtWorker.getBirtOutputFileName());
            if (UtilValidate.isNotEmpty(outputFileName)) {
                outputFileName = BirtUtil.encodeReportName(outputFileName);
                String format = BirtUtil.getMimeTypeFileExtension(contentType);
                if (!outputFileName.endsWith(format)) {
                    outputFileName = outputFileName.concat(format);
                }
                outputFileName = UtilHttp.canonicalizeParameter(outputFileName);
                response.setHeader("Content-Disposition", "attachment; filename=" + outputFileName);
            }

            // checking consistency between Birt content type and response content type
            if (!contentType.equals(response.getContentType())) {
                response.setContentType(contentType);
            }

            context.put(BirtWorker.getBirtLocale(), locale);
            Delegator delegator = (Delegator) request.getAttribute("delegator");
            String birtImageDirectory = EntityUtilProperties.getPropertyValue("birt", "birt.html.image.directory", delegator);
            context.put(BirtWorker.getBirtImageDirectory(), birtImageDirectory);
            BirtWorker.exportReport(design, context, contentType, response.getOutputStream());
        } catch (BirtException e) {
            throw new ViewHandlerException("Birt Error create engine: " + e.toString(), e);
        } catch (IOException e) {
            throw new ViewHandlerException("Error in the response writer/output stream: " + e.toString(), e);
        } catch (SQLException e) {
            throw new ViewHandlerException("get connection error: " + e.toString(), e);
        } catch (GenericEntityException e) {
            throw new ViewHandlerException("generic entity error: " + e.toString(), e);
        } catch (GeneralException e) {
            throw new ViewHandlerException("general error: " + e.toString(), e);
        } catch (SAXException se) {
            String errMsg = "Error SAX rendering " + page + " view handler: " + se.toString();
            Debug.logError(se, errMsg, MODULE);
            throw new ViewHandlerException(errMsg, se);
        } catch (ParserConfigurationException pe) {
            String errMsg = "Error parser rendering " + page + " view handler: " + pe.toString();
            Debug.logError(pe, errMsg, MODULE);
            throw new ViewHandlerException(errMsg, pe);
        }
    }
}
