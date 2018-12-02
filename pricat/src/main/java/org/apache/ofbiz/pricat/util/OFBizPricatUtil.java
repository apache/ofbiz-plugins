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
    
    public static final String module = OFBizPricatUtil.class.getName();
    
    protected static Method VMLDrawingMethod;
    
    // for POI 4.0.0 and later, this field can be removed
    protected static Method FindCommentShapeMethod;
    
    static {
        Method[] methods = XSSFSheet.class.getDeclaredMethods();
        for (Method method : methods) {
            if (method.getName().equals("getVMLDrawing")) {
                VMLDrawingMethod = method;
                break;
            }
        }
        
        // for POI 4.0.0 and later, this part can be removed
        methods = XSSFVMLDrawing.class.getDeclaredMethods();
        for (Method method : methods) {
            if (method.getName().equals("findCommentShape")) {
                FindCommentShapeMethod = method;
                break;
            }
        }
    }
    
    public static void formatCommentShape(XSSFSheet sheet, CellReference cell) {
        if (VMLDrawingMethod != null && FindCommentShapeMethod != null) {
            try {
                XSSFVMLDrawing vml = (XSSFVMLDrawing) VMLDrawingMethod.invoke(sheet, true);
                /** for POI 4.0 and later, use:
                CTShape ctshape = vml.findCommentShape(cell.getRow(), cell.getCol());
                */
                CTShape ctshape = (CTShape) FindCommentShapeMethod.invoke(vml, cell.getRow(), cell.getCol());
                ctshape.setType("#_x0000_t202");
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                Debug.logError(e, module);;
            }
        }
    }

    public static void formatCommentShape(XSSFSheet sheet, int rowNum, short colNum) {
        if (VMLDrawingMethod != null && FindCommentShapeMethod != null) {
            try {
                XSSFVMLDrawing vml = (XSSFVMLDrawing) VMLDrawingMethod.invoke(sheet, true);
                /** for POI 4.0 and later, use:
                CTShape ctshape = vml.findCommentShape(rowNum, colNum);
                */ 
                CTShape ctshape = (CTShape) FindCommentShapeMethod.invoke(vml, rowNum, colNum);
                ctshape.setType("#_x0000_t202");
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                Debug.logError(e, module);;
            }
        }
    }
}
