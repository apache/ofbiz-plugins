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
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
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
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
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
 * Development-only container that watches Java sources/classes and {@code services.xml}
 * files and applies changes to a running OFBiz instance without a restart.
 *
 * <h2>Activation</h2>
 * Add {@code -Dofbiz.hotreload=true -Djdk.attach.allowAttachSelf=true} to your JVM
 * arguments, then start OFBiz normally (or run {@code ./gradlew ofbizDev}, provided by
 * this plugin, which sets both automatically).
 *
 * <h2>How Java hot-reload works</h2>
 * <ol>
 *   <li>At startup this container self-attaches {@link HotSwapAgent} to the current JVM
 *       via the Attach API, obtaining a live {@link Instrumentation} instance — no
 *       {@code -javaagent} flag needed.</li>
 *   <li>A {@link WatchService} thread monitors every component's {@code src/main/java}
 *       directory. On save, changed {@code .java} files are compiled in-process (a JDK,
 *       not just a JRE, is required) into {@code build/classes/java/main/}. Running
 *       {@code ./gradlew -t classes} externally in a second terminal works too — the
 *       same WatchService also monitors that output directory directly for
 *       externally-produced {@code .class} files.</li>
 *   <li>Changes are debounced for 300 ms so a single compile run is handled as one batch.
 *       Each changed class already loaded in the JVM is updated in place via
 *       {@link Instrumentation#redefineClasses}, the same mechanism an IDE debugger uses
 *       for HotSwap. Because the {@link Class} object's identity never changes, every
 *       existing reference to it — including caches inside {@code JavaEventHandler} and
 *       {@code StandardJavaEngine} — automatically executes the new method bodies on the
 *       next call. No framework code needs to know this plugin exists.</li>
 *   <li>Brand-new classes need no special handling at all: they simply get loaded
 *       normally, from the same build output directory, the first time something
 *       references them.</li>
 * </ol>
 *
 * <h2>Structural changes</h2>
 * On a stock JVM, {@code redefineClasses} can only replace method bodies and static
 * initializers of a class that is already loaded — adding/removing methods or fields,
 * changing a method signature, or changing the class hierarchy still requires a restart.
 * Running on a JetBrains Runtime with {@code -XX:+AllowEnhancedClassRedefinition} (see
 * {@code ./gradlew ofbizDev -Photreload.enhanced=true}) lifts that restriction transparently: this class
 * calls the exact same {@code redefineClasses} API either way, so structural changes
 * just work when that flag is detected, with no code path change here.
 *
 * <h2>How services.xml changes are handled</h2>
 * Every component's {@code servicedef/} directory is also watched; on change, the
 * {@code service.ModelServiceMapByModel} {@link UtilCache} entry is cleared directly, so
 * the new/edited definition is re-read on the next service call.
 *
 * <h2>Scope</h2>
 * This container is intentionally dev-only. It has no effect when the system property is
 * absent, so it is safe to leave the registration in this component's
 * {@code ofbiz-component.xml} for all environments.
 */
public class DevReloadContainer implements Container {

    private static final String MODULE = DevReloadContainer.class.getName();
    private static final String SERVICE_MODEL_CACHE_NAME = "service.ModelServiceMapByModel";

    private String name;
    private WatchService watchService;
    private Thread watchThread;
    private ScheduledExecutorService debouncer;
    private Instrumentation instrumentation;

    // guarded by synchronized(this)
    private ScheduledFuture<?> pendingReload;
    private final Set<String> pendingChanges = new HashSet<>();

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

    // Counts across registerServicedefDirs()/registerSourceDirs()/registerAll(), so
    // start() can emit one aggregated warning instead of leaving individual failures
    // scattered in the log where they're easy to miss.
    private int watchDirsAttempted = 0;
    private int watchDirsFailed = 0;

    private Path classesDir;
    // Populated in start() before the watch thread launches; read-only after that.
    private final Set<Path> servicedefDirs = new HashSet<>();
    private final Set<Path> sourceRootDirs = new HashSet<>();

    /**
     * Directories that could not get a real {@link WatchService} registration (e.g.
     * macOS's kqueue-per-directory watch cost exhausting the process's practical watch
     * ceiling, well under a typical {@code ulimit -n}) and are polled every
     * {@link #POLL_INTERVAL_MS} instead, keyed by what kind of files under that
     * directory matter. This is the fallback of last resort: {@link #registerAll} tries
     * a real watch for every directory first and only falls here on failure, so nothing
     * is ever silently left unwatched — it just degrades from instant to
     * {@link #POLL_INTERVAL_MS}-latency for whichever directories didn't fit.
     */
    private final Map<Path, PollKind> pollDirs = new ConcurrentHashMap<>();
    private final Map<Path, FileTime> pollMTimes = new ConcurrentHashMap<>();
    private static final long POLL_INTERVAL_MS = 2000L;
    private ScheduledFuture<?> pollTask;

    /** Which kind of files under a {@link #pollDirs} entry matter, and how to react to one changing. */
    private enum PollKind { SOURCE_JAVA, COMPILED_CLASSES, SERVICEDEF_XML }

    /**
     * Component names to watch, from {@code -Dofbiz.hotreload.components}; {@code null}
     * means watch every component. Scoping is a performance optimization, not a
     * correctness requirement: even unscoped, {@link #pollDirs} guarantees nothing is
     * silently missed. Scoping just keeps everything on the fast, instant, event-driven
     * path instead of some directories falling back to polling. Set this property to a
     * comma-separated list of component names to scope watching to just the ones being
     * worked on.
     */
    private Set<String> allowedComponents;

    /**
     * Whether {@link #classesDir} ({@code build/classes/java/main}) itself is watched, from
     * {@code -Dofbiz.hotreload.watchBuildOutput}; defaults to {@code false}. This tree mirrors
     * every component's source tree and, unlike {@link #sourceRootDirs}, is <em>not</em>
     * narrowed by {@link #allowedComponents} (compiled output isn't organized per component),
     * so on a full checkout it roughly doubles the total directories watched. It only exists to
     * pick up externally-produced {@code .class} files (e.g. running {@code ./gradlew -t
     * classes} in a second terminal); in-process compiles hot-swap directly via
     * {@link #directReload} and never need it. Off by default so unscoped runs need
     * meaningfully fewer real watch registrations before falling back to polling.
     */
    private boolean watchBuildOutput;

    @Override
    public void init(List<StartupCommand> ofbizCommands, String name, String configFile) throws ContainerException {
        this.name = name;

        if (!"true".equalsIgnoreCase(System.getProperty("ofbiz.hotreload"))) {
            Debug.logInfo("DevReloadContainer is disabled. Use -Dofbiz.hotreload=true to enable.", MODULE);
            return;
        }

        String componentsProperty = System.getProperty("ofbiz.hotreload.components");
        if (componentsProperty != null && !componentsProperty.isBlank()) {
            allowedComponents = Arrays.stream(componentsProperty.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(java.util.stream.Collectors.toSet());
            Debug.logInfo("Hot-reload: scoped to components " + allowedComponents
                    + " (set via -Dofbiz.hotreload.components)", MODULE);
        }

        watchBuildOutput = "true".equalsIgnoreCase(System.getProperty("ofbiz.hotreload.watchBuildOutput"));

        try {
            instrumentation = HotSwapAgent.install();
            Debug.logInfo("Hot-reload: self-attached HotSwapAgent — Java class redefinition is available.", MODULE);
            if (enhancedRedefinitionRequested()) {
                Debug.logInfo("Hot-reload: -XX:+AllowEnhancedClassRedefinition detected — running on a JVM "
                        + "(e.g. JetBrains Runtime) that can also hot-swap structural changes (added/removed "
                        + "methods or fields, changed signatures), not just method bodies.", MODULE);
            } else {
                Debug.logInfo("Hot-reload: structural changes (added/removed methods or fields, changed "
                        + "signatures) will require a restart on this JVM. Run './gradlew ofbizDev "
                        + "-Photreload.enhanced=true' on a JetBrains Runtime to hot-swap those too.", MODULE);
            }
        } catch (Exception e) {
            Debug.logWarning("Hot-reload: could not self-attach HotSwapAgent (" + e.getMessage()
                    + "). Add -Djdk.attach.allowAttachSelf=true to JVM args. "
                    + "Java class changes will require a restart; services.xml auto-reload still works.", MODULE);
        }

        classesDir = Paths.get("build/classes/java/main");
        if (!Files.exists(classesDir)) {
            Debug.logWarning("Hot-reload: classes directory not found at " + classesDir.toAbsolutePath()
                    + ". Run './gradlew classes' first, then restart.", MODULE);
            return;
        }

        try {
            watchService = classesDir.getFileSystem().newWatchService();
        } catch (IOException e) {
            throw new ContainerException("DevReloadContainer: failed to initialise WatchService", e);
        }

        if (watchBuildOutput) {
            try {
                registerAll(classesDir);
            } catch (IOException e) {
                // registerAll() already falls back to polling per-directory for individual
                // register() failures; reaching here means something more fundamental broke
                // walking the tree at all (e.g. can't even list classesDir).
                Debug.logWarning("Hot-reload: could not fully walk " + classesDir + ": " + e.getMessage(), MODULE);
            }
            Debug.logInfo("Hot-reload: watching compiled-output directory " + classesDir.toAbsolutePath()
                    + " for externally-produced .class files (set via -Dofbiz.hotreload.watchBuildOutput=true).",
                    MODULE);
        } else {
            Debug.logInfo("Hot-reload: not watching " + classesDir.toAbsolutePath() + " (this tree isn't "
                    + "narrowed by -Dofbiz.hotreload.components and roughly doubles the total directories "
                    + "watched). In-process edits still hot-swap normally; running './gradlew -t classes' in a "
                    + "second terminal will not be picked up unless you set "
                    + "-Dofbiz.hotreload.watchBuildOutput=true (or -Photreload.watchBuildOutput=true with the "
                    + "ofbizDev Gradle task).", MODULE);
        }

        debouncer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ofbiz-hot-reload-debouncer");
            t.setDaemon(true);
            return t;
        });

        Debug.logInfo("DevReloadContainer ready — compiled output at " + classesDir.toAbsolutePath(), MODULE);
    }

    @Override
    public boolean start() throws ContainerException {
        if (watchService == null) {
            return true; // disabled
        }
        registerServicedefDirs();
        registerSourceDirs();
        if (watchDirsFailed > 0) {
            Debug.logWarning("Hot-reload: " + watchDirsFailed + " of " + watchDirsAttempted + " directory watch "
                    + "registrations hit file-descriptor/watch exhaustion (see warnings above for which ones) "
                    + "and are now polled every " + POLL_INTERVAL_MS + "ms instead of instantly. Java or "
                    + "services.xml changes there still hot-reload, just with a few seconds of extra latency. "
                    + "Scope hot-reload to just the components you're working on with "
                    + "-Dofbiz.hotreload.components=compA,compB (or -Photreload.components=compA,compB with "
                    + "the ofbizDev Gradle task) to keep everything on the fast, instant "
                    + "path instead.", MODULE);
        }
        watchThread = new Thread(this::watchLoop, "ofbiz-hot-reload-watcher");
        watchThread.setDaemon(true);
        watchThread.start();
        pollTask = debouncer.scheduleWithFixedDelay(
                this::pollFallbackDirs, POLL_INTERVAL_MS, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
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
            if (allowedComponents != null && !allowedComponents.contains(sri.getComponentConfig().getComponentName())) {
                continue;
            }
            try {
                URL url = sri.createResourceHandler().getURL();
                if (!"file".equals(url.getProtocol())) {
                    continue; // skip non-filesystem resources (classpath jars, etc.)
                }
                Path dir = Paths.get(new URI(url.toString())).getParent();
                if (dir != null && Files.isDirectory(dir) && servicedefDirs.add(dir)) {
                    watchDirsAttempted++;
                    try {
                        dir.register(watchService,
                                StandardWatchEventKinds.ENTRY_CREATE,
                                StandardWatchEventKinds.ENTRY_MODIFY);
                        Debug.logInfo("Hot-reload: watching servicedef directory " + dir, MODULE);
                    } catch (IOException e) {
                        try {
                            fallBackToPolling(dir, PollKind.SERVICEDEF_XML, e);
                        } catch (Throwable t) {
                            // See registerAll()'s equivalent guard: one directory's fallback
                            // failing must not abort watching every remaining component.
                            Debug.logError(t, "Hot-reload: could not fall back to polling for " + dir
                                    + " -- this directory will not be watched or polled.", MODULE);
                        }
                    }
                }
            } catch (GenericConfigException | java.net.URISyntaxException e) {
                Debug.logWarning("Hot-reload: could not register servicedef dir for "
                        + sri.getLocation() + ": " + e.getMessage(), MODULE);
            } catch (Throwable t) {
                // Defensive: a single component's servicedef registration must not be able
                // to abort the loop and leave every subsequent component's servicedef
                // directory unwatched.
                Debug.logError(t, "Hot-reload: unexpected error registering servicedef dir for "
                        + sri.getLocation(), MODULE);
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
            if (allowedComponents != null && !allowedComponents.contains(cc.getComponentName())) continue;
            Path srcDir = cc.rootLocation().resolve("src/main/java");
            if (Files.isDirectory(srcDir) && sourceRootDirs.add(srcDir)) {
                try {
                    registerAll(srcDir);
                    Debug.logInfo("Hot-reload: watching source directory " + srcDir, MODULE);
                } catch (IOException e) {
                    // registerAll() already falls back to polling per-directory for
                    // individual register() failures; reaching here means something more
                    // fundamental broke walking the tree at all (e.g. can't list srcDir).
                    Debug.logWarning("Hot-reload: could not walk source dir " + srcDir + ": " + e.getMessage(), MODULE);
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
                    // a redefinition attempt against a now-missing file.
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

        Debug.logInfo("Hot-reload: detected changes in " + batch, MODULE);

        if (instrumentation == null) {
            Debug.logWarning("Hot-reload: HotSwapAgent not attached — " + batch
                    + " compiled but not applied to the running JVM. Restart to pick it up.", MODULE);
            return;
        }

        List<ClassDefinition> defs = new ArrayList<>();
        for (String className : batch) {
            Path classFile = classesDir.resolve(className.replace('.', '/') + ".class");
            try {
                Class<?> loaded = findLoadedClass(className);
                if (loaded == null) {
                    // Never loaded yet in this JVM — nothing to redefine. It will simply
                    // load fresh, with the new bytecode, the first time something
                    // references it. No special handling needed.
                    continue;
                }
                defs.add(new ClassDefinition(loaded, Files.readAllBytes(classFile)));
            } catch (IOException e) {
                Debug.logError(e, "Hot-reload: failed to read class file for " + className, MODULE);
            }
        }

        if (defs.isEmpty()) {
            Debug.logInfo("Hot-reload: nothing already loaded to redefine for " + batch, MODULE);
            return;
        }

        try {
            instrumentation.redefineClasses(defs.toArray(new ClassDefinition[0]));
            // Clear service definition cache so newly added service methods are discovered.
            // We deliberately do NOT clear webapp.Controller caches here — controller.xml
            // has not changed, only .class files have, and clearing those caches triggers
            // Groovy re-compilation of screen expressions which can fail unexpectedly.
            UtilCache.clearCache(SERVICE_MODEL_CACHE_NAME);
            Debug.logInfo("Hot-reload complete for: " + batch, MODULE);
        } catch (UnsupportedOperationException e) {
            // Thrown when a change adds/removes a method or field, changes a method
            // signature, or changes the class hierarchy — the plain JVM redefinition API
            // cannot apply that without a restart, the same limit IDE debugger HotSwap has.
            Debug.logWarning("Hot-reload: " + batch + " contains a structural change (added/removed "
                    + "method or field, changed signature, changed hierarchy) that the JVM cannot "
                    + "hot-swap. Restart OFBiz to pick it up. (" + e.getMessage() + ")", MODULE);
        } catch (Throwable e) {
            Debug.logError(e, "Hot-reload failed for " + batch, MODULE);
        }
    }

    /**
     * Best-effort detection of whether this JVM was launched with
     * {@code -XX:+AllowEnhancedClassRedefinition} (e.g. a JetBrains Runtime), which is
     * what allows {@link Instrumentation#redefineClasses} to also apply structural
     * changes instead of just method bodies. Purely informational — the actual
     * capability is exercised (and, if absent, reported) when a redefinition is
     * attempted in {@link #applyReload()}.
     */
    private static boolean enhancedRedefinitionRequested() {
        return ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .anyMatch(arg -> arg.contains("AllowEnhancedClassRedefinition"));
    }

    /** Searches classes already loaded in the JVM for one matching {@code className}. */
    private Class<?> findLoadedClass(String className) {
        for (Class<?> c : instrumentation.getAllLoadedClasses()) {
            if (c.getName().equals(className)) {
                return c;
            }
        }
        return null;
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
            UtilCache.clearCache(SERVICE_MODEL_CACHE_NAME);
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
                    // All of them must be redefined too — otherwise the inner class still
                    // resolves through its stale, previously-loaded bytecode.
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
                    // Only relevant if that watch is enabled in the first place (watchBuildOutput);
                    // otherwise there is nothing registered under classesDir to refresh.
                    // Catch Exception (not just IOException) because ClosedWatchServiceException
                    // extends IllegalStateException, which is a RuntimeException — it can be
                    // thrown here if OFBiz is shutting down while a compile finishes.
                    if (watchBuildOutput) {
                        try {
                            registerAll(classesDir);
                        } catch (Exception e) {
                            Debug.logWarning("Hot-reload: could not re-register class dirs: " + e.getMessage(), MODULE);
                        }
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

    /**
     * Recursively registers every directory under {@code start} with the WatchService.
     * A directory whose registration fails (e.g. watch/descriptor exhaustion) falls back
     * to polling via {@link #fallBackToPolling} instead of aborting the whole walk, so
     * one overloaded directory never leaves the rest of the tree unwatched.
     */
    private void registerAll(Path start) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                watchDirsAttempted++;
                try {
                    dir.register(watchService,
                            StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_MODIFY);
                } catch (IOException e) {
                    try {
                        fallBackToPolling(dir, kindOf(dir), e);
                    } catch (Throwable t) {
                        // fallBackToPolling()/kindOf() are not expected to throw, but this has
                        // been observed to fail (e.g. a transient classloading error) under
                        // heavy directory-watch exhaustion. Letting anything escape here -- even
                        // an Error -- would propagate out of walkFileTree and crash container
                        // startup entirely, which is strictly worse than leaving this one
                        // directory unwatched and unpolled.
                        Debug.logError(t, "Hot-reload: could not fall back to polling for " + dir
                                + " -- this directory will not be watched or polled.", MODULE);
                    }
                } catch (Throwable t) {
                    // Same reasoning: register() itself should only throw IOException, but
                    // nothing here is worth crashing the whole startup over.
                    Debug.logError(t, "Hot-reload: unexpected error registering watch for " + dir
                            + " -- this directory will not be watched or polled.", MODULE);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** Classifies a directory so poll fallback knows which file suffix and reload path apply. */
    private PollKind kindOf(Path dir) {
        return dir.startsWith(classesDir) ? PollKind.COMPILED_CLASSES : PollKind.SOURCE_JAVA;
    }

    // -------------------------------------------------------------------------
    // Poll fallback (for directories that couldn't get a real WatchService registration)
    // -------------------------------------------------------------------------

    /**
     * Records {@code dir} as needing to be polled instead of watched, seeds a baseline of
     * its current files' mtimes (so the very first poll tick doesn't treat every
     * pre-existing file as "changed"), and logs why.
     */
    private void fallBackToPolling(Path dir, PollKind kind, IOException cause) {
        watchDirsFailed++;
        pollDirs.put(dir, kind);
        try (var entries = Files.list(dir)) {
            String suffix = suffixFor(kind);
            entries.filter(p -> p.toString().endsWith(suffix)).forEach(p -> {
                try {
                    pollMTimes.put(p, Files.getLastModifiedTime(p));
                } catch (IOException ignored) {
                    // Best-effort baseline; a missed entry just means its first real
                    // change gets dispatched once even if unchanged, which is harmless.
                }
            });
        } catch (IOException e) {
            Debug.logWarning("Hot-reload: could not seed poll baseline for " + dir + ": " + e.getMessage(), MODULE);
        }
        Debug.logWarning("Hot-reload: could not watch " + dir + " (" + cause.getMessage() + ") -- polling it "
                + "every " + POLL_INTERVAL_MS + "ms instead, so changes there are still noticed, just not "
                + "instantly.", MODULE);
    }

    private static String suffixFor(PollKind kind) {
        return switch (kind) {
        case SOURCE_JAVA -> ".java";
        case COMPILED_CLASSES -> ".class";
        case SERVICEDEF_XML -> ".xml";
        };
    }

    /**
     * Runs every {@link #POLL_INTERVAL_MS} on {@link #debouncer}. For each directory in
     * {@link #pollDirs}, lists its direct children: changed files matching that
     * directory's {@link PollKind} feed into the same debounced reload pipeline a real
     * WatchService event would; newly-appeared subdirectories get a real registration
     * attempt via {@link #registerAll}, which itself falls back to polling again if that
     * still doesn't fit -- so the poll set only ever covers exactly what doesn't fit.
     */
    private void pollFallbackDirs() {
        if (pollDirs.isEmpty()) {
            return;
        }
        for (Map.Entry<Path, PollKind> entry : pollDirs.entrySet()) {
            Path dir = entry.getKey();
            PollKind kind = entry.getValue();
            if (!Files.isDirectory(dir)) {
                pollDirs.remove(dir); // deleted; nothing left to poll here
                continue;
            }
            String suffix = suffixFor(kind);
            try (var entries = Files.list(dir)) {
                entries.forEach(child -> {
                    if (Files.isDirectory(child)) {
                        if (!pollDirs.containsKey(child)) {
                            try {
                                registerAll(child);
                            } catch (IOException e) {
                                Debug.logWarning("Hot-reload: poll fallback could not register new directory "
                                        + child + ": " + e.getMessage(), MODULE);
                            }
                        }
                        return;
                    }
                    if (!child.toString().endsWith(suffix)) {
                        return;
                    }
                    try {
                        FileTime mtime = Files.getLastModifiedTime(child);
                        FileTime previous = pollMTimes.put(child, mtime);
                        // previous == null means this file was never seen before -- either
                        // it's brand new (must be dispatched, same as a WatchService
                        // ENTRY_CREATE would) or fallBackToPolling()'s seed pass somehow
                        // missed it. Either way, treating "never seen" as "changed" is the
                        // only way a poll-fallback directory doesn't silently miss new files.
                        if (previous == null || !previous.equals(mtime)) {
                            dispatchPolledChange(child, kind);
                        }
                    } catch (IOException ignored) {
                        // Transient stat failure (e.g. file removed mid-scan); skip this tick.
                    }
                });
            } catch (IOException e) {
                Debug.logWarning("Hot-reload: poll fallback could not list " + dir + ": " + e.getMessage(), MODULE);
            }
        }
    }

    private void dispatchPolledChange(Path file, PollKind kind) {
        switch (kind) {
        case SOURCE_JAVA -> scheduleCompile(file);
        case COMPILED_CLASSES -> scheduleReload(file);
        case SERVICEDEF_XML -> scheduleServiceXmlReload(file);
        }
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
