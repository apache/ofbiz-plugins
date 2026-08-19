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
package org.apache.ofbiz.example;

import java.util.Map;

import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.testtools.TestRunServices;

/**
 * Component-scoped wrapper around {@code TestRunServices}' runTestSuite/getTestRunStatus, forced to
 * this component ("example") only - a caller can never trigger or poll another component's tests
 * through these two services, regardless of what componentName (if any) they supply. See
 * plugins/supporting-docs/specs/2026-08-19-component-scoped-test-run-rest-design.md for the full
 * design and why this lives here rather than in a shared/generic form.
 *
 * <p>Meant to double as the copy-paste template for any other component adopting the same pattern:
 * copy this class, rename it and its two methods, and change {@code COMPONENT_NAME} - no changes to
 * {@code TestRunServices} itself are needed.
 */
public final class ExampleTestRunServices {

    private static final String COMPONENT_NAME = "example";

    private ExampleTestRunServices() {
    }

    public static Map<String, Object> runExampleTestSuite(DispatchContext dctx, Map<String, ?> context) {
        return TestRunServices.runScopedTestSuite(dctx, context, COMPONENT_NAME);
    }

    public static Map<String, Object> getExampleTestRunStatus(DispatchContext dctx, Map<String, ?> context) {
        return TestRunServices.getScopedTestRunStatus(dctx, context, COMPONENT_NAME);
    }
}
