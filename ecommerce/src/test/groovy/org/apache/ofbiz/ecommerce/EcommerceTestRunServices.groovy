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
package org.apache.ofbiz.ecommerce

import org.apache.ofbiz.testtools.TestRunServices

// Component-scoped wrapper around TestRunServices' runTestSuite/getTestRunStatus, forced to this
// component ("ecommerce") only - a caller can never trigger or poll another component's tests
// through these two services, regardless of what componentName (if any) they supply.
//
// Copied from plugins/example's ExampleTestRunServices.groovy template - see that file's javadoc
// for the full rationale.
//
// Not a script-level variable: top-level locals in a Groovy service script aren't visible inside
// the def methods below (they belong to the generated run() method, not the class), so the fixed
// component name is a literal in each method instead.

Map runEcommerceTestSuite() {
    return TestRunServices.runScopedTestSuite(dctx, parameters, 'ecommerce')
}

Map getEcommerceTestRunStatus() {
    return TestRunServices.getScopedTestRunStatus(dctx, parameters, 'ecommerce')
}
