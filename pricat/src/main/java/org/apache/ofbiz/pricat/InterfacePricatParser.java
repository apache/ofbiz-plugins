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

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.entity.transaction.GenericTransactionException;

/**
 * Interface of pricat parser.
 */
public interface InterfacePricatParser {
    String PARSE_EXCEL = "parse_excel";
    String CONFIRM = "confirm_action";
    String[] messageLabels = new String[] {"FORMAT_DEFAULT", "FORMAT_WARNING", "FORMAT_HEADLINE", "FORMAT_NOTE", "FORMAT_OK", "FORMAT_ERROR", "FORMAT_THROWABLE"};
    List<String> messages = Collections.unmodifiableList(Arrays.asList(messageLabels));
    String tempFilesFolder = "runtime/pricat/";
    String FileDateTimePattern = "yyyyMMddHHmmss";
    String defaultColorName = "DefaultColor";
    String defaultDimensionName = "DefaultDimension";
    String defaultCategoryName = "DefaultCategory";
    String EXCEL_TEMPLATE_TYPE = "excelTemplateType";
    String FACILITY_ID = "facilityId";
    String RESOURCE = "PricatUiLabels";
    String PRICAT_FILE = "__PRICAT_FILE__";
    String DEFAULT_PRICAT_TYPE = "ApacheOFBiz";
    Map<String, String> PricatTypeLabels = UtilMisc.toMap(DEFAULT_PRICAT_TYPE, "ApacheOFBizPricatTemplate", "SamplePricat", "SamplePricatTemplate");
    int HISTORY_MAX_FILENUMBER = UtilProperties.getPropertyAsInteger("pricat.properties", "pricat.history.max.filenumber", 20);
    void parsePricatExcel();
    void writeCommentsToFile(XSSFWorkbook workbook, XSSFSheet sheet);
    void initBasicConds(List<String> orgPartyIds);
    boolean existsCurrencyId(XSSFSheet sheet);
    void parseRowByRow(XSSFSheet sheet);
    boolean parseCellContentsAndStore(XSSFRow row, List<Object> cellContents) throws GenericTransactionException;
    Map<String, Object> updateSkuPrice(String skuId, String ownerPartyId, BigDecimal memberPrice);

    String updateSku(XSSFRow row, String productId, String ownerPartyId, String facilityId, String barcode, BigDecimal inventory,
            String colorId, String color, String dimensionId, String dimension, BigDecimal listPrice, BigDecimal averageCost);

    Map<String, Object> updateColorAndDimension(String productId, String ownerPartyId, String color, String dimension);

    Map<String, Object> getDimensionIds(String productId, String ownerPartyId, String dimension);

    Map<String, Object> getColorIds(String productId, String ownerPartyId, String color);

    String getProductId(XSSFRow row, String brandId, String modelName, String productName, String productCategoryId, String ownerPartyId, BigDecimal listPrice);

    String getBrandId(String brandName, String ownerPartyId);

    Object getCellContent(List<Object> cellContents, String colName);

    String getProductCategoryId(List<Object> cellContents, String ownerPartyId);

    boolean isFacilityOk(XSSFRow row, String facilityName, String facilityId);

    List<Object> getCellContents(XSSFRow row, List<Object[]> colNames, int size);

    boolean isTableHeaderMatched(XSSFSheet sheet);

    boolean isVersionSupported(XSSFSheet sheet);

    boolean containsDataRows(XSSFSheet sheet);

    boolean isNumOfSheetsOK(XSSFWorkbook workbook);

    void setFacilityId(String selectedFacilityId);

    void endExcelImportHistory(String logFileName, String thruReasonId);

    boolean hasErrorMessages();
}
