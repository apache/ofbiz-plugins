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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.fileupload.FileItem;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.transaction.GenericTransactionException;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.htmlreport.InterfaceReport;
import org.apache.ofbiz.order.finaccount.FinAccountHelper;
import org.apache.ofbiz.pricat.AbstractPricatParser;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceUtil;
import org.apache.poi.hssf.usermodel.HSSFDataFormatter;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Sample pricat excel parser.
 */
public class SamplePricatParser extends AbstractPricatParser {

    public static final Map<String, List<Object[]>> COL_NAMES_LIST = UtilMisc.toMap("V1.1", genExcelHeaderNames("V1.1"));
    public static final int HEADER_ROW_NO = 4;
    private static final String MODULE = SamplePricatParser.class.getName();
    private List<String> headerColNames = new ArrayList<>();

    public SamplePricatParser(LocalDispatcher dispatcher, Delegator delegator, Locale locale, InterfaceReport report,
                              Map<String, String[]> facilities, File pricatFile, GenericValue userLogin) {
        super(dispatcher, delegator, locale, report, facilities, pricatFile, userLogin);
    }

    /**
     * The Object[] have 4 elements, they are:
     * 1. Header Label Name.
     * 2. Cell data type to return.
     * 3. Boolean value to indicate whether the column is required.
     * 4. Boolean value to indicate whether the column is a price when cell data type is BigDecimal, this element is optional.
     * @param version
     * @return List of Object[]
     */
    private static List<Object[]> genExcelHeaderNames(String version) {
        switch (version) {
        case "V1.1":
        default:
            return genExcelHeaderNamesV1();
        }
    }

    /**
     * Get V1.1 pricat excel header names and attributes.
     * @return list of Object[]
     */
    private static List<Object[]> genExcelHeaderNamesV1() {
        List<Object[]> listHeaderName = new ArrayList<>();
        listHeaderName.add(new Object[]{"Facility Name",
                CellType.STRING,
                Boolean.TRUE});
        listHeaderName.add(new Object[]{"FacilityId",
                CellType.STRING,
                Boolean.TRUE});
        listHeaderName.add(new Object[]{"Category L1",
                CellType.STRING,
                Boolean.FALSE});
        listHeaderName.add(new Object[]{"Category L2",
                CellType.STRING,
                Boolean.FALSE});
        listHeaderName.add(new Object[]{"Category L3",
                CellType.STRING,
                Boolean.FALSE});
        listHeaderName.add(new Object[]{"Category L4",
                CellType.STRING,
                Boolean.FALSE});
        listHeaderName.add(new Object[]{"Brand",
                CellType.STRING,
                Boolean.TRUE});
        listHeaderName.add(new Object[]{"Style No",
                CellType.STRING,
                Boolean.TRUE});
        listHeaderName.add(new Object[]{"Product Name",
                CellType.STRING,
                Boolean.TRUE});
        listHeaderName.add(new Object[]{"Color",
                CellType.STRING,
                Boolean.FALSE});
        listHeaderName.add(new Object[]{"Size",
                CellType.STRING,
                Boolean.FALSE});
        listHeaderName.add(new Object[]{"Barcode",
                CellType.STRING,
                Boolean.FALSE});
        listHeaderName.add(new Object[]{"Stock Qty",
                CellType.NUMERIC,
                Boolean.TRUE});
        listHeaderName.add(new Object[]{"Average Cost",
                CellType.NUMERIC,
                Boolean.TRUE,
                Boolean.TRUE});
        listHeaderName.add(new Object[]{"List Price",
                CellType.NUMERIC,
                Boolean.TRUE,
                Boolean.TRUE});
        listHeaderName.add(new Object[]{"Member Price",
                CellType.NUMERIC,
                Boolean.FALSE,
                Boolean.TRUE});
        return listHeaderName;
    }

    /**
     * Parse pricat excel file in xlsx format.
     */
    public void parsePricatExcel(boolean writeFile) {
        XSSFWorkbook workbook = null;
        try {
            // 1. read the pricat excel file
            FileInputStream is = new FileInputStream(getPricatFile());

            // 2. use POI to load this bytes
            getReport().print(UtilProperties.getMessage(RESOURCE, "ParsePricatFileStatement", new Object[]{getPricatFile().getName()}, getLocale()),
                    InterfaceReport.FORMAT_DEFAULT);
            try {
                workbook = new XSSFWorkbook(is);
                getReport().println(UtilProperties.getMessage(RESOURCE, "ok", getLocale()), InterfaceReport.FORMAT_OK);
            } catch (IOException e) {
                getReport().println(e);
                getReport().println(UtilProperties.getMessage(RESOURCE, "PricatSuggestion", getLocale()), InterfaceReport.FORMAT_ERROR);
                return;
            }

            // 3. only first sheet will be parsed
            // 3.1 verify the file has a sheet at least
            setFormatter(new HSSFDataFormatter(getLocale()));
            isNumOfSheetsOK(workbook);

            // 3.2 verify the version is supported
            XSSFSheet sheet = workbook.getSheetAt(0);
            if (!isVersionSupported(sheet)) {
                return;
            }

            // 3.3 get currencyId
            existsCurrencyId(sheet);

            // 3.4 verify the table header row is just the same as column names, if not, print error and return
            if (!isTableHeaderMatched(sheet)) {
                return;
            }

            // 3.5 verify the first table has 6 rows at least
            containsDataRows(sheet);

            if (UtilValidate.isNotEmpty(getErrorMessages())) {
                getReport().println(UtilProperties.getMessage(RESOURCE, "HeaderContainsError", getLocale()), InterfaceReport.FORMAT_ERROR);
                return;
            }

            // 4. parse data
            // 4.1 parse row by row and store the contents into database
            parseRowByRow(sheet);
            if (UtilValidate.isNotEmpty(getErrorMessages())) {
                getReport().println(UtilProperties.getMessage(RESOURCE, "DataContainsError", getLocale()), InterfaceReport.FORMAT_ERROR);
                if (writeFile) {
                    setSequenceNum(getReport().getSequenceNum());
                    writeCommentsToFile(workbook, sheet);
                }
            }

            // 5. clean up the log files and commented Excel files
            cleanupLogAndCommentedExcel();
        } catch (IOException e) {
            getReport().println(e);
            Debug.logError(e, MODULE);
        } finally {
            if (UtilValidate.isNotEmpty(getFileItems())) {
                // remove tmp files
                FileItem fi = null;
                for (int i = 0; i < getFileItems().size(); i++) {
                    fi = getFileItems().get(i);
                    fi.delete();
                }
            }
            if (workbook != null) {
                try {
                    workbook.close();
                } catch (IOException e) {
                    Debug.logError(e, MODULE);
                }
            }
        }
    }
    /** exists currency id */
    @Override
    public boolean existsCurrencyId(XSSFSheet sheet) {
        InterfaceReport report = getReport();
        Locale locale = getLocale();
        Map<CellReference, String> errorMessages = getErrorMessages();
        report.print(UtilProperties.getMessage(RESOURCE, "StartCheckCurrencyId", locale), InterfaceReport.FORMAT_NOTE);
        XSSFCell currencyIdCell = sheet.getRow(2).getCell(1);
        setCurrencyId(currencyIdCell.getStringCellValue().trim().toUpperCase());
        String currencyId = getCurrencyId();
        if (UtilValidate.isEmpty(currencyId)) {
            String errorMessage = UtilProperties.getMessage(RESOURCE, "CurrencyIdRequired", locale);
            report.println(errorMessage, InterfaceReport.FORMAT_ERROR);
            errorMessages.put(new CellReference(currencyIdCell), errorMessage);
            return false;
        } else {
            try {
                GenericValue currencyUom = EntityQuery.use(getDelegator()).from("Uom").where("uomId", currencyId).queryOne();
                if (!"CURRENCY_MEASURE".equals(currencyUom.getString("uomTypeId"))) {
                    String errorMessage = UtilProperties.getMessage(RESOURCE, "CurrencyIdNotCurrency", new Object[]{currencyId}, locale);
                    report.println(errorMessage, InterfaceReport.FORMAT_ERROR);
                    errorMessages.put(new CellReference(currencyIdCell), errorMessage);
                    return false;
                }
            } catch (GenericEntityException e) {
                String errorMessage = UtilProperties.getMessage(RESOURCE, "CurrencyIdNotFound", new Object[]{currencyId}, locale);
                report.println(errorMessage, InterfaceReport.FORMAT_ERROR);
                errorMessages.put(new CellReference(currencyIdCell), errorMessage);
                return false;
            }
            report.print(UtilProperties.getMessage(RESOURCE, "CurrencyIdIs", new Object[]{currencyId}, locale), InterfaceReport.FORMAT_NOTE);
            report.println(" ... " + UtilProperties.getMessage(RESOURCE, "ok", locale), InterfaceReport.FORMAT_OK);
        }
        return true;
    }
    /** parse row by row */
    @Override
    public void parseRowByRow(XSSFSheet sheet) {
        InterfaceReport report = getReport();
        Locale locale = getLocale();
        int rows = sheet.getLastRowNum() + 1;
        List<Object[]> colNames = COL_NAMES_LIST.get(getPricatFileVersion());
        int colNumber = colNames.size();

        int emptyRowStart = -1;
        int emptyRowEnd = -1;
        for (int i = HEADER_ROW_NO + 1; i < rows; i++) {
            XSSFRow row = sheet.getRow(i);
            if (UtilValidate.isEmpty(row) || isEmptyRow(row, colNumber, false)) {
                if (emptyRowStart == -1) {
                    report.print("(" + (i + 1) + ") ", InterfaceReport.FORMAT_NOTE);
                    emptyRowStart = i;
                } else {
                    emptyRowEnd = i;
                }
                continue;
            } else {
                if (emptyRowStart != -1) {
                    if (emptyRowEnd != -1) {
                        report.print(" - (" + (emptyRowEnd + 1) + ") ", InterfaceReport.FORMAT_NOTE);
                    }
                    report.print(UtilProperties.getMessage(RESOURCE, "ExcelEmptyRow", locale), InterfaceReport.FORMAT_NOTE);
                    report.println(" ... " + UtilProperties.getMessage(RESOURCE, "skipped", locale), InterfaceReport.FORMAT_NOTE);
                    emptyRowStart = -1;
                    emptyRowEnd = -1;
                }
            }
            report.print("(" + (i + 1) + ") ", InterfaceReport.FORMAT_NOTE);
            List<Object> cellContents = getCellContents(row, colNames, colNumber);
            try {
                if (parseCellContentsAndStore(row, cellContents)) {
                    report.println(" ... " + UtilProperties.getMessage(RESOURCE, "ok", locale), InterfaceReport.FORMAT_OK);
                } else {
                    report.println(" ... " + UtilProperties.getMessage(RESOURCE, "skipped", locale), InterfaceReport.FORMAT_NOTE);
                }
            } catch (GenericTransactionException e) {
                report.println(e);
            }
        }
        if (emptyRowEnd != -1) {
            report.print(" - (" + (emptyRowEnd + 1) + ") ", InterfaceReport.FORMAT_NOTE);
            report.print(UtilProperties.getMessage(RESOURCE, "ExcelEmptyRow", locale), InterfaceReport.FORMAT_NOTE);
            report.println(" ... " + UtilProperties.getMessage(RESOURCE, "skipped", locale), InterfaceReport.FORMAT_NOTE);
        }
    }
    /**
     * Check data according to business logic. If data is ok, store it.
     * @param row
     * @param cellContents
     * @return
     * @throws GenericTransactionException
     */
    @Override
    public boolean parseCellContentsAndStore(XSSFRow row, List<Object> cellContents) throws GenericTransactionException {
        if (UtilValidate.isEmpty(cellContents)) {
            return false;
        }
        switch (getPricatFileVersion()) {
        case "V1.1":
        default:
            return parseCellContentsAndStoreV1(row, cellContents);
        }
    }
    /** parse cell contents and store */
    private boolean parseCellContentsAndStoreV1(XSSFRow row, List<Object> cellContents) throws GenericTransactionException {
        if (UtilValidate.isEmpty(cellContents)) {
            return false;
        }
        // 1. check if facilityId is in the facilities belong to the user, or if the name is correct for the id
        String facilityName = (String) getCellContent(cellContents, "Facility Name");
        String facilityId = (String) getCellContent(cellContents, "FacilityId");
        if (!isFacilityOk(row, facilityName, facilityId)) {
            return false;
        }

        // 2. get productCategoryId
        String ownerPartyId = getFacilities().get(facilityId)[1];
        String productCategoryId = getProductCategoryId(cellContents, ownerPartyId);

        // 3. get productFeatureId of brand
        String brandName = (String) getCellContent(cellContents, "Brand");
        String brandId = getBrandId(brandName, ownerPartyId);
        if (UtilValidate.isEmpty(brandId)) {
            return false;
        }

        // 4. get productId from brandId, model name
        String modelName = (String) getCellContent(cellContents, "Style No");
        String productName = (String) getCellContent(cellContents, "Product Name");
        BigDecimal listPrice = (BigDecimal) getCellContent(cellContents, "List Price");
        String productId = getProductId(row, brandId, modelName, productName, productCategoryId, ownerPartyId, listPrice);
        if (UtilValidate.isEmpty(productId) || UtilValidate.isEmpty(listPrice)) {
            return false;
        }

        // 5. update color and size if necessary
        String color = (String) getCellContent(cellContents, "Color");
        if (UtilValidate.isEmpty(color) || UtilValidate.isEmpty(color.trim())) {
            color = DEFAULT_COL_NAME;
        }
        String dimension = (String) getCellContent(cellContents, "Size");
        if (UtilValidate.isEmpty(dimension) || UtilValidate.isEmpty(dimension.trim())) {
            dimension = DEFAULT_DIM_NAME;
        }
        Map<String, Object> features = updateColorAndDimension(productId, ownerPartyId, color, dimension);
        if (ServiceUtil.isError(features)) {
            if (features.containsKey("index") && String.valueOf(features.get("index")).contains("0")) {
                int cell = headerColNames.indexOf("Color");
                XSSFCell colorCell = row.getCell(cell);
                getErrorMessages().put(new CellReference(colorCell), UtilProperties.getMessage(RESOURCE, "PricatColorError", getLocale()));
            }
            if (features.containsKey("index") && String.valueOf(features.get("index")).contains("1")) {
                int cell = headerColNames.indexOf("Size");
                XSSFCell colorCell = row.getCell(cell);
                getErrorMessages().put(new CellReference(colorCell), UtilProperties.getMessage(RESOURCE, "PricatDimensionError", getLocale()));
            }
            return false;
        }
        String colorId = (String) features.get("colorId");
        String dimensionId = (String) features.get("dimensionId");

        // 6. update skuIds by productId
        String barcode = (String) getCellContent(cellContents, "Barcode");
        BigDecimal inventory = (BigDecimal) getCellContent(cellContents, "Stock Qty");
        BigDecimal averageCost = (BigDecimal) getCellContent(cellContents, "Average Cost");
        String skuId = updateSku(row, productId, ownerPartyId, facilityId, barcode, inventory, colorId, color, dimensionId, dimension, listPrice,
                averageCost);
        if (UtilValidate.isEmpty(skuId)) {
            return false;
        }

        // 7. store prices
        BigDecimal memberPrice = (BigDecimal) getCellContent(cellContents, "Member Price");
        Map<String, Object> results = updateSkuPrice(skuId, ownerPartyId, memberPrice);
        if (ServiceUtil.isError(results)) {
            return false;
        }

        return true;
    }
    /** update sku */
    @Override
    public String updateSku(XSSFRow row, String productId, String ownerPartyId, String facilityId, String barcode, BigDecimal inventory,
                            String colorId, String color, String dimensionId, String dimension, BigDecimal listPrice, BigDecimal averageCost) {
        return "sampleSkuId";
    }
    /** get product id */
    @Override
    public String getProductId(XSSFRow row, String brandId, String modelName, String productName, String productCategoryId, String ownerPartyId,
                               BigDecimal listPrice) {
        return "sampleProductId";
    }

    @Override
    public Object getCellContent(List<Object> cellContents, String colName) {
        if (UtilValidate.isNotEmpty(headerColNames) && headerColNames.contains(colName)) {
            return cellContents.get(headerColNames.indexOf(colName));
        }
        return null;
    }

    @Override
    public String getProductCategoryId(List<Object> cellContents, String ownerPartyId) {
        return "sampleProductCategoryId";
    }

    @Override
    public boolean isFacilityOk(XSSFRow row, String facilityName, String facilityId) {
        InterfaceReport report = getReport();
        Locale locale = getLocale();
        Map<String, String[]> facilities = getFacilities();
        Map<CellReference, String> errorMessages = getErrorMessages();
        if (!facilities.containsKey(facilityId)) {
            if (UtilValidate.isEmpty(facilityId) && facilities.keySet().size() == 1) {
                if (UtilValidate.isEmpty(facilityName)) {
                    return true;
                } else {
                    String theFacilityId = (String) facilities.keySet().toArray()[0];
                    String name = facilities.get(theFacilityId)[0];
                    if (!name.equals(facilityName)) {
                        String errorMessage = UtilProperties.getMessage(RESOURCE, "FacilityNameNotMatchId", new Object[]{theFacilityId, name,
                                facilityName}, locale);
                        report.println();
                        report.print(errorMessage, InterfaceReport.FORMAT_ERROR);
                        XSSFCell cell = row.getCell(0);
                        errorMessages.put(new CellReference(cell), errorMessage);
                        return false;
                    }
                }
            } else {
                String errorMessage = UtilProperties.getMessage(RESOURCE, "FacilityNotBelongToYou", new Object[]{facilityName, facilityId}, locale);
                report.println();
                report.print(errorMessage, InterfaceReport.FORMAT_ERROR);
                XSSFCell cell = row.getCell(1);
                errorMessages.put(new CellReference(cell), errorMessage);
                return false;
            }
        } else {
            String name = facilities.get(facilityId)[0];
            if (!name.equals(facilityName)) {
                String errorMessage = UtilProperties.getMessage(RESOURCE, "FacilityNameNotMatchId", new Object[]{facilityId, name, facilityName},
                        locale);
                report.println();
                report.print(errorMessage, InterfaceReport.FORMAT_ERROR);
                XSSFCell cell = row.getCell(0);
                errorMessages.put(new CellReference(cell), errorMessage);
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isTableHeaderMatched(XSSFSheet sheet) {
        InterfaceReport report = getReport();
        Locale locale = getLocale();
        List<Object[]> columnNames = COL_NAMES_LIST.get(getPricatFileVersion());
        short cols = sheet.getRow(HEADER_ROW_NO).getLastCellNum();
        report.print(UtilProperties.getMessage(RESOURCE, "StartCheckHeaderColNum", new Object[]{getPricatFileVersion()}, locale),
                InterfaceReport.FORMAT_NOTE);
        if (cols != columnNames.size()) {
            report.print(UtilProperties.getMessage(RESOURCE, "HeaderColNumNotMatch", new Object[]{String.valueOf(cols),
                    String.valueOf(columnNames.size())}, locale), InterfaceReport.FORMAT_WARNING);
            if (cols < columnNames.size()) {
                report.println(UtilProperties.getMessage(RESOURCE, "HeaderColNumShortThanRequired",
                        new Object[]{String.valueOf(columnNames.size())}, locale), InterfaceReport.FORMAT_ERROR);
                return false;
            } else {
                report.println(UtilProperties.getMessage(RESOURCE, "UseHeaderColNum", new Object[]{String.valueOf(columnNames.size())}, locale),
                        InterfaceReport.FORMAT_WARNING);
                cols = (short) columnNames.size();
            }
        } else {
            report.println(UtilProperties.getMessage(RESOURCE, "ok", locale), InterfaceReport.FORMAT_OK);
        }

        report.print(UtilProperties.getMessage(RESOURCE, "StartCheckHeaderColLabel", new Object[]{getPricatFileVersion()}, locale),
                InterfaceReport.FORMAT_NOTE);
        boolean foundLabelNotMatch = false;
        for (int i = 0; i < cols; i++) {
            String coltext = sheet.getRow(HEADER_ROW_NO).getCell(i).getStringCellValue().trim();
            headerColNames.add(coltext);
            Object[] versionColumn = columnNames.get(i);
            if (!coltext.equals(versionColumn[0])) {
                report.println(UtilProperties.getMessage(RESOURCE, "HeaderColLabelNotMatch", new Object[]{String.valueOf(HEADER_ROW_NO + 1),
                        String.valueOf(i + 1), coltext, versionColumn[0]}, locale), InterfaceReport.FORMAT_ERROR);
                foundLabelNotMatch = true;
            } else {
                report.print(" " + coltext, InterfaceReport.FORMAT_NOTE);
                if (i < cols - 1) {
                    report.print(",", InterfaceReport.FORMAT_NOTE);
                }
            }
        }
        if (foundLabelNotMatch) {
            report.println();
            return false;
        }
        report.println(" ... " + UtilProperties.getMessage(RESOURCE, "ok", locale), InterfaceReport.FORMAT_OK);
        return true;
    }

    @Override
    public boolean isVersionSupported(XSSFSheet sheet) {
        InterfaceReport report = getReport();
        Locale locale = getLocale();
        String pricatFileVersion = getPricatFileVersion();
        report.print(UtilProperties.getMessage(RESOURCE, "StartCheckPricatVersion", locale), InterfaceReport.FORMAT_NOTE);
        pricatFileVersion = sheet.getRow(2).getCell(0).getStringCellValue().trim();
        if (COL_NAMES_LIST.containsKey(pricatFileVersion)) {
            report.print(" " + pricatFileVersion + " ... ", InterfaceReport.FORMAT_NOTE);
            report.println(UtilProperties.getMessage(RESOURCE, "ok", locale), InterfaceReport.FORMAT_OK);
        } else {
            report.println(UtilProperties.getMessage(RESOURCE, "error", locale), InterfaceReport.FORMAT_ERROR);
            report.println(UtilProperties.getMessage(RESOURCE, "PricatVersionNotSupport", new Object[]{pricatFileVersion}, locale),
                    InterfaceReport.FORMAT_ERROR);
            return false;
        }
        return true;
    }

    @Override
    public boolean containsDataRows(XSSFSheet sheet) {
        int rows = sheet.getPhysicalNumberOfRows();
        if (rows > HEADER_ROW_NO + 1) {
            getReport().println(UtilProperties.getMessage(RESOURCE, "PricatTableRows", new Object[]{String.valueOf(HEADER_ROW_NO + 1),
                    String.valueOf(rows - HEADER_ROW_NO - 1), sheet.getSheetName()}, getLocale()), InterfaceReport.FORMAT_NOTE);
        } else {
            getReport().println(UtilProperties.getMessage(RESOURCE, "PricatNoDataRows", new Object[]{sheet.getSheetName()}, getLocale()),
                    InterfaceReport.FORMAT_ERROR);
            return false;
        }
        return true;
    }

    @Override
    public void parsePricatExcel() {
        parsePricatExcel(true);
    }

    /**
     * Get data by version definition.
     * @param row
     * @param colNames
     * @param size
     * @return
     */
    @Override
    public List<Object> getCellContents(XSSFRow row, List<Object[]> colNames, int size) {
        List<Object> results = new ArrayList<>();
        InterfaceReport report = getReport();
        Locale locale = getLocale();
        Map<CellReference, String> errorMessages = getErrorMessages();
        Map<String, String[]> facilities = getFacilities();
        boolean foundError = false;
        if (isEmptyRow(row, size, true)) {
            return null;
        }

        // check and get data
        for (int i = 0; i < size; i++) {
            XSSFCell cell = null;
            if (row.getPhysicalNumberOfCells() > i) {
                cell = row.getCell(i);
            }
            if (cell == null) {
                if ((Boolean) colNames.get(i)[2] && (facilities.keySet().size() > 1 || (facilities.keySet().size() == 1 && i >= 2))) {
                    report.print(UtilProperties.getMessage(RESOURCE, "ErrorColCannotEmpty", new Object[]{colNames.get(i)[0]}, locale),
                            InterfaceReport.FORMAT_WARNING);
                    cell = row.createCell(i);
                    errorMessages.put(new CellReference(cell), UtilProperties.getMessage(RESOURCE, "ErrorColCannotEmpty",
                            new Object[]{colNames.get(i)[0]}, locale));
                    foundError = true;
                    results.add(null);
                    continue;
                } else {
                    cell = row.createCell(i);
                }
            }
            CellType cellType = cell.getCellType();
            String cellValue = getFormatter().formatCellValue(cell);
            if (UtilValidate.isNotEmpty(cellValue) && UtilValidate.isNotEmpty(cellValue.trim())) {
                if (cellType == CellType.FORMULA) {
                    try {
                        cellValue = BigDecimal.valueOf(cell.getNumericCellValue())
                                .setScale(FinAccountHelper.getDecimals(), FinAccountHelper.getRounding()).toString();
                    } catch (IllegalStateException e) {
                        try {
                            cellValue = cell.getStringCellValue();
                        } catch (IllegalStateException e1) {
                            // do nothing
                        }
                    }
                    report.print(((i == 0) ? "" : ", ") + cellValue, InterfaceReport.FORMAT_NOTE);
                } else {
                    report.print(((i == 0) ? "" : ", ") + cellValue, InterfaceReport.FORMAT_NOTE);
                }
            } else {
                report.print(((i == 0) ? "" : ","), InterfaceReport.FORMAT_NOTE);
            }
            if ((Boolean) colNames.get(i)[2] && UtilValidate.isEmpty(cellValue) && (facilities.keySet().size() > 1
                    || (facilities.keySet().size() == 1 && i >= 2))) {
                report.print(UtilProperties.getMessage(RESOURCE, "ErrorColCannotEmpty", new Object[]{colNames.get(i)[0]}, locale),
                        InterfaceReport.FORMAT_WARNING);
                errorMessages.put(new CellReference(cell), UtilProperties.getMessage(RESOURCE, "ErrorColCannotEmpty",
                        new Object[]{colNames.get(i)[0]}, locale));
                foundError = true;
                results.add(null);
                continue;
            }
            if ((Boolean) colNames.get(i)[2] && cellType != colNames.get(i)[1]) {
                // String warningMessage = "";
                if (colNames.get(i)[1] == CellType.STRING) {
                    if (UtilValidate.isNotEmpty(cellValue) && UtilValidate.isNotEmpty(cellValue.trim())) {
                        results.add(cellValue);
                    } else {
                        results.add(null);
                    }
                } else if (colNames.get(i)[1] == CellType.NUMERIC) {
                    if (cell.getCellType() != CellType.STRING) {
                        cell.setCellType(CellType.STRING);
                    }
                    try {
                        results.add(BigDecimal.valueOf(Double.parseDouble(cell.getStringCellValue()))
                                .setScale(FinAccountHelper.getDecimals(), FinAccountHelper.getRounding()));
                    } catch (NumberFormatException e) {
                        results.add(null);
                        errorMessages.put(new CellReference(cell), UtilProperties.getMessage(RESOURCE, "ErrorParseValueToNumeric", locale));
                    }
                }
            } else {
                if (UtilValidate.isEmpty(cellValue) || UtilValidate.isEmpty(cellValue.trim())) {
                    results.add(null);
                    continue;
                }
                if (colNames.get(i)[1] == CellType.STRING) {
                    if (cell.getCellType() == CellType.STRING) {
                        cellValue = cell.getStringCellValue().trim();
                        results.add(cellValue);
                    } else {
                        results.add(cellValue.trim());
                    }
                } else if (colNames.get(i)[1] == CellType.NUMERIC) {
                    if (cell.getCellType() == CellType.STRING) {
                        try {
                            results.add(BigDecimal.valueOf(Double.valueOf(cell.getStringCellValue())));
                        } catch (NumberFormatException e) {
                            results.add(null);
                            errorMessages.put(new CellReference(cell), UtilProperties.getMessage(RESOURCE, "ErrorParseValueToNumeric", locale));
                        }
                    } else if (cell.getCellType() == CellType.NUMERIC) {
                        try {
                            results.add(BigDecimal.valueOf(cell.getNumericCellValue())
                                    .setScale(FinAccountHelper.getDecimals(), FinAccountHelper.getRounding()));
                        } catch (NumberFormatException e) {
                            results.add(null);
                            errorMessages.put(new CellReference(cell), UtilProperties.getMessage(RESOURCE, "ErrorParseValueToNumeric", locale));
                        }
                    } else {
                        try {
                            results.add(BigDecimal.valueOf(Double.valueOf(cellValue))
                                    .setScale(FinAccountHelper.getDecimals(), FinAccountHelper.getRounding()));
                        } catch (NumberFormatException e) {
                            results.add(null);
                            errorMessages.put(new CellReference(cell), UtilProperties.getMessage(RESOURCE, "ErrorParseValueToNumeric", locale));
                        }
                    }
                }
            }
        }
        if (foundError) {
            return null;
        }
        return results;
    }

    @Override
    protected int getHeaderRowNo() {
        return HEADER_ROW_NO;
    }
}
