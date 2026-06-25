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
package org.apache.ofbiz.awssecrets;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.apache.ofbiz.base.util.GeneralException;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;

public class AwsSecretsManagerProviderTest {

    private static final long ONE_HOUR_MS = 3_600_000L;

    // -- Happy path --

    @Test
    public void getSecretReturnsSecretString() throws GeneralException {
        SecretsManagerClient client = clientReturning("jdbc-password.mydb", "s3cr3t");
        AwsSecretsManagerProvider provider = new AwsSecretsManagerProvider(client, ONE_HOUR_MS, "", "");

        assertEquals("s3cr3t", provider.getSecret("jdbc-password.mydb"));
    }

    @Test
    public void getSecretCachesPreventsSecondAwsCall() throws GeneralException {
        SecretsManagerClient client = clientReturning("jdbc-password.mydb", "s3cr3t");
        AwsSecretsManagerProvider provider = new AwsSecretsManagerProvider(client, ONE_HOUR_MS, "", "");

        provider.getSecret("jdbc-password.mydb");
        provider.getSecret("jdbc-password.mydb"); // second call — should hit cache

        verify(client, times(1)).getSecretValue(any(GetSecretValueRequest.class));
    }

    @Test
    public void getSecretAppliesSecretNamePrefix() throws GeneralException {
        SecretsManagerClient client = mock(SecretsManagerClient.class);
        ArgumentCaptor<GetSecretValueRequest> captor = ArgumentCaptor.forClass(GetSecretValueRequest.class);
        when(client.getSecretValue(captor.capture()))
                .thenReturn(response("val"));

        AwsSecretsManagerProvider provider = new AwsSecretsManagerProvider(client, ONE_HOUR_MS, "myapp/prod/", "");
        provider.getSecret("jdbc-password.ofbiz");

        assertEquals("myapp/prod/jdbc-password.ofbiz", captor.getValue().secretId());
    }

    @Test
    public void getSecretExtractsJsonFieldWhenConfigured() throws GeneralException {
        String json = "{\"username\":\"dbuser\",\"password\":\"dbpass\"}";
        SecretsManagerClient client = clientReturning("jdbc-password.mydb", json);
        AwsSecretsManagerProvider provider = new AwsSecretsManagerProvider(client, ONE_HOUR_MS, "", "password");

        assertEquals("dbpass", provider.getSecret("jdbc-password.mydb"));
    }

    @Test
    public void getSecretUsesRawStringWhenNoJsonFieldConfigured() throws GeneralException {
        String rawPassword = "plain-text-password";
        SecretsManagerClient client = clientReturning("jdbc-password.mydb", rawPassword);
        AwsSecretsManagerProvider provider = new AwsSecretsManagerProvider(client, ONE_HOUR_MS, "", "");

        assertEquals("plain-text-password", provider.getSecret("jdbc-password.mydb"));
    }

    // -- Cache invalidation --

    @Test
    public void invalidateCacheForcesRefetchOnNextCall() throws GeneralException {
        SecretsManagerClient client = clientReturning("jdbc-password.mydb", "val");
        AwsSecretsManagerProvider provider = new AwsSecretsManagerProvider(client, ONE_HOUR_MS, "", "");

        provider.getSecret("jdbc-password.mydb");
        provider.invalidateCache();
        provider.getSecret("jdbc-password.mydb");

        verify(client, times(2)).getSecretValue(any(GetSecretValueRequest.class));
    }

    @Test
    public void getSecretExpiredCacheEntryTriggersRefetch() throws GeneralException {
        SecretsManagerClient client = clientReturning("jdbc-password.mydb", "val");
        // TTL of -1 ms means the entry expires immediately (expiresAt is in the past)
        AwsSecretsManagerProvider provider = new AwsSecretsManagerProvider(client, -1L, "", "");

        provider.getSecret("jdbc-password.mydb");
        provider.getSecret("jdbc-password.mydb"); // cache entry is expired — must re-fetch

        verify(client, times(2)).getSecretValue(any(GetSecretValueRequest.class));
    }

    // -- Key aliasing --

    @Test
    public void getSecretUsesAliasedNameWhenConfigured() throws GeneralException {
        SecretsManagerClient client = mock(SecretsManagerClient.class);
        ArgumentCaptor<GetSecretValueRequest> captor = ArgumentCaptor.forClass(GetSecretValueRequest.class);
        when(client.getSecretValue(captor.capture())).thenReturn(response("val"));

        AwsSecretsManagerProvider provider = new AwsSecretsManagerProvider(client, ONE_HOUR_MS, "", "",
                Map.of("jdbc-password.mysql-ofbiz", "prod/ofbiz/mysql_db_password"));
        provider.getSecret("jdbc-password.mysql-ofbiz");

        assertEquals("prod/ofbiz/mysql_db_password", captor.getValue().secretId());
    }

    @Test
    public void getSecretCachesByLogicalKeyNotAliasedName() throws GeneralException {
        SecretsManagerClient client = mock(SecretsManagerClient.class);
        when(client.getSecretValue(any(GetSecretValueRequest.class))).thenReturn(response("val"));

        AwsSecretsManagerProvider provider = new AwsSecretsManagerProvider(client, ONE_HOUR_MS, "", "",
                Map.of("jdbc-password.mysql-ofbiz", "prod/ofbiz/mysql_db_password"));
        provider.getSecret("jdbc-password.mysql-ofbiz");
        provider.getSecret("jdbc-password.mysql-ofbiz"); // second call — should hit cache

        verify(client, times(1)).getSecretValue(any(GetSecretValueRequest.class));
    }

    @Test
    public void getSecretFallsBackToLogicalKeyWhenNoAliasConfigured() throws GeneralException {
        SecretsManagerClient client = mock(SecretsManagerClient.class);
        ArgumentCaptor<GetSecretValueRequest> captor = ArgumentCaptor.forClass(GetSecretValueRequest.class);
        when(client.getSecretValue(captor.capture())).thenReturn(response("val"));

        AwsSecretsManagerProvider provider = new AwsSecretsManagerProvider(client, ONE_HOUR_MS, "", "",
                Map.of("some.other.key", "some/other/alias"));
        provider.getSecret("jdbc-password.mysql-ofbiz");

        assertEquals("jdbc-password.mysql-ofbiz", captor.getValue().secretId());
    }

    // -- Error handling --

    @Test(expected = GeneralException.class)
    public void getSecretThrowsWhenSecretNotFound() throws GeneralException {
        SecretsManagerClient client = mock(SecretsManagerClient.class);
        when(client.getSecretValue(any(GetSecretValueRequest.class)))
                .thenThrow(ResourceNotFoundException.builder().message("not found").build());

        new AwsSecretsManagerProvider(client, ONE_HOUR_MS, "", "")
                .getSecret("jdbc-password.missing");
    }

    @Test(expected = GeneralException.class)
    public void getSecretThrowsWhenJsonFieldMissing() throws GeneralException {
        String json = "{\"username\":\"dbuser\"}"; // no "password" field
        SecretsManagerClient client = clientReturning("jdbc-password.mydb", json);

        new AwsSecretsManagerProvider(client, ONE_HOUR_MS, "", "password")
                .getSecret("jdbc-password.mydb");
    }

    @Test(expected = GeneralException.class)
    public void getSecretThrowsWhenSecretStringIsNull() throws GeneralException {
        SecretsManagerClient client = mock(SecretsManagerClient.class);
        // secretString() returns null — this is a binary secret
        when(client.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(GetSecretValueResponse.builder().build());

        new AwsSecretsManagerProvider(client, ONE_HOUR_MS, "", "")
                .getSecret("binary-secret");
    }

    // -- helpers --

    private static SecretsManagerClient clientReturning(String secretId, String secretValue) {
        SecretsManagerClient client = mock(SecretsManagerClient.class);
        when(client.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(response(secretValue));
        return client;
    }

    private static GetSecretValueResponse response(String secretValue) {
        return GetSecretValueResponse.builder().secretString(secretValue).build();
    }
}
