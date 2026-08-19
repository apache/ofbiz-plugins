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
import org.apache.ofbiz.service.ServiceValidationException
import org.apache.ofbiz.testtools.JunitJupiterTest
import org.apache.ofbiz.testtools.JupiterTestHelper
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource

/**
 * Jupiter test-cases run through testdef's jupiter-test-suite element (see plugins/example/testdef/tests.xml),
 * side-by-side with org.apache.ofbiz.example.test.ExampleTests in the same test-suite. Runs inside the full
 * ofbiz --test container, so JupiterTestExtension injects the suite's own Delegator/LocalDispatcher.
 * Implementing JupiterTestHelper needs no field declarations, yet Groovy's getter-as-property syntax still
 * exposes them as bare delegator/dispatcher (backed by JupiterTestHelper's getDelegator()/getDispatcher()),
 * so call sites read exactly like the old field-injection style - delegator.findOne(...), dispatcher.runSync(...).
 *
 * <p>Demonstrates a {@code @ParameterizedTest} with real CSV-driven invocations, a
 * {@code @MethodSource}-driven table with multiple typed columns (including an expected-failure
 * row), and a {@code @Disabled} test that shows up as a logged, reportable skip instead of
 * silently disappearing.
 */
@JunitJupiterTest
class ExampleJupiterTests implements JupiterTestHelper {

    @Test
    @Order(1)
    void shouldCreateExample() {
        GenericValue userLogin = delegator.findOne('UserLogin', [userLoginId: 'system'], false)
        String exampleTypeId = testParams.exampleTypeId ?: 'CONTRIVED'
        String exampleName = testParams.exampleName ?: 'Test Example - Integration'
        String statusId = testParams.statusId ?: 'EXST_IN_DESIGN'

        Map<String, Object> result = dispatcher.runSync('createExample', [
                exampleTypeId: exampleTypeId,
                exampleName: exampleName,
                statusId: statusId,
                userLogin: userLogin
        ])
        assert ServiceUtil.isSuccess(result)

        GenericValue example = from('Example').where('exampleId', result.exampleId).queryOne()
        assert example != null
        assert example.exampleTypeId == exampleTypeId
    }

    @ParameterizedTest(name = '[{index}] exampleTypeId={0}')
    @Order(2)
    @CsvSource([
            'CONTRIVED',
            'INSPIRED',
            'REAL_WORLD',
            'MADE_UP'
    ])
    void shouldCreateExampleAcrossTypes(String exampleTypeId) {
        GenericValue userLogin = delegator.findOne('UserLogin', [userLoginId: 'system'], false)
        // exampleTypeId is deliberately left CSV-driven, not testParams-driven - that's the one
        // value this method exists to vary across invocations. statusId isn't varied by the CSV
        // source, so it's the one field here that can take a caller override the same way
        // shouldCreateExample/shouldCreateExampleWithParams do.
        String statusId = testParams.statusId ?: 'EXST_IN_DESIGN'
        Map<String, Object> result = dispatcher.runSync('createExample', [
                exampleTypeId: exampleTypeId,
                exampleName: 'Test Example - ' + exampleTypeId,
                statusId: statusId,
                userLogin: userLogin
        ])
        assert ServiceUtil.isSuccess(result)
    }

    @Disabled('OFBIZ-XXXXX: sample only - demonstrates a documented, reportable skip; not a real defect')
    @Test
    @Order(3)
    void shouldUpdateExampleUnderConcurrentLoad() {
        GenericValue userLogin = delegator.findOne('UserLogin', [userLoginId: 'system'], false)
        Map<String, Object> result = dispatcher.runSync('updateExample', [exampleId: 'TestExampleUpdate', userLogin: userLogin])
        assert ServiceUtil.isSuccess(result)
    }

    @ParameterizedTest(name = '[{index}] {0}')
    @Order(4)
    @MethodSource('exampleCreationCases')
    void shouldCreateExampleAcrossTypesAndStatuses(String label, String exampleTypeId, String statusId,
            String exampleName, boolean expectSuccess) {
        GenericValue userLogin = delegator.findOne('UserLogin', [userLoginId: 'system'], false)
        Map<String, Object> serviceCtx = [
                exampleTypeId: exampleTypeId,
                statusId: statusId,
                exampleName: exampleName,
                userLogin: userLogin
        ]
        if (expectSuccess) {
            Map<String, Object> result = dispatcher.runSync('createExample', serviceCtx)
            assert ServiceUtil.isSuccess(result) : label
            GenericValue example = from('Example').where('exampleId', result.exampleId).queryOne()
            assert example != null
            assert example.exampleTypeId == exampleTypeId
            assert example.statusId == statusId
        } else {
            // A required attribute missing (exampleTypeId/exampleName) fails validation before the
            // service body runs, so runSync throws instead of returning an error result map.
            Assertions.assertThrows(ServiceValidationException) {
                dispatcher.runSync('createExample', serviceCtx)
            }
        }
    }

    @Test
    @Order(5)
    void shouldCreateExampleWithParams() {
        GenericValue userLogin = delegator.findOne('UserLogin', [userLoginId: 'system'], false)
        String exampleTypeId = testParams.exampleTypeId ?: 'CONTRIVED'
        String exampleName = testParams.exampleName ?: 'Test Example - Default'
        String statusId = testParams.statusId ?: 'EXST_IN_DESIGN'

        Map<String, Object> result = dispatcher.runSync('createExample', [
                exampleTypeId: exampleTypeId,
                exampleName: exampleName,
                statusId: statusId,
                userLogin: userLogin
        ])

        assert ServiceUtil.isSuccess(result)
        GenericValue example = from('Example').where('exampleId', result.exampleId).queryOne()
        Assertions.assertEquals(exampleName, example.exampleName)
        Assertions.assertEquals(exampleTypeId, example.exampleTypeId)
        Assertions.assertEquals(statusId, example.statusId)
    }

    @Test
    @Order(6)
    void shouldUpdateExample() {
        GenericValue userLogin = delegator.findOne('UserLogin', [userLoginId: 'system'], false)
        // Initial-state fields reuse the same testParams keys shouldCreateExample uses - both tests
        // create a fresh Example the same way, so sharing key names here is harmless (each test's
        // record is independent). The post-update target fields use their own, distinct keys
        // (updatedExampleName/updatedStatusId) - those can't share names with the initial-state keys
        // above, or one testParams map couldn't set a different "before" and "after" value in the
        // same call.
        String exampleTypeId = testParams.exampleTypeId ?: 'CONTRIVED'
        String exampleName = testParams.exampleName ?: 'Test Example - Before Update'
        String statusId = testParams.statusId ?: 'EXST_IN_DESIGN'

        Map<String, Object> createResult = dispatcher.runSync('createExample', [
                exampleTypeId: exampleTypeId,
                exampleName: exampleName,
                statusId: statusId,
                userLogin: userLogin
        ])
        assert ServiceUtil.isSuccess(createResult)
        String exampleId = createResult.exampleId

        String updatedExampleName = testParams.updatedExampleName ?: 'Test Example - After Update'
        String updatedStatusId = testParams.updatedStatusId ?: 'EXST_APPROVED'

        Map<String, Object> updateResult = dispatcher.runSync('updateExample', [
                exampleId: exampleId,
                exampleName: updatedExampleName,
                statusId: updatedStatusId,
                userLogin: userLogin
        ])
        assert ServiceUtil.isSuccess(updateResult)
        assert updateResult.oldStatusId == statusId

        GenericValue example = from('Example').where('exampleId', exampleId).queryOne()
        assert example != null
        assert example.exampleTypeId == exampleTypeId
        assert example.exampleName == updatedExampleName
        assert example.statusId == updatedStatusId
    }

    @SuppressWarnings('UnusedPrivateMethod')
    private static List<Arguments> exampleCreationCases() {
        [
                Arguments.of('real-world-in-design', 'REAL_WORLD', 'EXST_IN_DESIGN', 'Test Example - Real World', true),
                Arguments.of('made-up-defined', 'MADE_UP', 'EXST_DEFINED', 'Test Example - Made Up', true),
                Arguments.of('contrived-approved', 'CONTRIVED', 'EXST_APPROVED', 'Test Example - Contrived', true),
                Arguments.of('inspired-implemented', 'INSPIRED', 'EXST_IMPLEMENTED', 'Test Example - Inspired', true),
                Arguments.of('missing-example-type-fails', null, 'EXST_IN_DESIGN', 'Test Example - Missing Type', false),
                Arguments.of('missing-example-name-fails', 'REAL_WORLD', 'EXST_IN_DESIGN', null, false),
        ]
    }

}
