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
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.GeneralException;
import org.apache.ofbiz.base.util.StringUtil;
import org.apache.ofbiz.base.util.UtilGenerics;
import org.apache.ofbiz.base.util.UtilHttp;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.webapp.control.RequestHandler;
import org.apache.ofbiz.webapp.taglib.ContentUrlTag;
import org.apache.ofbiz.widget.WidgetWorker;
import org.apache.ofbiz.widget.model.AbstractModelAction;
import org.apache.ofbiz.widget.model.ModelForm;
import org.apache.ofbiz.widget.model.ModelMenu;
import org.apache.ofbiz.widget.model.ModelMenuItem;
import org.apache.ofbiz.widget.model.ModelScreen;
import org.apache.ofbiz.widget.model.ModelScreenWidget;
import org.apache.ofbiz.widget.model.ModelScreenWidget.ColumnContainer;
import org.apache.ofbiz.widget.model.ModelScreenWidget.ScreenImage;
import org.apache.ofbiz.widget.model.ModelTheme;
import org.apache.ofbiz.widget.model.ScreenFactory;
import org.apache.ofbiz.widget.renderer.MenuStringRenderer;
import org.apache.ofbiz.widget.renderer.Paginator;
import org.apache.ofbiz.widget.renderer.ScreenStringRenderer;
import org.apache.ofbiz.widget.renderer.VisualTheme;
import org.xml.sax.SAXException;

public class FrontJsScreenRenderer implements ScreenStringRenderer {
    private static final String MODULE = FrontJsScreenRenderer.class.getName();
    private FrontJsOutput output;
    private String rendererName;
    private int screenLetsIdCounter = 1; // not really usable because most of time FrontJsRenderer is call for just a screenlet,
                                         //  not for a screen with multiple screenlet included

    FrontJsScreenRenderer(String name, FrontJsOutput output) {
        this.output = output;
        rendererName = name;
    }
    public String getRendererName() {
        return rendererName;
    }

    public void renderScreenBegin(Appendable writer, Map<String, Object> context) throws IOException {
        // nothing to do, it's only a human logic readable element
    }

    public void renderScreenEnd(Appendable writer, Map<String, Object> context) throws IOException {
        // nothing to do, it's only a human logic readable element
    }

    public void renderSectionBegin(Appendable writer, Map<String, Object> context, ModelScreenWidget.Section section) throws IOException {
        // nothing to do, it's only a human logic readable element
    }
    public void renderSectionEnd(Appendable writer, Map<String, Object> context, ModelScreenWidget.Section section) throws IOException {
        // nothing to do, it's only a human logic readable element
    }

    public void renderContainerBegin(Appendable writer, Map<String, Object> context, ModelScreenWidget.Container container) throws IOException {
        String containerId = container.getId(context);
        String type = container.getType(context);
        if (UtilValidate.isNotEmpty(type)) {
            throw new IOException("FrontJsRender: type property in container tag, not yet implemented in container"
                               + ((containerId!=null)? "with id="+containerId : ""));
        }
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("id", containerId); // used as areaId
        String autoUpdateTarget = container.getAutoUpdateTargetExdr(context);
        if (UtilValidate.isNotEmpty(autoUpdateTarget)) {
            parameters.put("autoUpdateTarget", autoUpdateTarget);
        }
        String watcherName = container.getWatcherNameExdr(context);
        if (UtilValidate.isNotEmpty(watcherName)) {
            List<String> watcherList = StringUtil.split(watcherName,",");
            if (watcherList.size()>1) {
                StringBuilder watcherNameStr =  new StringBuilder();
                watcherNameStr.append(watcherList.get(0).trim());
                for (int i = 1; i < watcherList.size(); i++) {
                    watcherNameStr.append("-");
                    watcherNameStr.append(watcherList.get(i).trim());
                }
                parameters.put("watcherName", watcherNameStr.toString());
            } else {
                parameters.put("watcherName", watcherName);
            }
        }
        if (! "2".equals(container.getAutoUpdateInterval(context))) { // 2 is the default value, if empty
            throw new IOException("FrontJsRender: auto-update-interval property in container tag, not yet implemented in container"
                    + ((containerId!=null)? "with id="+containerId : ""));
        }
        if (UtilValidate.isNotEmpty(container.getStyle(context))) {
            Debug.logWarning("style property is used (="+container.getStyle(context)+
                             ") in container with id="+containerId+" it's not manage by FrontFjRenderer", MODULE);
            parameters.put("style", container.getStyle(context));
        }
        this.output.pushScreen("ContainerOpen", parameters);
    }

    public void renderContainerEnd(Appendable writer, Map<String, Object> context, ModelScreenWidget.Container container) throws IOException {
        this.output.popScreen("ContainerClose");
    }

    public void renderLabel(Appendable writer, Map<String, Object> context, ModelScreenWidget.Label label) throws IOException {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("text", label.getText(context));
        if (UtilValidate.isNotEmpty(label.getId(context))) attributes.put("id", label.getId(context));
        if (UtilValidate.isNotEmpty(label.getStyle(context))) attributes.put("style", label.getStyle(context));
        this.output.putScreen("Label", attributes);
    }

    public void renderVueJs(Appendable writer, Map<String, Object> context, ModelScreenWidget.VueJs vuejs) throws IOException {
        Map<String, Object> attributes = vuejs.getParameterMap(context);
        attributes.put("componentName", vuejs.getComponentName(context));
        this.output.putScreen("VueJs", attributes);
    }

    public void renderHorizontalSeparator(Appendable writer, Map<String, Object> context, ModelScreenWidget.HorizontalSeparator separator) throws IOException {
        Map<String, Object> attributes = new HashMap<>();
        if (UtilValidate.isNotEmpty(separator.getId(context))) {
            Debug.logWarning("separator id is used (="+separator.getId(context)+
                             ")  it's not manage by FrontFjRenderer", MODULE);
            attributes.put("id", separator.getId(context));
        }
        if (UtilValidate.isNotEmpty(separator.getName())) {
            Debug.logWarning("separator name is used (="+separator.getName()+
                             ")  it's not manage by FrontFjRenderer", MODULE);
            attributes.put("name", separator.getName());
        }
        if (UtilValidate.isNotEmpty(separator.getStyle(context))) {
            Debug.logWarning("separator style is used (="+separator.getStyle(context)+
                             ")  it's not manage by FrontFjRenderer", MODULE);
            attributes.put("style", separator.getStyle(context));
        }
        this.output.putScreen("HorizontalSeparator", attributes);
    }

    // not yet tested, it's very, very similar to MenuRenderer.renderLink which is tested
    public void renderLink(Appendable writer, Map<String, Object> context, ModelScreenWidget.ScreenLink link) throws IOException {
        HttpServletRequest request = (HttpServletRequest) context.get("request");
        VisualTheme visualTheme = UtilHttp.getVisualTheme(request);
        ModelTheme modelTheme = visualTheme.getModelTheme();

        if (link.getLink().getRequestConfirmation()) {
            throw new IOException("Render (Macro and FrontJs): requestConfirmation is used in a screenLink and it's not yet implemented"
                               + "for link with target="+ link.getTarget(context));
        }

        Map<String, Object> parameters = new HashMap<>();
        String target = link.getTarget(context);
        if (UtilValidate.isNotEmpty(link.getId(context)))    parameters.put("id",    link.getId(context));
        if (UtilValidate.isNotEmpty(link.getStyle(context))) parameters.put("style", link.getStyle(context));
        if (UtilValidate.isNotEmpty(link.getName(context)))  parameters.put("name",  link.getName(context));
        if (UtilValidate.isNotEmpty(link.getText(context)))  parameters.put("text",  link.getText(context));
        String height = link.getHeight();
        if (UtilValidate.isEmpty(height)) {
            height = String.valueOf(modelTheme.getLinkDefaultLayeredModalHeight());
        }
        parameters.put("height", height);
        String width = link.getWidth();
        if (UtilValidate.isEmpty(width)) {
            width = String.valueOf(modelTheme.getLinkDefaultLayeredModalWidth());
        }
        parameters.put("width", width);
        // targetWindow is used for setArea, if link-type="anchor"
        if (UtilValidate.isNotEmpty(link.getTargetWindow(context))) parameters.put("targetWindow", link.getTargetWindow(context));
        if (UtilValidate.isNotEmpty(link.getUrlMode())) parameters.put("urlMode", link.getUrlMode());

        // uniqueItemName is used for link-type='hidden-form' but this link-type is not currently supported by vuejs
        String uniqueItemName = link.getModelScreen().getName() + "_LF_" + UtilMisc.<String>addToBigDecimalInMap(context, "screenUniqueItemIndex", BigDecimal.ONE);
        parameters.put("uniqueItemName", uniqueItemName);
        String linkType = "";
        if (UtilValidate.isNotEmpty(target)) {
            linkType = WidgetWorker.determineAutoLinkType(link.getLinkType(), target, link.getUrlMode(), request);
        }
        // Workaround OH 2019-03-04 currently in VueLink hidden-form is not correctly manage, so use "auto" as link-type not hidden-form
        //   should be study when hidden-form will be manage
        parameters.put("linkType", linkType);
        parameters.put("linkType", link.getLinkType());
        // End of workaround
        // linkUrl is no more sent but if link-type=inter-app it's needed to have String externalLoginKey = (String) request.getAttribute("externalLoginKey"); (cf WidgetWorker.buildHyperlinkUrl)
        parameters.put("target", target);
        parameters.put("parameterMap", link.getParameterMap(context));
        ScreenImage img = link.getImage();
        if (img != null) {
            parameters.put("img", createImageParameters(context, img));
        }
        this.output.putScreen("Link", parameters);
    }
    // Made this a separate method so it can be externalized and reused.
    // used by renderLink method
    // copy from MenuRenderer
    private Map<String, Object> createImageParameters(Map<String, Object> context, ScreenImage image) {
        HttpServletResponse response = (HttpServletResponse) context.get("response");
        HttpServletRequest request = (HttpServletRequest) context.get("request");
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("id", image.getId(context));
        parameters.put("style", image.getStyle(context));
        parameters.put("width", image.getWidth(context));
        parameters.put("height", image.getHeight(context));
        parameters.put("border", image.getBorder(context));
        // title attribute not exist for image in link in screen but exist in menu
        //parameters.put("title", image.getTitleExdr().expandString(context));
        String src = image.getSrc(context);
        if (UtilValidate.isNotEmpty(src) && request != null && response != null) {
            String urlMode = image.getUrlMode();
            if ("ofbiz".equalsIgnoreCase(urlMode)) {
                ServletContext ctx = (ServletContext) request.getAttribute("servletContext");
                RequestHandler rh = (RequestHandler) ctx.getAttribute("_REQUEST_HANDLER_");
                src = rh.makeLink(request, response, src, false, false, false);
            } else if ("content".equalsIgnoreCase(urlMode)) {
                StringBuilder newURL = new StringBuilder();
                ContentUrlTag.appendContentPrefix(request, newURL);
                newURL.append(src);
                src = newURL.toString();
            }
        }
        parameters.put("src", src);
        return parameters;
    }

    // not yet used, (no use case in screens.xml using FrontJsRenderer)
    //  currently Images is associated to vue-error to generate the Warning message in screen.
    public void renderImage(Appendable writer, Map<String, Object> context, ModelScreenWidget.ScreenImage image) throws IOException {
        if (image == null) {
            return ;
        }
        String src = image.getSrc(context);

        String urlMode = image.getUrlMode();
        boolean fullPath = false;
        boolean secure = false;
        boolean encode = false;
        HttpServletResponse response = (HttpServletResponse) context.get("response");
        HttpServletRequest request = (HttpServletRequest) context.get("request");
        String urlString = "";
        if (urlMode != null && "intra-app".equalsIgnoreCase(urlMode)) {
            if (request != null && response != null) {
                ServletContext ctx = (ServletContext) request.getAttribute("servletContext");
                RequestHandler rh = (RequestHandler) ctx.getAttribute("_REQUEST_HANDLER_");
                urlString = rh.makeLink(request, response, src, fullPath, secure, encode);
            } else {
                urlString = src;
            }
        } else  if (urlMode != null && "content".equalsIgnoreCase(urlMode)) {
            if (request != null && response != null) {
                StringBuilder newURL = new StringBuilder();
                ContentUrlTag.appendContentPrefix(request, newURL);
                newURL.append(src);
                urlString = newURL.toString();
            }
        } else {
            urlString = src;
        }
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("src", src);
        parameters.put("id", image.getId(context));
        parameters.put("style", image.getStyle(context));
        parameters.put("wid", image.getWidth(context));
        parameters.put("hgt", image.getHeight(context));
        parameters.put("border", image.getBorder(context));
        parameters.put("alt", image.getAlt(context));
        parameters.put("urlString", urlString);
        this.output.putScreen("Image", parameters);
    }

    // not yet used, (no use case in screens.xml using FrontJsRenderer)
    //  currently ContentBegin is associated to vue-error to generate the Warning message in screen.
    public void renderContentBegin(Appendable writer, Map<String, Object> context, ModelScreenWidget.Content content) throws IOException {
        String editRequest = content.getEditRequest(context);
        String enableEditName = content.getEnableEditName(context);
        String enableEditValue = (String)context.get(enableEditName);

        if (Debug.verboseOn()) {
            Debug.logVerbose("directEditRequest:" + editRequest, MODULE);
        }

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("editRequest", editRequest);
        parameters.put("enableEditValue", enableEditValue == null ? "" : enableEditValue);
        parameters.put("editContainerStyle", content.getEditContainerStyle(context));
        this.output.putScreen("ContentBegin", parameters);
    }

    // not yet used, (no use case in screens.xml using FrontJsRenderer)
    public void renderContentBody(Appendable writer, Map<String, Object> context, ModelScreenWidget.Content content) throws IOException {
        /* when it will be used, code should be copied from MacroScreenRenderer and at minimal

                writer.append(renderedContent);
           should be replace by
                Map<String, Object> cb = new HashMap<>();
                cb.put("content", renderedContent);
                this.output.add("ContentBody", cb);
        */
    }

    // not yet used, (no use case in screens.xml using FrontJsRenderer)
    public void renderContentEnd(Appendable writer, Map<String, Object> context, ModelScreenWidget.Content content) throws IOException {
        // when it will be used, code should be copied from MacroScreenRenderer and adapted
    }

    // not yet used, (no use case in screens.xml using FrontJsRenderer)
    public void renderContentFrame(Appendable writer, Map<String, Object> context, ModelScreenWidget.Content content) throws IOException {
        // when it will be used, code should be copied from MacroScreenRenderer and adapted
    }

    // not yet used, (no use case in screens.xml using FrontJsRenderer)
    public void renderSubContentBegin(Appendable writer, Map<String, Object> context, ModelScreenWidget.SubContent content) throws IOException {
        // when it will be used, code should be copied from MacroScreenRenderer and adapted
    }

    // not yet used, (no use case in screens.xml using FrontJsRenderer)
    public void renderSubContentBody(Appendable writer, Map<String, Object> context, ModelScreenWidget.SubContent content) throws IOException {
        /* when it will be used, code should be copied from MacroScreenRenderer and at minimal

                writer.append(renderedContent);
           should be replace by
                Map<String, Object> cb = new HashMap<>();
                cb.put("content", renderedContent);
                this.output.add("ContentBody", cb);
*/
    }

    // not yet used, (no use case in screens.xml using FrontJsRenderer)
    public void renderSubContentEnd(Appendable writer, Map<String, Object> context, ModelScreenWidget.SubContent content) throws IOException {
        // when it will be used, code should be copied from MacroScreenRenderer and adapted
    }


    public void renderScreenletBegin(Appendable writer, Map<String, Object> context, boolean collapsed, ModelScreenWidget.Screenlet screenlet) throws IOException {
        /* currently theme is not manage
        HttpServletRequest request = (HttpServletRequest) context.get("request");
        HttpServletResponse response = (HttpServletResponse) context.get("response");
        VisualTheme visualTheme = UtilHttp.getVisualTheme(request);
        ModelTheme modelTheme = visualTheme.getModelTheme();
        */

        String title = screenlet.getTitle(context);
        boolean collapsible = screenlet.collapsible();
        ModelScreenWidget.Menu tabMenu = screenlet.getTabMenu();
        ModelScreenWidget.Menu navMenu = screenlet.getNavigationMenu();
        ModelScreenWidget.Form navForm = screenlet.getNavigationForm();

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("title", title);
        parameters.put("name", screenlet.getName());
        parameters.put("collapsible", collapsible);
        if (screenlet.saveCollapsed()) parameters.put("saveCollapsed", true);
        if (UtilValidate.isNotEmpty (screenlet.getId(context))) {
            parameters.put("id", screenlet.getId(context));
            parameters.put("collapsibleAreaId", screenlet.getId(context) + "_col");
        } else {
            if (collapsible) Debug.logWarning("Screenlet collapsible without an id", MODULE);
            parameters.put("id", "screenlet_" + screenLetsIdCounter);
            parameters.put("collapsibleAreaId","screenlet_" + screenLetsIdCounter + "_col");
            screenLetsIdCounter++;
        }
        if (! screenlet.padded()) { // default value is true, equal false only if attribute is present with false or naviguation-form is used
            Debug.logWarning("screenlet attribute padded is used in screenlet with title="+title+
                    "  it's not manage by FrontFjRenderer", MODULE);
            parameters.put("padded", screenlet.padded());
        }
        parameters.put("collapsed", collapsed);
        parameters.put("showMore", (Boolean) (UtilValidate.isNotEmpty(title) || navMenu != null || navForm != null || collapsible));
        this.output.pushScreen("ScreenletBegin", parameters);

        if (tabMenu != null) {
            // generate menu object, to be able to put it in parameters rather than as children
            //   it's more easy for a frontJs component to manage attributes than sub-components
            parameters.put("tabMenu", getMenuOutput(writer, context, tabMenu.getModelMenu(context)));

        }
        if (navMenu != null || navForm != null ) {
            if (navMenu != null) {
                // generate menu object, to be able to put it in parameters rather than as children
                //   it's more easy for a frontJs component to manage attributes than sub-components
                parameters.put("navMenu", getMenuOutput(writer, context, navMenu.getModelMenu(context)));
            } else if (navForm != null) {
                Debug.logWarning("navigation-form is used in screenlet with title="+title+
                        " it's not manage by VueJs screenlet component", MODULE);
                parameters.put("navForm",renderScreenletPaginateMenu(writer, context, navForm));
            }
        }
    }
    // used by the method just above
    private Map<String, Object> getMenuOutput(Appendable writer, Map<String, Object> context, ModelMenu menu) throws IOException{
        MenuStringRenderer menuStringRenderer = (MenuStringRenderer)context.get("menuStringRenderer");
        AbstractModelAction.runSubActions(menu.getActions(), context);
        menuStringRenderer.renderMenuOpen(writer, context, menu);

        menuStringRenderer.renderFormatSimpleWrapperOpen(writer, context, menu);
        for (ModelMenuItem item : menu.getMenuItemList()) {
            if (item.shouldBeRendered(context)) {
                AbstractModelAction.runSubActions(item.getActions(), context);
                menuStringRenderer.renderMenuItem(writer, context, item);
            }
        }
        menuStringRenderer.renderFormatSimpleWrapperClose(writer, context, menu);
        menuStringRenderer.renderMenuClose(writer, context, menu);
        return this.output.getAndRemoveScreen();
    }

    public void renderScreenletSubWidget(Appendable writer, Map<String, Object> context, ModelScreenWidget subWidget, ModelScreenWidget.Screenlet screenlet) throws GeneralException, IOException  {
        subWidget.renderWidgetString(writer, context, this);
        // currently NavigationForm included in screenlet bar is not managed, maybe in future it will be necessary to add something like
        //   if (subWidget.equals(screenlet.getNavigationForm())) { ...   (see field renderPagination in FrontJsFormRenderer)
    }
    public void renderScreenletEnd(Appendable writer, Map<String, Object> context, ModelScreenWidget.Screenlet screenlet) throws IOException {
        this.output.popScreen("ScreenletEnd");
    }

    // not yet used, (no use case in screens.xml using FrontJsRenderer)
    //  need to be review before using, use method renderNextPrev from FrontJsFormRenderer to know which attribute is needed (renderNextPrev is used)
    protected Map<String, Object> renderScreenletPaginateMenu(Appendable writer, Map<String, Object> context, ModelScreenWidget.Form form) throws IOException {
        HttpServletResponse response = (HttpServletResponse) context.get("response");
        HttpServletRequest request = (HttpServletRequest) context.get("request");
        ModelForm modelForm;
        try {
            modelForm = form.getModelForm(context);
        } catch (Exception e) {
            throw new IOException(e);
        }
        modelForm.runFormActions(context);
        Paginator.preparePager(modelForm, context);
        String targetService = modelForm.getPaginateTarget(context);
        if (targetService == null) {
            targetService = "${targetService}";
        }

        // get the parametrized pagination index and size fields
        int paginatorNumber = WidgetWorker.getPaginatorNumber(context);
        String viewIndexParam = modelForm.getMultiPaginateIndexField(context);
        String viewSizeParam = modelForm.getMultiPaginateSizeField(context);

        int viewIndex = Paginator.getViewIndex(modelForm, context);
        int viewSize = Paginator.getViewSize(modelForm, context);
        int listSize = Paginator.getListSize(context);

        int highIndex = Paginator.getHighIndex(context);
        int actualPageSize = Paginator.getActualPageSize(context);

        // if this is all there seems to be (if listSize < 0, then size is unknown)
        if (actualPageSize >= listSize && listSize >= 0) {
            return null;
        }

        // for legacy support, the viewSizeParam is VIEW_SIZE and viewIndexParam is VIEW_INDEX when the fields are "viewSize" and "viewIndex"
        if (("viewIndex" + "_" + paginatorNumber).equals(viewIndexParam)) {
            viewIndexParam = "VIEW_INDEX" + "_" + paginatorNumber;
        }
        if (("viewSize" + "_" + paginatorNumber).equals(viewSizeParam)) {
            viewSizeParam = "VIEW_SIZE" + "_" + paginatorNumber;
        }

        ServletContext ctx = (ServletContext) request.getAttribute("servletContext");
        RequestHandler rh = (RequestHandler) ctx.getAttribute("_REQUEST_HANDLER_");

        Map<String, Object> inputFields = UtilGenerics.cast(context.get("requestParameters"));
        // strip out any multi form fields if the form is of type multi
        if ("multi".equals(modelForm.getType())) {
            inputFields = UtilHttp.removeMultiFormParameters(inputFields);
        }
        String queryString = UtilHttp.urlEncodeArgs(inputFields);
        // strip legacy viewIndex/viewSize params from the query string
        queryString = UtilHttp.stripViewParamsFromQueryString(queryString, "" + paginatorNumber);
        // strip parametrized index/size params from the query string
        HashSet<String> paramNames = new HashSet<>();
        paramNames.add(viewIndexParam);
        paramNames.add(viewSizeParam);
        queryString = UtilHttp.stripNamedParamsFromQueryString(queryString, paramNames);

        String anchor = "";
        String paginateAnchor = modelForm.getPaginateTargetAnchor();
        if (paginateAnchor != null) {
            anchor = "#" + paginateAnchor;
        }

        // preparing the link text, so that later in the code we can reuse this and just add the viewIndex
        String prepLinkText = "";
        prepLinkText = targetService;
        if (prepLinkText.indexOf('?') < 0) {
            prepLinkText += "?";
        } else if (!prepLinkText.endsWith("?")) {
            prepLinkText += "&amp;";
        }
        if (UtilValidate.isNotEmpty(queryString) && !"null".equals(queryString)) {
            prepLinkText += queryString + "&amp;";
        }
        prepLinkText += viewSizeParam + "=" + viewSize + "&amp;" + viewIndexParam + "=";

        String linkText;


        // The current screenlet title bar navigation syling requires rendering
        // these links in reverse order
        // Last button
        String lastLinkUrl = "";
        if (highIndex < listSize) {
            int lastIndex = UtilMisc.getViewLastIndex(listSize, viewSize);
            linkText = prepLinkText + lastIndex + anchor;
            lastLinkUrl = rh.makeLink(request, response, linkText);
        }
        String nextLinkUrl = "";
        if (highIndex < listSize) {
            linkText = prepLinkText + (viewIndex + 1) + anchor;
            // - make the link
            nextLinkUrl = rh.makeLink(request, response, linkText);
        }
        String previousLinkUrl = "";
        if (viewIndex > 0) {
            linkText = prepLinkText + (viewIndex - 1) + anchor;
            previousLinkUrl = rh.makeLink(request, response, linkText);
        }
        String firstLinkUrl = "";
        if (viewIndex > 0) {
            linkText = prepLinkText + 0 + anchor;
            firstLinkUrl = rh.makeLink(request, response, linkText);
        }

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("lowIndex", Paginator.getLowIndex(context));
        parameters.put("actualPageSize", actualPageSize);
        parameters.put("listSize", listSize);
        parameters.put("paginateLastStyle", modelForm.getPaginateLastStyle());
        parameters.put("lastLinkUrl", lastLinkUrl);
        parameters.put("paginateLastLabel", modelForm.getPaginateLastLabel(context));
        parameters.put("paginateNextStyle", modelForm.getPaginateNextStyle());
        parameters.put("nextLinkUrl", nextLinkUrl);
        parameters.put("paginateNextLabel", modelForm.getPaginateNextLabel(context));
        parameters.put("paginatePreviousStyle", modelForm.getPaginatePreviousStyle());
        parameters.put("paginatePreviousLabel", modelForm.getPaginatePreviousLabel(context));
        parameters.put("previousLinkUrl", previousLinkUrl);
        parameters.put("paginateFirstStyle", modelForm.getPaginateFirstStyle());
        parameters.put("paginateFirstLabel", modelForm.getPaginateFirstLabel(context));
        parameters.put("firstLinkUrl", firstLinkUrl);
        return parameters;
        //this.output.putScreen("ScreenletPaginateMenu", parameters);
    }

    //  PortalPage is managed only for confMode="N" (show it) so renderer is only column management
    //     currently PortalPage send a ColunmContainer and portalColunm a column,
    //     and a container around each portlet, so very similar with a screen approach
    //  in future, portalPage should be deprecated because could be create some security issues and it's possible
    //             to manage same look and functionalities with screen and a dedicated sub-component in webtools
    public void renderPortalPageBegin(Appendable writer, Map<String, Object> context, ModelScreenWidget.PortalPage portalPage) throws GeneralException, IOException {
        String portalPageId = portalPage.getActualPortalPageId(context);
        String originalPortalPageId = portalPage.getOriginalPortalPageId(context);
        String confMode = portalPage.getConfMode(context);

        Map<String, Object> cb = new HashMap<>();
        cb.put("originalPortalPageId", originalPortalPageId);
        cb.put("portalPageId", portalPageId);
        cb.put("confMode", confMode);
        if ("Y".equals(confMode)) {
            throw new IOException("Render FrontJsScreen : include-portal-page with confMode=Y is used in a screen and it's not yet implemented"
                               + "  portalPageId="+ portalPageId);
            // this.output.pushScreen("PortalPageBegin", cb);
        }
        this.output.pushScreen("ColumnContainerBegin", new HashMap<String, Object>());
    }

    // CF renderPortalPageBegin
    public void renderPortalPageEnd(Appendable writer, Map<String, Object> context, ModelScreenWidget.PortalPage portalPage) throws GeneralException, IOException {
        // this.output.popScreen("PortalPageEnd"); should be re-activate if confMode should be manage
        this.output.popScreen("ColumnContainerEnd");
    }

    // CF renderPortalPageBegin
    public void renderPortalPageColumnBegin(Appendable writer, Map<String, Object> context, ModelScreenWidget.PortalPage portalPage, GenericValue portalPageColumn) throws GeneralException, IOException {
        String portalPageId = portalPage.getActualPortalPageId(context);
        String columnWidthPixels = portalPageColumn.getString("columnWidthPixels");

        // manage only with grid system, so no absolue value
        if (columnWidthPixels != null) {
            Debug.logWarning("PortalPage with a column with width in pixel not null, FrontJs renderer manage only Width in percentage"+
                    " portalPageid="+portalPageId, MODULE);
        }

        Map<String, Object> cb = new HashMap<>();
        // first release very simple, to transform % to a grid class
        String columnWidth;
        Long columnWidthPercentage = portalPageColumn.getLong("columnWidthPercentage");
        if (columnWidthPercentage != null) {
            if (columnWidthPercentage < 9) columnWidth = "md-1";
            else if (columnWidthPercentage < 17) columnWidth = "md-2";
            else if (columnWidthPercentage < 25) columnWidth = "md-3";
            else if (columnWidthPercentage < 34) columnWidth = "md-4";
            else if (columnWidthPercentage < 42) columnWidth = "md-5";
            else if (columnWidthPercentage < 51) columnWidth = "md-6";
            else if (columnWidthPercentage < 59) columnWidth = "md-7";
            else if (columnWidthPercentage < 67) columnWidth = "md-8";
            else if (columnWidthPercentage < 76) columnWidth = "md-9";
            else if (columnWidthPercentage < 84) columnWidth = "md-10";
            else if (columnWidthPercentage < 92) columnWidth = "md-11";
            else columnWidth = "md-12";
            cb.put("style", columnWidth);
        }
        this.output.pushScreen("ColumnBegin", cb);
    }

    // CF renderPortalPageBegin
    public void renderPortalPageColumnEnd(Appendable writer, Map<String, Object> context, ModelScreenWidget.PortalPage portalPage, GenericValue portalPageColumn) throws GeneralException, IOException {
        this.output.popScreen("ColumnEnd");
    }

    // CF renderPortalPageBegin
    public void renderPortalPagePortletBegin(Appendable writer, Map<String, Object> context, ModelScreenWidget.PortalPage portalPage, GenericValue portalPortlet) throws GeneralException, IOException {
        String portalPageId = portalPage.getActualPortalPageId(context);
        String portalPortletId = portalPortlet.getString("portalPortletId");
        String portletSeqId = portalPortlet.getString("portletSeqId");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("id",portalPortletId+"-"+portletSeqId);
        if (portalPortlet.containsKey("watcherName")){
            parameters.put("autoUpdateTarget", "showPortletFj/"+portalPageId+"/"+portalPortletId+"/"+portletSeqId);
            String watcherName = portalPortlet.getString("watcherName");
            if (UtilValidate.isNotEmpty(watcherName)) {
                List<String> watcherList = StringUtil.split(watcherName,",");
                if (watcherList.size()>1) {
                    StringBuilder watcherNameStr =  new StringBuilder();
                    watcherNameStr.append(watcherList.get(0).trim());
                    for (int i = 1; i < watcherList.size(); i++) {
                        watcherNameStr.append("-");
                        watcherNameStr.append(watcherList.get(i).trim());
                    }
                    parameters.put("watcherName", watcherNameStr.toString());
                } else {
                    parameters.put("watcherName", watcherName);
                }
            }
        }
        this.output.pushScreen("ContainerOpen", parameters);
    }

    public void renderPortalPagePortletEnd(Appendable writer, Map<String, Object> context, ModelScreenWidget.PortalPage portalPage, GenericValue portalPortlet) throws GeneralException, IOException {
        this.output.popScreen("ContainerClose");
    }

    public void renderPortalPagePortletBody(Appendable writer, Map<String, Object> context, ModelScreenWidget.PortalPage portalPage, GenericValue portalPortlet) throws GeneralException, IOException {
        String portalPortletId = portalPortlet.getString("portalPortletId");
        String portletSeqId = portalPortlet.getString("portletSeqId");
        String screenName = portalPortlet.getString("screenName");
        String screenLocation = portalPortlet.getString("screenLocation");
        context.put("portalPortletId", portalPortletId);
        context.put("portletSeqId", portletSeqId);
        context.put("currentAreaId", portalPortletId + "-" + portletSeqId);

        ModelScreen modelScreen = null;
        if (UtilValidate.isNotEmpty(screenName) && UtilValidate.isNotEmpty(screenLocation)) {
            try {
                modelScreen = ScreenFactory.getScreenFromLocation(screenLocation, screenName);
            } catch (IOException | SAXException | ParserConfigurationException e) {
                String errMsg = "Error rendering portlet ID [" + portalPortletId + "]: " + e.toString();
                Debug.logError(e, errMsg, MODULE);
                throw new RuntimeException(errMsg);
            }
        }
        if (writer != null && context != null) {
            modelScreen.renderScreenString(writer, context, this);
        } else {
            Debug.logError("Null on some Path: writer" + writer + ", context: " + context, MODULE);
        }
    }

    @Override
    public void renderColumnContainer(Appendable writer, Map<String, Object> context, ColumnContainer columnContainer) throws IOException {
        String id = columnContainer.getId(context);
        String style = columnContainer.getStyle(context);
        Map<String, Object> parameters = new HashMap<>();
        if (UtilValidate.isNotEmpty(id)) parameters.put("id", id);
        if (UtilValidate.isNotEmpty(style)) parameters.put("style", style);
        this.output.pushScreen("ColumnContainerBegin", parameters);
        for (ModelScreenWidget.Column column : columnContainer.getColumns()) {
            id = column.getId(context);
            style = column.getStyle(context);
            parameters = new HashMap<>();
            if (UtilValidate.isNotEmpty(id)) parameters.put("id", id);
            if (UtilValidate.isNotEmpty(style)) parameters.put("style", style);
            this.output.pushScreen("ColumnBegin", parameters);
            for (ModelScreenWidget subWidget : column.getSubWidgets()) {
                try {
                    subWidget.renderWidgetString(writer, context, this);
                } catch (GeneralException e) {
                    throw new IOException(e);
                }
            }
            this.output.popScreen("ColumnEnd");
        }
        this.output.popScreen("ColumnContainerEnd");
    }

}
