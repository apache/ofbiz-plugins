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
package org.apache.ofbiz.ws.rs.model;

import java.util.ArrayList;
import java.util.List;

public class ModelResource {

    private List<ModelOperation> operations;
    private String name;
    private String path;
    private String displayName;
    private String description;
    private boolean enabled;

    /**
     * @return the operation
     */
    public List<ModelOperation> getOperations() {
        return operations == null ? new ArrayList<ModelOperation>() : operations;
    }

    /**
     * @param operation
     * @return
     */
    public ModelResource addOperation(ModelOperation operation) {
        if (this.operations == null) {
            this.operations = new ArrayList<>();
        }
        this.operations.add(operation);
        return this;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @param name
     * @return
     */
    public ModelResource name(String name) {
        this.name = name;
        return this;
    }

    /**
     * @param path
     * @return
     */
    public ModelResource path(String path) {
        this.path = path;
        return this;
    }

    /**
     * @return the path
     */
    public String getPath() {
        return path;
    }

    /**
     * @param path the path to set
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * @return the displayName
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * @param displayName
     * @return
     */
    public ModelResource displayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    /**
     * @param displayName the displayName to set
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @param description
     * @return
     */
    public ModelResource description(String description) {
        this.description = description;
        return this;
    }

    /**
     * @return the enabled
     */
    public Boolean getEnabled() {
        return enabled;
    }

    /**
     * @param enabled the enabled to set
     */
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @param enabled
     * @return
     */
    public ModelResource enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * @return
     */
    @Override
    public String toString() {
        // TODO Auto-generated method stub
        return "name: " + name + ", path: " + path + ", displayName: " + displayName + ", description: " + description
                + ", enabled: " + enabled;
    }

}
