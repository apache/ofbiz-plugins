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
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ofbiz.base.util.StringUtil;
import org.apache.ofbiz.base.util.UtilCodec;
import org.apache.ofbiz.base.util.UtilHttp;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.webapp.control.RequestHandler;
import org.apache.ofbiz.webapp.taglib.ContentUrlTag;
import org.apache.ofbiz.widget.WidgetWorker;
import org.apache.ofbiz.widget.model.CommonWidgetModels.Image;
import org.apache.ofbiz.widget.model.ModelMenu;
import org.apache.ofbiz.widget.model.ModelMenuItem;
import org.apache.ofbiz.widget.model.ModelMenuItem.MenuLink;
import org.apache.ofbiz.widget.model.ModelTheme;
import org.apache.ofbiz.widget.renderer.MenuStringRenderer;
import org.apache.ofbiz.widget.renderer.VisualTheme;

public class FrontJsMenuRenderer implements MenuStringRenderer {

    private static final String MODULE = FrontJsMenuRenderer.class.getName();
    private final HttpServletRequest request;
    private final HttpServletResponse response;
    private FrontJsOutput output;

    FrontJsMenuRenderer(FrontJsOutput output, HttpServletRequest request, HttpServletResponse response) {
        this.output = output;
        this.request = request;
        this.response = response;
    }

    // Made this a separate method so it can be externalized and reused.
    private Map<String, Object> createImageParameters(Map<String, Object> context, Image image) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("id", image.getId(context));
        parameters.put("style", image.getStyle(context));
        parameters.put("width", image.getWidth(context));
        parameters.put("height", image.getHeight(context));
        parameters.put("border", image.getBorder(context));
        parameters.put("title", image.getTitleExdr().expandString(context));
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

    private boolean isDisableIfEmpty(ModelMenuItem menuItem, Map<String, Object> context) {
        boolean disabled = false;
        String disableIfEmpty = menuItem.getDisableIfEmpty();
        if (UtilValidate.isNotEmpty(disableIfEmpty)) {
            List<String> keys = StringUtil.split(disableIfEmpty, "|");
            for (String key : keys) {
                Object obj = context.get(key);
                if (UtilValidate.isEmpty(obj)) {
                    disabled = true;
                    break;
                }
            }
        }
        return disabled;
    }

    private boolean isHideIfSelected(ModelMenuItem menuItem, Map<String, Object> context) {
        ModelMenu menu = menuItem.getModelMenu();
        String currentMenuItemName = menu.getSelectedMenuItemContextFieldName(context);
        String currentItemName = menuItem.getName();
        Boolean hideIfSelected = menuItem.getHideIfSelected();
        return (hideIfSelected != null && hideIfSelected && currentMenuItemName != null && currentMenuItemName.equals(currentItemName));
    }

    @Override
    public void renderFormatSimpleWrapperClose(Appendable writer, Map<String, Object> context, ModelMenu menu) {
        // Nothing to do.
    }

    @Override
    public void renderFormatSimpleWrapperOpen(Appendable writer, Map<String, Object> context, ModelMenu menu) {
        // Nothing to do.
    }

    @Override
    public void renderFormatSimpleWrapperRows(Appendable writer, Map<String, Object> context, Object menu) throws IOException {
        List<ModelMenuItem> menuItemList = ((ModelMenu) menu).getMenuItemList();
        for (ModelMenuItem currentMenuItem : menuItemList) {
            renderMenuItem(writer, context, currentMenuItem);
        }
    }

    @Override
    public void renderImage(Appendable writer, Map<String, Object> context, Image image) {
        Map<String, Object> parameters = createImageParameters(context, image);
        this.output.putScreen("Image", parameters);
    }

    @Override
    public void renderLink(Appendable writer, Map<String, Object> context, MenuLink link) throws IOException {
        if (link.getLink().getRequestConfirmation()) {
            throw new IOException("Render (Macro and FrontJs): requestConfirmation is used in a menuLink and it's not yet implemented"
                               + "for link with target="+ link.getTarget(context));
        }

        Map<String, Object> parameters = new HashMap<>();
        VisualTheme visualTheme = UtilHttp.getVisualTheme(request);
        ModelTheme modelTheme = visualTheme.getModelTheme();
        String target = link.getTarget(context);
        ModelMenuItem menuItem = link.getLinkMenuItem();
        if (isDisableIfEmpty(menuItem, context)) {
            target = null;
        }
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
        StringBuilder uniqueItemName = new StringBuilder(menuItem.getModelMenu().getName());
        uniqueItemName.append("_").append(menuItem.getName()).append("_LF_").append(UtilMisc.addToBigDecimalInMap(context, "menuUniqueItemIndex", BigDecimal.ONE));
        if (menuItem.getModelMenu().getExtraIndex(context) != null) {
            uniqueItemName.append("_").append(menuItem.getModelMenu().getExtraIndex(context));
        }
        if (context.containsKey("itemIndex")) {
            if (context.containsKey("parentItemIndex")) {
                uniqueItemName.append(context.get("parentItemIndex")).append("_").append(context.get("itemIndex"));
            } else {
                uniqueItemName.append("_").append(context.get("itemIndex"));
            }
        }
        parameters.put("uniqueItemName", uniqueItemName.toString());
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
        String linkUrl = "";
        if (UtilValidate.isNotEmpty(target)) {
            if (!"hidden-form".equals(linkType)) {
                StringBuilder sb = new StringBuilder();
                WidgetWorker.buildHyperlinkUrl(sb, target, link.getUrlMode(), "layered-modal".equals(linkType)?null:link.getParameterMap(context), link.getPrefix(context),
                        link.getFullPath(), link.getSecure(), link.getEncode(), request, response, context);
                linkUrl = sb.toString();
            }
        }
        parameters.put("target", target);
        parameters.put("linkUrl", linkUrl);
        parameters.put("parameterMap", link.getParameterMap(context));
        Image img = link.getImage();
        if (img != null) {
            parameters.put("img", createImageParameters(context, img));
        }
        this.output.putScreen("Link", parameters);
    }

    @Override
    public void renderMenuOpen(Appendable writer, Map<String, Object> context, ModelMenu menu) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("id", menu.getId());
        parameters.put("style", menu.getMenuContainerStyle(context));
        parameters.put("title", menu.getTitle(context));
        this.output.pushScreen("MenuBegin", parameters);
    }

    @Override
    public void renderMenuItem(Appendable writer, Map<String, Object> context, ModelMenuItem menuItem) throws IOException {
        if (isHideIfSelected(menuItem, context)) {
            return;
        }
        Map<String, Object> parameters = new HashMap<>();
        String style = menuItem.getWidgetStyle();
        if (menuItem.isSelected(context)) {
            String selectedStyle = menuItem.getSelectedStyle();
            if (UtilValidate.isEmpty(selectedStyle)) {
                selectedStyle = "selected";
            }
            if (UtilValidate.isNotEmpty(style)) {
                style += " " ;
            }
            style += selectedStyle ;
        }
        if (this.isDisableIfEmpty(menuItem, context)) {
            style = menuItem.getDisabledTitleStyle();
        }
        if (style == null) {
            style = "";
        }
        String alignStyle = menuItem.getAlignStyle();
        if (UtilValidate.isNotEmpty(alignStyle)) {
            style = style.concat(" ").concat(alignStyle);
        }
        parameters.put("style", style);
        parameters.put("toolTip", menuItem.getTooltip(context));
        boolean containsNestedMenus = !menuItem.getMenuItemList().isEmpty();
        parameters.put("containsNestedMenus", containsNestedMenus);
        this.output.pushScreen("MenuItemBegin", parameters);
        String linkStr;
        MenuLink link = menuItem.getLink();
        if (link != null) {
            renderLink(writer, context, link);
        } else {
            linkStr = menuItem.getTitle(context);
            UtilCodec.SimpleEncoder simpleEncoder = (UtilCodec.SimpleEncoder) context.get("simpleEncoder");
            if (simpleEncoder != null) {
                linkStr = simpleEncoder.encode(linkStr);
            }
            parameters.put("Link", linkStr);
        }

        if (containsNestedMenus) {
            for (ModelMenuItem childMenuItem : menuItem.getMenuItemList()) {
                renderMenuItem(writer, context, childMenuItem);
            }
        }
        parameters.clear();
        parameters.put("containsNestedMenus", containsNestedMenus);
        this.output.popScreen("MenuItemEnd");
    }

    @Override
    public void renderMenuClose(Appendable writer, Map<String, Object> context, ModelMenu menu) {
        this.output.popScreen("MenuEnd");
    }
}
