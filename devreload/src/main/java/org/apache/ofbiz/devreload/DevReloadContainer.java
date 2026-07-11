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
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import org.apache.commons.io.FileUtils;
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
 * this plugin, which sets both automatically — the only supported command). That Gradle
 * task requires a DCEVM-patched JVM and fails fast with setup instructions if none is
 * resolvable; see "Structural changes" below for why.
 *
 * <h2>How Java hot-reload works</h2>
 * <ol>
 *   <li>At startup this container self-attaches {@link HotSwapAgent} to the current JVM
 *       via the Attach API, obtaining a live {@link Instrumentation} instance — no
 *       {@code -javaagent} flag needed.</li>
 *   <li>A {@link WatchService} thread monitors every component's {@code src/main/java}
 *       directory. On save, changed {@code .java} files are compiled in-process (a JDK,
 *       not just a JRE, is required) into {@code build/devreload/classes/} — a directory
 *       private to this plugin, deliberately <em>not</em> Gradle's own
 *       {@code build/classes/java/main/}. Writing into Gradle's managed output would
 *       leave its incremental-build cache unaware of the change: if a source file is
 *       later reverted to content Gradle already has a snapshot for (e.g. via
 *       {@code git checkout}) while no hot-reload session is watching, Gradle's next
 *       {@code compileJava} would see matching input and skip recompiling, silently
 *       leaving the stale hot-swapped {@code .class} file in place. This plugin's output
 *       directory is cleared and recreated on every {@link #init}, so a fresh run never
 *       inherits a previous session's bytecode, and is placed ahead of Gradle's own
 *       output on the classpath (see {@code build.gradle}) so it always wins when a class
 *       exists in both places. Running {@code ./gradlew -t classes} externally in a
 *       second terminal still works — the same WatchService also monitors Gradle's own
 *       output directory directly for externally-produced {@code .class} files, when
 *       {@code -Dofbiz.hotreload.watchBuildOutput=true}.</li>
 *   <li>Changes are debounced for 300 ms so a single compile run is handled as one batch.
 *       Each changed class already loaded in the JVM is updated in place via
 *       {@link Instrumentation#redefineClasses}, the same mechanism an IDE debugger uses
 *       for HotSwap. Because the {@link Class} object's identity never changes, every
 *       existing reference to it — including caches inside {@code JavaEventHandler} and
 *       {@code StandardJavaEngine} — automatically executes the new method bodies on the
 *       next call. No framework code needs to know this plugin exists.</li>
 *   <li>Brand-new classes need no special handling at all: they simply get loaded
 *       normally, from this plugin's build output directory, the first time something
 *       references them.</li>
 * </ol>
 *
 * <h2>Structural changes</h2>
 * On a stock JVM, {@code redefineClasses} can only replace method bodies and static
 * initializers of a class that is already loaded — adding/removing methods or fields,
 * changing a method signature, or changing the class hierarchy still requires a restart.
 * This is a hard JVM limitation, not something this plugin can work around on a stock
 * JVM. Running on a DCEVM-patched JVM with {@code -XX:+AllowEnhancedClassRedefinition}
 * lifts that restriction transparently: this class calls the exact same
 * {@code redefineClasses} API either way, so structural changes just work when that flag
 * is present. Because a stock JVM would silently defer that surprise to whenever someone
 * adds a method, {@code ./gradlew ofbizDev}'s build.gradle task treats a DCEVM-patched
 * JVM as required, not optional: it resolves one via {@code -PdcevmHome}/
 * {@code DCEVM_HOME}, launches with that flag automatically, and fails fast with setup
 * instructions instead of starting OFBiz at all if none is resolvable. Plain stock-JDK
 * operation is not a supported outcome of that command.
 *
 * <h2>How services.xml changes are handled</h2>
 * Every component's {@code servicedef/} directory is also watched; on change, the
 * {@code service.ModelServiceMapByModel} {@link UtilCache} entry is cleared directly, so
 * the new/edited definition is re-read on the next service call.
 *
 * <h2>Directory watch limits</h2>
 * The OS may refuse to watch a directory once a process-wide ceiling is reached (most
 * commonly hit on macOS on a full checkout). This container does not try to work around
 * that itself: a directory whose registration fails is simply left unwatched, with a
 * warning naming it, rather than silently falling back to some slower alternate
 * mechanism. Narrow the set of directories with {@code -Dofbiz.hotreload.components}
 * (or {@code -Photreload.components=compA,compB} with the {@code ofbizDev} Gradle task)
 * to fit under the ceiling — see this component's README for the full guidance.
 *
 * <h2>Scope</h2>
 * This container is intentionally dev-only. It has no effect when the system property is
 * absent, so it is safe to leave the registration in this component's
 * {@code ofbiz-component.xml} for all environments.
 */
public class DevReloadContainer implements Container {

    private static final String MODULE = DevReloadContainer.class.getName();
    private static final String SERVICE_MODEL_CACHE_NAME = "service.ModelServiceMapByModel";

    // Shared by start()'s aggregated warning and warnUnwatched()'s per-directory warning,
    // so the two can't silently drift apart if the property/flag names ever change.
    private static final String SCOPE_HINT = "-Dofbiz.hotreload.components=compA,compB (or "
            + "-Photreload.components=compA,compB with the ofbizDev Gradle task) to fit under "
            + "the OS watch limit.";

    private String name;
    private WatchService watchService;
    private Thread watchThread;
    private ScheduledExecutorService debounceExecutor;
    private Instrumentation instrumentation;

    // The executor is supplied lazily (a Supplier, not a direct reference) because these
    // fields are initialized before startDebounceExecutor() creates debounceExecutor --
    // see Debouncer's own javadoc.
    private final Debouncer<String> classReloadDebouncer = new Debouncer<>(() -> debounceExecutor, this::applyReload);
    private final Debouncer<Path> xmlReloadDebouncer = new Debouncer<>(() -> debounceExecutor, this::applyServiceXmlReload);
    private final Debouncer<Path> compileDebouncer = new Debouncer<>(() -> debounceExecutor, this::applyCompile);

    // Counts across registerServicedefDirs()/registerSourceDirs()/registerAll(), so
    // start() can emit one aggregated warning instead of leaving individual failures
    // scattered in the log where they're easy to miss.
    private int watchDirsAttempted = 0;
    private int watchDirsFailed = 0;

    /** Gradle's own compiled-output directory, {@code build/classes/java/main}. Read-only
     *  from this class's perspective: only ever watched (when {@code watchBuildOutput} is
     *  on) for externally-produced {@code .class} files, never written to directly. */
    private Path classesDir;

    /**
     * This plugin's own compiled-output directory, {@code build/devreload/classes},
     * deliberately separate from Gradle's {@link #classesDir}. In-process compiles
     * (see {@link #applyCompile}) write here instead of into Gradle's managed output, so
     * Gradle's incremental {@code compileJava} up-to-date check is never confused by
     * writes it didn't make itself. Cleared and recreated fresh on every {@link #init},
     * and placed ahead of Gradle's output on the runtime classpath (see
     * {@code plugins/devreload/build.gradle}) — so when a class exists in both
     * directories, this one always wins (see {@link #resolveClassFile}).
     */
    private Path hotReloadOutputDir;

    // Populated in start() before the watch thread launches; read-only after that.
    private final Set<Path> servicedefDirs = new HashSet<>();
    private final Set<Path> sourceRootDirs = new HashSet<>();

    /**
     * Component names to watch, from {@code -Dofbiz.hotreload.components}; {@code null}
     * means watch every component. Set this property to a comma-separated list of
     * component names to keep the total number of watched directories under the OS's
     * per-process ceiling on a large checkout.
     */
    private Set<String> allowedComponents;

    /** {@code true} when {@code componentName} should be watched: no scoping set, or it's in the scoped set. */
    private boolean isAllowedComponent(String componentName) {
        return allowedComponents == null || allowedComponents.contains(componentName);
    }

    /**
     * Whether {@link #classesDir} ({@code build/classes/java/main}) itself is watched, from
     * {@code -Dofbiz.hotreload.watchBuildOutput}; defaults to {@code false}. This tree mirrors
     * every component's source tree and, unlike {@link #sourceRootDirs}, is <em>not</em>
     * narrowed by {@link #allowedComponents} (compiled output isn't organized per component),
     * so on a full checkout it roughly doubles the total directories watched. It only exists to
     * pick up externally-produced {@code .class} files (e.g. running {@code ./gradlew -t
     * classes} in a second terminal); in-process compiles hot-swap directly and never need it.
     * Off by default so unscoped runs need meaningfully fewer real watch registrations.
     */
    private boolean watchBuildOutput;

    @Override
    public void init(List<StartupCommand> ofbizCommands, String name, String configFile) throws ContainerException {
        this.name = name;

        if (!"true".equalsIgnoreCase(System.getProperty("ofbiz.hotreload"))) {
            Debug.logInfo("DevReloadContainer is disabled. Use -Dofbiz.hotreload=true to enable.", MODULE);
            return;
        }

        parseHotReloadProperties();
        attachHotSwapAgent();

        if (!prepareClassesDir()) {
            return;
        }
        if (!prepareHotReloadOutputDir()) {
            return;
        }
        createWatchService();
        registerBuildOutputWatchIfEnabled();
        startDebounceExecutor();

        Debug.logInfo("DevReloadContainer ready — compiled output at " + hotReloadOutputDir.toAbsolutePath(), MODULE);
    }

    /**
     * Reads {@code -Dofbiz.hotreload.components} and {@code -Dofbiz.hotreload.watchBuildOutput},
     * populating {@link #allowedComponents} and {@link #watchBuildOutput}.
     */
    private void parseHotReloadProperties() {
        String componentsProperty = System.getProperty("ofbiz.hotreload.components");
        if (componentsProperty != null && !componentsProperty.isBlank()) {
            allowedComponents = Arrays.stream(componentsProperty.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
            Debug.logInfo("Hot-reload: scoped to components " + allowedComponents
                    + " (set via -Dofbiz.hotreload.components)", MODULE);
        }

        watchBuildOutput = "true".equalsIgnoreCase(System.getProperty("ofbiz.hotreload.watchBuildOutput"));
    }

    /**
     * Self-attaches {@link HotSwapAgent} so Java class redefinition is available. Failure
     * here is non-fatal and never aborts {@link #init}: Java hot-swap is simply disabled
     * while {@code services.xml} auto-reload still works.
     */
    private void attachHotSwapAgent() {
        try {
            instrumentation = HotSwapAgent.install();
            Debug.logInfo("Hot-reload: self-attached HotSwapAgent — Java class redefinition is available.", MODULE);
            if (enhancedRedefinitionRequested()) {
                Debug.logInfo("Hot-reload: -XX:+AllowEnhancedClassRedefinition detected — running on a JVM "
                        + "(e.g. JetBrains Runtime) that can also hot-swap structural changes (added/removed "
                        + "methods or fields, changed signatures), not just method bodies.", MODULE);
            } else {
                Debug.logWarning("Hot-reload: structural changes (added/removed methods or fields, changed "
                        + "signatures) will require a restart on this JVM. './gradlew ofbizDev' requires a "
                        + "DCEVM-patched JVM and refuses to start without one, so you're most likely seeing "
                        + "this because hot-reload was activated some other way on a stock JDK. See "
                        + "plugins/devreload/README.md to set up DCEVM_HOME.", MODULE);
            }
        } catch (Exception e) {
            Debug.logWarning("Hot-reload: could not self-attach HotSwapAgent (" + e.getMessage()
                    + "). Add -Djdk.attach.allowAttachSelf=true to JVM args. "
                    + "Java class changes will require a restart; services.xml auto-reload still works.", MODULE);
        }
    }

    /** Sets {@link #classesDir}. Returns {@code false} (after logging) if it doesn't exist yet. */
    private boolean prepareClassesDir() {
        classesDir = Paths.get("build/classes/java/main");
        if (!Files.exists(classesDir)) {
            Debug.logWarning("Hot-reload: classes directory not found at " + classesDir.toAbsolutePath()
                    + ". Run './gradlew classes' first, then restart.", MODULE);
            return false;
        }
        return true;
    }

    /**
     * Sets {@link #hotReloadOutputDir} and clears/recreates it. Returns {@code false} (after
     * logging) if the directory could not be prepared -- without a writable output directory,
     * in-process compilation cannot work at all, so the whole container is disabled up front
     * instead of continuing to a misleading "ready" log.
     */
    private boolean prepareHotReloadOutputDir() {
        // build.gradle passes this down as -Dofbiz.hotreload.outputDir, computed from the
        // same value it prepends to the classpath, so the path exists in exactly one place
        // rather than being hardcoded independently here too. The literal default below is
        // only a fallback for the (unsupported) case of starting this container without
        // going through the ofbizDev Gradle task.
        hotReloadOutputDir = Paths.get(System.getProperty("ofbiz.hotreload.outputDir", "build/devreload/classes"));
        try {
            FileUtils.deleteDirectory(hotReloadOutputDir.toFile());
            Files.createDirectories(hotReloadOutputDir);
            return true;
        } catch (IOException e) {
            Debug.logWarning("Hot-reload: could not prepare this plugin's own output directory "
                    + hotReloadOutputDir.toAbsolutePath() + ": " + e.getMessage(), MODULE);
            return false;
        }
    }

    /** Creates {@link #watchService} on {@link #classesDir}'s filesystem. */
    private void createWatchService() throws ContainerException {
        try {
            watchService = classesDir.getFileSystem().newWatchService();
        } catch (IOException e) {
            throw new ContainerException("DevReloadContainer: failed to initialise WatchService", e);
        }
    }

    /**
     * Registers {@link #classesDir} with the WatchService when {@link #watchBuildOutput} is
     * on, so externally-produced {@code .class} files (e.g. {@code ./gradlew -t classes} in
     * a second terminal) are picked up too.
     */
    private void registerBuildOutputWatchIfEnabled() {
        if (watchBuildOutput) {
            try {
                registerAll(classesDir);
            } catch (IOException e) {
                logWalkFailure(classesDir, e);
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
    }

    /** Starts the daemon executor backing every {@link Debouncer}. */
    private void startDebounceExecutor() {
        debounceExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ofbiz-hot-reload-debouncer");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Picks whichever of this plugin's own compiled output ({@code overlay}, under
     * {@link #hotReloadOutputDir}) or Gradle's ({@code fallback}, under {@link #classesDir})
     * is the right one to redefine from. {@code overlay} always wins when it exists,
     * matching its position ahead of {@code fallback} on the runtime classpath (see
     * {@code build.gradle}) — a class compiled here is always the one a caller would load.
     * Returns {@code null} if neither exists.
     */
    private Path resolveClassFile(Path overlay, Path fallback) {
        if (Files.exists(overlay)) {
            return overlay;
        }
        if (Files.exists(fallback)) {
            return fallback;
        }
        return null;
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
                    + "and are NOT being watched — changes there will not hot-reload until you restart. Scope "
                    + "hot-reload to just the components you're working on with " + SCOPE_HINT, MODULE);
        }
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
            if (!isAllowedComponent(sri.getComponentConfig().getComponentName())) {
                continue;
            }
            try {
                URL url = sri.createResourceHandler().getURL();
                if (!"file".equals(url.getProtocol())) {
                    continue; // skip non-filesystem resources (classpath jars, etc.)
                }
                Path dir = Paths.get(new URI(url.toString())).getParent();
                if (dir != null && Files.isDirectory(dir) && servicedefDirs.add(dir) && registerWatch(dir)) {
                    Debug.logInfo("Hot-reload: watching servicedef directory " + dir, MODULE);
                }
            } catch (GenericConfigException | URISyntaxException e) {
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
            if (cc.rootLocation() == null) {
                continue;
            }
            if (!isAllowedComponent(cc.getComponentName())) {
                continue;
            }
            Path srcDir = cc.rootLocation().resolve("src/main/java");
            if (Files.isDirectory(srcDir) && sourceRootDirs.add(srcDir)) {
                try {
                    registerAll(srcDir);
                    Debug.logInfo("Hot-reload: watching source directory " + srcDir, MODULE);
                } catch (IOException e) {
                    logWalkFailure(srcDir, e);
                }
            }
        }
        if (!sourceRootDirs.isEmpty()) {
            Debug.logInfo("Hot-reload: Java source auto-compilation active — save a .java file and it reloads automatically.", MODULE);
        }
    }

    @Override
    public void stop() throws ContainerException {
        if (debounceExecutor != null) {
            debounceExecutor.shutdownNow();
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
                    // New package directory (or a whole new subtree -- e.g. a `git checkout`
                    // that adds a package, an IDE "extract to new package" refactor, or
                    // unzipping a folder) -- register it AND dispatch any files already inside
                    // it. The OS/JDK WatchService does not retroactively report CREATE events
                    // for files that already existed before a directory was registered, so
                    // without this seeding step, files that land here in the same operation
                    // that created the directory would silently never compile until each one
                    // is individually re-saved.
                    try {
                        registerAllAndSeed(changed);
                    } catch (IOException e) {
                        Debug.logError(e, "DevReloadContainer: failed to register new directory: " + changed, MODULE);
                    }
                } else if (kind == StandardWatchEventKinds.ENTRY_CREATE || kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                    // Ignore ENTRY_DELETE so that removing a source file (and its .class
                    // output) does not cause a redefinition attempt against a now-missing file.
                    dispatchChangedFile(dir, changed);
                }
            }
            if (!key.reset()) {
                Debug.logWarning("Hot-reload: watch key became invalid (directory deleted?): "
                        + key.watchable() + ". WatchService will no longer detect changes in that directory.", MODULE);
            }
        }
    }

    /**
     * Routes a single created/modified regular file through the correct reload pipeline --
     * a {@code .class} file under {@link #classesDir}, a {@code .xml} file under a
     * registered servicedef directory, or a {@code .java} file under a registered source
     * root. Shared between {@link #watchLoop}'s live WatchService events and
     * {@link #registerAllAndSeed}'s seeding of files that already existed when a new
     * directory was first discovered.
     */
    private void dispatchChangedFile(Path dir, Path changed) {
        String name = changed.toString();
        if (name.endsWith(".class")) {
            reloadClassFile(classesDir, changed);
        } else if (name.endsWith(".xml") && servicedefDirs.stream().anyMatch(dir::startsWith)) {
            xmlReloadDebouncer.add(changed);
        } else if (name.endsWith(".java") && sourceRootDirs.stream().anyMatch(dir::startsWith)) {
            compileDebouncer.add(changed);
        }
    }

    // -------------------------------------------------------------------------
    // Debounced batching
    // -------------------------------------------------------------------------

    /** Resolves {@code changed} to a class name relative to {@code baseDir} and, if valid, queues it for reload. */
    private void reloadClassFile(Path baseDir, Path changed) {
        String className = toClassName(baseDir, changed);
        if (className != null) {
            classReloadDebouncer.add(className);
        }
    }

    private void applyReload(Set<String> batch) {
        Debug.logInfo("Hot-reload: detected changes in " + batch, MODULE);

        if (instrumentation == null) {
            Debug.logWarning("Hot-reload: HotSwapAgent not attached — " + batch
                    + " compiled but not applied to the running JVM. Restart to pick it up.", MODULE);
            return;
        }

        Map<String, Class<?>> loadedByName = indexLoadedClasses(batch);

        List<ClassDefinition> defs = new ArrayList<>();
        for (String className : batch) {
            Path relative = Paths.get(className.replace('.', '/') + ".class");
            Path overlayFile = hotReloadOutputDir.resolve(relative);
            Path gradleFile = classesDir.resolve(relative);
            Path classFile = resolveClassFile(overlayFile, gradleFile);
            if (classFile == null) {
                Debug.logWarning("Hot-reload: detected a change for " + className
                        + " but could not find its compiled output in either "
                        + hotReloadOutputDir.toAbsolutePath() + " or " + classesDir.toAbsolutePath(), MODULE);
                continue;
            }
            Class<?> loaded = loadedByName.get(className);
            if (loaded == null) {
                // Never loaded yet in this JVM — nothing to redefine. It will simply
                // load fresh, with the new bytecode, the first time something
                // references it, from whichever directory resolveClassFile() would
                // pick (overlay first on the classpath too, see build.gradle).
                continue;
            }
            try {
                defs.add(new ClassDefinition(loaded, Files.readAllBytes(classFile)));
            } catch (IOException e) {
                Debug.logError(e, "Hot-reload: failed to read class file for " + className, MODULE);
            }
        }

        if (defs.isEmpty()) {
            Debug.logInfo("Hot-reload: nothing already loaded to redefine for " + batch, MODULE);
            return;
        }

        redefineWithPerClassFallback(defs);
    }

    /**
     * Builds a {@code className -> Class} map covering just the classes in {@code batch},
     * scanning {@link Instrumentation#getAllLoadedClasses} once instead of once per class
     * name — that array holds every class loaded in the JVM (routinely tens of thousands
     * in a running OFBiz instance), so re-scanning it per class name in a batch made every
     * save pay for an O(batchSize * totalLoadedClasses) walk.
     */
    private Map<String, Class<?>> indexLoadedClasses(Set<String> batch) {
        Map<String, Class<?>> byName = new HashMap<>();
        for (Class<?> c : instrumentation.getAllLoadedClasses()) {
            if (batch.contains(c.getName())) {
                byName.put(c.getName(), c);
            }
        }
        return byName;
    }

    /**
     * Redefines every class in {@code defs} in one {@link Instrumentation#redefineClasses}
     * call when possible — the common, fast path. If the JVM rejects the whole batch
     * because one class contains a structural change it can't apply
     * ({@link UnsupportedOperationException}), falls back to redefining each class
     * individually instead: otherwise, an unrelated valid method-body edit that happened
     * to land in the same debounced batch as an unsupported structural change would
     * silently fail to apply right along with it.
     */
    private void redefineWithPerClassFallback(List<ClassDefinition> defs) {
        try {
            instrumentation.redefineClasses(defs.toArray(new ClassDefinition[0]));
            // Clear service definition cache so newly added service methods are discovered.
            // We deliberately do NOT clear webapp.Controller caches here — controller.xml
            // has not changed, only .class files have, and clearing those caches triggers
            // Groovy re-compilation of screen expressions which can fail unexpectedly.
            UtilCache.clearCache(SERVICE_MODEL_CACHE_NAME);
            Debug.logInfo("Hot-reload complete for: " + classNames(defs), MODULE);
            return;
        } catch (UnsupportedOperationException e) {
            Debug.logWarning("Hot-reload: batch redefinition rejected (" + e.getMessage()
                    + ") -- retrying each class individually so other, valid changes in the "
                    + "same save still apply.", MODULE);
        } catch (Throwable e) {
            Debug.logError(e, "Hot-reload failed for " + classNames(defs), MODULE);
            return;
        }

        List<String> applied = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        for (ClassDefinition def : defs) {
            String className = def.getDefinitionClass().getName();
            try {
                instrumentation.redefineClasses(def);
                applied.add(className);
            } catch (UnsupportedOperationException e) {
                rejected.add(className);
            } catch (Throwable e) {
                Debug.logError(e, "Hot-reload failed for " + className, MODULE);
                rejected.add(className);
            }
        }
        if (!applied.isEmpty()) {
            UtilCache.clearCache(SERVICE_MODEL_CACHE_NAME);
            Debug.logInfo("Hot-reload complete for: " + applied, MODULE);
        }
        if (!rejected.isEmpty()) {
            // ./gradlew ofbizDev only ever runs on a DCEVM-patched JVM (see build.gradle),
            // which already lifts the plain-JVM restriction to method bodies only, so
            // add/remove method-or-field and signature changes normally succeed here. This
            // still fires for the narrower set of changes DCEVM itself can't apply either
            // (e.g. a changed class hierarchy) -- the same remaining limit an IDE debugger's
            // HotSwap has even on a capable JVM.
            Debug.logWarning("Hot-reload: " + rejected + " contain a structural change (added/removed "
                    + "method or field, changed signature, changed hierarchy) that the JVM cannot "
                    + "hot-swap. Restart OFBiz to pick it up.", MODULE);
        }
    }

    private static List<String> classNames(List<ClassDefinition> defs) {
        return defs.stream().map(def -> def.getDefinitionClass().getName()).collect(Collectors.toList());
    }

    /**
     * Best-effort detection of whether this JVM was launched with
     * {@code -XX:+AllowEnhancedClassRedefinition} (e.g. a JetBrains Runtime), which is
     * what allows {@link Instrumentation#redefineClasses} to also apply structural
     * changes instead of just method bodies. Purely informational — the actual
     * capability is exercised (and, if absent, reported) when a redefinition is
     * attempted in {@link #applyReload}.
     */
    private static boolean enhancedRedefinitionRequested() {
        return ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .anyMatch(arg -> arg.contains("AllowEnhancedClassRedefinition"));
    }

    private void applyServiceXmlReload(Set<Path> batch) {
        Debug.logInfo("Hot-reload: service XML changed " + batch + " — clearing service model cache", MODULE);
        try {
            UtilCache.clearCache(SERVICE_MODEL_CACHE_NAME);
            Debug.logInfo("Hot-reload: service model cache cleared; definitions will be re-read on next service call", MODULE);
        } catch (Throwable e) {
            Debug.logError(e, "Hot-reload: failed to clear service model cache", MODULE);
        }
    }

    private void applyCompile(Set<Path> batch) {
        Debug.logInfo("Hot-reload: compiling " + batch, MODULE);
        try {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                return;
            }
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            // Explicit UTF-8: the project's real compileJava forces options.encoding = 'UTF-8'
            // (root build.gradle). Passing null here would fall back to Charset.defaultCharset(),
            // which isn't guaranteed to be UTF-8 on every JVM/OS (notably pre-JDK18, or any JVM
            // launched with an overridden -Dfile.encoding) and would silently decode source files
            // containing non-ASCII characters differently than Gradle's own compile does.
            try (StandardJavaFileManager standardFm =
                    compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
                standardFm.setLocation(StandardLocation.CLASS_OUTPUT,
                        List.of(hotReloadOutputDir.toAbsolutePath().toFile()));
                RecordingFileManager fm = new RecordingFileManager(standardFm);
                // Reuse the running JVM's classpath — it already contains all OFBiz jars.
                List<String> options = Arrays.asList("-cp", System.getProperty("java.class.path"), "-proc:none");
                var units = standardFm.getJavaFileObjectsFromPaths(batch);
                boolean ok = compiler.getTask(null, fm, diagnostics, options, null, units).call();
                if (ok) {
                    Debug.logInfo("Hot-reload: compilation successful", MODULE);

                    // Reload every .class file the compiler actually wrote for this batch, per
                    // RecordingFileManager -- covers inner/anonymous classes (Foo$1.class) and,
                    // unlike a name-based guess derived from the source file's own base name,
                    // also a secondary top-level class in the same source file, or a lone
                    // non-public top-level class named differently from its file (both legal
                    // Java). A name-based guess would compile such a class to disk correctly
                    // but never queue it for redefinition, leaving it silently running stale
                    // bytecode.
                    for (Path absFile : fm.outputFiles) {
                        Path rel = hotReloadOutputDir.resolve(
                                hotReloadOutputDir.toAbsolutePath().relativize(absFile));
                        reloadClassFile(hotReloadOutputDir, rel);
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
                    Debug.logWarning("Hot-reload: compilation failed — fix the error and save again\n"
                            + formatErrors(diagnostics), MODULE);
                }
            }
        } catch (Throwable e) {
            Debug.logError(e, "Hot-reload: compilation error", MODULE);
        }
    }

    /** Formats the error-level diagnostics from a failed compile, one per line, as {@code file:line: message}. */
    private static String formatErrors(DiagnosticCollector<JavaFileObject> diagnostics) {
        StringBuilder sb = new StringBuilder();
        for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
            if (d.getKind() != Diagnostic.Kind.ERROR) {
                continue;
            }
            String source = d.getSource() != null ? d.getSource().getName() : "?";
            sb.append("  ").append(source).append(':').append(d.getLineNumber())
                    .append(": ").append(d.getMessage(null)).append('\n');
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Recursively registers every directory under {@code start} with the WatchService.
     * A directory whose registration fails (e.g. watch/descriptor exhaustion) is logged
     * and left unwatched via {@link #warnUnwatched} instead of aborting the whole walk,
     * so one overloaded directory never leaves the rest of the tree unwatched.
     */
    private void registerAll(Path start) throws IOException {
        walkAndRegister(start, false);
    }

    /**
     * Logs that {@link #registerAll} itself failed for {@code dir} -- something more
     * fundamental broke walking the tree at all (e.g. can't even list {@code dir}), as
     * opposed to an individual directory failing to register, which {@code registerAll()}
     * already logs and skips on its own via {@link #warnUnwatched}.
     */
    private void logWalkFailure(Path dir, IOException e) {
        Debug.logWarning("Hot-reload: could not fully walk " + dir + ": " + e.getMessage(), MODULE);
    }

    /**
     * Like {@link #registerAll}, but also dispatches every regular file already present
     * under {@code start} through {@link #dispatchChangedFile}, as if a CREATE event had
     * fired for each. Used only when a brand-new directory materializes mid-session (see
     * {@link #watchLoop}) -- deliberately not used for the bulk registration done at
     * startup or the periodic re-registration in {@link #applyCompile}, where
     * re-dispatching every already-known file on every call would be both wasteful and
     * cause spurious repeat reloads.
     */
    private void registerAllAndSeed(Path start) throws IOException {
        walkAndRegister(start, true);
    }

    private void walkAndRegister(Path start, boolean seedExistingFiles) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                registerWatch(dir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (seedExistingFiles) {
                    dispatchChangedFile(file.getParent(), file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Registers {@code dir} with the WatchService for create/modify events, incrementing
     * {@link #watchDirsAttempted} and calling {@link #warnUnwatched} on failure instead of
     * letting it propagate. Shared by {@link #registerServicedefDirs} and
     * {@link #walkAndRegister} so both directory-registration paths get the same specific
     * failure handling instead of each hand-rolling its own copy.
     *
     * @return {@code true} if the registration succeeded.
     */
    private boolean registerWatch(Path dir) {
        watchDirsAttempted++;
        try {
            dir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
            return true;
        } catch (IOException e) {
            warnUnwatched(dir, e);
        } catch (Throwable t) {
            // register() itself should only throw IOException, but nothing here is worth
            // crashing the whole startup over.
            Debug.logError(t, "Hot-reload: unexpected error registering watch for " + dir
                    + " -- this directory will not be watched.", MODULE);
        }
        return false;
    }

    /**
     * Records that {@code dir} could not get a WatchService registration (most commonly
     * the OS's per-process watch ceiling, e.g. macOS's kqueue-per-directory cost) and
     * logs why. The directory is simply left unwatched: changes there require a restart
     * (or {@code -Dofbiz.hotreload.components}/{@code -Photreload.components} to narrow
     * the watched set below the ceiling) rather than falling back to some slower
     * alternate mechanism.
     */
    private void warnUnwatched(Path dir, IOException cause) {
        watchDirsFailed++;
        Debug.logWarning("Hot-reload: could not watch " + dir + " (" + cause.getMessage() + ") -- changes "
                + "there will not be picked up until OFBiz is restarted. Narrow the watched set with "
                + SCOPE_HINT, MODULE);
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
