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
package org.apache.ofbiz.example.test.jupiter

import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.service.ServiceUtil
import org.apache.ofbiz.testtools.JupiterTestEngine
import org.apache.ofbiz.testtools.JupiterTestHelper
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Jupiter test-cases run through testdef's jupiter-test-suite element (see plugins/example/testdef/tests.xml),
 * side-by-side with org.apache.ofbiz.example.test.ExampleTests in the same test-suite. Runs inside the full
 * ofbiz --test container, so JupiterTestExtension injects the suite's own Delegator/LocalDispatcher.
 * Implementing JupiterTestHelper needs no field declarations, yet Groovy's getter-as-property syntax still
 * exposes them as bare delegator/dispatcher (backed by JupiterTestHelper's getDelegator()/getDispatcher()),
 * so call sites read exactly like the old field-injection style - delegator.findOne(...), dispatcher.runSync(...).
 *
 * <p>Demonstrates a {@code @ParameterizedTest} with real CSV-driven invocations, and a
 * {@code @Disabled} test that shows up as a logged, reportable skip instead of silently disappearing.
 */
@JupiterTestEngine
class ExampleJupiterTests implements JupiterTestHelper {

    @Test
    void shouldCreateExample() {
        GenericValue userLogin = delegator.findOne('UserLogin', [userLoginId: 'system'], false)
        Map<String, Object> result = dispatcher.runSync('createExample', [
                exampleTypeId: 'CONTRIVED',
                exampleName: 'Test Example - Integration',
                statusId: 'EXST_IN_DESIGN',
                userLogin: userLogin
        ])
        assert ServiceUtil.isSuccess(result)

        GenericValue example = from('Example').where('exampleId', result.exampleId).queryOne()
        assert example != null
        assert example.exampleTypeId == 'CONTRIVED'
    }

    @ParameterizedTest(name = '[{index}] exampleTypeId={0}')
    @CsvSource([
            'CONTRIVED',
            'INSPIRED',
            'REAL_WORLD',
            'MADE_UP'
    ])
    void shouldCreateExampleAcrossTypes(String exampleTypeId) {
        GenericValue userLogin = delegator.findOne('UserLogin', [userLoginId: 'system'], false)
        Map<String, Object> result = dispatcher.runSync('createExample', [
                exampleTypeId: exampleTypeId,
                exampleName: 'Test Example - ' + exampleTypeId,
                statusId: 'EXST_IN_DESIGN',
                userLogin: userLogin
        ])
        assert ServiceUtil.isSuccess(result)
    }

    @Disabled('OFBIZ-XXXXX: sample only - demonstrates a documented, reportable skip; not a real defect')
    @Test
    void shouldUpdateExampleUnderConcurrentLoad() {
        GenericValue userLogin = delegator.findOne('UserLogin', [userLoginId: 'system'], false)
        Map<String, Object> result = dispatcher.runSync('updateExample', [exampleId: 'TestExampleUpdate', userLogin: userLogin])
        assert ServiceUtil.isSuccess(result)
    }

}
