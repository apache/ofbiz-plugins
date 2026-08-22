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
package org.apache.ofbiz.scrum.test

import org.apache.ofbiz.service.ServiceUtil
import org.apache.ofbiz.testtools.JunitJupiterTest
import org.apache.ofbiz.testtools.JupiterTestHelper
import org.junit.jupiter.api.Test

@JunitJupiterTest
class TaskTests implements JupiterTestHelper {

    // Migrated from TaskTests.xml:testViewScrumRevisionBadCall
    // Tests that viewScrumRevision service handles a bad repository URL gracefully
    @Test
    void testViewScrumRevisionBadCall() {
        String repository = testParams.repository ?: '--diff+--diff-cmd=/bin/ls'
        String revision = testParams.revision ?: '7'
        Map serviceCtx = [
                repository: repository,
                revision: revision,
                userLogin: userLogin
        ]
        Map serviceResult = dispatcher.runSync('viewScrumRevision', serviceCtx)
        assert ServiceUtil.isSuccess(serviceResult)
        assert !serviceResult.logMessage
    }

}
