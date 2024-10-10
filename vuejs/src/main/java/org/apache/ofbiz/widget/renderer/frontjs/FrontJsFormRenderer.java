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
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilCodec;
import org.apache.ofbiz.base.util.UtilFormatOut;
import org.apache.ofbiz.base.util.UtilGenerics;
import org.apache.ofbiz.base.util.UtilHttp;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.base.util.string.FlexibleStringExpander;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.model.ModelEntity;
import org.apache.ofbiz.entity.model.ModelReader;
import org.apache.ofbiz.webapp.control.RequestHandler;
import org.apache.ofbiz.webapp.taglib.ContentUrlTag;
import org.apache.ofbiz.widget.WidgetWorker;
import org.apache.ofbiz.widget.model.CommonWidgetModels;
import org.apache.ofbiz.widget.model.FieldInfo;
import org.apache.ofbiz.widget.model.ModelForm;
import org.apache.ofbiz.widget.model.ModelFormField;
//import org.apache.ofbiz.widget.model.ModelFormField.AutoComplete;
import org.apache.ofbiz.widget.model.ModelFormField.CheckField;
import org.apache.ofbiz.widget.model.ModelFormField.ContainerField;
import org.apache.ofbiz.widget.model.ModelFormField.DateFindField;
import org.apache.ofbiz.widget.model.ModelFormField.DateTimeField;
import org.apache.ofbiz.widget.model.ModelFormField.DisplayEntityField;
import org.apache.ofbiz.widget.model.ModelFormField.DisplayField;
import org.apache.ofbiz.widget.model.ModelFormField.DropDownField;
import org.apache.ofbiz.widget.model.ModelFormField.FieldInfoWithOptions;
import org.apache.ofbiz.widget.model.ModelFormField.FileField;
import org.apache.ofbiz.widget.model.ModelFormField.HiddenField;
import org.apache.ofbiz.widget.model.ModelFormField.HyperlinkField;
import org.apache.ofbiz.widget.model.ModelFormField.IgnoredField;
import org.apache.ofbiz.widget.model.ModelFormField.ImageField;
import org.apache.ofbiz.widget.model.ModelFormField.LookupField;
import org.apache.ofbiz.widget.model.ModelFormField.MenuField;
import org.apache.ofbiz.widget.model.ModelFormField.OptionValue;
import org.apache.ofbiz.widget.model.ModelFormField.PasswordField;
import org.apache.ofbiz.widget.model.ModelFormField.RadioField;
import org.apache.ofbiz.widget.model.ModelFormField.RangeFindField;
import org.apache.ofbiz.widget.model.ModelFormField.ResetField;
import org.apache.ofbiz.widget.model.ModelFormField.SubHyperlink;
import org.apache.ofbiz.widget.model.ModelFormField.SubmitField;
import org.apache.ofbiz.widget.model.ModelFormField.TextField;
import org.apache.ofbiz.widget.model.ModelFormField.TextFindField;
import org.apache.ofbiz.widget.model.ModelFormField.TextareaField;
//import org.apache.ofbiz.widget.model.ModelFormFieldBuilder;
import org.apache.ofbiz.widget.model.ModelScreenWidget;
import org.apache.ofbiz.widget.model.ThemeFactory;
import org.apache.ofbiz.widget.model.ModelForm.UpdateArea;
import org.apache.ofbiz.widget.renderer.FormRenderer;
import org.apache.ofbiz.widget.renderer.FormStringRenderer;
import org.apache.ofbiz.widget.renderer.Paginator;
import org.apache.ofbiz.widget.renderer.UtilHelpText;
import org.apache.ofbiz.widget.renderer.VisualTheme;

//import com.ibm.icu.util.Calendar;

//import org.apache.ofbiz.entity.GenericValue;


public final class FrontJsFormRenderer implements FormStringRenderer {
    private static final String NOT_YET_SUPPORTED = "Not yet supported";
    private static final String MODULE = FrontJsFormRenderer.class.getName();
    private FrontJsOutput output;
    private final UtilCodec.SimpleEncoder internalEncoder;
    private final RequestHandler rh;
    private final HttpServletRequest request;
    private final HttpServletResponse response;
    private final boolean javaScriptEnabled;
    private final VisualTheme visualTheme;
    private boolean renderPagination = true;

    public FrontJsFormRenderer(FrontJsOutput output, HttpServletRequest request, HttpServletResponse response) {
        this.request = request;
        this.response = response;
        this.visualTheme = ThemeFactory.resolveVisualTheme(request);
        ServletContext ctx = (ServletContext) request.getAttribute("servletContext");
        this.rh = (RequestHandler) ctx.getAttribute("_REQUEST_HANDLER_");
        this.javaScriptEnabled = UtilHttp.isJavaScriptEnabled(request);
        internalEncoder = UtilCodec.getEncoder("string");
        this.output = output;
    }
    public void setRenderPagination(boolean renderPagination) {
        this.renderPagination = renderPagination;
    }

    private String encode(String value, ModelFormField modelFormField, Map<String, Object> context) {
        if (UtilValidate.isEmpty(value)) {
            return value;
        }
        UtilCodec.SimpleEncoder encoder = (UtilCodec.SimpleEncoder) context.get("simpleEncoder");
        if (modelFormField.getEncodeOutput() && encoder != null) {
            value = encoder.encode(value);
        } else {
            value = internalEncoder.encode(value);
        }
        return value;
    }

    private static String encodeDoubleQuotes(String htmlString) {
        return htmlString.replaceAll("\"", "\\\\\"");
    }

    private List<String> getPkList(String entityName, ModelReader entityModelReader) {
        ModelEntity modelEntity = null;
        if (UtilValidate.isNotEmpty(entityName)) {
            try {
                modelEntity = entityModelReader.getModelEntity(entityName);
            } catch (GenericEntityException e) {
                Debug.logError(e, MODULE);
            }
            if (modelEntity == null) {
                throw new IllegalArgumentException("Error finding Entity with name " + entityName
                        + " for defaut-entity-name in a form widget");
            } else {
                return modelEntity.getPkFieldNames();
            }
        }
        return null;
    }

    public void renderLabel(Map<String, Object> context, ModelScreenWidget.Label label) {
        String labelText = label.getText(context);
        if (UtilValidate.isEmpty(labelText)) {
            // nothing to render
            return;
        }
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("text", labelText);
        this.output.putScreen("Label", attributes);
    }

    public void renderDisplayField(Appendable writer, Map<String, Object> context, DisplayField displayField) {
        ModelFormField modelFormField = displayField.getModelFormField();
        String idName = modelFormField.getCurrentContainerId(context);
        String description = displayField.getDescription(context);
        String type = displayField.getType();
        String imageLocation = displayField.getImageLocation(context);
        String title = "";
        String name = modelFormField.getName();
        Map<String, Object> attributes = new HashMap<>();
        Integer size = Integer.valueOf("0");
        if (UtilValidate.isNotEmpty(displayField.getSize())) {
            try {
                size = Integer.parseInt(displayField.getSize());
            } catch (NumberFormatException nfe) {
                Debug.logError(nfe, "Error reading size of a field fieldName=" + displayField.getModelFormField().getFieldName()
                                    + " FormName= " + displayField.getModelFormField().getModelForm().getName(), MODULE);
            }
            Debug.logWarning("displayField attribute size is used in form with name=" + modelFormField.getModelForm().getName()
                    + "  it's not manage by FrontFjRenderer", MODULE);
            attributes.put("size", displayField.getSize());
        }
//        not yet use case for this, but substring should be done on vuejs
//        if (UtilValidate.isNotEmpty(description) && size > 0 && description.length() > size) {
//            title = description;
//            description = description.substring(0, size - 8) + "..." + description.substring(description.length() - 5);
//        }

        if ("single".equals(modelFormField.getModelForm().getType())) { this.addTitle(attributes, modelFormField, context);}
        attributes.put("formName", displayField.getModelFormField().getModelForm().getName());
        attributes.put("description", encodeDoubleQuotes(description));
        attributes.put("name", name);

        ModelFormField.InPlaceEditor inPlaceEditor = displayField.getInPlaceEditor();
        if (inPlaceEditor != null) {
            attributes.put("inPlaceEditor", true);
            String url = inPlaceEditor.getUrl(context);
            attributes.put("url", url);
            Map<String, Object> fieldMap = inPlaceEditor.getFieldMap(context);
            attributes.put("fieldMap", fieldMap);
            if (UtilValidate.isNotEmpty(inPlaceEditor.getSavingText())) {
                Debug.logWarning("displayField in-place-editor saving-text attribute is used in form with name="
                                 + modelFormField.getModelForm().getName()
                                 + "  it's not manage by FrontFjRenderer", MODULE);
                attributes.put("savingText", inPlaceEditor.getSavingText());
            }
        }

        if (UtilValidate.isNotEmpty(type)) {attributes.put("type", type);}
        if (UtilValidate.isNotEmpty(size)) {attributes.put("size", size);}
        if (UtilValidate.isNotEmpty(imageLocation)) {attributes.put("imageLocation", imageLocation);}
        if (UtilValidate.isNotEmpty(idName)) { attributes.put("idName", idName);}
        if (UtilValidate.isNotEmpty(title)) { attributes.put("title", title);}
        if (UtilValidate.isNotEmpty(modelFormField.getWidgetStyle())) { attributes.put("class", modelFormField.getWidgetStyle());}
        if (UtilValidate.isNotEmpty(modelFormField.shouldBeRed(context))) { attributes.put("alert", "true");}
        this.appendTooltip(attributes, context, modelFormField);

        // putRecord only if attribute "description" not exist for the <display tag
        if (UtilValidate.isEmpty(displayField.getDescription())
                || "${description}".equals(displayField.getDescription().toString())
                || displayField instanceof DisplayEntityField) {
            this.output.putScreen("DisplayField", attributes, modelFormField.getName(), encodeDoubleQuotes(description));
        } else {
            this.output.putScreen("DisplayField", attributes);
        }
    }

    public void renderHyperlinkField(Appendable writer, Map<String, Object> context, HyperlinkField hyperlinkField) {
        ModelFormField modelFormField = hyperlinkField.getModelFormField();
        HashMap<String, Object> attributes = new HashMap<>();
        if (!hyperlinkField.getTarget(context).isEmpty()) {
            attributes.put("target", hyperlinkField.getTarget(context));
        }
        if (!hyperlinkField.getTargetWindow(context).isEmpty()) {
            attributes.put("targetWindow", hyperlinkField.getTargetWindow(context));
        }
        Map<String, String> parameterMap = hyperlinkField.getParameterMap(context, modelFormField.getEntityName(), modelFormField.getServiceName());
        if (!parameterMap.isEmpty()) {
            attributes.put("parameterMap", parameterMap);
        }
        if (UtilValidate.isNotEmpty(modelFormField.getWidgetStyle())) attributes.put("style", modelFormField.getWidgetStyle());
        if (UtilValidate.isNotEmpty(hyperlinkField.getRequestConfirmation()))
                attributes.put("requestConfirmation", hyperlinkField.getRequestConfirmation());
        if (UtilValidate.isNotEmpty(hyperlinkField.getConfirmationMsg(context)))
                attributes.put("confirmationMessage", hyperlinkField.getConfirmationMsg(context));
        if (UtilValidate.isNotEmpty(hyperlinkField.getImageLocation(context)))
                attributes.put("imgSrc", hyperlinkField.getImageLocation(context));
        if (UtilValidate.isNotEmpty(hyperlinkField.getImageTitle(context)))
                attributes.put("imgTitle", hyperlinkField.getImageTitle(context));
        if (UtilValidate.isNotEmpty(hyperlinkField.getUrlMode()))
                attributes.put("urlMode", hyperlinkField.getUrlMode());
        if (UtilValidate.isNotEmpty(hyperlinkField.getLinkType()))
            attributes.put("linkType", hyperlinkField.getLinkType());
        List<UpdateArea> clickUpdateAreas = modelFormField.getOnClickUpdateAreas(context);
        if (!clickUpdateAreas.isEmpty()) {
            List<Map<String, Object>> updateAreasValid = new LinkedList<>();
            for (UpdateArea updateArea : clickUpdateAreas) {
                updateAreasValid.add(updateArea.toMap(context));
            }
            attributes.put("clickUpdateAreas", updateAreasValid);
        }
        attributes.put("description", hyperlinkField.getDescription(context));
        String value = modelFormField.getEntry(context);
        if (hyperlinkField.getDescription(context).equals(value)) {
            String key = modelFormField.getName();
            this.output.putScreen("HyperlinkField", attributes, key, value);
        } else {
            this.output.putScreen("HyperlinkField", attributes);
        }
    }

    public void renderMenuField(Appendable writer, Map<String, Object> context, MenuField menuField) throws IOException {
        throw new IOException("FrontJsRender: include-menu field, not yet implemented in form for form name="
                + menuField.getModelFormField().getModelForm().getName());
        //menuField.renderFieldString(writer, context, null);
        //this.output.putScreen("MenuField", new HashMap<String, Object>());
    }

    /**
     * With frontJs, it's better to have title field attributes as field attributes not in an other element at the same level.
     * @param attributes
     * @param modelFormField
     * @param context
     */
    private void addTitle(Map<String, Object> attributes, ModelFormField modelFormField, Map<String, Object> context) {
        attributes.put("fieldTitle", modelFormField.getTitle(context));
        if (UtilValidate.isNotEmpty(modelFormField.getTitleStyle())) attributes.put("titlestyle", modelFormField.getTitleStyle());
        if ( modelFormField.getRequiredField()) {
            String requiredStyle = modelFormField.getRequiredFieldStyle();
            if (UtilValidate.isNotEmpty(requiredStyle)) {
                attributes.put("titlestyle", requiredStyle);
            }
        }
        String displayHelpText = UtilProperties.getPropertyValue("widget", "widget.form.displayhelpText");
        if ("Y".equals(displayHelpText) && UtilValidate.isNotEmpty(modelFormField.getEntityName()) ) {
            Delegator delegator = WidgetWorker.getDelegator(context);
            Locale locale = (Locale) context.get("locale");
            String entityName = modelFormField.getEntityName();
            String fieldName = modelFormField.getFieldName();
            String helpText = UtilHelpText.getEntityFieldDescription(entityName, fieldName, delegator, locale);
            attributes.put("fieldHelpText", encodeDoubleQuotes(helpText));
        }
    }

    private void addAlertAndClass(Map<String, Object> attributes, ModelFormField modelFormField, Map<String, Object> context) {
        String name = modelFormField.getParameterName(context);
        String formName = modelFormField.getModelForm().getName();
        // First attribute alert and className generate only a warning
        String className = "";
        String alert = "false";
        if (UtilValidate.isNotEmpty(modelFormField.getWidgetStyle())) {
            className = modelFormField.getWidgetStyle();
            if (modelFormField.shouldBeRed(context)) {
                alert = "true";
            }
        }
        //check for required field style on single forms
        if ("single".equals(modelFormField.getModelForm().getType()) && modelFormField.getRequiredField()) {
            String requiredStyle = modelFormField.getRequiredFieldStyle();
            if (UtilValidate.isEmpty(requiredStyle)) {
                requiredStyle = "required";
            }
            if (UtilValidate.isEmpty(className)) {
                className = requiredStyle;
            } else {
                className = requiredStyle + " " + className;
            }
        }
        if (UtilValidate.isNotEmpty(className) || "true".equals(alert)) {
            Debug.logWarning("Field with alert or class attribute is used for field name="+name+
                    " in form with name="+formName +
                    "  it's not manage by FrontFjRenderer", MODULE);
            if (UtilValidate.isNotEmpty(className))          attributes.put("className", className);
            if ("true".equals(alert))                        attributes.put("alert", alert);
        }
    }

    public void renderTextField(Appendable writer, Map<String, Object> context, TextField textField) throws IOException {
        ModelFormField modelFormField = textField.getModelFormField();
        String name = modelFormField.getParameterName(context);
        String value = modelFormField.getEntry(context, textField.getDefaultValue(context));
        Integer textSize = textField.getSize();
        Integer maxlength = -1;
        if (textField.getMaxlength() != null) {
            maxlength = textField.getMaxlength();
        }
        String id = modelFormField.getCurrentContainerId(context);
        String formName = textField.getModelFormField().getModelForm().getName();

        Map<String, Object> attributes = new HashMap<>();
        if ("single".equals(modelFormField.getModelForm().getType())) this.addTitle(attributes, modelFormField, context);
        attributes.put("formName", formName);
        attributes.put("name", name);
        attributes.put("value", value);
        attributes.put("textSize", textSize); // not manage by frontJs
        if (maxlength > -1)                              attributes.put("maxlength", maxlength);
        if (UtilValidate.isNotEmpty(id))                 attributes.put("id", id);
        if (UtilValidate.isNotEmpty(textField.getMask())) attributes.put("mask", textField.getMask());
        this.appendTooltip(attributes, context, modelFormField);
        this.addAsterisks(attributes, context, modelFormField);
        this.output.putScreen("TextField", attributes, name, value);

        // All not manage attributes
        // First attribute alert and className generate only a warning
        this.addAlertAndClass(attributes, modelFormField, context);
        // Second attributes list, generate an error
        String placeholder = textField.getPlaceholder(context);
        List<UpdateArea> updateAreas = modelFormField.getOnChangeUpdateAreas();
        String event = modelFormField.getEvent();
        String action = modelFormField.getAction(context);
        boolean disabled = modelFormField.getDisabled();
        String ajaxUrl = createAjaxParamsFromUpdateAreas(updateAreas, "", context);
        boolean readonly = textField.getReadonly();
        String tabindex = modelFormField.getTabindex();
        if (disabled || readonly || !textField.getClientAutocompleteField()
                     || UtilValidate.isNotEmpty(event) || UtilValidate.isNotEmpty(action)
                     || UtilValidate.isNotEmpty(placeholder) || UtilValidate.isNotEmpty(tabindex)) {

            if (UtilValidate.isNotEmpty(event))              attributes.put("event", event);
            if (UtilValidate.isNotEmpty(action))             attributes.put("action", action);
            if (! textField.getClientAutocompleteField())    attributes.put("clientAutocomplete", "false"); //the default value is true
            if (UtilValidate.isNotEmpty(ajaxUrl))            attributes.put("ajaxUrl", ajaxUrl);
            if (UtilValidate.isNotEmpty(placeholder))        attributes.put("placeholder", placeholder);
            if (UtilValidate.isNotEmpty(tabindex))           attributes.put("tabindex", tabindex);
            if (disabled)                                    attributes.put("disabled", disabled);
            if (readonly)                                    attributes.put("readonly", readonly);
            throw new IOException("FrontJsRender: a attribute is not yet implemented for text-field name=" + name
                    + " in form for form name=" + modelFormField.getModelForm().getName()
                    + " attribute is one of : disabled("+disabled+"), readonly("+readonly
                    + "), !clientAutocomplete("+!textField.getClientAutocompleteField()+"), event("+event
                    + "), action("+action+"), updateAreas("+updateAreas+"), ajaxUrl("+ajaxUrl+"), placeholder("+placeholder
                    + "), tabindex("+tabindex+")");

        }
    }

    public void renderTextareaField(Appendable writer, Map<String, Object> context, TextareaField textareaField) throws IOException {
        ModelFormField modelFormField = textareaField.getModelFormField();
        String name = modelFormField.getParameterName(context);
        String id = modelFormField.getCurrentContainerId(context);
        Integer maxlength = -1;
        if (textareaField.getMaxlength() != null) {
            maxlength = textareaField.getMaxlength();
        }
        String value = modelFormField.getEntry(context, textareaField.getDefaultValue(context));
        String formName = modelFormField.getModelForm().getName();
        Map<String, Object> attributes = new HashMap<>();
        if ("single".equals(modelFormField.getModelForm().getType())) this.addTitle(attributes, modelFormField, context);

        attributes.put("formName", formName);
        attributes.put("name", name);
        attributes.put("value", value);
        if (maxlength > -1)                              attributes.put("maxlength", maxlength);
        if (UtilValidate.isNotEmpty(id))                 attributes.put("id", id);
        this.appendTooltip(attributes, context, modelFormField);
        this.addAsterisks(attributes, context, modelFormField);
        this.output.putScreen("TextAreaField", attributes, name, value);

        // All not manage attributes
        // First attribute alert and className generate only a warning
        this.addAlertAndClass(attributes, modelFormField, context);
        // cols and rows are always present because they have default value
        attributes.put("cols", textareaField.getCols()); // default value 60
        attributes.put("rows", textareaField.getRows()); // default value 2
        if (textareaField.getCols() != 60 || textareaField.getRows() !=2 ) {
            Debug.logWarning("textAreaField with cols or/and rows attribute is used for field name="+name+
                    " in form with name="+formName +
                    "  it's not manage by FrontFjRenderer", MODULE);
        }

        if (textareaField.getVisualEditorEnable()) {
            attributes.put("visualEditorEnable", "true");
            if (UtilValidate.isNotEmpty(textareaField.getVisualEditorButtons(context))) {
                attributes.put("disabled", textareaField.getVisualEditorButtons(context));
            } else {
                attributes.put("disabled", "maxi");
            }
        }
        String tabindex = modelFormField.getTabindex();
        String event = modelFormField.getEvent();
        String action = modelFormField.getAction(context);
        boolean disabled = modelFormField.getDisabled();
        if (textareaField.isReadOnly() || textareaField.getVisualEditorEnable()
                || UtilValidate.isNotEmpty(event) || UtilValidate.isNotEmpty(action)
                || disabled || UtilValidate.isNotEmpty(tabindex)) {

            Map<String, Object> userLogin = UtilGenerics.cast(context.get("userLogin"));
            String language = "en";
            if (userLogin != null) {
                language = UtilValidate.isEmpty((String) userLogin.get("lastLocale")) ? "en" : (String) userLogin.get("lastLocale");
            }
            attributes.put("language", language);
            if (textareaField.isReadOnly())        attributes.put("readonly", "readonly");
            if (UtilValidate.isNotEmpty(event))    attributes.put("event", event);
            if (UtilValidate.isNotEmpty(action))   attributes.put("action", action);
            if (disabled)                          attributes.put("disabled", disabled);
            if (UtilValidate.isNotEmpty(tabindex)) attributes.put("tabindex", tabindex);
            throw new IOException("FrontJsRender: a attribute is not yet implemented for textArea-field name=" + name
                    + " in form for form name=" + modelFormField.getModelForm().getName()
                    + " attribute is one of : readonly("+textareaField.isReadOnly()
                    + "), visualEditorEnable("+textareaField.getVisualEditorEnable()
                    + "), editorButtons("+textareaField.getVisualEditorButtons(context)
                    + "), event("+event + "), action("+action
                    + "), disabled("+disabled+ "), tabindex("+tabindex+")");
        }
    }

    public void renderDateTimeField(Appendable writer, Map<String, Object> context, DateTimeField dateTimeField) throws IOException {
        ModelFormField modelFormField = dateTimeField.getModelFormField();
        String name = modelFormField.getParameterName(context);;
        String formName = modelFormField.getModelForm().getName();
        String id = modelFormField.getCurrentContainerId(context);
        Map<String, Object> attributes = new HashMap<>();
        if ("single".equals(modelFormField.getModelForm().getType())) this.addTitle(attributes, modelFormField, context);

        String contextValue = modelFormField.getEntry(context, dateTimeField.getDefaultValue(context));

        attributes.put("formName", formName);
        attributes.put("name", name);
        attributes.put("value", contextValue);
        if ("12".equals(dateTimeField.getClock())) attributes.put("isTwelveHour", "Y");
        if (UtilValidate.isNotEmpty(id))           attributes.put("id", id); // not yet used in vuejs, waiting use case
        this.addAsterisks(attributes, context, modelFormField);
        this.appendTooltip(attributes, context, modelFormField);
        this.output.putScreen("DateTimeField", attributes, name, contextValue);

        // All not-manage attributes
        // First attribute alert and className generate only a warning
        this.addAlertAndClass(attributes, modelFormField, context);
        // second: mask=, nothing to do currently because with material design it's managed
        /*
        String mask = dateTimeField.getMask();
        String formattedMask = "";
        if ("Y".equals(mask)) {
            if ("date".equals(dateTimeField.getType())) {
                formattedMask = "9999-99-99";
            } else if ("time".equals(dateTimeField.getType())) {
                formattedMask = "99:99:99";
            } else if ("timestamp".equals(dateTimeField.getType())) {
                formattedMask = "9999-99-99 99:99:99";
            }
            attributes.put("formattedMask", formattedMask);
        }
        */
        // third, warning, with material design it's always text and "dropdown", so put a warning
        //      to check if current management is correct for field where it's used
        if ("time-dropdown".equals(dateTimeField.getInputMethod()) || !"1".equals(dateTimeField.getStep()) ) {
            Debug.logWarning("date-time Field with time-dropdown or/and step attribute is used for field name="+name+
                    " in form with name="+formName +
                    "  it's not manage by FrontFjRenderer", MODULE);
        }
        //last, list of attributes which generate error if present because not manage and should be.
        FlexibleStringExpander defaultValue = dateTimeField.getDefaultValue();
        String event = modelFormField.getEvent();
        String action = modelFormField.getAction(context);
        boolean disabled = modelFormField.getDisabled();
        String tabindex = modelFormField.getTabindex();
        if (! "timestamp".equals(dateTimeField.getType())
                     || UtilValidate.isNotEmpty(event) || UtilValidate.isNotEmpty(action) || disabled
                     || UtilValidate.isNotEmpty(defaultValue) || UtilValidate.isNotEmpty(tabindex)) {

            attributes.put("dateType", dateTimeField.getType());
            if (UtilValidate.isNotEmpty(event))              attributes.put("event", event);
            if (UtilValidate.isNotEmpty(action))             attributes.put("action", action);
            if (disabled)                                    attributes.put("disabled", disabled);
            if (UtilValidate.isNotEmpty(tabindex))           attributes.put("tabindex", tabindex);
            if (UtilValidate.isNotEmpty(defaultValue))       attributes.put("defaultDateTimeString",
                                                     dateTimeField.getDefaultDateTimeString(context));
            throw new IOException("FrontJsRender: a attribute is not yet implemented for date-time-field name=" + name
                    + " in form for form name=" + modelFormField.getModelForm().getName()
                    + " attribute is one of : dateType("+dateTimeField.getType()
                    + "), defaultValue("+dateTimeField.getDefaultDateTimeString(context)
                    + "), event("+event + "), action("+action + "), tabindex("+tabindex+")");
        }
    }

    public void renderDropDownField(Appendable writer, Map<String, Object> context, DropDownField dropDownField) throws IOException {
        ModelFormField modelFormField = dropDownField.getModelFormField();
        ModelForm modelForm = modelFormField.getModelForm();
        String id = modelFormField.getCurrentContainerId(context);
        String name = modelFormField.getName();
        String formName = modelForm.getName();
        String currentValue = modelFormField.getEntry(context);
        List<OptionValue> allOptionValues = dropDownField.getAllOptionValues(context, WidgetWorker.getDelegator(context));
        Integer textSize = 0;
        if (UtilValidate.isNotEmpty(dropDownField.getTextSize())) {
            try {
                textSize = Integer.parseInt(dropDownField.getTextSize());
            } catch (NumberFormatException nfe) {
                Debug.logError(nfe, "Error reading size of a field fieldName=" + dropDownField.getModelFormField().getFieldName() + " FormName= " + dropDownField.getModelFormField().getModelForm().getName(), MODULE);
            }
            if (textSize > 0 && UtilValidate.isNotEmpty(currentValue) && currentValue.length() > textSize) {
                currentValue = currentValue.substring(0, textSize - 8) + "..." + currentValue.substring(currentValue.length() - 5);
            }
        }
        String explicitDescription;
        String currentDescription = null;
        if (UtilValidate.isNotEmpty(currentValue)) {
            for (OptionValue optionValue : allOptionValues) {
                if (optionValue.getKey().equals(currentValue)) {
                    currentDescription = optionValue.getDescription();
                    break;
                }
            }
        }
        explicitDescription = (currentDescription != null ? currentDescription : dropDownField.getCurrentDescription(context));
        if (UtilValidate.isEmpty(explicitDescription)) {
            explicitDescription = (FieldInfoWithOptions.getDescriptionForOptionKey(currentValue, allOptionValues));
        }
        if (textSize > 0 && UtilValidate.isNotEmpty(explicitDescription) && explicitDescription.length() > textSize) {
            explicitDescription = explicitDescription.substring(0, textSize - 8) + "..." + explicitDescription.substring(explicitDescription.length() - 5);
        }
        explicitDescription = encode(explicitDescription, modelFormField, context);
        /* allow-multiple is not yet manage, currently generate an error message
        List<String> currentValueList = null;
        if (UtilValidate.isNotEmpty(currentValue) && dropDownField.getAllowMultiple()) {
            // If currentValue is Array, it will start with [
            if (currentValue.startsWith("[")) {
                currentValueList = StringUtil.toList(currentValue);
            } else {
                currentValueList = UtilMisc.toList(currentValue);
            }
        }*/

        List<Map<String, String>> options = new ArrayList<>();
        Iterator<OptionValue> optionValueIter = allOptionValues.iterator();
        while (optionValueIter.hasNext()) {
            OptionValue optionValue = optionValueIter.next();

            Map<String, String> option = new HashMap<>();
            String key = encode(optionValue.getKey(), modelFormField, context);
            option.put("key", key);
            String description = optionValue.getDescription();
            if (textSize > 0 && description.length() > textSize) {
                description = description.substring(0, textSize - 8) + "..." + description.substring(description.length() - 5);
            }
            option.put("description", encode(description.replaceAll("'", "\\\\\'"), modelFormField, context));  // replaceAll("'", "\\\\\'") related to OFBIZ-6504
            /* allow-multiple is not yet manage, currently generate an error message
            if (UtilValidate.isNotEmpty(currentValueList)) {
                option.put("selected", "selected");
            }
            */
            options.add(option);
        }

        Map<String, Object> cb = new HashMap<>();
        if ("single".equals(modelFormField.getModelForm().getType())) this.addTitle(cb, modelFormField, context);
        this.addAsterisks(cb, context, modelFormField);
        cb.put("name", name);
        cb.put("formName", formName);
        cb.put("id", id);
        cb.put("currentValue", currentValue);
        cb.put("options", options);
        String noCurrentSelectedKey = dropDownField.getNoCurrentSelectedKey(context);
        if (UtilValidate.isNotEmpty(noCurrentSelectedKey)) cb.put("noCurrentSelectedKey", noCurrentSelectedKey);
        this.appendTooltip(cb, context, modelFormField);
        this.output.putScreen("DropDownField", cb, name, explicitDescription);

        // not used by Vue.Js, but seems to be usable !
        cb.put("explicitDescription", explicitDescription);

        // All not-manage attributes
        // First attribute alert and className generate only a warning
        this.addAlertAndClass(cb, modelFormField, context);
        //if (dropDownField.getAllowEmpty()) cb.put("allowEmpty", "Y"); TODO test what is manage
        //Second, list of attributes which generate error if present because not manage and should be.
        String event = modelFormField.getEvent();
        String action = modelFormField.getAction(context);
        boolean disabled = modelFormField.getDisabled();
        String tabindex = modelFormField.getTabindex();
        String conditionGroup = modelFormField.getConditionGroup();
       if ( dropDownField.getAllowMultiple()
                     || ! "selected".equals(dropDownField.getCurrent())
                     || dropDownField.getOtherFieldSize() > 0
                     || ! "1".equals(dropDownField.getSize())
                     || dropDownField.getAutoComplete() != null
                     || UtilValidate.isNotEmpty(event) || UtilValidate.isNotEmpty(action) || disabled
                     || UtilValidate.isNotEmpty(tabindex) || UtilValidate.isNotEmpty(conditionGroup)) {

            if (dropDownField.getAllowMultiple()) cb.put("multiple", "multiple");
            if (! "selected".equals(dropDownField.getCurrent())) cb.put("dDFCurrent", dropDownField.getCurrent());
            if (UtilValidate.isNotEmpty(currentValue) && "first-in-list".equals(dropDownField.getCurrent())) {
                cb.put("firstInList", "first-in-list"); // if the current value should go first, stick it in
            }
            if (! "1".equals(dropDownField.getSize())) cb.put("size", dropDownField.getSize());
            // for autocomplete see the commented code just after
            // for otherFieldSize see the commented code just after
            if (UtilValidate.isNotEmpty(event))              cb.put("event", event);
            if (UtilValidate.isNotEmpty(action))             cb.put("action", action);
            if (UtilValidate.isNotEmpty(tabindex))           cb.put("tabindex", tabindex);
            if (UtilValidate.isNotEmpty(conditionGroup))     cb.put("conditionGroup", conditionGroup);
            throw new IOException("FrontJsRender: a attribute is not yet implemented for drop-down-field name=" + name
                    + " in form for form name=" + modelFormField.getModelForm().getName()
                    + " attribute is one of : noCurrentSelectedKey("+noCurrentSelectedKey
                    + "), allow-multiple("+dropDownField.getAllowMultiple()
                    + "), current("+dropDownField.getCurrent()
                    + "), size("+dropDownField.getSize()
                    + "), other-field-size("+dropDownField.getOtherFieldSize()
                    + "), auto-complete("+dropDownField.getAutoComplete()
                    + "), event("+event + "), action("+action+"), disabled("+disabled
                    + "), tabindex("+tabindex+"), conditionGroup("+conditionGroup+")");
        }

        /* Begin autocomplete options management
        AutoComplete autoComplete = dropDownField.getAutoComplete();
        boolean ajaxEnabled = autoComplete != null && this.javaScriptEnabled;
        cb.put("ajaxEnabled", ajaxEnabled);
        StringBuilder ajaxOptions = new StringBuilder();
        int count = 0;
        Iterator<OptionValue> optionValueIter2 = allOptionValues.iterator(); // new iterator because,
                                        //I not know how to re-initialize it the beginning of the list
        while (optionValueIter2.hasNext()) {
            OptionValue optionValue = optionValueIter2.next();
            if (ajaxEnabled) {
                count++;
                ajaxOptions.append(optionValue.getKey()).append(": ");
                ajaxOptions.append(" '").append(optionValue.getDescription()).append("'");
                if (count != allOptionValues.size()) {
                    ajaxOptions.append(", ");
                }
            }
        }
        cb.put("ajaxOptions", ajaxOptions.toString());
        cb.put("frequency",     autoComplete.getFrequency());
        cb.put("minChars",      autoComplete.getMinChars());
        cb.put("choices",       autoComplete.getChoices());
        cb.put("autoSelect",    autoComplete.getAutoSelect());
        cb.put("partialSearch", autoComplete.getPartialSearch());
        cb.put("partialChars",  autoComplete.getPartialChars());
        cb.put("ignoreCase",    autoComplete.getIgnoreCase());
        cb.put("fullSearch",    autoComplete.getFullSearch());
        // End autocomplete options management

        // Begin otherFieldSize management (if > 0)
        String otherValue = "";
        String otherFieldName = "";
        int otherFieldSize = dropDownField.getOtherFieldSize();
        if (otherFieldSize > 0) {
            otherFieldName = dropDownField.getParameterNameOther(context);
        }
        // Adapted from work by Yucca Korpela
        // http://www.cs.tut.fi/~jkorpela/forms/combo.html
        if (otherFieldSize > 0) {
            fieldName = modelFormField.getParameterName(context);
            Map<String, ?> dataMap = modelFormField.getMap(context);
            if (dataMap == null) {
                dataMap = context;
            }
            Object otherValueObj = dataMap.get(otherFieldName);
            otherValue = (otherValueObj == null) ? "" : otherValueObj.toString();
            cb.put("otherFieldName", otherFieldName);
            cb.put("otherValue", otherValue);
            cb.put("otherFieldSize", otherFieldSize);

        }
        */

    }

    public void renderCheckField(Appendable writer, Map<String, Object> context, CheckField checkField) throws IOException {
        ModelFormField modelFormField = checkField.getModelFormField();
        String currentValue = modelFormField.getEntry(context);
        Boolean allChecked = checkField.isAllChecked(context);
        String id = modelFormField.getCurrentContainerId(context);
        String name = modelFormField.getName();
        List<OptionValue> allOptionValues = checkField.getAllOptionValues(context, WidgetWorker.getDelegator(context));
        List<Map<String, String>> optionValues = new LinkedList<>();
        for (OptionValue optionValue : allOptionValues) {
            optionValues.add(UtilMisc.toMap(optionValue.getKey(), optionValue.getDescription()));
        }
        Map<String, Object> cb = new HashMap<>();
        if ("single".equals(modelFormField.getModelForm().getType())) this.addTitle(cb, modelFormField, context);
        this.addAsterisks(cb, context, modelFormField);
        cb.put("optionValues", optionValues);
        cb.put("id", id);
        if (allChecked) cb.put("allChecked", true);
        cb.put("currentValue", currentValue);
        cb.put("name", name);

        this.appendTooltip(cb, context, modelFormField);
        this.output.putScreen("CheckField", cb, name, currentValue);
        if (UtilValidate.isNotEmpty(cb)) { // condition always true, so IOException always occurs
            throw new IOException("FrontJsRender: check-field is not yet manage for field name=" + name
                    + " in form for form name=" + modelFormField.getModelForm().getName());
        }
        // All not-manage attributes
        // First attribute alert and className generate only a warning
        this.addAlertAndClass(cb, modelFormField, context);
        //Second, list of attributes which generate error if present because not manage and should be.
        String event = modelFormField.getEvent();
        String action = modelFormField.getAction(context);
        boolean disabled = modelFormField.getDisabled();
        String tabindex = modelFormField.getTabindex();
        String conditionGroup = modelFormField.getConditionGroup();
        if (disabled
                || UtilValidate.isNotEmpty(event) || UtilValidate.isNotEmpty(action) || disabled
                || UtilValidate.isNotEmpty(conditionGroup) || UtilValidate.isNotEmpty(tabindex)) {
            if (UtilValidate.isNotEmpty(event))              cb.put("event", event);
            if (UtilValidate.isNotEmpty(action))             cb.put("action", action);
            if (disabled)                                    cb.put("disabled", disabled);
            if (UtilValidate.isNotEmpty(tabindex))           cb.put("tabindex", tabindex);
            if (UtilValidate.isNotEmpty(conditionGroup))     cb.put("conditionGroup", conditionGroup);
            throw new IOException("FrontJsRender: a attribute is not yet implemented for check-field name=" + name
                    + " in form for form name=" + modelFormField.getModelForm().getName()
                    + " attribute is one of : disabled("+disabled
                    + "), event("+event + "), action("+action+"), disabled("+disabled
                    + "), tabindex("+tabindex+"), conditionGroup("+conditionGroup+")");

       }
    }

    public void renderRadioField(Appendable writer, Map<String, Object> context, RadioField radioField) throws IOException {
        ModelFormField modelFormField = radioField.getModelFormField();
        String currentValue = modelFormField.getEntry(context);
        String name = modelFormField.getName();
        String formName = radioField.getModelFormField().getModelForm().getName();

        Map<String, Object> cb = new HashMap<>();
        if ("single".equals(modelFormField.getModelForm().getType())) this.addTitle(cb, modelFormField, context);

        List<UpdateArea> clickUpdateAreas = modelFormField.getOnClickUpdateAreas();
        if (!clickUpdateAreas.isEmpty()) {
            List<Map<String, Object>> clickUpdateAreasList = new ArrayList<>();
            for (UpdateArea updateArea : clickUpdateAreas) {
                clickUpdateAreasList.add(updateArea.toMap(context));
            }
            cb.put("clickUpdateAreas", clickUpdateAreasList);
        }

        List<OptionValue> allOptionValues = radioField.getAllOptionValues(context, WidgetWorker.getDelegator(context));
        List<Map<String, String>> items = new ArrayList<>();
        for (OptionValue optionValue : allOptionValues) {
            items.add(UtilMisc.toMap(
                    "key", optionValue.getKey(),
                    "description",encode(optionValue.getDescription(), modelFormField, context)));
        }
        cb.put("items", items);

        cb.put("currentValue", currentValue);
        cb.put("name", name);
        cb.put("formName", formName);
        this.addAsterisks(cb, context, modelFormField);
        this.appendTooltip(cb, context, modelFormField);
        this.output.putScreen("RadioField", cb, name, currentValue);

        // All not-manage attributes
        // First attribute alert and className generate only a warning
        this.addAlertAndClass(cb, modelFormField, context);
        //Second, list of attributes which generate error if present because not manage and should be.
        String event = modelFormField.getEvent();
        String action = modelFormField.getAction(context);
        boolean disabled = modelFormField.getDisabled();
        String tabindex = modelFormField.getTabindex();
        String conditionGroup = modelFormField.getConditionGroup();
        String noCurrentSelectedKey = radioField.getNoCurrentSelectedKey(context); // info: it is manage for drop-donw
        if ( UtilValidate.isNotEmpty(noCurrentSelectedKey)
                || UtilValidate.isNotEmpty(event) || UtilValidate.isNotEmpty(action) || disabled
                || UtilValidate.isNotEmpty(conditionGroup) || UtilValidate.isNotEmpty(tabindex)) {
            if (UtilValidate.isNotEmpty(event))              cb.put("event", event);
            if (UtilValidate.isNotEmpty(action))             cb.put("action", action);
            if (disabled)                                    cb.put("disabled", disabled);
            if (UtilValidate.isNotEmpty(tabindex))           cb.put("tabindex", tabindex);
            if (UtilValidate.isNotEmpty(conditionGroup))     cb.put("conditionGroup", conditionGroup);
            throw new IOException("FrontJsRender: a attribute is not yet implemented for radio-field name=" + name
                    + " in form for form name=" + modelFormField.getModelForm().getName()
                    + " attribute is one of: noCurrentSelectedKey("+noCurrentSelectedKey
                    + "), event("+event + "), action("+action+"), disabled("+disabled
                    + "), tabindex("+tabindex+"), conditionGroup("+conditionGroup+")");

       }
    }

    public void renderSubmitField(Appendable writer, Map<String, Object> context, SubmitField submitField) throws IOException {
        ModelFormField modelFormField = submitField.getModelFormField();
        ModelForm modelForm = modelFormField.getModelForm();
        String title = modelFormField.getTitle(context);
        String name = modelFormField.getName();
        String formName = FormRenderer.getCurrentFormName(modelForm, context);
        String formId = FormRenderer.getCurrentContainerId(modelForm, context);

        Map<String, Object> cb = new HashMap<>();
        if ("single".equals(modelFormField.getModelForm().getType())) this.addTitle(cb, modelFormField, context);

        cb.put("formName", formName);
        cb.put("title", encode(title, modelFormField, context));
        cb.put("name", name);

        List<UpdateArea> updateAreas = modelForm.getOnSubmitUpdateAreas(context);
        // This is here for backwards compatibility. Use on-event-update-area
        // elements instead.
        String backgroundSubmitRefreshTarget = submitField.getBackgroundSubmitRefreshTarget(context);
        if (UtilValidate.isNotEmpty(backgroundSubmitRefreshTarget)) {
            if (updateAreas == null) {
                updateAreas = new LinkedList<>();
            }
            updateAreas.add(new UpdateArea("submit", formId, backgroundSubmitRefreshTarget));
        }
        if (!updateAreas.isEmpty()) {
            List<Map<String, Object>> updateAreasValid = new LinkedList<>();
            for (UpdateArea updateArea : updateAreas) {
                updateAreasValid.add(updateArea.toMap(context));
            }
            cb.put("updateAreas", updateAreasValid);
        }

        this.appendTooltip(cb, context, modelFormField);
        this.output.putScreen("SubmitField", cb);

        // All not-manage attributes
        // First attribute alert and className generate only a warning
        this.addAlertAndClass(cb, modelFormField, context);
        if (UtilValidate.isNotEmpty(submitField.getButtonType()) && ! "button".equals(submitField.getButtonType())) {
            Debug.logWarning("Submit Field with button-type = "+submitField.getButtonType()+" is used for field name="+name+
                    " in form with name="+formName +
                    "  it's not manage by FrontFjRenderer", MODULE);
            cb.put("buttonType", submitField.getButtonType());
        }
        //Second, list of attributes which generate error if present because not manage and should be.
        String imgSrc = submitField.getImageLocation(context);
        String confirmation = submitField.getConfirmation(context);
        String event = modelFormField.getEvent();
        String action = modelFormField.getAction(context);
        boolean disabled = modelFormField.getDisabled();
        String tabindex = modelFormField.getTabindex();
        String conditionGroup = modelFormField.getConditionGroup();
        if ( UtilValidate.isNotEmpty(imgSrc) || UtilValidate.isNotEmpty(confirmation)
                || UtilValidate.isNotEmpty(event) || UtilValidate.isNotEmpty(action) || disabled
                || UtilValidate.isNotEmpty(conditionGroup) || UtilValidate.isNotEmpty(tabindex)) {
            if (UtilValidate.isNotEmpty(imgSrc))             cb.put("imgSrc", imgSrc);
            if (UtilValidate.isNotEmpty(confirmation))       cb.put("confirmation", confirmation);
            if (UtilValidate.isNotEmpty(event))              cb.put("event", event);
            if (UtilValidate.isNotEmpty(action))             cb.put("action", action);
            if (disabled)                                    cb.put("disabled", disabled);
            if (UtilValidate.isNotEmpty(tabindex))           cb.put("tabindex", tabindex);
            if (UtilValidate.isNotEmpty(conditionGroup))     cb.put("conditionGroup", conditionGroup);
            throw new IOException("FrontJsRender: a attribute is not yet implemented for submit-field name=" + name
                    + " in form for form name=" + modelFormField.getModelForm().getName()
                    + " attribute is one of: imgSrc("+ imgSrc
                    + "), confirmation("+confirmation
                    + "), event("+event + "), action("+action+"), disabled("+disabled
                    + "), tabindex("+tabindex+"), conditionGroup("+conditionGroup+")");

       }
    }

    // OHE point of review, to be continue
    public void renderResetField(Appendable writer, Map<String, Object> context, ResetField resetField) {
        ModelFormField modelFormField = resetField.getModelFormField();
        String name = modelFormField.getParameterName(context);
        String className = "";
        String alert = "false";
        if (UtilValidate.isNotEmpty(modelFormField.getWidgetStyle())) {
            className = modelFormField.getWidgetStyle();
            if (modelFormField.shouldBeRed(context)) {
                alert = "true";
            }
        }
        String title = modelFormField.getTitle(context);
        Map<String, Object> cb = new HashMap<>();
        if ("single".equals(modelFormField.getModelForm().getType())) this.addTitle(cb, modelFormField, context);
        cb.put("className", className);
        cb.put("alert", alert);
        cb.put("name", name);
        cb.put("title", title);
        this.appendTooltip(cb, context, modelFormField);
        this.output.putScreen("ResetField", cb);
    }

    public void renderHiddenField(Appendable writer, Map<String, Object> context, HiddenField hiddenField) {
        ModelFormField modelFormField = hiddenField.getModelFormField();
        String value = hiddenField.getValue(context);
        this.renderHiddenField(writer, context, modelFormField, value);
    }

    public void renderHiddenField(Appendable writer, Map<String, Object> context, ModelFormField modelFormField, String value) {
        String name = modelFormField.getName();
        String action = modelFormField.getAction(context);
        String conditionGroup = modelFormField.getConditionGroup();
        String event = modelFormField.getEvent();
        String id = modelFormField.getCurrentContainerId(context);
        String formName = FormRenderer.getCurrentFormName(modelFormField.getModelForm(), context);
        Map<String, Object> cb = new HashMap<>();
        cb.put("name", name);
        cb.put("conditionGroup", conditionGroup);
        cb.put("value", value);
        cb.put("id", id);
        if (event != null) {
            cb.put("event", event);
        }
        if (action != null) {
            cb.put("action", action);
        }
        if (formName != null) {
            cb.put("formName", formName);
        }
        this.output.putScreen("HiddenField", cb);
    }

    public void renderIgnoredField(Appendable writer, Map<String, Object> context, IgnoredField ignoredField) {
        // do nothing, it's an ignored field; could add a comment or something if we wanted to
    }

    public void renderFieldTitle(Appendable writer, Map<String, Object> context, ModelFormField modelFormField) throws IOException {
        String titleText = modelFormField.getTitle(context);
        String style = modelFormField.getTitleStyle();
        String id = modelFormField.getCurrentContainerId(context);
        StringBuilder sb = new StringBuilder();
        if (UtilValidate.isNotEmpty(titleText)) {
            if (" ".equals(titleText)) {
                this.output.putScreen("FormatEmptySpace", new HashMap<>());
            } else {
                titleText = UtilHttp.encodeAmpersands(titleText);
                titleText = encode(titleText, modelFormField, context);
                if (UtilValidate.isNotEmpty(modelFormField.getHeaderLink())) {
                    StringBuilder targetBuffer = new StringBuilder();
                    FlexibleStringExpander target = FlexibleStringExpander.getInstance(modelFormField.getHeaderLink());
                    String fullTarget = target.expandString(context);
                    targetBuffer.append(fullTarget);
                    String targetType = CommonWidgetModels.Link.DEFAULT_URL_MODE;
                    if (UtilValidate.isNotEmpty(targetBuffer.toString()) && targetBuffer.toString().toLowerCase(Locale
                            .getDefault()).startsWith("javascript:")) {
                        targetType = "plain";
                    }
                    Map<String, Object> cb = new HashMap<>();
                    makeHyperlinkString(cb, modelFormField.getHeaderLinkStyle(), targetType, targetBuffer.toString(), null, titleText, "", modelFormField, this.request, this.response, context, "");
                    cb.put("name", modelFormField.getModelForm().getName());
                    cb.put("title", cb.remove("HyperlinkString"));
                    this.output.putScreen("FieldTitle", cb);
                } else if (modelFormField.isSortField()) {
                    renderSortField(writer, context, modelFormField, titleText);
                } else if (modelFormField.isRowSubmit()) { // TODO devra être ré-activé
                    Map<String, Object> cb = new HashMap<>();
                    cb.put("name", modelFormField.getModelForm().getName());
                    cb.put("title", titleText);
                    cb.put("showSelectAll", "Y");
                    this.output.putScreen("HyperlinkTitle", cb);
                } else {
                    sb.append(titleText);
                }
            }
        }
        if (!sb.toString().isEmpty()) {
            //check for required field style on single forms
            if ("single".equals(modelFormField.getModelForm().getType()) && modelFormField.getRequiredField()) {
                String requiredStyle = modelFormField.getRequiredFieldStyle();
                if (UtilValidate.isNotEmpty(requiredStyle)) {
                    style = requiredStyle;
                }
            }
            Map<String, Object> cb = new HashMap<>();
            cb.put("style", style);
            String displayHelpText = UtilProperties.getPropertyValue("widget", "widget.form.displayhelpText");
            if ("Y".equals(displayHelpText)) {
                Delegator delegator = WidgetWorker.getDelegator(context);
                Locale locale = (Locale) context.get("locale");
                String entityName = modelFormField.getEntityName();
                String fieldName = modelFormField.getFieldName();
                String helpText = UtilHelpText.getEntityFieldDescription(entityName, fieldName, delegator, locale);

                cb.put("fieldHelpText", encodeDoubleQuotes(helpText));
            }
            cb.put("title", sb.toString());
            if (UtilValidate.isNotEmpty(id)) {
                cb.put("id", id);
                cb.put("for", id);
            }
            if (! "single".equals(modelFormField.getModelForm().getType())) {
                this.output.putScreen("FieldTitle", cb);
            }
        }
    }

    public void renderSingleFormFieldTitle(Appendable writer, Map<String, Object> context, ModelFormField modelFormField) throws IOException {
        renderFieldTitle(writer, context, modelFormField);
    }

    public void renderFormOpen(Appendable writer, Map<String, Object> context, ModelForm modelForm) throws IOException {
        String action = null;
        Map<String, Object> data = null;
        String targetType = modelForm.getTargetType();
        String target = modelForm.getTarget(context, targetType);
        /* buildHyperlinkUri not supports uri with parameters write with {xxxx}
        // should be study to know if it's a bug or parameters like this should not appear
        // in the code review, should test if targetType = inter-app <= not manage
        StringBuilder linkUrl = new StringBuilder();
        if (UtilValidate.isNotEmpty(target)) {
        	final URI linkUri = WidgetWorker.buildHyperlinkUri(target, targetType, null, null, false, false, true, request, response);
        	linkUrl.append(linkUri.toString());
        }
        */
        String formType = modelForm.getType();
        String targetWindow = modelForm.getTargetWindow(context);
        String containerId = FormRenderer.getCurrentContainerId(modelForm, context);
        String containerStyle = modelForm.getContainerStyle();
        String autocomplete = "";
        String name = FormRenderer.getCurrentFormName(modelForm, context);
        String viewIndexField = modelForm.getMultiPaginateIndexField(context);
        String viewSizeField = modelForm.getMultiPaginateSizeField(context);
        int viewIndex = Paginator.getViewIndex(modelForm, context);
        int viewSize = Paginator.getViewSize(modelForm, context);
        boolean useRowSubmit = modelForm.getUseRowSubmit();
        if (!modelForm.getClientAutocompleteFields()) {
            autocomplete = "off";
        }
        String hasRequiredField = "";
        for (ModelFormField formField : modelForm.getFieldList()) {
            if (formField.getRequiredField()) {
                hasRequiredField = "Y";
                break;
            }
        }
        String focusFieldName = modelForm.getFocusFieldName();
        Map<String, Object> cb = new HashMap<>();
        /* buildHyperlinkUri not supports uri with parameters write with {xxxx}
        if (!target.isEmpty() && !linkUrl.toString().isEmpty()) {
            cb.put("linkUrl", linkUrl.toString());
        } else {
            cb.put("linkUrl", "");
        }
        */
        if (!target.isEmpty()) {
            cb.put("target", target);
            cb.put("targetType", targetType);
        } else {
            cb.put("target", "");
        }
        cb.put("formType", formType);
        cb.put("targetWindow", targetWindow);
        cb.put("containerId", containerId);
        cb.put("containerStyle", containerStyle);
        cb.put("autocomplete", autocomplete);
        cb.put("name", name);
        cb.put("focusFieldName", focusFieldName);
        cb.put("hasRequiredField", hasRequiredField);
        cb.put("viewIndexField", viewIndexField);
        cb.put("viewSizeField", viewSizeField);
        cb.put("viewIndex", viewIndex);
        cb.put("viewSize", viewSize);
        cb.put("useRowSubmit", useRowSubmit);
        cb.put("entityName", modelForm.getDefaultEntityName());
//        OH 2019.03.12 à priori non utilisé, s'il faut le réactiver, il faut gérer le format des champs date, cf ExampleFeatureppl
//        if (!modelForm.getDefaultMapName().equals("") && ((GenericValue) context.get(modelForm.getDefaultMapName())) != null) {
//            cb.put("primaryKey", ((GenericValue) context.get(modelForm.getDefaultMapName())).getPrimaryKey());
//        }

        // Begin data
        String defaultEntityName = modelForm.getDefaultEntityName();
        if (UtilValidate.isNotEmpty(defaultEntityName)) {
            action = "PUSH_ENTITY";
            data = new HashMap<>();
            data.put("entityName", defaultEntityName);
            data.put("primaryKeys", getPkList(defaultEntityName, ((Delegator)context.get("delegator")).getModelReader()));
        }
        // End data
        this.output.pushScreen("FormOpen", cb, action, data);
    }

    public void renderFormClose(Appendable writer, Map<String, Object> context, ModelForm modelForm) {
//        HashMap<String, Object> hashMapStringObject = new HashMap<String, Object>();
        String action = null;

        // Begin data
        String defaultEntityName = modelForm.getDefaultEntityName();
        ModelReader entityModelReader = ((Delegator)context.get("delegator")).getModelReader();
        ModelEntity modelEntity = null;
        if (UtilValidate.isNotEmpty(defaultEntityName)) {
            try {
                modelEntity = entityModelReader.getModelEntity(defaultEntityName);
            } catch (GenericEntityException e) {
                Debug.logError(e, MODULE);
            }
            if (modelEntity == null) {
                throw new IllegalArgumentException("Error finding Entity with name " + defaultEntityName
                        + " for defaut-entity-name in a form widget");
            } else {
//                List<String> pkList = modelEntity.getPkFieldNames();
                action = "POP_ENTITY";
            }
        }
        // End data

        this.output.popScreen("FormClose", action);
    }

    public void renderMultiFormClose(Appendable writer, Map<String, Object> context, ModelForm modelForm) {
        // see if there is anything that needs to be added outside of the multi-form
//        Map<String, Object> wholeFormContext = UtilGenerics.checkMap(context.get("wholeFormContext"));
//        todo: Have to understand this below
//        if (UtilValidate.isNotEmpty(wholeFormContext)) {
//            cb.put("wholeFormContext", wholeFormContext);
//        }
        this.output.popScreen("MultiFormClose");

    }

    public void renderFormatListWrapperOpen(Appendable writer, Map<String, Object> context, ModelForm modelForm) {
        String action = null;
        Map<String, Object> data = null;
        Map<String, Object> inputFields = UtilGenerics.cast(context.get("requestParameters"));
        Object obj = context.get("queryStringMap");
        Map<String, Object> queryStringMap = (obj instanceof Map) ? UtilGenerics.cast(obj) : null;
        if (UtilValidate.isNotEmpty(queryStringMap)) {
            inputFields.putAll(queryStringMap);
        }
        if ("multi".equals(modelForm.getType())) {
            inputFields = UtilHttp.removeMultiFormParameters(inputFields);
        }
        String queryString = UtilHttp.urlEncodeArgs(inputFields);
        context.put("_QBESTRING_", queryString);
        if (this.renderPagination) {
            this.renderNextPrev(writer, context, modelForm);
        }
        List<ModelFormField> childFieldList = modelForm.getFieldList();
        List<String> columnStyleList = new LinkedList<>();
        List<String> fieldNameList = new LinkedList<>();
        for (ModelFormField childField : childFieldList) {
            int childFieldType = childField.getFieldInfo().getFieldType();
            if (childFieldType == FieldInfo.HIDDEN || childFieldType == FieldInfo.IGNORED) {
                continue;
            }
            String areaStyle = childField.getTitleAreaStyle();
            if (UtilValidate.isEmpty(areaStyle)) {
                areaStyle = "";
            }
            if (fieldNameList.contains(childField.getName())) {
                if (UtilValidate.isNotEmpty(areaStyle)) {
                    columnStyleList.set(fieldNameList.indexOf(childField.getName()), areaStyle);
                }
            } else {
                columnStyleList.add(areaStyle);
                fieldNameList.add(childField.getName());
            }
        }
        String columnStyleListString =
                columnStyleList.stream().map(str -> "'" + str + "'").collect(Collectors.joining(", "));
        Map<String, Object> cb = new HashMap<>();
        cb.put("formName", modelForm.getName());
        cb.put("style", FlexibleStringExpander.expandString(modelForm.getDefaultTableStyle(), context));
        if (UtilValidate.isNotEmpty(columnStyleListString)) {
            // this is a fix for forms with no fields
            cb.put("columnStyles", columnStyleListString);
        }

        // Begin data
        String defaultEntityName = modelForm.getDefaultEntityName();
        if (UtilValidate.isNotEmpty(defaultEntityName)) {
            data = new HashMap<>();
            action = "PUSH_ENTITY";
            data.put("entityName", defaultEntityName);
            data.put("primaryKeys", getPkList(defaultEntityName, ((Delegator)context.get("delegator")).getModelReader()));
        } else {
          //TODO add a log info to explain that store.entity will be not used. Logical when data will not be update by other portlet
        }
        cb.put("listSize", Paginator.getListSize(context));
        // End data
        this.output.pushScreen("ListWrapperOpen", cb, action, data);
    }
    public void renderEmptyFormDataMessage(Appendable writer, Map<String, Object> context, ModelForm modelForm) {
        Map<String, Object> cb = new HashMap<>();
//        cb.put("message", modelForm.getEmptyFormDataMessage(context));
        this.output.putScreen("EmptyFormDataMessage", cb);
    }
    public void renderFormatListWrapperClose(Appendable writer, Map<String, Object> context, ModelForm modelForm) {
        if (UtilValidate.isNotEmpty(modelForm.getDefaultEntityName())) {
            this.output.popScreen("ListWrapperClose", "POP_ENTITY");
        } else {
            this.output.popScreen("ListWrapperClose");
        }
        if (this.renderPagination) {
            this.renderNextPrev(writer, context, modelForm);
        }
    }

    public void renderFormatHeaderOpen(Appendable writer, Map<String, Object> context, ModelForm modelForm) {
        this.output.pushScreen("HeaderOpen", new HashMap<String, Object>());
    }

    public void renderFormatHeaderClose(Appendable writer, Map<String, Object> context, ModelForm modelForm) {
        this.output.popScreen("HeaderClose");
    }

    public void renderFormatHeaderRowOpen(Appendable writer, Map<String, Object> context, ModelForm modelForm) {
        String headerStyle = FlexibleStringExpander.expandString(modelForm.getHeaderRowStyle(), context);
        Map<String, Object> cb = new HashMap<>();
        cb.put("style", headerStyle);
        this.output.pushScreen("HeaderRowOpen", cb);
    }

    public void renderFormatHeaderRowClose(Appendable writer, Map<String, Object> context, ModelForm modelForm) {
        this.output.popScreen("HeaderRowClose");
    }

    public void renderFormatHeaderRowCellOpen(Appendable writer, Map<String, Object> context, ModelForm modelForm, ModelFormField modelFormField, int positionSpan) {
        String areaStyle = modelFormField.getTitleAreaStyle();
        Map<String, Object> cb = new HashMap<>();
        cb.put("style", areaStyle);
        cb.put("positionSpan", positionSpan);
        this.output.pushScreen("HeaderRowCellOpen", cb);
    }

    public void renderFormatHeaderRowCellClose(Appendable writer, Map<String, Object> context, ModelForm modelForm, ModelFormField modelFormField) {
        this.output.popScreen("HeaderRowCellClose");
    }

    public void renderFormatHeaderRowFormCellOpen(Appendable writer, Map<String, Object> context, ModelForm modelForm) {
        String areaStyle = modelForm.getFormTitleAreaStyle();
        Map<String, Object> cb = new HashMap<>();
        cb.put("style", areaStyle);
        this.output.pushScreen("HeaderRowFormCellOpen", cb);
    }

    public void renderFormatHeaderRowFormCellClose(Appendable writer, Map<String, Object> context, ModelForm modelForm) {
        this.output.popScreen("HeaderRowFormCellClose");
    }

    public void renderFormatHeaderRowFormCellTitleSeparator(Appendable writer, Map<String, Object> context, ModelForm modelForm, ModelFormField modelFormField, boolean isLast) {
        String titleStyle = modelFormField.getTitleStyle();
        Map<String, Object> cb = new HashMap<>();
        cb.put("style", titleStyle);
        cb.put("isLast", isLast);
        this.output.putScreen("FormatHeaderRowFormCellTitleSeparator", cb);
    }

    public void renderFormatItemRowOpen(Appendable writer, Map<String, Object> context, ModelForm modelForm) {
        Integer itemIndex = (Integer) context.get("itemIndex");
        String altRowStyles = "";
        String evenRowStyle = "";
        String oddRowStyle = "";
        if (itemIndex != null) {
            altRowStyles = modelForm.getStyleAltRowStyle(context);
            if (itemIndex % 2 == 0) {
                evenRowStyle = modelForm.getEvenRowStyle();
            } else {
                oddRowStyle = FlexibleStringExpander.expandString(modelForm.getOddRowStyle(), context);
            }
        }
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("formName", modelForm.getName());
        attributes.put("itemIndex", String.valueOf(itemIndex));
        if (UtilValidate.isNotEmpty(altRowStyles)) attributes.put("altRowStyles", altRowStyles);
        if (UtilValidate.isNotEmpty(evenRowStyle)) attributes.put("evenRowStyle", evenRowStyle);
        if (UtilValidate.isNotEmpty(oddRowStyle))  attributes.put("oddRowStyle", oddRowStyle);
        //                                             //newRecord
        this.output.pushScreen("ItemRowOpen", attributes, "NEW_RECORD", context);
    }

    public void renderFormatItemRowClose(Appendable writer, Map<String, Object> context, ModelForm modelForm) {
        this.output.popScreen("ItemRowClose", "STORE_RECORD");
    }

    public void renderFormatItemRowCellOpen(Appendable writer, Map<String, Object> context, ModelForm modelForm, ModelFormField modelFormField, int positionSpan) {
        String areaStyle = modelFormField.getWidgetAreaStyle();
        Map<String, Object> cb = new HashMap<>();
        cb.put("fieldName", modelFormField.getName());
        cb.put("style", areaStyle);
        cb.put("positionSpan", positionSpan);
        this.output.pushScreen("ItemRowCellOpen", cb);
    }

    public void renderFormatItemRowCellClose(Appendable writer, Map<String, Object> context, ModelForm modelForm, ModelFormField modelFormField) {
        this.output.popScreen("ItemRowCellClose");
    }

    public void renderFormatItemRowFormCellOpen(Appendable writer, Map<String, Object> context, ModelForm modelForm) {
        String areaStyle = modelForm.getFormTitleAreaStyle();
        Map<String, Object> cb = new HashMap<>();
        cb.put("style", areaStyle);
        this.output.pushScreen("ItemRowFormCellOpen", cb);
    }

    public void renderFormatItemRowFormCellClose(Appendable writer, Map<String, Object> context, ModelForm modelForm) {
        this.output.popScreen("ItemRowFormCellClose");
    }

    public void renderFormatSingleWrapperOpen(Appendable writer, Map<String, Object> context, ModelForm modelForm) {
        String style = FlexibleStringExpander.expandString(modelForm.getDefaultTableStyle(), context);
        Map<String, Object> cb = new HashMap<>();
        cb.put("formName", modelForm.getName());
        cb.put("style", style);
        this.output.pushScreen("SingleWrapperOpen", cb, "NEW_RECORD", context);
    }

    public void renderFormatSingleWrapperClose(Appendable writer, Map<String, Object> context, ModelForm modelForm) {
        this.output.popScreen("SingleWrapperClose", "STORE_RECORD");
    }

    public void renderFormatFieldRowOpen(Appendable writer, Map<String, Object> context, ModelForm modelForm) {
        this.output.pushScreen("FieldRowOpen", new HashMap<String, Object>());
    }

    public void renderFormatFieldRowClose(Appendable writer, Map<String, Object> context, ModelForm modelForm) {
        this.output.popScreen("FieldRowClose");
    }

    public void renderFormatFieldRowTitleCellOpen(Appendable writer, Map<String, Object> context, ModelFormField modelFormField) {
        String style = modelFormField.getTitleAreaStyle();
        Map<String, Object> cb = new HashMap<>();
        if (!style.isEmpty()) cb.put("style", style);
        //this.output.pushScreen("FieldRowTitleCellOpen", cb);
    }

    public void renderFormatFieldRowTitleCellClose(Appendable writer, Map<String, Object> context, ModelFormField modelFormField) {
        //this.output.popScreen("FieldRowTitleCellClose");
    }

    public void renderFormatFieldRowSpacerCell(Appendable writer, Map<String, Object> context, ModelFormField modelFormField) {
    }

    public void renderFormatFieldRowWidgetCellOpen(Appendable writer, Map<String, Object> context, ModelFormField modelFormField, int positions, int positionSpan, Integer nextPositionInRow) {
        String areaStyle = modelFormField.getWidgetAreaStyle();
        Map<String, Object> cb = new HashMap<>();
        cb.put("positionSpan", positionSpan);
        cb.put("style", areaStyle);
        //this.output.pushScreen("FieldRowWidgetCellOpen", cb);
    }

    public void renderFormatFieldRowWidgetCellClose(Appendable writer, Map<String, Object> context, ModelFormField modelFormField, int positions, int positionSpan, Integer nextPositionInRow) {
        //this.output.popScreen("FieldRowWidgetCellClose");
    }

    public void renderFormatEmptySpace(Appendable writer, Map<String, Object> context, ModelForm modelForm) {
        this.output.putScreen("FormatEmptySpace", new HashMap<>());
    }

    public void renderTextFindField(Appendable writer, Map<String, Object> context, TextFindField textFindField) {
        String fieldName = null;
        String fieldValue = null;
        ModelFormField modelFormField = textFindField.getModelFormField();
        String defaultOption = textFindField.getDefaultOption(context);
        String conditionGroup = modelFormField.getConditionGroup();
        String className = "";
        String alert = "false";
        String opEquals = "";
        String opBeginsWith = "";
        String opContains = "";
        String opIsEmpty = "";
        String opNotEqual = "";
        String name = modelFormField.getName();
        Integer size = textFindField.getSize();
        String maxlength = "";
        String autocomplete = "";
        if (UtilValidate.isNotEmpty(modelFormField.getWidgetStyle())) {
            className = modelFormField.getWidgetStyle();
            if (modelFormField.shouldBeRed(context)) {
                alert = "true";
            }
        }
        Locale locale = (Locale) context.get("locale");
        if (!textFindField.getHideOptions()) {
            opEquals = UtilProperties.getMessage("conditionalUiLabels", "equals", locale);
            opBeginsWith = UtilProperties.getMessage("conditionalUiLabels", "begins_with", locale);
            opContains = UtilProperties.getMessage("conditionalUiLabels", "contains", locale);
            opIsEmpty = UtilProperties.getMessage("conditionalUiLabels", "is_empty", locale);
            opNotEqual = UtilProperties.getMessage("conditionalUiLabels", "not_equal", locale);
        }
        String value = modelFormField.getEntry(context, textFindField.getDefaultValue(context));
        if (value == null) {
            value = "";
        }
        if (textFindField.getMaxlength() != null) {
            maxlength = textFindField.getMaxlength().toString();
        }
        if (!textFindField.getClientAutocompleteField()) {
            autocomplete = "off";
        }
        String titleStyle = "";
        if (UtilValidate.isNotEmpty(modelFormField.getTitleStyle())) {
            titleStyle = modelFormField.getTitleStyle();
        }
        String ignoreCase = UtilProperties.getMessage("conditionalUiLabels", "ignore_case", locale);
        boolean ignCase = textFindField.getIgnoreCase(context);
        boolean hideIgnoreCase = textFindField.getHideIgnoreCase();
        String tabindex = modelFormField.getTabindex();
        String formName = modelFormField.getModelForm().getName();
        Map<String, Object> cb = new HashMap<>();
        this.addTitle(cb, modelFormField, context);
        cb.put("name", name);
        cb.put("value", value);
        cb.put("defaultOption", defaultOption);
        cb.put("opEquals", opEquals);
        cb.put("opBeginsWith", opBeginsWith);
        cb.put("opContains", opContains);
        cb.put("opIsEmpty", opIsEmpty);
        cb.put("opNotEqual", opNotEqual);
        cb.put("className", className);
        cb.put("alert", alert);
        cb.put("size", size);
        cb.put("maxlength", maxlength);
        cb.put("autocomplete", autocomplete);
        cb.put("titleStyle", titleStyle);
        cb.put("hideIgnoreCase", hideIgnoreCase);
        cb.put("ignCase", ignCase);
        cb.put("ignoreCase", ignoreCase);
        cb.put("tabindex", tabindex);
        cb.put("conditionGroup", conditionGroup);
        cb.put("formName", formName);
        Map<String, Object> data = new HashMap<>();
        fieldName = name;
        fieldValue = value;
        // TODO check if recordPointer is used, should read VueTextFindField.vue to check.
        Map<String, Object> pointer = output.getRecordPointer(context);
        if (pointer != null) {
            pointer.put("field", name);
            data.put("recordPointer", pointer);
        }
        cb.put("data", data);
        this.appendTooltip(cb, context, modelFormField);
        this.output.putScreen("TextFindField", cb, fieldName, fieldValue);
    }

    public void renderRangeFindField(Appendable writer, Map<String, Object> context, RangeFindField rangeFindField) {
        ModelFormField modelFormField = rangeFindField.getModelFormField();
        Locale locale = (Locale) context.get("locale");
        String opEquals = UtilProperties.getMessage("conditionalUiLabels", "equals", locale);
        String opGreaterThan = UtilProperties.getMessage("conditionalUiLabels", "greater_than", locale);
        String opGreaterThanEquals = UtilProperties.getMessage("conditionalUiLabels", "greater_than_equals", locale);
        String opLessThan = UtilProperties.getMessage("conditionalUiLabels", "less_than", locale);
        String opLessThanEquals = UtilProperties.getMessage("conditionalUiLabels", "less_than_equals", locale);
        String conditionGroup = modelFormField.getConditionGroup();
        String className = "";
        String alert = "false";
        if (UtilValidate.isNotEmpty(modelFormField.getWidgetStyle())) {
            className = modelFormField.getWidgetStyle();
            if (modelFormField.shouldBeRed(context)) {
                alert = "true";
            }
        }
        String name = modelFormField.getParameterName(context);
        Integer size = rangeFindField.getSize();
        String value = modelFormField.getEntry(context, rangeFindField.getDefaultValue(context));
        if (value == null) {
            value = "";
        }
        Integer maxlength = rangeFindField.getMaxlength();
        String autocomplete = "";

        if (!rangeFindField.getClientAutocompleteField()) {
            autocomplete = "off";
        }
        String titleStyle = modelFormField.getTitleStyle();

        if (titleStyle == null) {
            titleStyle = "";
        }
        String defaultOptionFrom = rangeFindField.getDefaultOptionFrom();
        String value2 = modelFormField.getEntry(context);
        if (value2 == null) {
            value2 = "";
        }
        String defaultOptionThru = rangeFindField.getDefaultOptionThru();
        String tabindex = modelFormField.getTabindex();
        Map<String, Object> cb = new HashMap<>();
        cb.put("className", className);
        cb.put("alert", alert);
        cb.put("name", name);
        cb.put("value", value);
        cb.put("size", size);
        if (maxlength != null) {
            cb.put("maxlength", maxlength);
        }
        cb.put("autocomplete", autocomplete);
        cb.put("titleStyle", titleStyle);
        cb.put("defaultOptionFrom", defaultOptionFrom);
        cb.put("opEquals", opEquals);
        cb.put("opGreaterThan", opGreaterThan);
        cb.put("opGreaterThanEquals", opGreaterThanEquals);
        cb.put("opLessThan", opLessThan);
        cb.put("opLessThanEquals", opLessThanEquals);
        cb.put("value2", value2);
        cb.put("defaultOptionThru", defaultOptionThru);
        cb.put("conditionGroup", conditionGroup);
        cb.put("tabindex", tabindex);
        this.appendTooltip(cb, context, modelFormField);
        this.output.putScreen("RangeFindField", cb);
    }

    public void renderDateFindField(Appendable writer, Map<String, Object> context, DateFindField dateFindField) {
        ModelFormField modelFormField = dateFindField.getModelFormField();
        Locale locale = (Locale) context.get("locale");
        String opEquals = UtilProperties.getMessage("conditionalUiLabels", "equals", locale);
        String opGreaterThan = UtilProperties.getMessage("conditionalUiLabels", "greater_than", locale);
        String opSameDay = UtilProperties.getMessage("conditionalUiLabels", "same_day", locale);
        String opGreaterThanFromDayStart = UtilProperties.getMessage("conditionalUiLabels", "greater_than_from_day_start", locale);
        String opLessThan = UtilProperties.getMessage("conditionalUiLabels", "less_than", locale);
        String opUpToDay = UtilProperties.getMessage("conditionalUiLabels", "up_to_day", locale);
        String opUpThruDay = UtilProperties.getMessage("conditionalUiLabels", "up_thru_day", locale);
        String opIsEmpty = UtilProperties.getMessage("conditionalUiLabels", "is_empty", locale);
        String conditionGroup = modelFormField.getConditionGroup();
        Map<String, String> uiLabelMap = UtilGenerics.cast(context.get("uiLabelMap"));
        if (uiLabelMap == null) {
            Debug.logWarning("Could not find uiLabelMap in context", MODULE);
        }
        String localizedInputTitle = "", localizedIconTitle = "";
        String className = "";
        String alert = "false";
        if (UtilValidate.isNotEmpty(modelFormField.getWidgetStyle())) {
            className = modelFormField.getWidgetStyle();
            if (modelFormField.shouldBeRed(context)) {
                alert = "true";
            }
        }
        String name = modelFormField.getParameterName(context);
        // the default values for a timestamp
        int size = 25;
        int maxlength = 30;
        String dateType = dateFindField.getType();
        if ("date".equals(dateType)) {
            size = maxlength = 10;
            if (uiLabelMap != null) {
                localizedInputTitle = uiLabelMap.get("CommonFormatDate");
            }
        } else if ("time".equals(dateFindField.getType())) {
            size = maxlength = 8;
            if (uiLabelMap != null) {
                localizedInputTitle = uiLabelMap.get("CommonFormatTime");
            }
        } else {
            if (uiLabelMap != null) {
                localizedInputTitle = uiLabelMap.get("CommonFormatDateTime");
            }
        }
        String value = modelFormField.getEntry(context, dateFindField.getDefaultValue(context));
        if (value == null) {
            value = "";
        }
        // search for a localized label for the icon
        if (uiLabelMap != null) {
            localizedIconTitle = uiLabelMap.get("CommonViewCalendar");
        }
        String formName = "";
        String defaultDateTimeString = "";
        String imgSrc = "";
        // add calendar pop-up button and seed data IF this is not a "time" type date-find
        if (!"time".equals(dateFindField.getType())) {
            ModelForm modelForm = modelFormField.getModelForm();
            formName = FormRenderer.getCurrentFormName(modelForm, context);
            defaultDateTimeString = UtilHttp.encodeBlanks(modelFormField.getEntry(context, dateFindField.getDefaultDateTimeString(context)));
            imgSrc = this.appendContentUrl("/images/cal.gif");
        }
        String defaultOptionFrom = dateFindField.getDefaultOptionFrom(context);
        String defaultOptionThru = dateFindField.getDefaultOptionThru(context);
        String value2 = modelFormField.getEntry(context);
        if (value2 == null) {
            value2 = "";
        }
        if (context.containsKey("parameters")) {
            Map<String, Object> parameters = UtilGenerics.cast(context.get("parameters"));
            if (parameters.containsKey(name + "_fld0_value")) {
                value = (String) parameters.get(name + "_fld0_value");
            }
            if (parameters.containsKey(name + "_fld1_value")) {
                value2 = (String) parameters.get(name + "_fld1_value");
            }
        }

        String titleStyle = "";
        if (UtilValidate.isNotEmpty(modelFormField.getTitleStyle())) {
            titleStyle = modelFormField.getTitleStyle();
        }
        String tabindex = modelFormField.getTabindex();
        Map<String, Object> cb = new HashMap<>();
        cb.put("className", className);
        cb.put("alert", alert);
        cb.put("name", name);
        cb.put("localizedInputTitle", localizedInputTitle);
        cb.put("value", value);
        cb.put("value2", value2);
        cb.put("size", size);
        cb.put("maxlength", maxlength);
        cb.put("dateType", dateType);
        cb.put("formName", formName);
        cb.put("defaultDateTimeString", defaultDateTimeString);
        cb.put("imgSrc", imgSrc);
        cb.put("conditionGroup", conditionGroup);
        cb.put("localizedIconTitle", localizedIconTitle);
        cb.put("titleStyle", titleStyle);
        cb.put("defaultOptionFrom", defaultOptionFrom);
        cb.put("defaultOptionThru", defaultOptionThru);
        cb.put("opEquals", opEquals);
        cb.put("opSameDay", opSameDay);
        cb.put("opGreaterThanFromDayStart", opGreaterThanFromDayStart);
        cb.put("opGreaterThan", opGreaterThan);
        cb.put("opGreaterThan", opGreaterThan);
        cb.put("opLessThan", opLessThan);
        cb.put("opUpToDay", opUpToDay);
        cb.put("opUpThruDay", opUpThruDay);
        cb.put("opIsEmpty", opIsEmpty);
        cb.put("tabindex", tabindex);
        this.appendTooltip(cb, context, modelFormField);
        this.output.putScreen("DateFindField", cb);
    }

    public void renderLookupField(Appendable writer, Map<String, Object> context, LookupField lookupField) {
        ModelFormField modelFormField = lookupField.getModelFormField();
        String lookupFieldFormName = lookupField.getFormName(context);
        String conditionGroup = modelFormField.getConditionGroup();
        String className = "";
        String alert = "false";
        if (UtilValidate.isNotEmpty(modelFormField.getWidgetStyle())) {
            className = modelFormField.getWidgetStyle();
            if (modelFormField.shouldBeRed(context)) {
                alert = "true";
            }
        }
        //check for required field style on single forms
        if ("single".equals(modelFormField.getModelForm().getType()) && modelFormField.getRequiredField()) {
            String requiredStyle = modelFormField.getRequiredFieldStyle();
            if (UtilValidate.isEmpty(requiredStyle)) {
                requiredStyle = "required";
            }
            if (UtilValidate.isEmpty(className)) {
                className = requiredStyle;
            } else {
                className = requiredStyle + " " + className;
            }
        }
        String name = modelFormField.getParameterName(context);
        String value = modelFormField.getEntry(context, lookupField.getDefaultValue(context));
        if (value == null) {
            value = "";
        }
        Integer size = lookupField.getSize();
        Integer maxlength = lookupField.getMaxlength();
        String id = modelFormField.getCurrentContainerId(context);
        List<UpdateArea> updateAreas = modelFormField.getOnChangeUpdateAreas();
        //add default ajax auto completer to all lookup fields
        if (UtilValidate.isEmpty(updateAreas) && UtilValidate.isNotEmpty(lookupFieldFormName)) {
            String autoCompleterTarget = null;
            if (lookupFieldFormName.indexOf('?') == -1) {
                autoCompleterTarget = lookupFieldFormName + "?";
            } else {
                autoCompleterTarget = lookupFieldFormName + "&amp;amp;";
            }
            autoCompleterTarget = autoCompleterTarget + "ajaxLookup=Y";
            updateAreas = new LinkedList<>();
            updateAreas.add(new UpdateArea("change", id, autoCompleterTarget));
        }
        boolean ajaxEnabled = UtilValidate.isNotEmpty(updateAreas) && this.javaScriptEnabled;
        String autocomplete = "";
        if (!lookupField.getClientAutocompleteField() || !ajaxEnabled) {
            autocomplete = "off";
        }
        String event = modelFormField.getEvent();
        String action = modelFormField.getAction(context);
        boolean readonly = lookupField.getReadonly();
        // add lookup pop-up button
        String descriptionFieldName = lookupField.getDescriptionFieldName();
        ModelForm modelForm = modelFormField.getModelForm();
        String formName = modelFormField.getParentFormName();
        if (UtilValidate.isEmpty(formName)) {
            formName = FormRenderer.getCurrentFormName(modelForm, context);
        }
        String imgSrc = "";
        List<String> targetParameterList = lookupField.getTargetParameterList(context);
        /* OH 13.12.2019 simplification for targetParameter
        // FIXME: refactor using the StringUtils methods
        StringBuilder targetParameterIter = new StringBuilder();
        targetParameterIter.append("[");
        for (String targetParameter : targetParameterList) {
            if (targetParameterIter.length() > 1) {
                targetParameterIter.append(",");
            }
            targetParameterIter.append("'");
            targetParameterIter.append(targetParameter);
            targetParameterIter.append("'");
        }
        targetParameterIter.append("]");
        */
        imgSrc = this.appendContentUrl("/images/fieldlookup.gif");
        String ajaxUrl = "";
        if (ajaxEnabled) {
            ajaxUrl = createAjaxParamsFromUpdateAreas(updateAreas, "", context);
        }
        String lookupPresentation = lookupField.getLookupPresentation();
        if (UtilValidate.isEmpty(lookupPresentation)) {
            lookupPresentation = "";
        }
        String lookupHeight = lookupField.getLookupHeight();
        String lookupWidth = lookupField.getLookupWidth();
        String lookupPosition = lookupField.getLookupPosition();
        String fadeBackground = lookupField.getFadeBackground();
        if (UtilValidate.isEmpty(fadeBackground)) {
            fadeBackground = "false";
        }
        Boolean isInitiallyCollapsed = lookupField.getInitiallyCollapsed();
        String clearText = "";
        Map<String, Object> uiLabelMap = UtilGenerics.cast(context.get("uiLabelMap"));
        if (uiLabelMap != null) {
            clearText = (String) uiLabelMap.get("CommonClear");
        } else {
            Debug.logWarning("Could not find uiLabelMap in context", MODULE);
        }
        Boolean showDescription = lookupField.getShowDescription();
        if (showDescription == null) {
            showDescription = "Y".equals(visualTheme.getModelTheme().getLookupShowDescription());
        }
        // lastViewName, used by lookup to remember the real last view name
        String lastViewName = request.getParameter("_LAST_VIEW_NAME_"); // Try to get it from parameters firstly
        if (UtilValidate.isEmpty(lastViewName)) { // get from session
            lastViewName = (String) request.getSession().getAttribute("_LAST_VIEW_NAME_");
        }
        if (UtilValidate.isEmpty(lastViewName)) {
            lastViewName = "";
        }
        String tabindex = modelFormField.getTabindex();
        Map<String, Object> cb = new HashMap<>();
        if ("single".equals(modelFormField.getModelForm().getType())) this.addTitle(cb, modelFormField, context);
        cb.put("className", className);
        cb.put("alert", alert);
        cb.put("name", name);
        cb.put("value", value);
        cb.put("size", size);
        cb.put("maxlength", maxlength != null ? maxlength : "");
        cb.put("id", id);
        if (event != null) {
            cb.put("event", event);
        }
        if (action != null) {
            cb.put("action", action);
        }
        cb.put("readonly", readonly);
        cb.put("autocomplete", autocomplete);
        cb.put("descriptionFieldName", descriptionFieldName);
        cb.put("formName", formName);
        cb.put("fieldFormName", lookupFieldFormName);
        cb.put("targetParameters", targetParameterList);
        cb.put("imgSrc", imgSrc);
        cb.put("ajaxUrl", ajaxUrl);
        cb.put("ajaxEnabled", ajaxEnabled);
        cb.put("presentation", lookupPresentation);
        if (UtilValidate.isNotEmpty(lookupHeight)) {
            cb.put("height", lookupHeight);
        }
        if (UtilValidate.isNotEmpty(lookupWidth)) {
            cb.put("width", lookupWidth);
        }
        if (UtilValidate.isNotEmpty(lookupPosition)) {
            cb.put("position", lookupPosition);
        }
        cb.put("fadeBackground", fadeBackground);
        cb.put("clearText", clearText);
        cb.put("showDescription", showDescription);
        cb.put("initiallyCollapsed", isInitiallyCollapsed);
        cb.put("lastViewName", lastViewName);
        cb.put("conditionGroup", conditionGroup);
        cb.put("tabindex", tabindex);
        cb.put("delegatorName", ((HttpSession)context.get("session")).getAttribute("delegatorName").toString());
        this.addAsterisks(cb, context, modelFormField);
        this.appendTooltip(cb, context, modelFormField);
        this.makeHyperlinkString(cb, lookupField.getSubHyperlink(), context);

        this.output.putScreen("LookupField", cb, name, value);

//        // OH Temporary code, send only attributes which are understand and manage in vueJs
//        Map<String, Object> attributes = new HashMap<>();
//        attributes.put("formName", formName);
//        attributes.put("name", name);
//        attributes.put("value", value);
//        attributes.put("id", modelFormField.getCurrentContainerId(context));
//        attributes.put("fieldFormName", lookupFieldFormName);
//        attributes.put("size", lookupField.getSize());
//        this.output.putScreen("LookupField", attributes, name, value);
    }

    public void renderNextPrev(Appendable writer, Map<String, Object> context, ModelForm modelForm) {
        boolean ajaxEnabled = false;
        List<UpdateArea> updateAreas = modelForm.getOnPaginateUpdateAreas();
        String targetService = modelForm.getPaginateTarget(context);
        if (this.javaScriptEnabled) {
            if (UtilValidate.isNotEmpty(updateAreas)) {
                ajaxEnabled = true;
            }
        }
        if (targetService == null) {
            targetService = "${targetService}";
        }
        if (UtilValidate.isEmpty(targetService) && updateAreas == null) {
            Debug.logWarning("Cannot paginate because TargetService is empty for the form: " + modelForm.getName(), MODULE);
            return;
        }
        // get the parameterized pagination index and size fields
        int paginatorNumber = WidgetWorker.getPaginatorNumber(context);
        String viewIndexParam = modelForm.getMultiPaginateIndexField(context);
        String viewSizeParam = modelForm.getMultiPaginateSizeField(context);
        int viewIndex = Paginator.getViewIndex(modelForm, context);
        int viewSize = Paginator.getViewSize(modelForm, context);
        int listSize = Paginator.getListSize(context);
        int lowIndex = Paginator.getLowIndex(context);
        int highIndex = Paginator.getHighIndex(context);
        int actualPageSize = Paginator.getActualPageSize(context);
        // needed for the "Page" and "rows" labels
        Map<String, String> uiLabelMap = UtilGenerics.cast(context.get("uiLabelMap"));
        String pageLabel = "";
        String commonDisplaying = "";
        if (uiLabelMap == null) {
            Debug.logWarning("Could not find uiLabelMap in context", MODULE);
        } else {
            pageLabel = uiLabelMap.get("CommonPage");
            Map<String, Integer> messageMap = UtilMisc.toMap("lowCount", lowIndex + 1, "highCount", lowIndex + actualPageSize, "total", Integer.valueOf(listSize));
            commonDisplaying = UtilProperties.getMessage("CommonUiLabels", "CommonDisplaying", messageMap, (Locale) context.get("locale"));
        }
        // for legacy support, the viewSizeParam is VIEW_SIZE and viewIndexParam is VIEW_INDEX when the fields are "viewSize" and "viewIndex"
        if (("viewIndex" + "_" + paginatorNumber).equals(viewIndexParam)) {
            viewIndexParam = "VIEW_INDEX" + "_" + paginatorNumber;
        }
        if (("viewSize" + "_" + paginatorNumber).equals(viewSizeParam)) {
            viewSizeParam = "VIEW_SIZE" + "_" + paginatorNumber;
        }
        String str = (String) context.get("_QBESTRING_");
        // strip legacy viewIndex/viewSize params from the query string
        String queryString = UtilHttp.stripViewParamsFromQueryString(str, "" + paginatorNumber);
        // strip parameterized index/size params from the query string
        Set<String> paramNames = new HashSet<>();
        paramNames.add(viewIndexParam);
        paramNames.add(viewSizeParam);
        queryString = UtilHttp.stripNamedParamsFromQueryString(queryString, paramNames);
        String anchor = "";
        String paginateAnchor = modelForm.getPaginateTargetAnchor();
        if (UtilValidate.isNotEmpty(paginateAnchor)) {
            anchor = "#" + paginateAnchor;
        }
        // Create separate url path String and request parameters String,
        // add viewIndex/viewSize parameters to request parameter String
        String urlPath = UtilHttp.removeQueryStringFromTarget(targetService);
        String prepLinkText = UtilHttp.getQueryStringFromTarget(targetService);
        String prepLinkSizeText;
        if (UtilValidate.isNotEmpty(queryString)) {
            queryString = UtilHttp.encodeAmpersands(queryString);
        }
        if (prepLinkText.indexOf('?') < 0) {
            prepLinkText += "?";
        } else if (!prepLinkText.endsWith("?")) {
            prepLinkText += "&amp;";
        }
        if (UtilValidate.isNotEmpty(queryString) && !"null".equals(queryString)) {
            prepLinkText += queryString + "&amp;";
        }
        prepLinkSizeText = prepLinkText + viewSizeParam + "='+this.value+'" + "&amp;" + viewIndexParam + "=0";
        prepLinkText += viewSizeParam + "=" + viewSize + "&amp;" + viewIndexParam + "=";
        if (ajaxEnabled) {
            // Prepare params for prototype.js
            prepLinkText = prepLinkText.replace("?", "");
            prepLinkText = prepLinkText.replace("&amp;", "&");
        }
        String linkText;
        String paginateStyle = modelForm.getPaginateStyle();
        String paginateFirstStyle = modelForm.getPaginateFirstStyle();
        String paginateFirstLabel = modelForm.getPaginateFirstLabel(context);
        String firstUrl = "";
        String ajaxFirstUrl = "";
        String paginatePreviousStyle = modelForm.getPaginatePreviousStyle();
        String paginatePreviousLabel = modelForm.getPaginatePreviousLabel(context);
        String previousUrl = "";
        String ajaxPreviousUrl = "";
        String selectUrl = "";
        String ajaxSelectUrl = "";
        String paginateViewSizeLabel = modelForm.getPaginateViewSizeLabel(context);
        String selectSizeUrl = "";
        String ajaxSelectSizeUrl = "";
        String paginateNextStyle = modelForm.getPaginateNextStyle();
        String paginateNextLabel = modelForm.getPaginateNextLabel(context);
        String nextUrl = "";
        String ajaxNextUrl = "";
        String paginateLastStyle = modelForm.getPaginateLastStyle();
        String paginateLastLabel = modelForm.getPaginateLastLabel(context);
        String lastUrl = "";
        String ajaxLastUrl = "";
        if (viewIndex > 0) {
            if (ajaxEnabled) {
                ajaxFirstUrl = createAjaxParamsFromUpdateAreas(updateAreas, prepLinkText + 0 + anchor, context);
            } else {
                linkText = prepLinkText + 0 + anchor;
                firstUrl = rh.makeLink(this.request, this.response, urlPath + linkText);
            }
        }
        if (viewIndex > 0) {
            if (ajaxEnabled) {
                ajaxPreviousUrl = createAjaxParamsFromUpdateAreas(updateAreas, prepLinkText + (viewIndex - 1) + anchor, context);
            } else {
                linkText = prepLinkText + (viewIndex - 1) + anchor;
                previousUrl = rh.makeLink(this.request, this.response, urlPath + linkText);
            }
        }
        // Page select dropdown
        if (listSize > 0 && this.javaScriptEnabled) {
            if (ajaxEnabled) {
                ajaxSelectUrl = createAjaxParamsFromUpdateAreas(updateAreas, prepLinkText + "' + this.value + '", context);
            } else {
                linkText = prepLinkText;
                if (linkText.startsWith("/")) {
                    linkText = linkText.substring(1);
                }
                selectUrl = rh.makeLink(this.request, this.response, urlPath + linkText);
            }
        }
        // Next button
        if (highIndex < listSize) {
            if (ajaxEnabled) {
                ajaxNextUrl = createAjaxParamsFromUpdateAreas(updateAreas, prepLinkText + (viewIndex + 1) + anchor, context);
            } else {
                linkText = prepLinkText + (viewIndex + 1) + anchor;
                nextUrl = rh.makeLink(this.request, this.response, urlPath + linkText);
            }
        }
        // Last button
        if (highIndex < listSize) {
            int lastIndex = UtilMisc.getViewLastIndex(listSize, viewSize);
            if (ajaxEnabled) {
                ajaxLastUrl = createAjaxParamsFromUpdateAreas(updateAreas, prepLinkText + lastIndex + anchor, context);
            } else {
                linkText = prepLinkText + lastIndex + anchor;
                lastUrl = rh.makeLink(this.request, this.response, urlPath + linkText);
            }
        }
        // Page size select dropdown
        if (listSize > 0 && this.javaScriptEnabled) {
            if (ajaxEnabled) {
                ajaxSelectSizeUrl = createAjaxParamsFromUpdateAreas(updateAreas, prepLinkSizeText + anchor, context);
            } else {
                linkText = prepLinkSizeText;
                if (linkText.startsWith("/")) {
                    linkText = linkText.substring(1);
                }
                selectSizeUrl = rh.makeLink(this.request, this.response, urlPath + linkText);
            }
        }
        Map<String, Object> cb = new HashMap<>();
        cb.put("paginateStyle", paginateStyle);
        cb.put("paginateFirstStyle", paginateFirstStyle);
        cb.put("viewIndex", viewIndex);
        cb.put("highIndex", highIndex);
        cb.put("listSize", listSize);
        cb.put("viewSize", viewSize);
        cb.put("ajaxEnabled", ajaxEnabled);
        cb.put("javaScriptEnabled", javaScriptEnabled);
        cb.put("ajaxFirstUrl", ajaxFirstUrl);
        cb.put("firstUrl", firstUrl);
        cb.put("paginateFirstLabel", paginateFirstLabel);
        cb.put("paginatePreviousStyle", paginatePreviousStyle);
        cb.put("ajaxPreviousUrl", ajaxPreviousUrl);
        cb.put("previousUrl", previousUrl);
        cb.put("paginatePreviousLabel", paginatePreviousLabel);
        cb.put("pageLabel", pageLabel);
        cb.put("ajaxSelectUrl", ajaxSelectUrl);
        cb.put("selectUrl", selectUrl);
        cb.put("ajaxSelectSizeUrl", ajaxSelectSizeUrl);
        cb.put("selectSizeUrl", selectSizeUrl);
        cb.put("commonDisplaying", commonDisplaying);
        cb.put("paginateNextStyle", paginateNextStyle);
        cb.put("ajaxNextUrl", ajaxNextUrl);
        cb.put("nextUrl", nextUrl);
        cb.put("paginateNextLabel", paginateNextLabel);
        cb.put("paginateLastStyle", paginateLastStyle);
        cb.put("ajaxLastUrl", ajaxLastUrl);
        cb.put("lastUrl", lastUrl);
        cb.put("paginateLastLabel", paginateLastLabel);
        cb.put("paginateViewSizeLabel", paginateViewSizeLabel);
        cb.put("paginateTarget", modelForm.getPaginateTarget());
        this.output.putScreen("NextPrev", cb);
    }

    public void renderFileField(Appendable writer, Map<String, Object> context, FileField textField) {
        ModelFormField modelFormField = textField.getModelFormField();
        String className = "";
        String alert = "false";
        String name = modelFormField.getParameterName(context);
        String value = modelFormField.getEntry(context, textField.getDefaultValue(context));
        Integer size = textField.getSize();
        String maxlength = "";
        String autocomplete = "";
        String formName = textField.getModelFormField().getModelForm().getName();
        if (UtilValidate.isNotEmpty(modelFormField.getWidgetStyle())) {
            className = modelFormField.getWidgetStyle();
            if (modelFormField.shouldBeRed(context)) {
                alert = "true";
            }
        }
        if (UtilValidate.isEmpty(value)) {
            value = "";
        }
        if (textField.getMaxlength() != null) {
            maxlength = textField.getMaxlength().toString();
        }
        if (!textField.getClientAutocompleteField()) {
            autocomplete = "off";
        }
        String tabindex = modelFormField.getTabindex();
        Map<String, Object> cb = new HashMap<>();
        cb.put("className", className);
        cb.put("alert", alert);
        cb.put("name", name);
        cb.put("value", value);
        cb.put("size", size);
        cb.put("maxlength", maxlength);
        cb.put("autocomplete", autocomplete);
        cb.put("tabindex", tabindex);
        cb.put("formName", formName);
        this.makeHyperlinkString(cb, textField.getSubHyperlink(), context);
        this.appendTooltip(cb, context, modelFormField);
        this.output.putScreen("FileField", cb);
    }

    public void renderPasswordField(Appendable writer, Map<String, Object> context, PasswordField passwordField) {
        ModelFormField modelFormField = passwordField.getModelFormField();
        String className = "";
        String alert = "false";
        String name = modelFormField.getParameterName(context);
        Integer size = passwordField.getSize();
        String maxlength = "";
        String id = modelFormField.getCurrentContainerId(context);
        String autocomplete = "";
        String formName = passwordField.getModelFormField().getModelForm().getName();
        if (UtilValidate.isNotEmpty(modelFormField.getWidgetStyle())) {
            className = modelFormField.getWidgetStyle();
            if (modelFormField.shouldBeRed(context)) {
                alert = "true";
            }
        }
        String value = modelFormField.getEntry(context, passwordField.getDefaultValue(context));
        if (value == null) {
            value = "";
        }
        if (passwordField.getMaxlength() != null) {
            maxlength = passwordField.getMaxlength().toString();
        }
        if (id == null) {
            id = "";
        }
        if (!passwordField.getClientAutocompleteField()) {
            autocomplete = "off";
        }

        //check for required field style on single forms
        if ("single".equals(modelFormField.getModelForm().getType()) && modelFormField.getRequiredField()) {
            String requiredStyle = modelFormField.getRequiredFieldStyle();
            if (UtilValidate.isEmpty(requiredStyle)) {
                requiredStyle = "required";
            }
            if (UtilValidate.isEmpty(className)) {
                className = requiredStyle;
            } else {
                className = requiredStyle + " " + className;
            }
        }

        String tabindex = modelFormField.getTabindex();
        Map<String, Object> cb = new HashMap<>();
        if ("single".equals(modelFormField.getModelForm().getType())) this.addTitle(cb, modelFormField, context);

        cb.put("className", className);
        cb.put("alert", alert);
        cb.put("name", name);
        cb.put("value", value);
        cb.put("size", size);
        cb.put("maxlength", maxlength);
        cb.put("id", id);
        cb.put("autocomplete", autocomplete);
        cb.put("tabindex", tabindex);
        cb.put("formName", formName);
        this.addAsterisks(cb, context, modelFormField);
        this.makeHyperlinkString(cb, passwordField.getSubHyperlink(), context);
        this.appendTooltip(cb, context, modelFormField);
        this.output.putScreen("PasswordField", cb);
    }

    public void renderImageField(Appendable writer, Map<String, Object> context, ImageField imageField) {
        ModelFormField modelFormField = imageField.getModelFormField();
        String value = modelFormField.getEntry(context, imageField.getValue(context));
        String description = imageField.getDescription(context);
        String alternate = imageField.getAlternate(context);
        String style = imageField.getStyle(context);
        if (UtilValidate.isEmpty(description)) {
            description = imageField.getModelFormField().getTitle(context);
        }
        if (UtilValidate.isEmpty(alternate)) {
            alternate = description;
        }
        if (UtilValidate.isNotEmpty(value)) {
            if (!value.startsWith("http")) {
                StringBuilder buffer = new StringBuilder();
                ContentUrlTag.appendContentPrefix(request, buffer);
                buffer.append(value);
                value = buffer.toString();
            }
        } else if (value == null) {
            value = "";
        }
        String event = modelFormField.getEvent();
        String action = modelFormField.getAction(context);
        Map<String, Object> cb = new HashMap<>();

        cb.put("value", value);
        cb.put("description", encode(description, modelFormField, context));
        cb.put("alternate", encode(alternate, modelFormField, context));
        cb.put("style", style);
        cb.put("event", event == null ? "" : event);
        cb.put("action", action == null ? "" : action);
        this.makeHyperlinkString(cb, imageField.getSubHyperlink(), context);
        this.appendTooltip(cb, context, modelFormField);
        this.output.putScreen("ImageField", cb);
    }

    public void renderFieldGroupOpen(Appendable writer, Map<String, Object> context, ModelForm.FieldGroup fieldGroup) {
        String style = fieldGroup.getStyle();
        String id = fieldGroup.getId();
        FlexibleStringExpander titleNotExpanded = FlexibleStringExpander.getInstance(fieldGroup.getTitle());
        String title = titleNotExpanded.expandString(context);
        Boolean collapsed = fieldGroup.initiallyCollapsed();
        String collapsibleAreaId = fieldGroup.getId() + "_body";
        Boolean collapsible = fieldGroup.collapsible();
        String expandToolTip = "";
        String collapseToolTip = "";
        if (UtilValidate.isNotEmpty(style) || UtilValidate.isNotEmpty(id) || UtilValidate.isNotEmpty(title)) {
            if (fieldGroup.collapsible()) {
                Map<String, Object> uiLabelMap = UtilGenerics.cast(context.get("uiLabelMap"));
                if (uiLabelMap != null) {
                    expandToolTip = (String) uiLabelMap.get("CommonExpand");
                    collapseToolTip = (String) uiLabelMap.get("CommonCollapse");
                }
            }
        }
        Map<String, Object> cb = new HashMap<>();

        if (style != null) {
            cb.put("style", style);
        }
        cb.put("id", id);
        cb.put("title", title);
        cb.put("collapsed", collapsed);
        cb.put("collapsibleAreaId", collapsibleAreaId);
        cb.put("collapsible", collapsible);
        cb.put("expandToolTip", expandToolTip);
        cb.put("collapseToolTip", collapseToolTip);
        this.output.pushScreen("FieldGroupOpen", cb);
    }

    public void renderFieldGroupClose(Appendable writer, Map<String, Object> context, ModelForm.FieldGroup fieldGroup) {
//        todo
//        String style = fieldGroup.getStyle();
//        String id = fieldGroup.getId();
//        FlexibleStringExpander titleNotExpanded = FlexibleStringExpander.getInstance(fieldGroup.getTitle());
//        String title = titleNotExpanded.expandString(context);
//        Map<String, Object> cb = new HashMap<>();
//
//        if (style != null) {
//            cb.put("style", style);
//        }
//        if (id != null) {
//            cb.put("id", id);
//        }
//        if (title != null) {
//            cb.put("title", title);
//        }
        this.output.popScreen("FieldGroupClose");
    }

    public void renderBanner(Appendable writer, Map<String, Object> context, ModelForm.Banner banner) {
        String style = banner.getStyle(context);
        String leftStyle = banner.getLeftTextStyle(context);
        if (UtilValidate.isEmpty(leftStyle)) {
            leftStyle = style;
        }
        String rightStyle = banner.getRightTextStyle(context);
        if (UtilValidate.isEmpty(rightStyle)) {
            rightStyle = style;
        }
        String leftText = banner.getLeftText(context);
        if (leftText == null) {
            leftText = "";
        }
        String text = banner.getText(context);
        if (text == null) {
            text = "";
        }
        String rightText = banner.getRightText(context);
        if (rightText == null) {
            rightText = "";
        }
        Map<String, Object> cb = new HashMap<>();

        cb.put("style", style);
        cb.put("leftStyle", leftStyle);
        cb.put("rightStyle", rightStyle);
        cb.put("leftText", leftText);
        cb.put("text", text);
        cb.put("rightText", rightText);
        this.output.putScreen("Banner", cb);
    }


    private void renderSortField(Appendable writer, Map<String, Object> context, ModelFormField modelFormField, String titleText) {
        boolean ajaxEnabled = false;
        ModelForm modelForm = modelFormField.getModelForm();
        List<UpdateArea> updateAreas = modelForm.getOnSortColumnUpdateAreas();
        if (updateAreas == null) {
            // For backward compatibility.
            updateAreas = modelForm.getOnPaginateUpdateAreas();
        }
        if (this.javaScriptEnabled) {
            if (UtilValidate.isNotEmpty(updateAreas)) {
                ajaxEnabled = true;
            }
        }
        String paginateTarget = modelForm.getPaginateTarget(context);
        if (paginateTarget.isEmpty() && updateAreas == null) {
            Debug.logWarning("Cannot sort because the paginate target URL is empty for the form: " + modelForm.getName(), MODULE);
            return;
        }
        String oldSortField = modelForm.getSortField(context);
        String sortFieldStyle = modelFormField.getSortFieldStyle();
        // if the entry-name is defined use this instead of field name
        String columnField = modelFormField.getEntryName();
        if (UtilValidate.isEmpty(columnField)) {
            columnField = modelFormField.getFieldName();
        }
        // switch between asc/desc order
        String newSortField = columnField;
        if (UtilValidate.isNotEmpty(oldSortField)) {
            if (oldSortField.equals(columnField)) {
                newSortField = "-" + columnField;
                sortFieldStyle = modelFormField.getSortFieldStyleDesc();
            } else if (("-" + columnField).equals(oldSortField)) {
                newSortField = columnField;
                sortFieldStyle = modelFormField.getSortFieldStyleAsc();
            }
        }
        String queryString = UtilHttp.getQueryStringFromTarget(paginateTarget).replace("?", "");
        Map<String, Object> paramMap = UtilHttp.getQueryStringOnlyParameterMap(queryString);
        String qbeString = (String) context.get("_QBESTRING_");
        if (qbeString != null) {
            qbeString = qbeString.replaceAll("&amp;", "&");
            paramMap.putAll(UtilHttp.getQueryStringOnlyParameterMap(qbeString));
        }
        paramMap.put(modelForm.getSortFieldParameterName(), newSortField);
        UtilHttp.canonicalizeParameterMap(paramMap);
        String linkUrl;
        if (ajaxEnabled) {
            linkUrl = createAjaxParamsFromUpdateAreas(updateAreas, paramMap, null, context);
        } else {
            StringBuilder sb = new StringBuilder("?");
            Iterator<Map.Entry<String, Object>> iter = paramMap.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<String, Object> entry = iter.next();
                sb.append(entry.getKey()).append("=").append(entry.getValue());
                if (iter.hasNext()) {
                    sb.append("&amp;");
                }
            }
            String newQueryString = sb.toString();
            String urlPath = UtilHttp.removeQueryStringFromTarget(paginateTarget);
            linkUrl = rh.makeLink(this.request, this.response, urlPath.concat(newQueryString));
        }
        Map<String, Object> cb = new HashMap<>();

        cb.put("style", sortFieldStyle);
        cb.put("title", titleText);
        cb.put("linkUrl", linkUrl);
        cb.put("ajaxEnabled", ajaxEnabled);
        String tooltip = modelFormField.getSortFieldHelpText(context);
        if (!tooltip.isEmpty()) {
            cb.put(" tooltip", tooltip);
        }
//        String target = modelForm.getPaginateTarget();
//        cb.put("paginateTarget", target);
        List<UpdateArea> onPaginateUpdateAreas = modelForm.getOnPaginateUpdateAreas();
        if (!onPaginateUpdateAreas.isEmpty()) {
            List<Map<String, Object>> onPaginateUpdateAreasJson = new ArrayList<>();
            for (UpdateArea updateArea : onPaginateUpdateAreas) {
                HashMap<String, Object> updateAreaJson = new HashMap<>();
                updateAreaJson.put("areaId", updateArea.getAreaId());
                updateAreaJson.put("areaTarget", updateArea.getAreaTarget());
                updateAreaJson.put("parameters", updateArea.getParameterMap(context));
                onPaginateUpdateAreasJson.add(updateAreaJson);
            }
            cb.put("onPaginateUpdateAreas", onPaginateUpdateAreasJson);
        } else {
            cb.put("onPaginateUpdateAreas", new ArrayList<>());
        }
        cb.put("paginateTarget", modelForm.getPaginateTarget());
        String entityField = modelFormField.getParameterName(context);
        cb.put("entityField", entityField);
        this.output.putScreen("SortField", cb);
    }

    /** Create an ajaxXxxx JavaScript CSV string from a list of UpdateArea objects. See
     * <code>OfbizUtil.js</code>.
     * @param updateAreas
     * @param extraParams Renderer-supplied additional target parameters
     * @param context
     * @return Parameter string or empty string if no UpdateArea objects were found
     */
    private String createAjaxParamsFromUpdateAreas(List<UpdateArea> updateAreas, Map<String, Object> extraParams, String anchor, Map<String, ? extends Object> context) {
        StringBuilder sb = new StringBuilder();
        Iterator<UpdateArea> updateAreaIter = updateAreas.iterator();
        while (updateAreaIter.hasNext()) {
            UpdateArea updateArea = updateAreaIter.next();
            sb.append(updateArea.getAreaId()).append(",");
            String ajaxTarget = updateArea.getAreaTarget(context);
            String urlPath = UtilHttp.removeQueryStringFromTarget(ajaxTarget);
            sb.append(this.rh.makeLink(this.request, this.response,urlPath)).append(",");
            String queryString = UtilHttp.getQueryStringFromTarget(ajaxTarget).replace("?", "");
            Map<String, Object> parameters = UtilHttp.getQueryStringOnlyParameterMap(queryString);
            Map<String, Object> ctx = UtilGenerics.cast(context);
            Map<String, Object> updateParams = UtilGenerics.cast(updateArea.getParameterMap(ctx));
            parameters.putAll(updateParams);
            UtilHttp.canonicalizeParameterMap(parameters);
            parameters.putAll(extraParams);
            Iterator<Map.Entry<String, Object>> paramIter = parameters.entrySet().iterator();
            while (paramIter.hasNext()) {
                Map.Entry<String, Object> entry = paramIter.next();
                sb.append(entry.getKey()).append("=").append(entry.getValue());
                if (paramIter.hasNext()) {
                    sb.append("&");
                }
            }
            if (anchor != null) {
                sb.append("#").append(anchor);
            }
            if (updateAreaIter.hasNext()) {
                sb.append(",");
            }
        }
        Locale locale = UtilMisc.ensureLocale(context.get("locale"));
        return FlexibleStringExpander.expandString(sb.toString(), context, locale);
    }

    public String createAjaxParamsFromUpdateAreas(List<UpdateArea> updateAreas, String extraParams, Map<String, ? extends Object> context) {

        if (updateAreas == null) {
            return "";
        }
        String ajaxUrl = "";
        boolean firstLoop = true;
        for (UpdateArea updateArea : updateAreas) {
            if (firstLoop) {
                firstLoop = false;
            } else {
                ajaxUrl += ",";
            }
            Map<String, Object> ctx = UtilGenerics.cast(context);
            Map<String, String> parameters = updateArea.getParameterMap(ctx);
            String targetUrl = updateArea.getAreaTarget(context);
            String ajaxParams;
            StringBuffer ajaxParamsBuffer = new StringBuffer();
            ajaxParamsBuffer.append(getAjaxParamsFromTarget(targetUrl));
            //add first parameters from updateArea parameters
            if (UtilValidate.isNotEmpty(parameters)) {
                for (Map.Entry<String, String> entry : parameters.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    //test if ajax parameters are not already into extraParams, if so do not add it
                    if (UtilValidate.isNotEmpty(extraParams) && extraParams.contains(value)) {
                        continue;
                    }
                    if (ajaxParamsBuffer.length() > 0 && ajaxParamsBuffer.indexOf(key) < 0) {
                        ajaxParamsBuffer.append("&");
                    }
                    if (ajaxParamsBuffer.indexOf(key) < 0) {
                        ajaxParamsBuffer.append(key).append("=").append(value);
                    }
                }
            }
            //then add parameters from request. Those parameters could end with an anchor so we must set ajax parameters first
            if (UtilValidate.isNotEmpty(extraParams)) {
                if (ajaxParamsBuffer.length() > 0 && !extraParams.startsWith("&")) {
                    ajaxParamsBuffer.append("&");
                }
                ajaxParamsBuffer.append(extraParams);
            }
            ajaxParams = ajaxParamsBuffer.toString();
            ajaxUrl += updateArea.getAreaId() + ",";
            //ajaxUrl += this.rh.makeLink(this.request, this.response, UtilHttp.removeQueryStringFromTarget(targetUrl));
            ajaxUrl += "," + ajaxParams;
        }
        Locale locale = UtilMisc.ensureLocale(context.get("locale"));
        return FlexibleStringExpander.expandString(ajaxUrl, context, locale);
    }

    /** Extracts parameters from a target URL string, prepares them for an Ajax
     * JavaScript call. This method is currently set to return a parameter string
     * suitable for the Prototype.js library.
     * @param target Target URL string
     * @return Parameter string
     */
    public static String getAjaxParamsFromTarget(String target) {
        String targetParams = UtilHttp.getQueryStringFromTarget(target);
        targetParams = targetParams.replace("?", "");
        targetParams = targetParams.replace("&amp;", "&");
        return targetParams;
    }

    /**
     * If tooltip attribute exist add it with it's own attributes
     * @param attributes
     * @param context
     * @param modelFormField
     */
    public void appendTooltip(Map<String, Object> attributes, Map<String, Object> context, ModelFormField modelFormField) {
        // render the tooltip, in other methods too
        String tooltip = modelFormField.getTooltip(context);
        if (UtilValidate.isNotEmpty(tooltip)) {
            Map<String, Object> tooltipAttr = new HashMap<>();
            tooltipAttr.put("tooltip", encodeDoubleQuotes(tooltip));
            tooltipAttr.put("tooltipStyle", modelFormField.getTooltipStyle());
            attributes.put("tooltip", tooltipAttr);
        }
    }

    public void makeHyperlinkString(Map<String, Object> element, SubHyperlink subHyperlink, Map<String, Object> context) {
        if (subHyperlink == null) {
            return;
        }
        if (subHyperlink.shouldUse(context)) {
            if (UtilValidate.isNotEmpty(subHyperlink.getWidth())) {
                this.request.setAttribute("width", subHyperlink.getWidth());
            }
            if (UtilValidate.isNotEmpty(subHyperlink.getHeight())) {
                this.request.setAttribute("height", subHyperlink.getHeight());
            }
            makeHyperlinkByType(element, subHyperlink.getLinkType(), subHyperlink.getStyle(context), subHyperlink.getUrlMode(),
                    subHyperlink.getTarget(context), subHyperlink.getParameterMap(context, subHyperlink.getModelFormField().getEntityName(), subHyperlink.getModelFormField().getServiceName()), subHyperlink.getDescription(context),
                    subHyperlink.getTargetWindow(context), "", subHyperlink.getModelFormField(), this.request, this.response,
                    context);
        }
    }

    public void addAsterisks(Map<String, Object> element, Map<String, Object> context, ModelFormField modelFormField) {
        String requiredField = "false";
        String requiredStyle = "";
        if (modelFormField.getRequiredField()) {
            requiredField = "true";
            requiredStyle = modelFormField.getRequiredFieldStyle();
        }
        Map<String, Object> cb = new HashMap<>();
        cb.put("requiredField", requiredField);
        cb.put("requiredStyle", requiredStyle);
        element.put("required", cb);
    }

    public String appendContentUrl(String location) {
        StringBuilder buffer = new StringBuilder();
        ContentUrlTag.appendContentPrefix(this.request, buffer);
        return  buffer.toString()  + location;
    }

    public void makeHyperlinkByType(Map<String, Object> element, String linkType, String linkStyle, String targetType, String target, Map<String, String> parameterMap, String description, String targetWindow, String confirmation, ModelFormField modelFormField, HttpServletRequest request,
                                    HttpServletResponse response, Map<String, Object> context) {
        element.put("HyperlinkByType", NOT_YET_SUPPORTED);
        /*
        String realLinkType = WidgetWorker.determineAutoLinkType(linkType, target, targetType, request);
        String encodedDescription = encode(description, modelFormField, context);
        // get the parameterized pagination index and size fields
        int paginatorNumber = WidgetWorker.getPaginatorNumber(context);
        ModelForm modelForm = modelFormField.getModelForm();
        ModelTheme modelTheme = visualTheme.getModelTheme();
        String viewIndexField = modelForm.getMultiPaginateIndexField(context);
        String viewSizeField = modelForm.getMultiPaginateSizeField(context);
        int viewIndex = Paginator.getViewIndex(modelForm, context);
        int viewSize = Paginator.getViewSize(modelForm, context);
        if (("viewIndex" + "_" + paginatorNumber).equals(viewIndexField)) {
            viewIndexField = "VIEW_INDEX" + "_" + paginatorNumber;
        }
        if (("viewSize" + "_" + paginatorNumber).equals(viewSizeField)) {
            viewSizeField = "VIEW_SIZE" + "_" + paginatorNumber;
        }
        if ("hidden-form".equals(realLinkType)) {
            parameterMap.put(viewIndexField, Integer.toString(viewIndex));
            parameterMap.put(viewSizeField, Integer.toString(viewSize));
            if ("multi".equals(modelForm.getType())) {
                WidgetWorker.makeHiddenFormLinkAnchor(writer, linkStyle, encodedDescription, confirmation, modelFormField, request, response, context);
                // this is a bit trickier, since we can't do a nested form we'll have to put the link to submit the form in place, but put the actual form def elsewhere, ie after the big form is closed
                Map<String, Object> wholeFormContext = UtilGenerics.checkMap(context.get("wholeFormContext"));
                Appendable postMultiFormWriter = wholeFormContext != null ? (Appendable) wholeFormContext.get("postMultiFormWriter") : null;
                if (postMultiFormWriter == null) {
                    postMultiFormWriter = new StringWriter();
                    wholeFormContext.put("postMultiFormWriter", postMultiFormWriter);
                }
                WidgetWorker.makeHiddenFormLinkForm(postMultiFormWriter, target, targetType, targetWindow, parameterMap, modelFormField, request, response, context);
            } else {
                WidgetWorker.makeHiddenFormLinkForm(writer, target, targetType, targetWindow, parameterMap, modelFormField, request, response, context);
                WidgetWorker.makeHiddenFormLinkAnchor(writer, linkStyle, encodedDescription, confirmation, modelFormField, request, response, context);
            }
        } else {
            if ("layered-modal".equals(realLinkType)) {
                String uniqueItemName = "Modal_".concat(UUID.randomUUID().toString().replace("-", "_"));
                String width = (String) this.request.getAttribute("width");
                if (UtilValidate.isEmpty(width)) {
                    width = String.valueOf(modelTheme.getLinkDefaultLayeredModalWidth());
                    this.request.setAttribute("width", width);
                }
                String height = (String) this.request.getAttribute("height");
                if (UtilValidate.isEmpty(height)) {
                    height = String.valueOf(modelTheme.getLinkDefaultLayeredModalHeight());
                    this.request.setAttribute("height", height);
                }
                this.request.setAttribute("uniqueItemName", uniqueItemName);
                makeHyperlinkString(cb, linkStyle, targetType, target, parameterMap, encodedDescription, confirmation, modelFormField, request, response, context, targetWindow);
                this.request.removeAttribute("uniqueItemName");
                this.request.removeAttribute("height");
                this.request.removeAttribute("width");
            } else {
                makeHyperlinkString(cb, linkStyle, targetType, target, parameterMap, encodedDescription, confirmation, modelFormField, request, response, context, targetWindow);
            }
        }
        */
    }

    public void makeHyperlinkString(Map<String, Object> element, String linkStyle, String targetType, String target, Map<String, String> parameterMap, String description, String confirmation, ModelFormField modelFormField, HttpServletRequest request, HttpServletResponse response, Map<String, Object> context,
                                    String targetWindow) throws IOException {
        if (description != null || UtilValidate.isNotEmpty(request.getAttribute("image"))) {
            StringBuilder linkUrl = new StringBuilder();
            final URI linkUri = WidgetWorker.buildHyperlinkUri(target, targetType, UtilValidate.isEmpty(request.getAttribute("uniqueItemName"))?parameterMap:null,
            		       null, false, false, true, request, response);
        	linkUrl.append(linkUri.toString());
            String event = "";
            String action = "";
            String imgSrc = "";
            String alt = "";
            String id = "";
            String uniqueItemName = "";
            String width = "";
            String height = "";
            String imgTitle = "";
            String hiddenFormName = WidgetWorker.makeLinkHiddenFormName(context, modelFormField);
            if (UtilValidate.isNotEmpty(modelFormField.getEvent()) && UtilValidate.isNotEmpty(modelFormField.getAction(context))) {
                event = modelFormField.getEvent();
                action = modelFormField.getAction(context);
            }
            if (UtilValidate.isNotEmpty(request.getAttribute("image"))) {
                imgSrc = request.getAttribute("image").toString();
            }
            if (UtilValidate.isNotEmpty(request.getAttribute("alternate"))) {
                alt = request.getAttribute("alternate").toString();
            }
            if (UtilValidate.isNotEmpty(request.getAttribute("imageTitle"))) {
                imgTitle = request.getAttribute("imageTitle").toString();
            }
            Integer size = Integer.valueOf("0");
            if (UtilValidate.isNotEmpty(request.getAttribute("descriptionSize"))) {
                size = Integer.valueOf(request.getAttribute("descriptionSize").toString());
            }
            if (UtilValidate.isNotEmpty(description) && size > 0 && description.length() > size) {
                imgTitle = description;
                description = description.substring(0, size - 8) + "..." + description.substring(description.length() - 5);
            }
            if (UtilValidate.isEmpty(imgTitle)) {
                imgTitle = modelFormField.getTitle(context);
            }
            if (UtilValidate.isNotEmpty(request.getAttribute("id"))) {
                id = request.getAttribute("id").toString();
            }
            if (UtilValidate.isNotEmpty(request.getAttribute("uniqueItemName"))) {
                uniqueItemName = request.getAttribute("uniqueItemName").toString();
                width = request.getAttribute("width").toString();
                height = request.getAttribute("height").toString();
            }
            StringBuilder targetParameters = new StringBuilder();
            if (UtilValidate.isNotEmpty(parameterMap) ) {
                targetParameters.append("{");
                for (Map.Entry<String, String> parameter : parameterMap.entrySet()) {
                    if (targetParameters.length() > 1) {
                        targetParameters.append(",");
                    }
                    targetParameters.append("'");
                    targetParameters.append(parameter.getKey());
                    targetParameters.append("':'");
                    targetParameters.append(parameter.getValue());
                    targetParameters.append("'");
                }
                targetParameters.append("}");
            }
            Map<String, Object> cb = new HashMap<>();
            cb.put("linkStyle", linkStyle == null ? "" : linkStyle);
            cb.put("hiddenFormName", hiddenFormName);
            cb.put("event", event);
            cb.put("action", action);
            cb.put("imgSrc", imgSrc);
            cb.put("title", imgTitle);
            cb.put("alternate", alt);
            cb.put("targetParameters", targetParameters.toString());
            cb.put("linkUrl", linkUrl.toString());
            cb.put("targetWindow", targetWindow);
            cb.put("description", description);
            cb.put("confirmation", confirmation);
            cb.put("uniqueItemName", uniqueItemName);
            cb.put("height", height);
            cb.put("width", width);
            cb.put("id", id);
            element.put("HyperlinkString", cb);
        }
    }

    public void makeHiddenFormLinkAnchor(Appendable writer, String linkStyle, String description, String confirmation, ModelFormField modelFormField, HttpServletRequest request, HttpServletResponse response, Map<String, Object> context) {
        if (UtilValidate.isNotEmpty(description) || UtilValidate.isNotEmpty(request.getAttribute("image"))) {
            String hiddenFormName = WidgetWorker.makeLinkHiddenFormName(context, modelFormField);
            String event = "";
            String action = "";
            String imgSrc = "";
            if (UtilValidate.isNotEmpty(modelFormField.getEvent()) && UtilValidate.isNotEmpty(modelFormField.getAction(context))) {
                event = modelFormField.getEvent();
                action = modelFormField.getAction(context);
            }
            if (UtilValidate.isNotEmpty(request.getAttribute("image"))) {
                imgSrc = request.getAttribute("image").toString();
            }
            Map<String, Object> cb = new HashMap<>();

            cb.put("linkStyle", linkStyle == null ? "" : linkStyle);
            cb.put("hiddenFormName", hiddenFormName);
            cb.put("event", event);
            cb.put("action", action);
            cb.put("imgSrc", imgSrc);
            cb.put("description", description);
            cb.put("confirmation ", confirmation);
            this.output.putScreen("HiddenFormLinkAnchor", cb);
        }
    }
/*
    public void makeHiddenFormLinkForm(Appendable writer, String target, String targetType, String targetWindow, List<CommonWidgetModels.Parameter> parameterList, ModelFormField modelFormField, HttpServletRequest request, HttpServletResponse response, Map<String, Object> context) throws IOException {
        StringBuilder actionUrl = new StringBuilder();
        WidgetWorker.buildHyperlinkUrl(actionUrl, target, targetType, null, null, false, false, true, request, response, context);
        String name = WidgetWorker.makeLinkHiddenFormName(context, modelFormField);
        StringBuilder parameters = new StringBuilder();
        parameters.append("[");
        for (CommonWidgetModels.Parameter parameter : parameterList) {
            if (parameters.length() > 1) {
                parameters.append(",");
            }
            parameters.append("{'name':'");
            parameters.append(parameter.getName());
            parameters.append("'");
            parameters.append(",'value':'");
            parameters.append(UtilCodec.getEncoder("html").encode(parameter.getValue(context)));
            parameters.append("'}");
        }
        parameters.append("]");
        Map<String, Object> cb = new HashMap<>();

        cb.put("actionUrl", actionUrl.toString());
        cb.put("name", name);
        cb.put("parameters", parameters.toString());
        cb.put(" targetWindow", targetWindow);
        this.output.put("HiddenFormLinkForm", cb);
    }
*/
    public void renderContainerFindField(Appendable writer, Map<String, Object> context, ContainerField containerField) {
        String id = containerField.getModelFormField().getIdName();
        String className = UtilFormatOut.checkNull(containerField.getModelFormField().getWidgetStyle());
        Map<String, Object> cb = new HashMap<>();

        cb.put("id", id);
        cb.put("className", className);
        this.output.putScreen("ContainerFindField", cb);
    }

}
