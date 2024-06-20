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
package org.apache.ofbiz.graphql;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilGenerics;
import org.apache.ofbiz.webapp.WebAppUtil;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.util.Enumeration;

public class AppServletContextListener implements ServletContextListener {

    public static final String MODULE = AppServletContextListener.class.getName();

    /**
     * @param sce
     */
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext servletContext = sce.getServletContext();
        // initialize the delegator
        WebAppUtil.getDelegator(servletContext);
        // initialize security
        WebAppUtil.getSecurity(servletContext);
        // initialize the services dispatcher
        WebAppUtil.getDispatcher(servletContext);

        Enumeration<String> initParamEnum = UtilGenerics.cast(sce.getServletContext().getInitParameterNames());
        while (initParamEnum.hasMoreElements()) {
            String initParamName = initParamEnum.nextElement();
            String initParamValue = sce.getServletContext().getInitParameter(initParamName);
            if (Debug.verboseOn()) {
                Debug.logVerbose("Adding web.xml context-param to application attribute with name [" + initParamName + "] and value ["
                        + initParamValue + "]", MODULE);
            }
            sce.getServletContext().setAttribute(initParamName, initParamValue);
        }

    }

    /**
     * @param sce
     */
    public void contextDestroyed(ServletContextEvent sce) {
        ServletContext context = sce.getServletContext();
        Debug.logInfo("GraphQL Context destroyed, removing delegator and dispatcher ", MODULE);
        context.removeAttribute("delegator");
        context.removeAttribute("dispatcher");
    }


}
