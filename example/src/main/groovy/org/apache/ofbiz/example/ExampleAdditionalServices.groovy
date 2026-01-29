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

Map deleteExample() {
    Map result = success()

    String exampleId = parameters.exampleId

    GenericValue example = delegator.findOne('Example', [exampleId: exampleId], false)

    if (example) {
        // Remove related records using the GenericValue helper
        example.removeRelated('ExampleItem')
        example.removeRelated('ExampleStatus')
        example.removeRelated('ExampleFeatureAppl')

        example.remove()
    }

    result.exampleId = exampleId

    return result
}
