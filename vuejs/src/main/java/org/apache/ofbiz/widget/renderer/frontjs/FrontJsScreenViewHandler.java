/*
 Licensed to the Apache Software Foundation (ASF) under one
 or more contributor license agreements.  See the NOTICE file
 distributed with this work for additional information
 regarding copyright ownership.  The ASF licenses this file
 to you under the Apache License, Version 2.0 (the
 "License"); you may not use this file except in compliance
 with the License.  You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing,
 software distributed under the License is distributed on an
 "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 KIND, either express or implied.  See the License for the
 specific language governing permissions and limitations
 under the License.
 */

package org.apache.ofbiz.widget.renderer.frontjs;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.ofbiz.base.lang.JSON;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.GeneralException;
import org.apache.ofbiz.base.util.UtilCodec;
import org.apache.ofbiz.base.util.UtilHttp;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.base.util.collections.MapStack;
import org.apache.ofbiz.webapp.view.AbstractViewHandler;
import org.apache.ofbiz.webapp.view.ViewHandlerException;
import org.apache.ofbiz.widget.model.ModelTheme;
import org.apache.ofbiz.widget.renderer.FormStringRenderer;
import org.apache.ofbiz.widget.renderer.MenuStringRenderer;
import org.apache.ofbiz.widget.renderer.ScreenRenderer;
import org.apache.ofbiz.widget.renderer.ScreenStringRenderer;
import org.apache.ofbiz.widget.renderer.TreeStringRenderer;
import org.apache.ofbiz.widget.renderer.VisualTheme;
import org.xml.sax.SAXException;

import freemarker.template.TemplateException;
import freemarker.template.utility.StandardCompress;


public class FrontJsScreenViewHandler extends AbstractViewHandler {

    private static final String MODULE = FrontJsScreenViewHandler.class.getName();

    protected ServletContext servletContext = null;

    @Override
    public void init(ServletContext context) {
        this.servletContext = context;
    }

    private ScreenStringRenderer loadRenderers(FrontJsOutput output,
                                               HttpServletRequest request, HttpServletResponse response,
                                               Map<String, Object> context) {
        ScreenStringRenderer screenStringRenderer = new FrontJsScreenRenderer(getName(), output);
        FormStringRenderer formStringRenderer = new FrontJsFormRenderer(output, request, response);
        context.put("formStringRenderer", formStringRenderer);

        TreeStringRenderer treeStringRenderer = new FrontJsTreeRenderer(output);
        context.put("treeStringRenderer", treeStringRenderer);
        MenuStringRenderer menuStringRenderer = new FrontJsMenuRenderer(output, request, response);
        context.put("menuStringRenderer", menuStringRenderer);
        return screenStringRenderer;
    }

    @Override
    public void render(String name, String page, String info, String contentType, String encoding, HttpServletRequest request, HttpServletResponse response) throws ViewHandlerException {
        try {
            Writer writer = response.getWriter();
            VisualTheme visualTheme = UtilHttp.getVisualTheme(request);
            ModelTheme modelTheme = visualTheme.getModelTheme();
            // compress output if configured to do so
            if (UtilValidate.isEmpty(encoding)) {
                encoding = modelTheme.getEncoding(getName());
            }
            boolean compressOutput = "compressed".equals(encoding);
            if (!compressOutput) {
                compressOutput = "true".equals(modelTheme.getCompress(getName()));
            }
            if (!compressOutput && this.servletContext != null) {
                compressOutput = "true".equals(this.servletContext.getAttribute("compressHTML"));
            }
            if (compressOutput) {
                // StandardCompress defaults to a 2k buffer. That could be increased
                // to speed up output.
                writer = new StandardCompress().getWriter(writer, null);
            }
            // writer will be not used during renderer, but only at the end of this method to send json result (the frontJsOutput)
            //   during all renderer process it's frontJsOutput which will be completed.
            FrontJsOutput frontJsOutput = new FrontJsOutput(name);
            MapStack<String> context = MapStack.create();
            ScreenRenderer.populateContextForRequest(context, null, request, response, servletContext);
            ScreenStringRenderer screenStringRenderer = loadRenderers(frontJsOutput, request, response, context);
            ScreenRenderer screens = new ScreenRenderer(writer, context, screenStringRenderer);
            context.put("screens", screens);
            context.put("simpleEncoder", UtilCodec.getEncoder(visualTheme.getModelTheme().getEncoder(getName())));
            screenStringRenderer.renderBegin(writer, context);
            screens.render(page);
            screenStringRenderer.renderEnd(writer, context);

            JSON json = JSON.from(frontJsOutput.output());
            String jsonStr = json.toString();
            // set the JSON content type
            response.setContentType("application/json");
            // jsonStr.length is not reliable for unicode characters
            response.setContentLength(jsonStr.getBytes("UTF8").length);
            writer.write(jsonStr);

            writer.flush();
        } catch (TemplateException e) {
            Debug.logError(e, "Error initializing screen renderer", MODULE);
            throw new ViewHandlerException(e.getMessage());
        } catch (IOException e) {
            throw new ViewHandlerException("Error in the response writer/output stream: " + e.toString(), e);
        } catch (SAXException | ParserConfigurationException e) {
            throw new ViewHandlerException("XML Error rendering page: " + e.toString(), e);
        } catch (GeneralException e) {
            throw new ViewHandlerException("Lower level error rendering page: " + e.toString(), e);
        }
    }
}
