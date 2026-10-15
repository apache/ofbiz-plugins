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
package org.apache.ofbiz.content.search;

import java.util.List;

import org.apache.ofbiz.base.container.Container;
import org.apache.ofbiz.base.start.StartupCommand;

/**
 * Stops the document indexers when OFBiz shuts down, before the datasources they read from get closed.
 */
public class DocumentIndexerContainer implements Container {

    private static final long STOP_TIMEOUT_MILLIS = 3000;

    private String name;

    @Override
    public void init(List<StartupCommand> ofbizCommands, String name, String configFile) {
        this.name = name;
    }

    @Override
    public boolean start() {
        return true;
    }

    @Override
    public void stop() {
        DocumentIndexer.stopAll(STOP_TIMEOUT_MILLIS);
    }

    @Override
    public String getName() {
        return name;
    }
}
