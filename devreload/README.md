# devreload

Development-only hot-reload for OFBiz. It removes the restart-and-wait cycle when
iterating on Java services, event handlers, and `services.xml` files — edit and save,
and the change is live in under a second, no restart needed.

The plugin is entirely self-contained: dropping this directory into a checkout (or
removing it) has zero effect on the rest of OFBiz either way, with no changes required
anywhere in `ofbiz-framework`.

## Usage

```
./gradlew ofbizDev
```

Starts OFBiz normally on a stock JDK, with hot-reload active. Editing an existing method
body or a `services.xml` file goes live within about 300ms of saving, with no restart
needed. It does not hot-swap structural changes (a brand-new method, a removed
method/field, a changed signature) — those still need a restart on a plain JDK.

```
./gradlew ofbizDev -Photreload.enhanced=true --no-watch-fs
```

Same command, with enhanced class redefinition turned on: it now runs on a JetBrains
Runtime instead of a stock JDK and additionally hot-swaps structural changes live, with
no restart. Requires a JetBrains Runtime — auto-detected from a local IntelliJ IDEA
install, or point at one explicitly with `-PjbrHome=/path/to/jbr` or the `JBR_HOME` env
var.

Both also support scoping to specific components for a faster startup:

```
./gradlew ofbizDev -Photreload.components=devreload,party
./gradlew ofbizDev -Photreload.enhanced=true -Photreload.components=devreload,party
```

### Default vs `-Photreload.enhanced=true`

| Aspect | Default | `-Photreload.enhanced=true` |
|---|---|---|
| JVM | stock JDK (whatever Gradle resolves normally) | JetBrains Runtime (auto-detected or `-PjbrHome`/`JBR_HOME`) |
| Method body edits | hot-swapped live | hot-swapped live |
| `services.xml` edits | hot-swapped live | hot-swapped live |
| Structural changes (new/removed method or field, changed signature) | needs a restart | hot-swapped live |
| Extra JVM flag | — | `-XX:+AllowEnhancedClassRedefinition` |
| Requires JetBrains Runtime | no | yes — fails fast with a clear error if none is found |

`--no-watch-fs` is recommended whenever you're running unscoped (no
`-Photreload.components=...`) on a full checkout, with or without the enhanced flag:
Gradle's own file-system watching competes with this plugin's `WatchService` for the
same macOS per-process directory-watch ceiling, and either one can push you over it on a
checkout the size of OFBiz trunk (~50+ components). Scoping with
`-Photreload.components=...` reduces the plugin's own watch count and is often enough
on its own.

### Why one Gradle task, two different mechanisms underneath

`ofbizDev` is registered as a different Gradle task *type* depending on whether
`-Photreload.enhanced=true` is passed, because the two modes need fundamentally
different ways of launching the JVM:

| Aspect | Default (`JavaExec`) | `-Photreload.enhanced=true` (`Exec`) |
|---|---|---|
| What it is | Gradle's Java-aware task type — you describe *what* to run (`classpath`, `mainClass`, `jvmArgs`) | Gradle's generic process-launch task type — you hand it the *exact* command line to run |
| Which JVM runs it | Whatever JVM Gradle resolves by its own toolchain rules | A specific JetBrains Runtime binary this plugin locates itself |
| Long classpaths (Windows) | Handled automatically — Gradle writes an argument file if the command line would exceed the OS length limit | Not handled — the full classpath is passed on the raw command line |
| `--debug-jvm` support | Yes, built in | No |
| Why used here | The plain path only needs "run this classpath + main class on a normal JVM" — exactly what `JavaExec` is for | `JavaExec`'s `executable` property (needed to point at the JBR binary) conflicts with Gradle's toolchain-derived `javaLauncher` convention once both are set, so pointing at a custom JVM has to bypass `JavaExec` entirely and build the command line by hand |

In short: `JavaExec` = "let Gradle be smart about running Java for you"; `Exec` = "get
out of the way, here's the exact command." The default path keeps `JavaExec` so it loses
none of Gradle's built-in safety nets; only the enhanced path pays the cost of manual
command-line construction, because that's the only way to point at a non-default JVM
binary.
