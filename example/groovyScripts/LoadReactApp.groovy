import groovy.json.JsonSlurper
import org.apache.ofbiz.widget.model.ScriptLinkHelper

import java.nio.file.Path

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

// Useful variables with example values:
// controlPath ->= /example/control
// contextRoot -> C:\dev\floss\ofbiz\ofbiz-framework\plugins\example\webapp\example\
// serverRoot -> https://localhost:8443
// context.application.context.context.path -> /example

// Path component of URL leading to the servlet mount point, e.g. '/example'.
String contextUrlPath = context.application.context.context.path
// Filesystem path to the webapp root, e.g. '$ofbiz-framework/plugins/example/webapp/example/'.
String contextFilesystemPath  = context.contextRoot

String reactAppDirectory = 'vite-react-app'
String reactAppUrlPath = contextUrlPath + '/' + reactAppDirectory + '/'

// Path to the vite react app manifest file. This lists all the files that are part of the react app.
// We read this file to identify the main javascript and css files.
// Example manifest file:
// {
//  "index.css": {
//    "file": "assets/index-e12e197a.css",
//    "src": "index.css"
//  },
//  "index.html": {
//    "assets": [
//      "assets/react-35ef61ed.svg"
//    ],
//    "css": [
//      "assets/index-e12e197a.css"
//    ],
//    "file": "assets/index-e5a516b1.js",
// ...
Path assetManifestPath = Path.of(contextFilesystemPath, reactAppDirectory, 'manifest.json')

// Extract the URL paths to the index javascript and css files from the manifest file.
Map<String, Object> assetManifest = new JsonSlurper().parse(assetManifestPath.toFile())
String stylesheetDistRelativePath = assetManifest.'index.css'.file
String javascriptDistRelativePath = assetManifest.'index.html'.file

// Return the stylesheet and javascript paths for use by the screen.
context.reactAppStylesheetUrlPath = reactAppUrlPath + stylesheetDistRelativePath
context.reactAppJavascriptUrlPath = reactAppUrlPath + javascriptDistRelativePath