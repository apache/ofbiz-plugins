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
import java.net.URI;
import java.net.URL;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import org.apache.ofbiz.base.component.ComponentConfig;
import org.apache.ofbiz.base.config.GenericConfigException;
import org.apache.ofbiz.base.container.Container;
import org.apache.ofbiz.base.container.ContainerException;
import org.apache.ofbiz.base.start.StartupCommand;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.cache.UtilCache;

/**
 * Development-only container that watches the Gradle build output directory for
 * changed {@code .class} files and hot-reloads them without restarting OFBiz.
 *
 * <h2>Activation</h2>
 * Add {@code -Dofbiz.hotreload=true} to your JVM arguments, then start OFBiz
 * normally (or run {@code ./gradlew ofbizDev}, provided by this plugin). In a
 * second terminal run {@code ./gradlew -t classes} so Gradle continuously
 * recompiles on every file save — or rely on this container's own in-process
 * compiler, which kicks in automatically when a JDK (not just a JRE) is used.
 *
 * <h2>How it works</h2>
 * <ol>
 *   <li>A {@link WatchService} thread monitors {@code build/classes/java/main/}
 *       recursively for {@code ENTRY_CREATE} and {@code ENTRY_MODIFY} events.</li>
 *   <li>Changes are debounced for 300 ms so that a single Gradle compile run
 *       (which may write several {@code .class} files) is handled as one batch.</li>
 *   <li>A fresh {@link HotReloadClassLoader} is created for the batch. It uses
 *       child-first delegation for the changed class names, so the new bytecode
 *       from the build output directory is used instead of the JVM's cached
 *       version. Callers (e.g. OFBiz's {@code StandardJavaEngine} and
 *       {@code JavaEventHandler}) pick this up via a small reflective hook so
 *       that core framework code has no compile-time dependency on this
 *       plugin.</li>
 *   <li>{@link UtilCache} entries for service definitions are cleared so newly
 *       added/changed services are discovered.</li>
 * </ol>
 *
 * <h2>Scope</h2>
 * This container is intentionally dev-only. It has no effect when the system
 * property is absent, so it is safe to leave the registration in this
 * component's {@code ofbiz-component.xml} for all environments.
 */
public class DevReloadContainer implements Container {

    private static final String MODULE = DevReloadContainer.class.getName();

    /** Classloader created on each reload; {@code null} when hot-reload is off. */
    private static final AtomicReference<HotReloadClassLoader> ACTIVE_LOADER = new AtomicReference<>();

    private String name;
    private WatchService watchService;
    private Thread watchThread;
    private ScheduledExecutorService debouncer;

    // guarded by synchronized(this)
    private ScheduledFuture<?> pendingReload;
    private final Set<String> pendingChanges = new HashSet<>();
    private final Set<String> allChangedClasses = new HashSet<>();

    // guarded by synchronized(this)
    private ScheduledFuture<?> pendingXmlReload;
    private final Set<Path> pendingXmlChanges = new HashSet<>();

    // guarded by synchronized(this)
    private ScheduledFuture<?> pendingCompile;
    private final Set<Path> pendingCompileFiles = new HashSet<>();

    /**
     * Epoch-ms when the last in-process compilation finished; 0 if never compiled.
     * Used together with {@link #scheduleReload} to suppress spurious macOS
     * WatchService ENTRY_CREATE storms for 10 s after an in-process compile.
     * In-process reloads bypass this via {@link #directReload}.
     */
    private long lastCompileFinishedAt = 0;

    private Path classesDir;
    // Populated in start() before the watch thread launches; read-only after that.
    private final Set<Path> servicedefDirs = new HashSet<>();
    private final Set<Path> sourceRootDirs = new HashSet<>();

    // -------------------------------------------------------------------------
    // Static API used by other subsystems (via reflection; see DevReloadHook)
    // -------------------------------------------------------------------------

    /**
     * Returns the active {@link HotReloadClassLoader}, or {@code null} when
     * hot-reload is disabled or has not fired yet.
     *
     * <p>Callers should fall back to the normal context classloader when this
     * returns {@code null}.
     */
    public static ClassLoader getActiveLoader() {
        return ACTIVE_LOADER.get();
    }

    // -------------------------------------------------------------------------
    // Container lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void init(List<StartupCommand> ofbizCommands, String name, String configFile) throws ContainerException {
        this.name = name;

        if (!"true".equalsIgnoreCase(System.getProperty("ofbiz.hotreload"))) {
            Debug.logInfo("DevReloadContainer is disabled. Use -Dofbiz.hotreload=true to enable.", MODULE);
            return;
        }

        classesDir = Paths.get("build/classes/java/main");
        if (!Files.exists(classesDir)) {
            Debug.logWarning("Hot-reload: classes directory not found at " + classesDir.toAbsolutePath()
                    + ". Run './gradlew classes' first, then restart.", MODULE);
            return;
        }

        try {
            watchService = classesDir.getFileSystem().newWatchService();
            registerAll(classesDir);
        } catch (IOException e) {
            throw new ContainerException("DevReloadContainer: failed to initialise WatchService", e);
        }

        debouncer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ofbiz-hot-reload-debouncer");
            t.setDaemon(true);
            return t;
        });

        Debug.logInfo("DevReloadContainer ready — watching " + classesDir.toAbsolutePath(), MODULE);
    }

    @Override
    public boolean start() throws ContainerException {
        if (watchService == null) {
            return true; // disabled
        }
        registerServicedefDirs();
        registerSourceDirs();
        watchThread = new Thread(this::watchLoop, "ofbiz-hot-reload-watcher");
        watchThread.setDaemon(true);
        watchThread.start();
        Debug.logInfo("DevReloadContainer started. Edit any Java or services.xml file and changes go live without a restart.", MODULE);
        return true;
    }

    /**
     * Registers every directory that contains a component service-definition XML file
     * (type="model") with the WatchService so that edits to those files are detected.
     * Called once from {@link #start()}, before the watch thread launches.
     */
    private void registerServicedefDirs() {
        for (ComponentConfig.ServiceResourceInfo sri : ComponentConfig.getAllServiceResourceInfos("model")) {
            try {
                URL url = sri.createResourceHandler().getURL();
                if (!"file".equals(url.getProtocol())) {
                    continue; // skip non-filesystem resources (classpath jars, etc.)
                }
                Path dir = Paths.get(new URI(url.toString())).getParent();
                if (dir != null && Files.isDirectory(dir) && servicedefDirs.add(dir)) {
                    dir.register(watchService,
                            StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_MODIFY);
                    Debug.logInfo("Hot-reload: watching servicedef directory " + dir, MODULE);
                }
            } catch (GenericConfigException | IOException | java.net.URISyntaxException e) {
                Debug.logWarning("Hot-reload: could not register servicedef dir for "
                        + sri.getLocation() + ": " + e.getMessage(), MODULE);
            }
        }
    }

    /**
     * Registers every component's {@code src/main/java} directory with the WatchService
     * so that saving a {@code .java} file triggers in-process compilation via
     * {@link ToolProvider#getSystemJavaCompiler()}. Falls back gracefully when running
     * on a JRE (compiler unavailable) — source watching is simply skipped.
     */
    private void registerSourceDirs() {
        if (ToolProvider.getSystemJavaCompiler() == null) {
            Debug.logWarning("Hot-reload: javax.tools.JavaCompiler not available (JRE, not JDK?). "
                    + "Java source auto-compilation disabled — use './gradlew -t classes' in a second terminal.", MODULE);
            return;
        }
        for (ComponentConfig cc : ComponentConfig.getAllComponents()) {
            if (cc.rootLocation() == null) continue;
            Path srcDir = cc.rootLocation().resolve("src/main/java");
            if (Files.isDirectory(srcDir) && sourceRootDirs.add(srcDir)) {
                try {
                    registerAll(srcDir);
                    Debug.logInfo("Hot-reload: watching source directory " + srcDir, MODULE);
                } catch (IOException e) {
                    Debug.logWarning("Hot-reload: could not watch source dir " + srcDir + ": " + e.getMessage(), MODULE);
                }
            }
        }
        if (!sourceRootDirs.isEmpty()) {
            Debug.logInfo("Hot-reload: Java source auto-compilation active — save a .java file and it reloads automatically.", MODULE);
        }
    }

    @Override
    public void stop() throws ContainerException {
        if (debouncer != null) {
            debouncer.shutdownNow();
        }
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                Debug.logError(e, "DevReloadContainer: error closing WatchService", MODULE);
            }
        }
        if (watchThread != null) {
            watchThread.interrupt();
        }
    }

    @Override
    public String getName() {
        return name;
    }

    // -------------------------------------------------------------------------
    // Watch loop
    // -------------------------------------------------------------------------

    private void watchLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException e) {
                break;
            }

            Path dir = (Path) key.watchable();
            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                Path changed = dir.resolve(((WatchEvent<Path>) event).context());

                if (kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(changed)) {
                    // New package directory created during compilation — register it.
                    try {
                        registerAll(changed);
                    } catch (IOException e) {
                        Debug.logError(e, "DevReloadContainer: failed to register new directory: " + changed, MODULE);
                    }
                } else if ((kind == StandardWatchEventKinds.ENTRY_CREATE || kind == StandardWatchEventKinds.ENTRY_MODIFY)
                        && changed.toString().endsWith(".class")) {
                    // Only react to written/updated class files. Ignore ENTRY_DELETE so
                    // that removing a source file (and its .class output) does not cause
                    // HotReloadClassLoader to attempt loading a non-existent class.
                    scheduleReload(changed);
                } else if ((kind == StandardWatchEventKinds.ENTRY_CREATE || kind == StandardWatchEventKinds.ENTRY_MODIFY)
                        && changed.toString().endsWith(".xml")
                        && servicedefDirs.contains(dir)) {
                    scheduleServiceXmlReload(changed);
                } else if ((kind == StandardWatchEventKinds.ENTRY_CREATE || kind == StandardWatchEventKinds.ENTRY_MODIFY)
                        && changed.toString().endsWith(".java")
                        && sourceRootDirs.stream().anyMatch(dir::startsWith)) {
                    scheduleCompile(changed);
                }
            }
            if (!key.reset()) {
                Debug.logWarning("Hot-reload: watch key became invalid (directory deleted?): "
                        + key.watchable() + ". WatchService will no longer detect changes in that directory.", MODULE);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Debounced reload
    // -------------------------------------------------------------------------

    private synchronized void scheduleReload(Path classFile) {
        String className = toClassName(classesDir, classFile);
        if (className == null) {
            return;
        }
        // On macOS the WatchService fires ENTRY_CREATE for every .class file in the
        // entire tree after the compiler writes one new file. Suppress ALL WatchService
        // class-file events for 10 s after an in-process compile. In-process reloads
        // use directReload() which bypasses this check, so no changes are missed.
        // After 10 s the check expires and external compilations (./gradlew classes)
        // flow through normally.
        if (System.currentTimeMillis() - lastCompileFinishedAt < 10_000L) {
            return;
        }
        pendingChanges.add(className);
        if (pendingReload != null) {
            pendingReload.cancel(false);
        }
        // Wait 300 ms after the last change so a single Gradle compile run
        // (which writes multiple .class files) is handled as one batch.
        try {
            pendingReload = debouncer.schedule(this::applyReload, 300, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            // Container is shutting down; pending changes will not be applied.
        }
    }

    /**
     * Schedules a class reload directly from the in-process compiler, bypassing the
     * macOS WatchService suppression window used by {@link #scheduleReload}.
     */
    private synchronized void directReload(Path classFile) {
        String className = toClassName(classesDir, classFile);
        if (className == null) {
            return;
        }
        pendingChanges.add(className);
        if (pendingReload != null) {
            pendingReload.cancel(false);
        }
        try {
            pendingReload = debouncer.schedule(this::applyReload, 300, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            // Container is shutting down; pending changes will not be applied.
        }
    }

    private synchronized void applyReload() {
        if (pendingChanges.isEmpty()) {
            return;
        }

        Set<String> batch = new HashSet<>(pendingChanges);
        pendingChanges.clear();
        // Grow the cumulative set so the new loader covers all changes from this
        // session, not just the latest batch. This prevents a second reload of a
        // different file from making the first file's changes invisible again.
        allChangedClasses.addAll(batch);

        Debug.logInfo("Hot-reload: detected changes in " + batch, MODULE);

        try {
            URL url = classesDir.toUri().toURL();
            // Use the defining classloader of DevReloadContainer (AppClassLoader) as
            // the HRL parent — deterministic and immune to TCCL mutation by Tomcat or
            // component initializers. TCCL of the debouncer thread would work today but
            // is an implicit invariant that could silently break if startup order changes.
            HotReloadClassLoader loader = new HotReloadClassLoader(
                    new URL[]{url},
                    DevReloadContainer.class.getClassLoader(),
                    allChangedClasses);

            ACTIVE_LOADER.set(loader);

            // Clear service definition cache so newly added service methods are discovered.
            // We deliberately do NOT clear webapp.Controller caches here — controller.xml
            // has not changed, only .class files have, and clearing those caches triggers
            // Groovy re-compilation of screen expressions which can fail unexpectedly.
            UtilCache.clearCache("service.ModelServiceMapByModel");

            Debug.logInfo("Hot-reload complete for: " + batch, MODULE);

        } catch (Throwable e) {
            Debug.logError(e, "Hot-reload failed", MODULE);
        }
    }

    private synchronized void scheduleServiceXmlReload(Path xmlFile) {
        pendingXmlChanges.add(xmlFile);
        if (pendingXmlReload != null) {
            pendingXmlReload.cancel(false);
        }
        try {
            pendingXmlReload = debouncer.schedule(this::applyServiceXmlReload, 300, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            // Container is shutting down; ignore.
        }
    }

    private synchronized void applyServiceXmlReload() {
        if (pendingXmlChanges.isEmpty()) {
            return;
        }
        Set<Path> batch = new HashSet<>(pendingXmlChanges);
        pendingXmlChanges.clear();

        Debug.logInfo("Hot-reload: service XML changed " + batch + " — clearing service model cache", MODULE);
        try {
            UtilCache.clearCache("service.ModelServiceMapByModel");
            Debug.logInfo("Hot-reload: service model cache cleared; definitions will be re-read on next service call", MODULE);
        } catch (Throwable e) {
            Debug.logError(e, "Hot-reload: failed to clear service model cache", MODULE);
        }
    }

    private synchronized void scheduleCompile(Path javaFile) {
        pendingCompileFiles.add(javaFile);
        if (pendingCompile != null) {
            pendingCompile.cancel(false);
        }
        try {
            pendingCompile = debouncer.schedule(this::applyCompile, 300, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            // shutting down
        }
    }

    private synchronized void applyCompile() {
        if (pendingCompileFiles.isEmpty()) {
            return;
        }
        Set<Path> batch = new HashSet<>(pendingCompileFiles);
        pendingCompileFiles.clear();

        Debug.logInfo("Hot-reload: compiling " + batch, MODULE);
        try {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                return;
            }
            try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null)) {
                fm.setLocation(StandardLocation.CLASS_OUTPUT,
                        List.of(classesDir.toAbsolutePath().toFile()));
                // Reuse the running JVM's classpath — it already contains all OFBiz jars.
                List<String> options = Arrays.asList("-cp", System.getProperty("java.class.path"), "-proc:none");
                var units = fm.getJavaFileObjectsFromPaths(batch);
                boolean ok = compiler.getTask(null, fm, null, options, null, units).call();
                if (ok) {
                    Debug.logInfo("Hot-reload: compilation successful", MODULE);

                    // Set the suppression timestamp BEFORE calling directReload so that
                    // scheduleReload() (called by the WatchService) sees it immediately.
                    lastCompileFinishedAt = System.currentTimeMillis();

                    // Collect all .class files produced by this compilation round.
                    // Each source file can produce multiple .class files when it contains
                    // inner or anonymous classes (e.g. Foo$Bar.class, Foo$1.class).
                    // All of them must be added to allChangedClasses so HotReloadClassLoader
                    // uses child-first delegation for inner classes too — otherwise the inner
                    // class still resolves through AppClassLoader's stale cached version.
                    List<Path> compiledRelative = new ArrayList<>();
                    for (Path src : batch) {
                        Path cf = sourceToClassFile(src); // relative path for the outer class
                        if (cf == null) {
                            continue;
                        }
                        String outerName = cf.getFileName().toString().replace(".class", "");
                        Path absOutputDir = cf.toAbsolutePath().getParent();
                        try (var dirStream = Files.list(absOutputDir)) {
                            dirStream.filter(absFile -> {
                                String fn = absFile.getFileName().toString();
                                // Match Foo.class and Foo$Inner.class / Foo$1.class
                                return fn.endsWith(".class")
                                        && (fn.equals(outerName + ".class")
                                                || fn.startsWith(outerName + "$"));
                            }).forEach(absFile -> {
                                // Convert absolute output path back to a relative path that
                                // is rooted at CWD (same type as classesDir) so that
                                // toClassName(classesDir, relPath) — which calls relativize —
                                // does not throw IllegalArgumentException.
                                Path rel = classesDir.resolve(
                                        classesDir.toAbsolutePath().relativize(absFile));
                                compiledRelative.add(rel);
                            });
                        } catch (IOException e) {
                            // Output dir unreadable; fall back to the outer class only.
                            compiledRelative.add(cf);
                        }
                    }

                    // Trigger reload via directReload() — not scheduleReload() — so these
                    // classes bypass the 10-second WatchService suppression window that was
                    // put in place to block the macOS mass-ENTRY_CREATE storm.
                    for (Path rel : compiledRelative) {
                        directReload(rel);
                    }

                    // Re-register class directories so external compilations (./gradlew classes
                    // run by a developer in a separate terminal) still reach the class watcher.
                    // Catch Exception (not just IOException) because ClosedWatchServiceException
                    // extends IllegalStateException, which is a RuntimeException — it can be
                    // thrown here if OFBiz is shutting down while a compile finishes.
                    try {
                        registerAll(classesDir);
                    } catch (Exception e) {
                        Debug.logWarning("Hot-reload: could not re-register class dirs: " + e.getMessage(), MODULE);
                    }
                } else {
                    Debug.logWarning("Hot-reload: compilation failed — fix the error and save again", MODULE);
                }
            }
        } catch (Throwable e) {
            Debug.logError(e, "Hot-reload: compilation error", MODULE);
        }
    }

    /**
     * Maps a {@code .java} source file to the corresponding {@code .class} output file
     * under {@link #classesDir}. Returns {@code null} if the source file is not under
     * any registered source root.
     */
    private Path sourceToClassFile(Path sourceFile) {
        for (Path srcRoot : sourceRootDirs) {
            if (sourceFile.startsWith(srcRoot)) {
                Path relative = srcRoot.relativize(sourceFile);
                String name = relative.toString();
                if (name.endsWith(".java")) {
                    String classRelative = name.substring(0, name.length() - ".java".length()) + ".class";
                    return classesDir.resolve(classRelative);
                }
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void registerAll(Path start) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                dir.register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Converts a {@code .class} file path relative to {@code baseDir} into a
     * binary class name.
     *
     * <p>Example: {@code com/example/Foo.class} → {@code com.example.Foo}
     */
    private static String toClassName(Path baseDir, Path classFile) {
        Path relative = baseDir.relativize(classFile);
        String s = relative.toString();
        if (!s.endsWith(".class")) {
            return null;
        }
        // Normalise path separator to '.' and strip the '.class' suffix
        return s.substring(0, s.length() - ".class".length())
                .replace(classFile.getFileSystem().getSeparator(), ".");
    }
}
