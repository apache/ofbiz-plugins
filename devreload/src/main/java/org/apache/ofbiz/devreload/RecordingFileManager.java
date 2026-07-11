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
package org.apache.ofbiz.devreload;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;

/**
 * Wraps the compiler's standard file manager to record the absolute path of every
 * {@code .class} file it actually writes, so {@link DevReloadContainer}'s post-compile
 * reload step can hot-swap exactly what the compiler produced instead of guessing from
 * the source file's own base name -- a guess that misses a secondary top-level class
 * declared in the same file, or a lone non-public top-level class named differently
 * from its file.
 */
final class RecordingFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
    final List<Path> outputFiles = new ArrayList<>();

    RecordingFileManager(StandardJavaFileManager fileManager) {
        super(fileManager);
    }

    @Override
    public JavaFileObject getJavaFileForOutput(JavaFileManager.Location location, String className,
            JavaFileObject.Kind kind, FileObject sibling) throws IOException {
        JavaFileObject file = super.getJavaFileForOutput(location, className, kind, sibling);
        outputFiles.add(Paths.get(file.toUri()));
        return file;
    }
}
