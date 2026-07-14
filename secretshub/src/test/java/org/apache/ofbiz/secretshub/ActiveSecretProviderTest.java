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
package org.apache.ofbiz.secretshub;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.ofbiz.envvar.EnvVarSecretProvider;
import org.junit.Test;

/**
 * Tests for {@link ActiveSecretProvider}'s provider-selection/fail-fast logic.
 *
 * <p>Only {@code env-var} is exercised for the "valid name" case: its no-arg constructor
 * does no network I/O, unlike the other six bundled providers (which build a remote SDK
 * client or make a real HTTP call), so it is the only one safe to construct in a plain
 * unit test.</p>
 */
public class ActiveSecretProviderTest {

    @Test
    public void buildThrowsWhenActiveIsBlank() throws Exception {
        try {
            ActiveSecretProvider.build("");
            fail("expected IllegalStateException for blank secret.provider.active");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("blank"));
        }
    }

    @Test
    public void buildThrowsWhenActiveIsUnrecognized() throws Exception {
        try {
            ActiveSecretProvider.build("not-a-real-provider");
            fail("expected IllegalStateException for unrecognized secret.provider.active");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("not-a-real-provider"));
        }
    }

    @Test
    public void buildReturnsMatchingProviderForValidName() throws Exception {
        assertTrue(ActiveSecretProvider.build("env-var") instanceof EnvVarSecretProvider);
    }
}
