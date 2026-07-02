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

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Set;

/**
 * A child-first class loader used by {@link DevReloadContainer} to hot-reload
 * changed classes without restarting OFBiz.
 *
 * <p>For every class name in {@code changedClasses}, this loader bypasses the
 * standard parent-first delegation and loads the class directly from the build
 * output directory, picking up the freshly compiled bytecode. All other classes
 * are resolved through the normal parent delegation chain so that shared types
 * (e.g. {@code DispatchContext}, {@code Map}) remain compatible.
 *
 * <p>A new instance is created for every reload event so there is no stale
 * class cache to worry about.
 */
public final class HotReloadClassLoader extends URLClassLoader {

    static {
        ClassLoader.registerAsParallelCapable();
    }

    private final Set<String> changedClasses;

    HotReloadClassLoader(URL[] urls, ClassLoader parent, Set<String> changedClasses) {
        super(urls, parent);
        this.changedClasses = Set.copyOf(changedClasses);
    }

    /**
     * Child-first for changed classes, parent-first for everything else.
     */
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (!changedClasses.contains(name)) {
            return super.loadClass(name, resolve);
        }
        synchronized (getClassLoadingLock(name)) {
            Class<?> already = findLoadedClass(name);
            if (already != null) {
                if (resolve) resolveClass(already);
                return already;
            }
            Class<?> c = findClass(name); // loads directly from build/classes/java/main/
            if (resolve) resolveClass(c);
            return c;
        }
    }
}
