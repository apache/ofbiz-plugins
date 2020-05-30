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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.apache.ofbiz.base.util.UtilGenerics;
import org.apache.ofbiz.base.util.UtilMisc;

public class FrontJsOutput {
    private static final String MODULE = FrontJsOutput.class.getName();


    Map<String, Object> output = new HashMap<>();
    private List<Map<String, Object>> viewScreen;
    private Map<String, Object> viewEntities = new HashMap<>();

    // stack of screen, a screen is a list of element (see list of renderer method (screen, form, menu,..), each one is a element)
    //  each element has 3 fields
    //  - "name" the element name (ex: Label, ScreenletBegin, DisplayField, HyperlinkField, ...). To have the complete list of screen element search all call to "output.*Screen("
    //  - "attributes" a map with the attributes
    //  - "stPointer" only there when it's a field with a link to the store in the FrontJs application, it's a map which data to be able to create the link
    //  - and a fourth for temporary Debug purpose "dataDebug"
    private Stack<List<Map<String, Object>>> screensStack;

    // stack of entity which is currently used for store records
    // contain 3 field
    // - "primaryKeys"  list of fieldName which are pk in entityName. Used to build pkValue for storePointer
    // - "list" list of records (each record contain its stId)
    // - "entityName"
    private Stack<Map<String, Object>> entitiesStack;

    // use to build record, a map with content of fields (fieldName : value) for a row or a single form
    //   similar as a GenericValue but with fields define in the form
    // it's a stack because in a field I can have a sub-list about an other entities
    private Stack<Map<String, Object>> recordsStack;

    FrontJsOutput(String name) {
        viewScreen = new ArrayList<>();
        output.put("viewScreenName", name);
        output.put("viewScreen", viewScreen);
        output.put("viewEntities", viewEntities);
        screensStack = new Stack<>();
        entitiesStack = new Stack<>();
        recordsStack = new Stack<>();
        screensStack.push(viewScreen);
    }


    /**
     * Add a new screenElement into the children of the top screen of the stack. <br/>
     * Should be used when element is not a field otherwise used {@link FrontJsOutput#putScreen(String, Map, String, String)}.
     *
     * @param name       : the element Name (see list of renderer method (screen, form, menu,..), each one is a element)
     * @param attributes : a map with all the attributes elements
     */
    void putScreen(String name, Map<String, Object> attributes) {
        putScreen(name, attributes, null, null);
    }

    /**
     * Add a new screenElement into the children of the top screen of the stack. <br/>
     * Should be used with fieldName!=null only when element is a field.<br/>
     * Add (fieldName : value) in the recordStack.
     *
     * @param name       : the element Name (see list of renderer method (screen, form, menu,..), each one is a element)
     * @param attributes : a map with all the attributes elements
     * @param fieldName  : if element is a field, its name otherwise should be null
     * @param fieldValue : if element is a field, its value otherwise should be null
     */
    void putScreen(String name, Map<String, Object> attributes, String fieldName, String fieldValue) {
        Map<String, Object> screen = new HashMap<>();
        screen.put("attributes", attributes);
        screen.put("name", name);
        screensStack.peek().add(screen);
        if (fieldName != null && !recordsStack.empty()) {
            Map<String, String> stPointer = new HashMap<>();
            stPointer.put("stEntityName", (String) this.entitiesStack.peek().get("entityName"));
            stPointer.put("id", (String) recordsStack.peek().get("stId"));
            stPointer.put("field", fieldName);
            screen.put("stPointer", stPointer);
            this.putRecord(fieldName, fieldValue);
            // for debug purpose only, should be remove when the old code with PUT_RECORD will be removed
            screen.put("dataDebug", UtilMisc.toMap("action", "PUT_RECORD", "key", fieldName, "value", fieldValue));
        }
    }

    /**
     * Read and return the first screenElement from screensStack.peek and remove it from screensStack.peek.
     * <br/>It's used for screenlet to be able to put tabMenu or navMenu as attribute and not as children.
     *    For this use case there will be only 1 screen in list when this method will be call.
     * <br/>If new use case appear, this method will work with last screen of the list
     * @return the first screenElement from screensStack.peek and remove it from screensStack.peek
     */
    Map<String, Object> getAndRemoveScreen() {
        Map<String, Object> screen = screensStack.peek().get(0);
        screensStack.peek().remove(0);
        return screen;
    }

    /**
     * Push a new screenElement into the children of the top screen of the stack. <br/>
     * Should be used only when element is not a field.
     *
     * @param name       : the element Name (see list of renderer method (screen, form, menu,..), each one is a element)
     * @param attributes : a map with all the attributes elements
     */
    void pushScreen(String name, Map<String, Object> attributes) {
        pushScreen(name, attributes, null, null);
    }

    /**
     * Push a new screenElement into the children of the top screen of the stack. <br/>
     * Should be used only when element is not a field.
     *
     * @param name       : the element Name (see list of renderer method (screen, form, menu,..), each one is a element)
     * @param attributes : a map with all the attributes elements
     * @param action     : a string determining action to perform on data (entityStack) can be one of [null, "PUSH_ENTITY", "NEW_RECORD"]
     * @param context    : a map with the data relative to the action to perform
     */
    void pushScreen(String name, Map<String, Object> attributes, String action, Map<String, Object> context) {
        Map<String, Object> screen = new HashMap<>();
        // there is no Open/Begin or Close  screenElement only screenElement with (or without) children
        screen.put("name", name.replace("Open", "").replace("Begin", ""));
        screen.put("attributes", attributes);

        List<Map<String, Object>> children = new ArrayList<Map<String, Object>>();
        screen.put("children", children);
        screensStack.peek().add(screen);
        screensStack.push(children);
        if (action != null) {
            if (action.equals("PUSH_ENTITY")) {
                // TODO
                this.pushEntity((String) context.get("entityName"), UtilGenerics.cast(context.get("primaryKeys")));
            }
            if (action.equals("NEW_RECORD")) {
                this.newRecord(context);
            }
        }
    }

    /**
     * Pop the top screenElement the current screen stack. <br/>
     * Should be used only when element is not a field.
     *
     * @param name       : the element Name (see list of renderer method (screen, form, menu,..), each one is a element)
     */
    void popScreen(String name) {popScreen(name, null);}
    /**
     * Pop the top screenElement the current screen stack and perform some data actions. <br/>
     * Should be used only when element is not a field.
     *
     * @param name   : the element Name (see list of renderer method (screen, form, menu,..), each one is a element)
     * @param action : a string with the action to perform on data (entityStack) can be one of [null, "POP_ENTITY", "STORE_RECORD"]
     */
    void popScreen(String name, String action) {
        screensStack.pop();
        if (action != null) {
            if (action.equals("STORE_RECORD")) {
                this.storeRecord();
            }
            if (action.equals("POP_ENTITY")) {
                this.popEntity();
            }
        }
    }

    /**
     * Push entity on the stack (all record will be relative to this entity until another one was push or this one was pop). <br/>
     * Should be called by pushScreen only.
     *
     * @param entityName  : the name of the entity to push
     * @param primaryKeys : the list of entity's primary key
     */
    private void pushEntity(String entityName, List<String> primaryKeys) {
        Map<String, Object> entity;
        if (!viewEntities.containsKey(entityName)) {
            entity = new HashMap<>();
            entity.put("primaryKeys", primaryKeys);
            entity.put("list", new ArrayList<Map<String, Object>>());
            entity.put("entityName", entityName);
            viewEntities.put(entityName, entity);
        } else {
            entity = UtilGenerics.cast(viewEntities.get(entityName));
        }
        entitiesStack.push(entity);
    }

    private void popEntity() {
        entitiesStack.pop();
    }

    /**
     * Push a new record on the stack and generate the primary key to use in front. <br/>
     * Should be called by pushScreen only.
     *
     * @param context       : the map of the current record (only used to get the primary key, it cant't be null)
     */
    private void newRecord(Map<String, Object> context) {
        if (!entitiesStack.empty()) {
            // currentRecord
            Map<String, Object> record = new HashMap<>();
            // build stPointerId
            List<String> pkList = UtilGenerics.cast(this.entitiesStack.peek().get("primaryKeys"));
            int i = 0;
            String pkey = "";
            do {
                pkey += context.get(pkList.get(i));
                i++;
            } while (i < pkList.size());
            record.put("stId", pkey);
            recordsStack.push(record);

            List<Map<String, Object>> entitiesStackPeekList = UtilGenerics.cast(entitiesStack.peek().get("list"));
            entitiesStackPeekList.add(record);
        }
    }

    private void storeRecord() {
        if (!recordsStack.empty()) {
            recordsStack.pop();
        }
    }

    private void putRecord(String fieldName, String value) {
        if (!recordsStack.empty()) {
            recordsStack.peek().put(fieldName, value);
        }
    }

    public Map<String, Object> output() {
        return this.output;
    }

    // TODO check if this method is used, seem not because "recordPointer" is not used in *.vue files
    public Map<String, Object> getRecordPointer(Map<String, Object> context) {
        if (!this.entitiesStack.empty()) {
            Map<String, Object> data = new HashMap<>();
            String entityName = (String) this.entitiesStack.peek().get("entityName");
            data.put("entity", entityName);
            List<String> primaryKeys = UtilGenerics.cast(this.entitiesStack.peek().get("primaryKeys"));
            data.put("id", context.get(primaryKeys.get(0)));
            return data;
        } else {
            return null;
        }
    }
}