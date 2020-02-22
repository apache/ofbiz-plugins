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

import org.apache.ofbiz.base.util.Debug;

// Dimension table(s) related functions

def loadCountryDimension(){
    queryListIterator = from("Geo").where("geoTypeId","COUNTRY").queryIterator();
    while(country = queryListIterator.next()){
        countryId = country.geoId;
        dimRecord = getDimensionRecord("CountryDimension","countryId", countryId);
        if(!dimRecord){
            dimensionId = countryId;
            newEntity = makeValue("CountryDimension");
            newEntity.dimensionId = dimensionId;
            newEntity.countryId = countryId;
            newEntity.countryCode = country.geoCode;
            newEntity.countryNumCode = country.geoSecCode;
            newEntity.tld = country.geoCode;
            countryTeleRecord = from("CountryTeleCode").where("countryCode", country.geoCode).queryOne();
            if(countryTeleRecord){
                newEntity.countryTeleCode = countryTeleRecord.teleCode;
            };
            newEntity.countryName = country.geoName;
            newEntity.create();
        };
    };
    queryListIterator.close();
};


def getDimensionRecord(dimensionEntityName, naturalKeyName, keyValue){
    dimensionRecord = from(dimensionEntityName).where(naturalKeyName, keyValue).queryOne();
    if(dimensionRecord){
        return dimensionRecord;
    };
};