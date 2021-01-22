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
package org.apache.ofbiz.pricat.sample;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ofbiz.base.location.ComponentLocationResolver;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilHttp;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.pricat.PricatEvents;

public class SamplePricatEvents extends PricatEvents {
    private static final String MODULE = SamplePricatEvents.class.getName();
    public static final String PRICAT_LAT_VERSION = UtilProperties.getPropertyValue("pricat", "pricat.latest.version", "V1.1");
    public static final String DEMO_PRICATE_FILE_NAME = "SamplePricatTemplate_" + PRICAT_LAT_VERSION + ".xlsx";
    public static final String DEMO_PRICATE_PATH = "component://pricat/webapp/pricatdemo/downloads/";
    /**
     * Download excel template.
     * @param request
     * @param response
     * @return
     */
    public static String downloadExcelTemplate(HttpServletRequest request, HttpServletResponse response) {
        String templateType = request.getParameter("templateType");
        if (UtilValidate.isEmpty(templateType)) {
            return "error";
        }
        try {
            String path = ComponentLocationResolver.getBaseLocation(DEMO_PRICATE_PATH).toString();
            String fileName = null;
            if ("pricatExcelTemplate".equals(templateType)) {
                fileName = DEMO_PRICATE_FILE_NAME;
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
}
