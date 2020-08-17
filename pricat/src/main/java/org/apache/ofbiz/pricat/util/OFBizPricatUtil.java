/*
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
 */
package org.apache.ofbiz.pricat.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.apache.ofbiz.base.util.Debug;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFVMLDrawing;

import com.microsoft.schemas.vml.CTShape;

public final class OFBizPricatUtil {
    private OFBizPricatUtil() { }

    private static final String MODULE = OFBizPricatUtil.class.getName();
    private static Method vmlDrawingMethod;
    // for POI 4.0.0 and later, this field can be removed
    private static Method findCommentShapeMethod;
    static {
        Method[] methods = XSSFSheet.class.getDeclaredMethods();
        for (Method method : methods) {
            if (method.getName().equals("getVMLDrawing")) {
                vmlDrawingMethod = method;
                break;
            }
        }
        // for POI 4.0.0 and later, this part can be removed
        methods = XSSFVMLDrawing.class.getDeclaredMethods();
        for (Method method : methods) {
            if (method.getName().equals("findCommentShape")) {
                findCommentShapeMethod = method;
                break;
            }
        }
    }
    public static void formatCommentShape(XSSFSheet sheet, CellReference cell) {
        if (vmlDrawingMethod != null && findCommentShapeMethod != null) {
            try {
                XSSFVMLDrawing vml = (XSSFVMLDrawing) vmlDrawingMethod.invoke(sheet, true);
                /** for POI 4.0 and later, use:
                CTShape ctshape = vml.findCommentShape(cell.getRow(), cell.getCol());
                */
                CTShape ctshape = (CTShape) findCommentShapeMethod.invoke(vml, cell.getRow(), cell.getCol());
                ctshape.setType("#_x0000_t202");
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                Debug.logError(e, MODULE);
            }
        }
    }
    public static void formatCommentShape(XSSFSheet sheet, int rowNum, short colNum) {
        if (vmlDrawingMethod != null && findCommentShapeMethod != null) {
            try {
                XSSFVMLDrawing vml = (XSSFVMLDrawing) vmlDrawingMethod.invoke(sheet, true);
                /** for POI 4.0 and later, use:
                CTShape ctshape = vml.findCommentShape(rowNum, colNum);
                */
                CTShape ctshape = (CTShape) findCommentShapeMethod.invoke(vml, rowNum, colNum);
                ctshape.setType("#_x0000_t202");
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                Debug.logError(e, MODULE);
            }
        }
    }
}
