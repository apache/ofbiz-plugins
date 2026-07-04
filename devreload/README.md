# devreload

Development-only hot-reload for OFBiz, with **zero dependency on framework code** —
this plugin is entirely self-contained. Dropping this directory (or setting
`enabled="false"` in its `ofbiz-component.xml`) removes it completely, with zero effect
on the rest of OFBiz. It is a strict no-op unless explicitly activated.

Watches compiled `.class` files, `services.xml` files, and (on a JDK) `.java` source
files, and reloads changed service/event classes and service definitions into a running
server without a restart.

## Usage

```
./gradlew ofbizDev
```

or, to activate hot-reload with any other start command:

```
-Dofbiz.hotreload=true -Djdk.attach.allowAttachSelf=true
```

While OFBiz is running, edit and save a `.java` file — this plugin compiles it in-process
(requires running on a JDK, not a JRE) and hot-swaps the result. Edits to existing
methods and `services.xml` changes go live within about 300ms, no restart needed.

Prefer compiling externally instead (e.g. running `./gradlew -t classes` in a second
terminal)? Add `-Dofbiz.hotreload.watchBuildOutput=true` (or
`-Photreload.watchBuildOutput=true` with the `ofbizDev`/`ofbizDevEnhanced` Gradle tasks)
to also watch `build/classes/java/main` for externally-produced `.class` files. This is
off by default: that tree isn't narrowed by `-Dofbiz.hotreload.components` the way
source/servicedef watching is, so on a full checkout it roughly doubles the total
directories watched for a workflow most people don't use — in-process compilation
already hot-swaps directly and never needs it.

## How it works

- **Java classes** — `DevReloadContainer` self-attaches a tiny agent (`HotSwapAgent`) to
  the current JVM via the standard Attach API (`com.sun.tools.attach.VirtualMachine`),
  obtaining a live `java.lang.instrument.Instrumentation`. When a watched `.class` file
  changes, it calls `Instrumentation.redefineClasses(...)` on the already-loaded
  `Class` object — the exact mechanism an IDE debugger uses for HotSwap. Because the
  `Class` object's identity never changes, every existing reference to it (including the
  event-class cache in `JavaEventHandler` and the per-call `loadClass` in
  `StandardJavaEngine`) automatically executes the new method body on the next call.
  Framework code is completely unaware this plugin exists — no cache, no classloader
  override, no reflective hook anywhere in `framework/`.
- **Brand-new classes** need no special handling: the in-process compiler writes them to
  the same `build/classes/java/main/` directory that's already on the running JVM's
  classpath, so they simply load normally the first time something references them.
- **`services.xml`** — every component's `servicedef/` directory is watched directly;
  on change, `UtilCache.clearCache("service.ModelServiceMapByModel")` is called
  in-process, so the new/edited definition is re-read on the next service call.
- **Container registration** — `ofbiz-component.xml` in this component declares its own
  `<container>` entry, auto-discovered by `ComponentConfig`/`ContainerLoader` like any
  other component. No framework file is touched.
- **Gradle task** — `build.gradle` in this component registers the `ofbizDev` task on
  the root project, including the `-Djdk.attach.allowAttachSelf=true` flag the
  self-attach step needs. No root `build.gradle` edit needed.
- **Poll fallback** — any directory that can't get a real `WatchService` registration
  (see "At scale" below) is polled every 2 seconds instead, so hot-reload still works
  everywhere, just with a little extra latency for whichever directories didn't fit.

## Structural changes (added/removed methods or fields) — `ofbizDevEnhanced`

Plain `Instrumentation.redefineClasses` can only replace method bodies and static
initializers of an already-loaded class. Adding/removing a method or field, changing a
method signature, or changing the class hierarchy on an *existing* loaded class normally
still requires a restart — this is a JVM limitation, not something this plugin works
around with extra code.

DCEVM patches lift exactly this restriction, and a JetBrains Runtime (JBR) — the JVM
IntelliJ IDEA itself bundles from 2024.2+ — already includes those patches. Since
`HotSwapAgent` just calls the same standard `Instrumentation.redefineClasses` API either
way, **no plugin code depends on which JVM is running** — running on a JBR with the
right flag is purely an environment choice:

```
./gradlew ofbizDevEnhanced --no-watch-fs
```

`--no-watch-fs` is recommended on every run of this task (see the callout right below for
why) — plain `./gradlew ofbizDevEnhanced` without it also works, just with the caveat
described there on a full checkout.

This locates a JetBrains Runtime (auto-detects the one bundled with a local IntelliJ IDEA
install, or point at one explicitly with `-PjbrHome=/path/to/jbr` or the `JBR_HOME` env
var) and adds `-XX:+AllowEnhancedClassRedefinition` — a flag that only exists on
DCEVM/JBR builds and fails hard on a stock JDK, which is why it's a separate task rather
than baked into `ofbizDev`. `DevReloadContainer` logs at startup whether it detected this
flag, so you always know which mode you're in. Verified end-to-end: adding a brand-new
method to an already-loaded class applied live, with zero restart, running this way.

> **Why `--no-watch-fs`:** on an unscoped trunk checkout, `ofbizDevEnhanced` launched
> through a normal, persistent Gradle daemon has been observed hitting macOS's
> per-process kqueue directory-watch ceiling (see "At scale" below) *during startup
> itself* — root-caused to Gradle's own file-system watching (`--watch-fs`, on by default
> since Gradle 7+), which keeps its own persistent watches over this same project tree
> for incremental-build purposes and competes with `DevReloadContainer`'s watches for
> the same ceiling. This is purely a Gradle build-performance feature with no effect on
> OFBiz itself or on any other terminal/invocation — disabling it here only affects this
> one command's own file-change bookkeeping. Confirmed two independent ways to
> avoid the collision entirely: launching the same JVM directly (bypassing the Gradle daemon
> altogether) and passing `--no-watch-fs` to keep the daemon but disable its own
> watching — both gave a fully healthy, unscoped startup with zero watch failures.
> Raising `ulimit -n` does **not** fix it (tested well past the OS's own per-process
> ceiling with no change), confirming this is kqueue's own internal watch-table limit,
> not a file-descriptor problem. `-Photreload.components=...` scoping (below) still
> works and remains useful for faster startup, but is no longer the only lever here. When
> the ceiling is hit despite these mitigations, the affected directories are now (as of
> the fix in `DevReloadContainer`) logged and skipped rather than crashing the whole JVM.

## At scale: nothing is ever silently missed

Recursively watching every component's source tree does not scale indefinitely: macOS's
`WatchService` registers one kqueue vnode watch per directory and has a practical
per-process ceiling well under a typical `ulimit -n` (observed around ~10-12k watches,
independent of the configured file-descriptor limit) — a checkout the size of OFBiz
trunk (~50+ components) can exceed it.

Two things narrow that count before it ever gets to the ceiling: `src/main/java` and
`servicedef/` watching is scoped by `-Dofbiz.hotreload.components` (see below), and
`build/classes/java/main` isn't watched at all unless you opt in with
`-Dofbiz.hotreload.watchBuildOutput=true` — that tree mirrors every component's source
tree but, unlike source/servicedef watching, isn't itself narrowed by component scoping
(compiled output isn't organized per component the way source is), so leaving it off by
default roughly halves the total directories watched for the common in-process-compile
workflow, which never needed it in the first place.

`registerAll` tries a real, instant, event-driven watch for every directory first. Any
directory that fails that registration falls back to being polled every 2 seconds
instead — so nothing is ever silently left unwatched; it just degrades from instant to a
couple of seconds of latency for whichever directories didn't fit. New subdirectories
discovered while polling get their own real-watch attempt too, falling back to polling
again themselves if needed, so the poll set only ever covers exactly what doesn't fit.
When this happens, `DevReloadContainer` logs a summary once at startup. (The fallback
path itself is guarded against throwing — a directory whose watch registration *and*
poll-fallback setup both fail is logged and left unwatched/unpolled rather than being
allowed to crash the whole container, which has been observed happening under heavy
watch-ceiling pressure; see the `ofbizDevEnhanced` note above.)

```
Hot-reload: N of M directory watch registrations hit file-descriptor/watch exhaustion
(see warnings above for which ones) and are now polled every 2000ms instead of
instantly. ...
```

### Scoping to specific components (optional, for speed)

The poll fallback above is enough of a safety net that scoping is purely optional — a
nice-to-have for a quicker startup while working on a couple of components. For
`ofbizDevEnhanced` on a full trunk-sized checkout specifically, `--no-watch-fs` (see the
callout above) is the fix for the directory-watch ceiling; scoping is complementary on
top of that, not a substitute for it. Scope to just the components you're actively
working on:

```
./gradlew ofbizDev -Photreload.components=devreload,party
./gradlew ofbizDevEnhanced -Photreload.components=devreload,party
```

Both `servicedef/` and `src/main/java` watching are limited to the named components;
everything else about hot-reload behaves the same.
