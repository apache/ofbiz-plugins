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

context.displayParams = context.displayParams ? context.displayParams : [:]
context.displayParams.POSTAL_ADDRESS   = context.displayParams.POSTAL_ADDRESS   ? context.displayParams.POSTAL_ADDRESS   : context.postalAddr
context.displayParams.EMAIL_ADDRESS    = context.displayParams.EMAIL_ADDRESS    ? context.displayParams.EMAIL_ADDRESS    : context.email
context.displayParams.TELECOM_NUMBER   = context.displayParams.TELECOM_NUMBER   ? context.displayParams.TELECOM_NUMBER   : context.telecom
context.displayParams.IP_ADDRESS       = context.displayParams.IP_ADDRESS       ? context.displayParams.IP_ADDRESS       : context.none
context.displayParams.DOMAIN_NAME      = context.displayParams.DOMAIN_NAME      ? context.displayParams.DOMAIN_NAME      : context.none
context.displayParams.WEB_ADDRESS      = context.displayParams.WEB_ADDRESS      ? context.displayParams.WEB_ADDRESS      : context.none
context.displayParams.INTERNAL_PARTYID = context.displayParams.INTERNAL_PARTYID ? context.displayParams.INTERNAL_PARTYID : context.none
context.displayParams.FTP_ADDRESS      = context.displayParams.FTP_ADDRESS      ? context.displayParams.FTP_ADDRESS      : context.none
context.displayParams.LDAP_ADDRESS     = context.displayParams.LDAP_ADDRESS     ? context.displayParams.LDAP_ADDRESS     : context.none

