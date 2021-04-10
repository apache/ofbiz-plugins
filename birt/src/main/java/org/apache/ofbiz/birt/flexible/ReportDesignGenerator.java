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

package org.apache.ofbiz.birt.flexible;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.GeneralException;
import org.apache.ofbiz.base.util.UtilGenerics;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.service.DispatchContext;
import org.eclipse.birt.core.exception.BirtException;
import org.eclipse.birt.core.framework.Platform;
import org.eclipse.birt.report.model.api.CachedMetaDataHandle;
import org.eclipse.birt.report.model.api.CellHandle;
import org.eclipse.birt.report.model.api.DataItemHandle;
import org.eclipse.birt.report.model.api.DesignConfig;
import org.eclipse.birt.report.model.api.DesignElementHandle;
import org.eclipse.birt.report.model.api.ElementFactory;
import org.eclipse.birt.report.model.api.GridHandle;
import org.eclipse.birt.report.model.api.IDesignEngine;
import org.eclipse.birt.report.model.api.IDesignEngineFactory;
import org.eclipse.birt.report.model.api.LabelHandle;
import org.eclipse.birt.report.model.api.PropertyHandle;
import org.eclipse.birt.report.model.api.ReportDesignHandle;
import org.eclipse.birt.report.model.api.RowHandle;
import org.eclipse.birt.report.model.api.ScalarParameterHandle;
import org.eclipse.birt.report.model.api.ScriptDataSetHandle;
import org.eclipse.birt.report.model.api.ScriptDataSourceHandle;
import org.eclipse.birt.report.model.api.SessionHandle;
import org.eclipse.birt.report.model.api.StructureFactory;
import org.eclipse.birt.report.model.api.activity.SemanticException;
import org.eclipse.birt.report.model.api.elements.DesignChoiceConstants;
import org.eclipse.birt.report.model.api.elements.structures.CachedMetaData;
import org.eclipse.birt.report.model.api.elements.structures.ColumnHint;
import org.eclipse.birt.report.model.api.elements.structures.ComputedColumn;
import org.eclipse.birt.report.model.api.elements.structures.HideRule;
import org.eclipse.birt.report.model.api.elements.structures.ResultSetColumn;
import org.eclipse.birt.report.model.elements.ReportItem;

import com.ibm.icu.util.ULocale;

/**
 * Report Design Generator Object - Handles flexible report design Generation from Master.
 */

public class ReportDesignGenerator {

    private static final String MODULE = ReportDesignGenerator.class.getName();
    private static final String RES_ERROR = "BirtErrorUiLabels";
    private Locale locale;
    private ElementFactory factory;
    /**
     * The generated design
     */
    private ReportDesignHandle design;
    private Map<String, String> dataMap;
    /**
     * Map of all filter supported by the report design
     */
    private Map<String, String> filterMap;
    /**
     * Service name to populate dataset of the report design
     */
    private String serviceName;
    private Map<String, String> fieldDisplayLabels;
    private Map<String, String> filterDisplayLabels;
    private String rptDesignName;
    private boolean generateFilters = false;

    public ReportDesignGenerator(Map<String, Object> context, DispatchContext dctx) throws GeneralException, SemanticException {
        locale = (Locale) context.get("locale");
        dataMap = UtilGenerics.cast(context.get("dataMap"));
        filterMap = UtilGenerics.cast(context.get("filterMap"));
        serviceName = (String) context.get("serviceName");
        fieldDisplayLabels = UtilGenerics.cast(context.get("fieldDisplayLabels"));
        filterDisplayLabels = UtilGenerics.cast(context.get("filterDisplayLabels"));
        rptDesignName = (String) context.get("rptDesignName");
        String writeFilters = (String) context.get("writeFilters");
        if (UtilValidate.isEmpty(dataMap)) {
            throw new GeneralException("Report design generator failed. Entry data map not found.");
        }
        if ("Y".equals(writeFilters)) {
            generateFilters = true;
        }
    }

    /**
     * Generate report design (rtdesign file).
     * @throws IOException
     * @throws GeneralException
     * @throws BirtException
     */
    public void buildReport() throws IOException, GeneralException, BirtException {
        DesignConfig config = new DesignConfig();
        Platform.startup();
        IDesignEngine engine = ((IDesignEngineFactory) Platform.createFactoryObject(
                IDesignEngineFactory.EXTENSION_DESIGN_ENGINE_FACTORY)).createDesignEngine(config);

        // creating main design elements
        SessionHandle session = engine.newSessionHandle(ULocale.forLocale(locale));
        design = session.createDesign();
        factory = design.getElementFactory();
        DesignElementHandle element = factory.newSimpleMasterPage("Page Master");
        design.getMasterPages().add(element);

        // create DataSource call
        createScriptedDataSource();

        // create DataSet call
        createScriptedDataset();

        // General design parameters
        design.setLayoutPreference(DesignChoiceConstants.REPORT_LAYOUT_PREFERENCE_AUTO_LAYOUT);
        design.setBidiOrientation(DesignChoiceConstants.BIDI_DIRECTION_LTR);
        design.setDefaultUnits(DesignChoiceConstants.UNITS_IN);
        design.setCreatedBy(design.getVersion());
        design.setImageDPI(96);

        // adding filters as parameters to make them available for design
        // first adding parameters to the design itself
        if (UtilValidate.isNotEmpty(filterMap)) {
            // adding filters within reportDesign if generateFilters is set to true
            GridHandle grid = null;
            int i = 0;
            if (generateFilters) {
                grid = factory.newGridItem(null, 2, filterMap.size());
                design.getBody().add(grid);
                grid.setWidth("100%");
            }

            for (String filter : filterMap.keySet()) {
                String birtType = BirtUtil.convertFieldTypeToBirtParameterType(filterMap.get(filter));
                if (birtType == null) {
                    throw new GeneralException(UtilProperties.getMessage(RES_ERROR, "BirtErrorConversionFieldToBirtFailed", locale));
                }
                // get label
                String displayFilterName;
                if (UtilValidate.isNotEmpty(filterDisplayLabels)) {
                    displayFilterName = filterDisplayLabels.get(filter);
                } else {
                    displayFilterName = filter;
                }
                ScalarParameterHandle scalParam = factory.newScalarParameter(filter);
                // scalParam.setDisplayName(displayFilterName); // TODO has no incidence at all right now, is only displayed when using birt's
                // report parameter system. Not our case. I leave it here if any idea arise of how to translate these.
                scalParam.setPromptText(displayFilterName);
                if ("javaObject".equals(birtType)) { //Fields of type='blob' are rejected by Birt: org.eclipse.birt.report.model.api.metadata
                    // .PropertyValueException: The choice value "javaObject" is not allowed.
                    throw new GeneralException("Fields of type='blob' are rejected by Birt. Create a view entity, based on the requested entity, "
                            + "where you exclude the field of type='blob'");
                } else {
                    scalParam.setDataType(birtType);
                }
                scalParam.setIsRequired(false);
                design.getParameters().add(scalParam);

                if (generateFilters) {
                    RowHandle row = (RowHandle) grid.getRows().get(i);
                    CellHandle cellLabel = (CellHandle) row.getCells().get(0);
                    CellHandle cellFilter = (CellHandle) row.getCells().get(1);
                    LabelHandle label = factory.newLabel(null);
                    label.setText(displayFilterName);
                    cellLabel.getContent().add(label);

                    // 1. create computed column and add it to grid column bindings
                    ComputedColumn computedCol = StructureFactory.createComputedColumn();
                    PropertyHandle computedSet = grid.getColumnBindings();
                    computedCol.setName(displayFilterName);
                    StringBuffer expression = new StringBuffer("params[\"");
                    expression.append(filter);
                    expression.append("\"]");
                    computedCol.setExpression(expression.toString());
                    computedSet.addItem(computedCol);

                    // 2. create data and add computed column to it
                    DataItemHandle data = factory.newDataItem(null);
                    data.setResultSetColumn(computedCol.getName());
                    cellFilter.getContent().add(data);

                    // add visibility rule on row
                    HideRule hideRule = StructureFactory.createHideRule();
                    StringBuffer expressionHide = new StringBuffer(expression);
                    expressionHide.append(".value == null || ");
                    expressionHide.append(expression);
                    expressionHide.append(".value == \"\"");
                    hideRule.setExpression(expressionHide.toString());
                    PropertyHandle propVisHandle = row.getPropertyHandle(ReportItem.VISIBILITY_PROP);
                    propVisHandle.addItem(hideRule);
                    i++;
                }
            }
            // second adding script within beforeFactory filling the parameters with values from inputFields
            createScriptedBeforeFactory();
        }

        /*//################ CODE HERE IF YOU WANT TO ADD GENERATED DESIGN / MAY BE WORTH USING RPTTEMPLATE AND-OR RPTLIBRARY ###################
        GridHandle grid = factory.newGridItem(null, 7, 3);
        design.getBody().add(grid);
        grid.setWidth("100%");
        RowHandle row = (RowHandle) grid.getRows().get(0);
        ImageHandle image = factory.newImage(null);
        CellHandle cell = (CellHandle) row.getCells().get(0);
        cell.getContent().add(image);
        image.setURL("https://ofbiz.apache.org/images/ofbiz_logo.png");
        LabelHandle label = factory.newLabel(null);
        cell = (CellHandle) row.getCells().get(1);
        cell.getContent().add(label);
        label.setText("Dat is dat test !");
        // ################ CODE HERE IF YOU WANT TO ADD GENERATED DESIGN / MAY BE WORTH USING RPTTEMPLATE AND-OR RPTLIBRARY ################### */

        design.saveAs(rptDesignName);
        design.close();
        if (Debug.infoOn()) {
            Debug.logInfo("####### Design generated: " + rptDesignName, MODULE);
        }
        session.closeAll(false);
        Platform.shutdown();
    }

    /**
     * Create the script that will be called within "Before Factory" step in Birt Report rendering process.
     * <p>This script is used to populate Birt design parameters from input</p>
     */
    private void createScriptedBeforeFactory() {
        StringBuffer beforeFactoryScript = new StringBuffer("Debug.logInfo(\"###### In beforeFactory\", MODULE);\n");
        beforeFactoryScript.append("var inputFields = reportContext.getParameterValue(\"parameters\");\n");
        beforeFactoryScript.append("//get a list of all report parameters\n");
        beforeFactoryScript.append("var parameters = reportContext.getDesignHandle().getAllParameters();\n");
        beforeFactoryScript.append("for (var i = 0; i < parameters.size(); i++) {\n");
        beforeFactoryScript.append("    var currentParam = parameters.get(i);\n");
        beforeFactoryScript.append("    var parametersName = currentParam.getName();\n");
        beforeFactoryScript.append("    params[parametersName].value = inputFields.get(parametersName);\n");
        beforeFactoryScript.append("}");
        design.setBeforeFactory(beforeFactoryScript.toString());
    }

    /**
     * Create the script that will define the OFBiz dataset in Birt Report design.
     * <p>This dataset will populate the OFBiz datasource of the design
     * with <code>records</code> returned by <code>serviceName</code> service</p>
     * @throws SemanticException
     * @throws GeneralException
     */
    private void createScriptedDataset() throws SemanticException, GeneralException {
        ScriptDataSetHandle dataSetHandle = factory.newScriptDataSet("Data Set");
        dataSetHandle.setDataSource("OFBiz");

        // set Initialize Birt script
        StringBuffer dataSetInitializeScript = new StringBuffer();
        dataSetInitializeScript.append("importPackage(Packages.org.eclipse.birt.report.engine.api);\n");
        dataSetInitializeScript.append("importPackage(Packages.org.apache.ofbiz.entity);\n");
        dataSetInitializeScript.append("importPackage(Packages.org.apache.ofbiz.service);\n");
        dataSetInitializeScript.append("importPackage(Packages.org.apache.ofbiz.base.util);\n");
        dataSetInitializeScript.append("importPackage(java.util);\n");
        dataSetInitializeScript.append("MODULE = \"" + rptDesignName + "\";");
        dataSetInitializeScript.append("Debug.logInfo(\"###### In initialize \", MODULE);");
        design.setInitialize(dataSetInitializeScript.toString());

        // set open Birt script
        StringBuffer dataSetOpenScript = new StringBuffer("importPackage(Packages.org.apache.ofbiz.birt);\n");
        dataSetOpenScript.append("Debug.logInfo(\"#### In open\", MODULE)\n");
        dataSetOpenScript.append("try {\n");
        dataSetOpenScript.append("    listRes = dispatcher.runSync(\"" + serviceName + "\", UtilMisc.toMap(\"userLogin\", reportContext"
                + ".getParameterValue(\"userLogin\"), \"locale\", reportContext.getParameterValue(\"locale\"), "
                + "\"reportContext\", reportContext));\n");
        dataSetOpenScript.append("    if (ServiceUtil.isError(listRes)) {\n");
        dataSetOpenScript.append("         Debug.logError(ServiceUtil.getErrorMessage(listRes));\n");
        dataSetOpenScript.append("    }\n");
        dataSetOpenScript.append("}\n");
        dataSetOpenScript.append("catch (e) { Debug.logError(e, MODULE); }\n");
        dataSetOpenScript.append("records = listRes.get(\"records\");\n");
        dataSetOpenScript.append("countOfRow = 0;\n");
        dataSetOpenScript.append("totalRow = records.size();\n");
        dataSetHandle.setOpen(dataSetOpenScript.toString());

        // set fetch Birt script
        StringBuffer dataSetFetchScript = new StringBuffer("if (countOfRow == totalRow) return false;\n");
        dataSetFetchScript.append("line = records.get(countOfRow);\n");
        for (String field : dataMap.keySet()) {
            dataSetFetchScript.append(field);
            dataSetFetchScript.append(" = line.get(\"");
            dataSetFetchScript.append(field);
            dataSetFetchScript.append("\"); row[\"");
            dataSetFetchScript.append(field);
            dataSetFetchScript.append("\"] = ");
            dataSetFetchScript.append(field);
            dataSetFetchScript.append(";\n");
        }

        dataSetFetchScript.append("countOfRow ++;\n");
        dataSetFetchScript.append("return true;");
        dataSetHandle.setFetch(dataSetFetchScript.toString());

        // #### define dataSet
        CachedMetaData cmd = StructureFactory.createCachedMetaData();
        CachedMetaDataHandle cachedMetaDataHandle = dataSetHandle.setCachedMetaData(cmd);
        PropertyHandle columnHintsSet = dataSetHandle.getPropertyHandle(ScriptDataSetHandle.COLUMN_HINTS_PROP);

        int i = 1;
        for (String field : dataMap.keySet()) {
            ResultSetColumn resultSetCol = StructureFactory.createResultSetColumn();
            resultSetCol.setColumnName(field);
            String birtType = BirtUtil.convertFieldTypeToBirtType(dataMap.get(field));
            if (birtType == null) {
                throw new GeneralException(UtilProperties.getMessage(RES_ERROR, "BirtErrorConversionFieldToBirtFailed", locale));
            }
            resultSetCol.setPosition(i);
            resultSetCol.setDataType(birtType);

            ColumnHint columnHint = StructureFactory.createColumnHint();
            columnHint.setProperty("columnName", field);
            columnHint.setProperty("analysis", "dimension");
            columnHint.setProperty("heading", field);
            // get label
            String displayName = null;
            if (UtilValidate.isNotEmpty(fieldDisplayLabels)) {
                displayName = fieldDisplayLabels.get(field);
            } else {
                displayName = field;
            }
            columnHint.setProperty("displayName", displayName);
            cachedMetaDataHandle.getResultSet().addItem(resultSetCol);
            columnHintsSet.addItem(columnHint);
            i++;
        }
        design.getDataSets().add(dataSetHandle);
    }

    /**
     * Create new dataSource named OFBiz
     */
    private void createScriptedDataSource() throws SemanticException {
        ScriptDataSourceHandle dataSource = factory.newScriptDataSource("OFBiz");
        design.getDataSources().add(dataSource);
    }
}
