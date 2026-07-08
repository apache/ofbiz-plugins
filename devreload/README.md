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

Same command, with enhanced class redefinition turned on: it now runs on a
DCEVM-patched JVM instead of a stock JDK and additionally hot-swaps structural changes
live, with no restart. Requires a DCEVM-patched JVM, pointed at explicitly with
`-PdcevmHome=/path/to/jvm` (the folder containing `bin/java`) or the `DCEVM_HOME` env
var — this is not auto-detected, since IDE install locations vary too much across
OS/Toolbox/version to guess reliably. JetBrains Runtime, which bundles DCEVM, ships
with IntelliJ IDEA under `<IDE install>/jbr` (`.../Contents/jbr` on macOS) and is the
easiest way to get one; standalone DCEVM builds work too.

DCEVM stands for **Dynamic Code Evolution Virtual Machine**. It's a patch to the HotSpot
JVM that lifts the stock class-redefinition restriction to method bodies only, so
structural changes (added/removed methods or fields, changed signatures) can also be
hot-swapped into a running JVM instead of requiring a restart.

Both also support scoping to specific components for a faster startup:

```
./gradlew ofbizDev -Photreload.components=devreload,party
./gradlew ofbizDev -Photreload.enhanced=true -Photreload.components=devreload,party
```

### Default vs `-Photreload.enhanced=true`

| Aspect | Default | `-Photreload.enhanced=true` |
|---|---|---|
| JVM | stock JDK (whatever Gradle resolves normally) | DCEVM-patched JVM, pointed at explicitly via `-PdcevmHome`/`DCEVM_HOME` |
| Method body edits | hot-swapped live | hot-swapped live |
| `services.xml` edits | hot-swapped live | hot-swapped live |
| Structural changes (new/removed method or field, changed signature) | needs a restart | hot-swapped live |
| Extra JVM flag | — | `-XX:+AllowEnhancedClassRedefinition` |
| Requires a DCEVM-patched JVM | no | yes — fails fast with a clear error if none is found |

`--no-watch-fs` is recommended whenever you're running unscoped (no
`-Photreload.components=...`) on a full checkout, with or without the enhanced flag:
Gradle's own file-system watching competes with this plugin's `WatchService` for the
same macOS per-process directory-watch ceiling, and either one can push you over it on a
checkout the size of OFBiz trunk (~50+ components). Scoping with
`-Photreload.components=...` reduces the plugin's own watch count and is often enough
on its own.

### One Gradle task, one mechanism, for both modes

`ofbizDev` is a single `JavaExec` task regardless of `-Photreload.enhanced=true` --
`JavaExec`'s `executable` property is simply pointed at the resolved DCEVM JVM binary
when enhanced mode is requested, leaving it unset (Gradle's normal default) otherwise.
That keeps `JavaExec`'s built-in safety nets (automatic classpath-argfile handling for
very long classpaths on Windows, `--debug-jvm` support) working in both modes, not just
the default one.

One non-obvious wrinkle: `executable` has to be set directly in the task's
configuration block, not from a task action (e.g. inside `doFirst`). Doing it from a
task action fails with `Toolchain from executable property does not match toolchain
from javaLauncher property` -- by the time a task action runs, `javaLauncher`'s
convention has already been finalized for execution, and overriding `executable` at
that point conflicts with it. Setting it during configuration, before that convention
finalizes, works cleanly. (No toolchain is actually configured anywhere in this
project, so nothing else here depends on this distinction -- it's specifically a
quirk of *when* `executable` is assigned relative to `javaLauncher`'s convention
resolution.)
