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
package org.apache.ofbiz.example

import org.apache.ofbiz.entity.GenericValue
import org.apache.ofbiz.service.ServiceUtil
import org.apache.ofbiz.service.testtools.OFBizTestCase

class ExampleTests extends OFBizTestCase {

    public ExampleTests(String name) {
        super(name)
    }

    void testUpdateExample() {
        Map<String, Object> serviceCtx = [:]
        serviceCtx.exampleId = 'TestExampleUpdate'
        serviceCtx.exampleName = 'Updated Test Example Name'
        serviceCtx.userLogin = userLogin

        Map<String, Object> serviceResult = dispatcher.runSync('updateExample', serviceCtx)
        assert ServiceUtil.isSuccess(serviceResult)

        GenericValue example = from('Example').where('exampleId', 'TestExampleUpdate').queryOne()
        assert example != null
        assert (Updated Test Example Name == (example.exampleName))
    }

    void testDeleteExample() {
        Map<String, Object> serviceCtx = [:]
        serviceCtx.exampleId = 'TestExampleDelete'
        serviceCtx.userLogin = userLogin

        Map<String, Object> serviceResult = dispatcher.runSync('deleteExample', serviceCtx)
        assert ServiceUtil.isSuccess(serviceResult)

        GenericValue example = from('Example').where('exampleId', 'TestExampleDelete').queryOne()
        assert example == null
    }

}
