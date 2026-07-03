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
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import com.sun.tools.attach.VirtualMachine;

/**
 * Self-attaching Java agent that gives {@link DevReloadContainer} access to
 * {@link Instrumentation#redefineClasses}, the same JVM mechanism an IDE debugger uses
 * for HotSwap — it replaces the bytecode of an already-loaded {@link Class} object in
 * place, so every existing reference to it (including cached {@code Class} objects in
 * framework classes like {@code JavaEventHandler}) picks up the new method bodies on the
 * very next call.
 *
 * <p>The agent jar is built on the fly from this class's own compiled bytecode and
 * attached to the current process via the JDK Attach API — no {@code -javaagent} JVM
 * flag, and no framework code changes. This class and {@link DevReloadContainer} are the
 * only two moving parts.
 *
 * <p>Requires the JVM flag {@code -Djdk.attach.allowAttachSelf=true} (a JDK 9+ safeguard
 * against a process attaching to itself). The {@code ofbizDev} Gradle task provided by
 * this plugin sets it automatically.
 */
public final class HotSwapAgent {

    private static volatile Instrumentation instrumentation;

    private HotSwapAgent() { }

    /** Invoked by the JVM once {@link VirtualMachine#loadAgent} loads this class as an agent. */
    public static void agentmain(String agentArgs, Instrumentation inst) {
        instrumentation = inst;
    }

    /**
     * Builds a minimal agent jar wrapping this class and self-attaches it to the current
     * JVM, returning the resulting {@link Instrumentation}. Safe to call more than once;
     * later calls return the instance obtained by the first successful attach.
     */
    static synchronized Instrumentation install() throws Exception {
        if (instrumentation != null) {
            return instrumentation;
        }
        Path agentJar = buildAgentJar();
        try {
            String pid = String.valueOf(ProcessHandle.current().pid());
            VirtualMachine vm = VirtualMachine.attach(pid);
            try {
                vm.loadAgent(agentJar.toAbsolutePath().toString());
            } finally {
                vm.detach();
            }
        } finally {
            Files.deleteIfExists(agentJar);
        }
        return instrumentation;
    }

    private static Path buildAgentJar() throws IOException {
        String resource = HotSwapAgent.class.getName().replace('.', '/') + ".class";
        Path jar = Files.createTempFile("devreload-hotswap-agent", ".jar");

        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.put(new Attributes.Name("Agent-Class"), HotSwapAgent.class.getName());
        attrs.put(new Attributes.Name("Can-Redefine-Classes"), "true");
        attrs.put(new Attributes.Name("Can-Retransform-Classes"), "true");

        try (InputStream in = HotSwapAgent.class.getClassLoader().getResourceAsStream(resource);
             OutputStream fos = Files.newOutputStream(jar);
             JarOutputStream jos = new JarOutputStream(fos, manifest)) {
            if (in == null) {
                throw new IOException("Could not locate compiled class on classpath: " + resource);
            }
            jos.putNextEntry(new ZipEntry(resource));
            in.transferTo(jos);
            jos.closeEntry();
        }
        return jar;
    }
}
