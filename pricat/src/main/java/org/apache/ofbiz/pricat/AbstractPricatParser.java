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
package org.apache.ofbiz.pricat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.commons.fileupload.FileItem;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.FileUtil;
import org.apache.ofbiz.base.util.UtilDateTime;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.htmlreport.InterfaceReport;
import org.apache.ofbiz.order.finaccount.FinAccountHelper;
import org.apache.ofbiz.pricat.util.OFBizPricatUtil;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceUtil;
import org.apache.poi.hssf.usermodel.HSSFDataFormatter;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.ClientAnchor.AnchorType;
import org.apache.poi.ss.util.CellAddress;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFAnchor;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.apache.poi.xssf.usermodel.XSSFCreationHelper;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFPictureData;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFShape;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Abstract class of pricat parser.
 */
public abstract class AbstractPricatParser implements InterfacePricatParser {

    private static final String MODULE = AbstractPricatParser.class.getName();
    private LocalDispatcher dispatcher;
    private Delegator delegator;
    private List<FileItem> fileItems;
    private File pricatFile;
    private String userLoginId;
    private GenericValue userLogin;
    private String pricatFileVersion;
    private String currencyId;
    private Map<CellReference, String> errorMessages = new HashMap<>();
    private HSSFDataFormatter formatter = new HSSFDataFormatter();
    private Map<String, String[]> facilities = new HashMap<>();
    private HttpSession session;
    private List<EntityCondition> basicCategoryConds;
    private List<EntityCondition> basicBrandConds;
    private String selectedPricatType = DEFAULT_PRICAT_TYPE;
    private String selectedFacilityId;
    private InterfaceReport report;
    private Locale locale;
    private long sequenceNum = -1L;
    /**
     * Gets pricat file version.
     * @return the pricat file version
     */
    public String getPricatFileVersion() {
        return pricatFileVersion;
    }
    /**
     * Gets delegator.
     * @return the delegator
     */
    public Delegator getDelegator() {
        return delegator;
    }

    /**
     * Sets delegator.
     * @param delegator the delegator
     */
    public void setDelegator(Delegator delegator) {
        this.delegator = delegator;
    }

    /**
     * Gets file items.
     * @return the file items
     */
    public List<FileItem> getFileItems() {
        return fileItems;
    }

    /**
     * Sets file items.
     * @param fileItems the file items
     */
    public void setFileItems(List<FileItem> fileItems) {
        this.fileItems = fileItems;
    }

    /**
     * Gets currency id.
     * @return the currency id
     */
    public String getCurrencyId() {
        return currencyId;
    }

    /**
     * Sets currency id.
     * @param currencyId the currency id
     */
    public void setCurrencyId(String currencyId) {
        this.currencyId = currencyId;
    }

    /**
     * Gets sequence num.
     * @return the sequence num
     */
    public long getSequenceNum() {
        return sequenceNum;
    }

    /**
     * Sets sequence num.
     * @param sequenceNum the sequence num
     */
    public void setSequenceNum(long sequenceNum) {
        this.sequenceNum = sequenceNum;
    }
    /**
     * Gets pricat file.
     * @return the pricat file
     */
    public File getPricatFile() {
        return pricatFile;
    }

    /**
     * Sets pricat file.
     * @param pricatFile the pricat file
     */
    public void setPricatFile(File pricatFile) {
        this.pricatFile = pricatFile;
    }

    /**
     * Gets formatter.
     * @return the formatter
     */
    public HSSFDataFormatter getFormatter() {
        return formatter;
    }

    /**
     * Sets formatter.
     * @param formatter the formatter
     */
    public void setFormatter(HSSFDataFormatter formatter) {
        this.formatter = formatter;
    }

    /**
     * Gets error messages.
     * @return the error messages
     */
    public Map<CellReference, String> getErrorMessages() {
        return errorMessages;
    }

    /**
     * Sets error messages.
     * @param errorMessages the error messages
     */
    public void setErrorMessages(Map<CellReference, String> errorMessages) {
        this.errorMessages = errorMessages;
    }

    /**
     * Gets facilities.
     * @return the facilities
     */
    public Map<String, String[]> getFacilities() {
        return facilities;
    }

    /**
     * Sets facilities.
     * @param facilities the facilities
     */
    public void setFacilities(Map<String, String[]> facilities) {
        this.facilities = facilities;
    }

    /**
     * Gets report.
     * @return the report
     */
    public InterfaceReport getReport() {
        return report;
    }

    /**
     * Sets report.
     * @param report the report
     */
    public void setReport(InterfaceReport report) {
        this.report = report;
    }

    /**
     * Gets locale.
     * @return the locale
     */
    public Locale getLocale() {
        return locale;
    }

    public AbstractPricatParser(LocalDispatcher dispatcher, Delegator delegator, Locale locale, InterfaceReport report,
                                Map<String, String[]> facilities, File pricatFile, GenericValue userLogin) {
        this.dispatcher = dispatcher;
        this.delegator = delegator;
        this.locale = locale;
        this.report = report;
        this.userLogin = userLogin;
        if (UtilValidate.isNotEmpty(userLogin)) {
            this.userLoginId = userLogin.getString("userLoginId");
        }
        this.facilities = facilities;
        this.pricatFile = pricatFile;
        initBasicConds(UtilMisc.toList(userLogin.getString("partyId")));
    }

    /**
     * Check whether a commented file exists.
     * @param request
     * @param sequenceNum
     * @return
     */
    public static boolean isCommentedExcelExists(HttpServletRequest request, Long sequenceNum) {
        GenericValue userLogin = (GenericValue) request.getSession().getAttribute("userLogin");
        if (UtilValidate.isEmpty(sequenceNum) || UtilValidate.isEmpty(userLogin)) {
            Debug.logError("sequenceNum[" + sequenceNum + "] or userLogin is empty", MODULE);
            return false;
        }
        String userLoginId = userLogin.getString("userLoginId");
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        GenericValue historyValue = null;
        try {
            historyValue = EntityQuery.use(delegator).from("ExcelImportHistory").where("userLoginId", userLoginId, "sequenceNum",
                    Long.valueOf(sequenceNum)).queryOne();
        } catch (NumberFormatException | GenericEntityException e) {
            Debug.logError(e.getMessage(), MODULE);
            return false;
        }
        if (UtilValidate.isEmpty(historyValue)) {
            Debug.logError("No ExcelImportHistory value found by sequenceNum[" + sequenceNum + "] and userLoginId[" + userLoginId + "].", MODULE);
            return false;
        }
        File file = FileUtil.getFile(TEMP_FILES_FOLDER + userLoginId + "/" + sequenceNum + ".xlsx");

        return file.exists();
    }

    @Override
    public void writeCommentsToFile(XSSFWorkbook workbook, XSSFSheet sheet) {
        report.println();
        report.print(UtilProperties.getMessage(RESOURCE, "WriteCommentsBackToExcel", locale), InterfaceReport.FORMAT_NOTE);
        FileOutputStream fos = null;
        XSSFCreationHelper factory = workbook.getCreationHelper();
        XSSFFont boldFont = workbook.createFont();
        boldFont.setFontName("Arial");
        boldFont.setBold(true);
        boldFont.setCharSet(134);
        boldFont.setFontHeightInPoints((short) 9);
        XSSFFont plainFont = workbook.createFont();
        plainFont.setFontName("Arial");
        plainFont.setCharSet(134);
        plainFont.setFontHeightInPoints((short) 9);

        XSSFSheet errorSheet = null;
        if (!errorMessages.keySet().isEmpty()) {
            String errorSheetName = UtilDateTime.nowDateString("yyyy-MM-dd HHmm") + " Errors";
            errorSheetName = WorkbookUtil.createSafeSheetName(errorSheetName);
            errorSheet = workbook.createSheet(errorSheetName);
            workbook.setSheetOrder(errorSheetName, 0);
            workbook.setActiveSheet(workbook.getSheetIndex(errorSheetName));
            XSSFDrawing drawingPatriarch = errorSheet.getDrawingPatriarch();
            if (drawingPatriarch == null) {
                drawingPatriarch = errorSheet.createDrawingPatriarch();
            }
            for (int i = 0; i <= getHeaderRowNo(); i++) {
                XSSFRow newRow = errorSheet.createRow(i);
                XSSFRow row = sheet.getRow(i);
                newRow.setHeight(row.getHeight());
                copyRow(row, newRow, factory, drawingPatriarch);
            }
            // copy merged regions
            for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
                CellRangeAddress mergedRegion = sheet.getMergedRegion(i);
                if (mergedRegion.getFirstRow() < getHeaderRowNo()) {
                    errorSheet.addMergedRegion(mergedRegion);
                }
            }
            // copy images
            List<XSSFPictureData> pics = workbook.getAllPictures();
            List<XSSFShape> shapes = sheet.getDrawingPatriarch().getShapes();
            for (int i = 0; i < shapes.size(); i++) {
                XSSFShape shape = shapes.get(i);
                XSSFAnchor anchor = shape.getAnchor();
                if (shape instanceof XSSFPicture && anchor instanceof XSSFClientAnchor) {
                    XSSFPicture pic = (XSSFPicture) shape;
                    XSSFClientAnchor clientAnchor = (XSSFClientAnchor) anchor;
                    if (clientAnchor.getRow1() < getHeaderRowNo()) {
                        for (int j = 0; j < pics.size(); j++) {
                            XSSFPictureData picture = pics.get(j);
                            if (picture.getPackagePart().getPartName().equals(pic.getPictureData().getPackagePart().getPartName())) {
                                drawingPatriarch.createPicture(clientAnchor, j);
                            }
                        }
                    }
                }
            }
        }
        try {
            // set comments in the original sheet
            XSSFDrawing patriarch = sheet.getDrawingPatriarch();
            for (CellReference cell : errorMessages.keySet()) {
                if (cell != null && errorMessages.get(cell) != null) {
                    XSSFComment comment = sheet.getCellComment(new CellAddress(cell.getRow(), cell.getCol()));
                    boolean isNewComment = false;
                    if (comment == null) {
                        XSSFClientAnchor anchor = factory.createClientAnchor();
                        anchor.setDx1(100);
                        anchor.setDx2(100);
                        anchor.setDy1(100);
                        anchor.setDy2(100);
                        anchor.setCol1(cell.getCol());
                        anchor.setCol2(cell.getCol() + 4);
                        anchor.setRow1(cell.getRow());
                        anchor.setRow2(cell.getRow() + 4);
                        anchor.setAnchorType(AnchorType.DONT_MOVE_AND_RESIZE);

                        comment = patriarch.createCellComment(anchor);
                        isNewComment = true;
                    }
                    XSSFRichTextString rts = factory.createRichTextString("OFBiz PriCat:\n");
                    rts.applyFont(boldFont);
                    rts.append(errorMessages.get(cell), plainFont);
                    comment.setString(rts);
                    comment.setAuthor("Apache OFBiz PriCat");
                    if (isNewComment) {
                        sheet.getRow(cell.getRow()).getCell(cell.getCol()).setCellComment(comment);
                        OFBizPricatUtil.formatCommentShape(sheet, cell);
                    }
                }
            }
            // set comments in the new error sheet
            XSSFDrawing errorPatriarch = errorSheet.getDrawingPatriarch();
            int newRowNum = getHeaderRowNo() + 1;
            Map<Integer, Integer> rowMapping = new HashMap<>();
            for (CellReference cell : errorMessages.keySet()) {
                if (cell != null && errorMessages.get(cell) != null) {
                    XSSFRow row = sheet.getRow(cell.getRow());
                    Integer rowNum = row.getRowNum();
                    int errorRow = newRowNum;
                    if (rowMapping.containsKey(rowNum)) {
                        errorRow = rowMapping.get(rowNum);
                    } else {
                        XSSFRow newRow = errorSheet.getRow(errorRow);
                        if (newRow == null) {
                            newRow = errorSheet.createRow(errorRow);
                        }
                        rowMapping.put(rowNum, errorRow);
                        newRow.setHeight(row.getHeight());
                        copyRow(row, newRow, factory, errorPatriarch);
                        newRowNum++;
                    }
                }
            }

            // write to file
            if (sequenceNum > 0L) {
                File commentedExcel = FileUtil.getFile(TEMP_FILES_FOLDER + userLoginId + "/" + sequenceNum + ".xlsx");
                fos = new FileOutputStream(commentedExcel);
                workbook.write(fos);
            } else {
                fos = new FileOutputStream(pricatFile);
                workbook.write(fos);
            }
            fos.flush();
            fos.close();
            workbook.close();
        } catch (IOException e) {
            report.println(e);
            Debug.logError(e, MODULE);
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    Debug.logError(e, MODULE);
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
        report.println(UtilProperties.getMessage(RESOURCE, "ok", locale), InterfaceReport.FORMAT_OK);
        report.println();
    }

    private void copyRow(XSSFRow sourceRow, XSSFRow targetRow, XSSFCreationHelper factory, XSSFDrawing patriarch) {
        for (int j = 0; j < sourceRow.getPhysicalNumberOfCells(); j++) {
            XSSFCell cell = sourceRow.getCell(j);
            if (cell != null) {
                XSSFCell newCell = targetRow.createCell(j);
                CellType cellType = cell.getCellType();
                newCell.setCellType(cellType);
                switch (cellType) {
                case BOOLEAN:
                    newCell.setCellValue(cell.getBooleanCellValue());
                    break;
                case ERROR:
                    newCell.setCellErrorValue(cell.getErrorCellValue());
                    break;
                case FORMULA:
                    newCell.setCellFormula(cell.getCellFormula());
                    break;
                case NUMERIC:
                    newCell.setCellValue(cell.getNumericCellValue());
                    break;
                case STRING:
                    newCell.setCellValue(cell.getRichStringCellValue());
                    break;
                default:
                    newCell.setCellValue(formatter.formatCellValue(cell));
                }
                if (cell.getCellComment() != null) {
                    XSSFClientAnchor anchor = factory.createClientAnchor();
                    anchor.setDx1(100);
                    anchor.setDx2(100);
                    anchor.setDy1(100);
                    anchor.setDy2(100);
                    anchor.setCol1(newCell.getColumnIndex());
                    anchor.setCol2(newCell.getColumnIndex() + 4);
                    anchor.setRow1(newCell.getRowIndex());
                    anchor.setRow2(newCell.getRowIndex() + 4);
                    anchor.setAnchorType(AnchorType.DONT_MOVE_AND_RESIZE);

                    XSSFComment comment = patriarch.createCellComment(anchor);
                    comment.setString(cell.getCellComment().getString());
                    newCell.setCellComment(comment);
                }
                newCell.setCellStyle(cell.getCellStyle());
                newCell.getSheet().setColumnWidth(newCell.getColumnIndex(), cell.getSheet().getColumnWidth(cell.getColumnIndex()));
            }
        }
    }

    @Override
    public void initBasicConds(List<String> orgPartyIds) {
        basicCategoryConds = new ArrayList<>();
        basicCategoryConds.add(EntityCondition.makeCondition("isPublic", "N"));
        //basicCategoryConds.add(EntityCondition.makeCondition("isDefault", "Y"));
        basicBrandConds = new ArrayList<>();
        basicBrandConds.add(EntityCondition.makeCondition("isPublic", "N"));
        basicBrandConds.add(EntityCondition.makeCondition("productFeatureTypeId", "BRAND"));
        List<EntityCondition> partyIdConds = new ArrayList<>();
        for (String orgPartyId : orgPartyIds) {
            partyIdConds.add(EntityCondition.makeCondition("ownerPartyId", orgPartyId));
        }
        if (UtilValidate.isNotEmpty(partyIdConds)) {
            basicCategoryConds.add(EntityCondition.makeCondition(partyIdConds, EntityOperator.OR));
            basicBrandConds.add(EntityCondition.makeCondition(partyIdConds, EntityOperator.OR));
        }
    }

    @Override
    public Map<String, Object> updateSkuPrice(String skuId, String ownerPartyId, BigDecimal memberPrice) {
        return ServiceUtil.returnSuccess();
    }

    @Override
    public Map<String, Object> updateColorAndDimension(String productId, String ownerPartyId, String color, String dimension) {
        Map<String, Object> results = ServiceUtil.returnSuccess();
        results.put("colorId", "sampleColorId");
        results.put("dimensionId", "sampleDimensionId");
        return results;
    }

    @Override
    public Map<String, Object> getDimensionIds(String productId, String ownerPartyId, String dimension) {
        Map<String, Object> results = ServiceUtil.returnSuccess();
        results.put("dimensionId", "sampleDimensionId");
        return results;
    }

    @Override
    public Map<String, Object> getColorIds(String productId, String ownerPartyId, String color) {
        Map<String, Object> results = ServiceUtil.returnSuccess();
        results.put("foundColor", Boolean.TRUE);
        results.put("colorId", "sampleColorId");
        return results;
    }

    @Override
    public String getBrandId(String brandName, String ownerPartyId) {
        return "sampleBrandId";
    }

    @Override
    public boolean isNumOfSheetsOK(XSSFWorkbook workbook) {
        report.print(UtilProperties.getMessage(RESOURCE, "CheckPricatHasSheet", locale), InterfaceReport.FORMAT_NOTE);
        int sheets = workbook.getNumberOfSheets();
        if (sheets < 1) {
            report.println(UtilProperties.getMessage(RESOURCE, "PricatTableNoSheet", locale), InterfaceReport.FORMAT_ERROR);
            return false;
        } else if (sheets >= 1) {
            report.println(UtilProperties.getMessage(RESOURCE, "ok", locale), InterfaceReport.FORMAT_OK);
            report.println(UtilProperties.getMessage(RESOURCE, "PricatTableOnlyParse1stSheet", locale), InterfaceReport.FORMAT_WARNING);
        }
        return true;
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
        boolean foundError = false;
        if (isEmptyRow(row, size, true)) {
            return null;
        }
        for (int i = 0; i < size; i++) {
            XSSFCell cell = null;
            if (row.getPhysicalNumberOfCells() > i) {
                cell = row.getCell(i);
            }
            if (cell == null) {
                if ((Boolean) colNames.get(i)[2]) {
                    report.print(UtilProperties.getMessage(RESOURCE, "ErrorColCannotEmpty", new Object[]{colNames.get(i)[0]}, locale),
                            InterfaceReport.FORMAT_WARNING);
                    errorMessages.put(new CellReference(cell), UtilProperties.getMessage(RESOURCE, "ErrorColCannotEmpty",
                            new Object[]{colNames.get(i)[0]}, locale));
                    foundError = true;
                    continue;
                } else {
                    cell = row.createCell(i);
                }
            }
            CellType cellType = cell.getCellType();
            String cellValue = formatter.formatCellValue(cell);
            if (UtilValidate.isNotEmpty(cellValue)) {
                if (cellType == CellType.FORMULA) {
                    cellValue = BigDecimal.valueOf(cell.getNumericCellValue()).setScale(FinAccountHelper.getDecimals(),
                            FinAccountHelper.getRounding()).toString();
                    report.print(((i == 0) ? "" : ", ") + cellValue, InterfaceReport.FORMAT_NOTE);
                } else {
                    report.print(((i == 0) ? "" : ", ") + cellValue, InterfaceReport.FORMAT_NOTE);
                }
            } else {
                report.print(((i == 0) ? "" : ","), InterfaceReport.FORMAT_NOTE);
            }
            if ((Boolean) colNames.get(i)[2] && UtilValidate.isEmpty(cellValue)) {
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
                    results.add(cellValue);
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
                if (UtilValidate.isEmpty(cellValue)) {
                    results.add(null);
                    continue;
                }
                if (colNames.get(i)[1] == CellType.STRING) {
                    if (cell.getCellType() == CellType.STRING) {
                        results.add(cell.getStringCellValue());
                    } else {
                        results.add(cellValue);
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
    public void setFacilityId(String selectedFacilityId) {
        this.selectedFacilityId = selectedFacilityId;
    }

    /**
     * Is empty row boolean.
     * @param row the row
     * @param size the size
     * @param display the display
     * @return the boolean
     */
    protected boolean isEmptyRow(XSSFRow row, int size, boolean display) {
        // check whether this row is empty
        if (UtilValidate.isEmpty(row)) {
            report.print(UtilProperties.getMessage(RESOURCE, "ExcelEmptyRow", locale), InterfaceReport.FORMAT_NOTE);
            return true;
        }
        boolean isEmptyRow = true;
        int physicalNumberOfCells = row.getPhysicalNumberOfCells();
        int i = 0;
        for (; i < size; i++) {
            XSSFCell cell = null;
            if (physicalNumberOfCells > i) {
                cell = row.getCell(i);
            }
            if (cell != null && UtilValidate.isNotEmpty(formatter.formatCellValue(cell))
                    && UtilValidate.isNotEmpty(formatter.formatCellValue(cell).trim())) {
                isEmptyRow = false;
                break;
            }
        }
        if (isEmptyRow) {
            if (display) {
                report.print(UtilProperties.getMessage(RESOURCE, "ExcelEmptyRow", locale), InterfaceReport.FORMAT_NOTE);
            }
            return true;
        } else if (!isEmptyRow && i > size) {
            if (display) {
                report.print(UtilProperties.getMessage(RESOURCE, "IgnoreDataOutOfRange", locale), InterfaceReport.FORMAT_NOTE);
            }
            return true;
        }
        return isEmptyRow;
    }

    protected abstract int getHeaderRowNo();

    @Override
    public synchronized void endExcelImportHistory(String logFileName, String thruReasonId) {
        Thread currentThread = Thread.currentThread();
        String threadName = null;
        if (currentThread instanceof PricatParseExcelHtmlThread) {
            threadName = ((PricatParseExcelHtmlThread) currentThread).getUUID().toString();
        }
        if (UtilValidate.isEmpty(threadName)) {
            return;
        }
        try {
            GenericValue historyValue = null;
            if (sequenceNum < 1L) {
                historyValue = EntityQuery.use(delegator).from("ExcelImportHistory").where("userLoginId", userLoginId, "logFileName", logFileName)
                        .orderBy("sequenceNum DESC").filterByDate().queryFirst();
            } else {
                historyValue = EntityQuery.use(delegator).from("ExcelImportHistory").where("userLoginId", userLoginId, "sequenceNum", sequenceNum)
                        .queryOne();
            }
            Timestamp now = UtilDateTime.nowTimestamp();
            if (UtilValidate.isEmpty(historyValue)) {
                historyValue = delegator.makeValue("ExcelImportHistory", UtilMisc.toMap("sequenceNum", sequenceNum, "userLoginId", userLoginId,
                        "fileName", pricatFile.getName(), "statusId", "EXCEL_IMPORTED", "fromDate", now,
                        "thruDate", now, "threadName", threadName, "logFileName", logFileName));
            } else {
                historyValue.set("statusId", "EXCEL_IMPORTED");
                historyValue.set("thruDate", now);
                if (pricatFile != null && pricatFile.exists()) {
                    historyValue.set("fileName", pricatFile.getName());
                }
                historyValue.set("thruReasonId", thruReasonId);
            }
            delegator.createOrStore(historyValue);
        } catch (GenericEntityException e) {
            Debug.logError(e, MODULE);
        }
    }

    @Override
    public boolean hasErrorMessages() {
        return !errorMessages.keySet().isEmpty();
    }

    /**
     * Cleanup log and commented excel.
     */
    protected void cleanupLogAndCommentedExcel() {
        try {
            report.print(UtilProperties.getMessage(RESOURCE, "CLEANUP_LOGANDEXCEL_BEGIN", locale), InterfaceReport.FORMAT_DEFAULT);
            List<GenericValue> historyValues = EntityQuery.use(delegator).from("ExcelImportHistory").where("userLoginId", userLoginId).orderBy(
                    "sequenceNum DESC").queryList();
            if (UtilValidate.isEmpty(historyValues) || historyValues.size() <= HISTORY_MAX_FILENUMBER) {
                report.print(UtilProperties.getMessage(RESOURCE, "HistoryLessThan", new Object[]{String.valueOf(HISTORY_MAX_FILENUMBER)}, locale),
                        InterfaceReport.FORMAT_NOTE);
                report.println(" ... " + UtilProperties.getMessage(RESOURCE, "skipped", locale), InterfaceReport.FORMAT_NOTE);
            } else {
                report.print(" ... " + UtilProperties.getMessage(RESOURCE, "HistoryEntryToRemove",
                        new Object[]{historyValues.size() - HISTORY_MAX_FILENUMBER}, locale), InterfaceReport.FORMAT_NOTE);
                List<GenericValue> valuesToRemove = new ArrayList<>();
                for (int i = HISTORY_MAX_FILENUMBER; i < historyValues.size(); i++) {
                    GenericValue historyValue = historyValues.get(i);
                    valuesToRemove.add(historyValue);
                    File excelFile = FileUtil.getFile(TEMP_FILES_FOLDER + userLoginId + "/" + historyValue.getLong("sequenceNum") + ".xlsx");
                    if (excelFile.exists()) {
                        try {
                            excelFile.delete();
                        } catch (SecurityException e) {
                            Debug.logError(e.getMessage(), MODULE);
                            report.print(e.getMessage(), InterfaceReport.FORMAT_ERROR);
                        }
                    }
                    File logFile = FileUtil.getFile(TEMP_FILES_FOLDER + userLoginId + "/" + historyValue.getLong("sequenceNum") + ".log");
                    if (logFile.exists()) {
                        try {
                            logFile.delete();
                        } catch (SecurityException e) {
                            Debug.logError(e.getMessage(), MODULE);
                            report.print(e.getMessage(), InterfaceReport.FORMAT_ERROR);
                        }
                    }
                }
                delegator.removeAll(valuesToRemove);
                report.println(" ... " + UtilProperties.getMessage(RESOURCE, "ok", locale), InterfaceReport.FORMAT_OK);
            }
            report.println();
        } catch (GenericEntityException e) {
            Debug.logError(e.getMessage(), MODULE);
        }
    }
}
