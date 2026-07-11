# devreload

Development-only hot-reload for OFBiz. Edit a Java service/event method, a
`services.xml` file, or add a brand-new method, and the change is live in under a
second — no restart, ever.

The plugin is entirely self-contained: dropping this directory into a checkout (or
removing it) has zero effect on the rest of OFBiz either way.

## Requirements

This plugin needs a **DCEVM (Dynamic Code Evolution Virtual Machine)** JVM to run.
Set it up once, before running `./gradlew ofbizDev --no-watch-fs`:

1. Get a DCEVM-patched JVM. Easiest source: JetBrains Runtime (JBR), bundled with
   IntelliJ IDEA under `<IDE install>/jbr` (`.../Contents/jbr` on macOS). Standalone
   DCEVM builds work too.
2. Point at it — set the `DCEVM_HOME` env var (e.g. in your shell profile) so every
   future run picks it up automatically, or pass `-PdcevmHome=/path/to/jvm` each time
   instead.

Add this to your shell profile (`~/.zshrc`, `~/.bashrc`, etc.) so it's always set:

```
export DCEVM_HOME="/Applications/IntelliJ IDEA.app/Contents/jbr"
```

(adjust the path to wherever your DCEVM-patched JVM lives — on macOS this is IntelliJ's
bundled JetBrains Runtime by default). Reload your shell (or run `source ~/.zshrc`) and
every future `./gradlew ofbizDev --no-watch-fs` picks it up automatically, with nothing
else to set.

## Usage

```
./gradlew ofbizDev --no-watch-fs
```

This is the only supported command — always run it exactly like this. It boots OFBiz
and everything hot-swaps live, no restart: method-body edits, `services.xml` changes,
and structural changes (new/removed methods or fields, changed signatures) alike.

`--no-watch-fs` disables Gradle's own file-system watching, which otherwise competes
with this plugin's `WatchService` for the same macOS per-process directory-watch
ceiling on a full checkout — without it, some directories can silently go unwatched.
It's harmless to include even on a smaller/scoped checkout, so it's part of the one
command to remember.

If DCEVM isn't set up, it fails immediately — before compiling or booting anything —
with the same setup steps as above.

Scope to specific components for a faster startup:

```
./gradlew ofbizDev --no-watch-fs -Photreload.components=devreload,party
```

---
# devreload — Dev Notes

`devreload` is a development-only OFBiz plugin that removes the restart step
from the edit → test loop. Save a `.java` file or a `services.xml` file and
the change is live in well under a second, in the already-running OFBiz
process.

It lives at `plugins/devreload` and is completely self-contained: dropping the
folder into a checkout adds the feature, deleting it removes the feature,
with zero effect on the rest of OFBiz either way.

---

## 1. The problem it solves

Without `devreload`, changing one line of Java meant:

```
edit .java → ./gradlew classes → kill OFBiz → wait 30-60s → restart → log in again → test
```

With `devreload` running (`./gradlew ofbizDev --no-watch-fs`):

```
edit .java          → save → compiled automatically → live in < 1s → test
edit *services.xml  → save → picked up automatically → live in < 1s → test
```

No restart, no re-login, no second terminal.

---

## 2. Who it's for / non-technical summary

| Question | Answer |
|---|---|
| Who benefits? | Any developer actively writing/debugging OFBiz Java services, event handlers, or `services.xml` files |
| Does it affect production? | No. It is completely inert unless started with a special flag (`-Dofbiz.hotreload=true`). Safe to have installed anywhere |
| Does it change OFBiz core code? | No. The current design needs zero changes to `ofbiz-framework` — it's a plugin only |
| What do I run? | One command: `./gradlew ofbizDev --no-watch-fs` |
| What do I need installed? | A DCEVM-patched JVM (e.g. JetBrains Runtime, bundled with IntelliJ IDEA) |

---

## 3. How it works (technical overview)

Four small classes do all the work:

| Class | Role |
|---|---|
| `DevReloadContainer` | Watches source/config directories, compiles changed Java in-process, and triggers reload |
| `HotSwapAgent` | A tiny self-attaching Java agent that gives `DevReloadContainer` access to `Instrumentation.redefineClasses` — the same mechanism an IDE debugger uses for HotSwap |
| `Debouncer<T>` | Coalesces rapid-fire change notifications (a burst of file-system events, or one compile producing several `.class` files) into a single batched action; shared by all three watch paths below |
| `RecordingFileManager` | Wraps the in-process compiler's file manager to record exactly which `.class` files it wrote, so the reload step hot-swaps precisely what was compiled instead of guessing from the source file's name |

At startup, `DevReloadContainer` self-attaches `HotSwapAgent` to the running
JVM (via the Attach API — no `-javaagent` flag needed). This grants access to
`Instrumentation`, which can replace the bytecode of an already-loaded class
in place. Because the `Class` object's identity never changes, every existing
reference to it (including OFBiz's own internal caches) automatically starts
running the new code on the very next call — no framework code needs to know
this plugin exists.

### The three watch paths

| Path | Watches | On change | Result |
|---|---|---|---|
| 1. Java source | Every component's `src/main/java` | Compiles the file in-process, then hot-swaps the resulting class(es) | New method bodies live in < 1s |
| 2. `services.xml` | Every component's `servicedef/` directory | Clears the `service.ModelServiceMapByModel` cache | OFBiz re-reads service definitions on the next call |
| 3. Build output (opt-in) | `build/classes/java/main` | Same hot-swap as Path 1 | Picks up `./gradlew -t classes` run in a second terminal |

Changes are debounced for 300ms so a burst of related file writes (e.g. one
compile producing several `.class` files) is handled as a single batch.

### Method-body edits vs. structural changes

| Change type | Stock JVM | DCEVM-patched JVM |
|---|---|---|
| Method body edit | ✅ Hot-swaps live | ✅ Hot-swaps live |
| New/removed method or field, changed signature ("structural change") | ❌ Requires restart | ✅ Hot-swaps live |
| Changed class hierarchy (superclass/interfaces) | ❌ Requires restart | ⚠️ Often hot-swaps live for classes with no existing instances (e.g. static-only service/event classes) — support varies by DCEVM build and isn't guaranteed; when the JVM can't apply it, that one class is rejected (see below), not silently broken |

Because structural changes are common during real development, `./gradlew
ofbizDev` **requires** a DCEVM-patched JVM and refuses to start without one —
rather than silently running in a degraded mode that "mostly" works.

Whether a given hierarchy change hot-swaps depends on the specific DCEVM build and
on whether instances of the class already exist — OFBiz's service/event classes are
static-only with no live instances, which is the case DCEVM handles best. When a
class's redefinition genuinely can't be applied, only that class is rejected (logged
by name); every other valid change saved in the same batch still applies. A rejected
class stays "stuck" against that diff until OFBiz restarts.

---

## 4. What does and doesn't hot-reload

| Works without restart | Needs a restart |
|---|---|
| Java method body changes | New OFBiz components (discovered at startup only) |
| New services / parameter changes in `services.xml` | `*UiLabels.xml` (loaded at startup) |
| New/removed methods, fields, changed signatures (DCEVM only) | `web.xml`, `*.properties` files |
| `controller.xml` (re-read every ~10s automatically — not tied to devreload at all) | `entitymodel*.xml` changes |
| | Changed class hierarchy (unreliable — often works for static-only classes with no instances, but isn't guaranteed) |

---

## 5. Usage

| Command | Effect |
|---|---|
| `./gradlew ofbizDev --no-watch-fs` | Start OFBiz with hot-reload enabled (the only supported way to run it) |
| `-Photreload.components=compA,compB` | Only watch these components — useful on a large checkout to stay under the OS's directory-watch limit |
| `-Photreload.watchBuildOutput=true` | Also watch Gradle's own build output for externally-compiled classes |
| `-PdcevmHome=/path/to/jvm` or `DCEVM_HOME` env var | Points at the DCEVM-patched JVM (not auto-detected, by design) |

`--no-watch-fs` disables Gradle's own file-watching, which otherwise competes
with `devreload`'s own watcher for the same OS-level directory-watch budget
(most noticeable on macOS on a full checkout).

---

## 6. Requirements

| Requirement | Why |
|---|---|
| A JDK, not just a JRE | In-process compilation uses `javax.tools.JavaCompiler` (`ToolProvider.getSystemJavaCompiler()`), which is only present in a full JDK. Running on a JRE disables Java auto-compilation (a warning is logged); `services.xml` reload still works |
| A DCEVM-patched JVM | Required by the `ofbizDev` Gradle task specifically (not by `DevReloadContainer` itself) so that structural changes (new/removed methods/fields, changed signatures) hot-swap instead of silently requiring an unannounced restart. Easiest source: JetBrains Runtime (JBR), bundled with IntelliJ IDEA under `<IDE install>/jbr` (`.../Contents/jbr` on macOS) |
| `-Djdk.attach.allowAttachSelf=true` | A JDK 9+ safeguard against a process attaching to itself; required for `HotSwapAgent` to self-attach. Set automatically by `./gradlew ofbizDev` |

---

## 7. System properties, Gradle properties, and paths

Everything `devreload` reads or writes, in one place.

| Property / path | Set by | Default | Purpose |
|---|---|---|---|
| `-Dofbiz.hotreload=true` | `ofbizDev` task (automatic) | unset (disabled) | Master on/off switch. Absent or not `"true"` → `DevReloadContainer.init()` returns immediately, fully inert |
| `-Djdk.attach.allowAttachSelf=true` | `ofbizDev` task (automatic) | unset | Lets `HotSwapAgent` self-attach via the Attach API |
| `-Dofbiz.hotreload.components` | `-Photreload.components=compA,compB` | unset (watch every component) | Comma-separated component names to scope watching to, so the total watched-directory count fits under the OS's per-process ceiling |
| `-Dofbiz.hotreload.watchBuildOutput` | `-Photreload.watchBuildOutput=true` | `false` | Whether `build/classes/java/main` is also watched, to pick up externally-produced `.class` files (e.g. `./gradlew -t classes` in a second terminal) |
| `-Dofbiz.hotreload.outputDir` | `ofbizDev` task (automatic) | `build/devreload/classes` | This plugin's own compiled-output directory. Cleared and recreated on every start; placed ahead of Gradle's own output on the runtime classpath |
| `-XX:+AllowEnhancedClassRedefinition` | `ofbizDev` task, only once a DCEVM JVM is resolved | not set on a stock JVM | Lifts the stock-JVM restriction so `redefineClasses` also accepts structural changes |
| `-PdcevmHome=/path/to/jvm` | manual, per invocation | unset | One-off override pointing at a DCEVM-patched JVM's home directory |
| `DCEVM_HOME` (env var) | manual, shell profile | unset | Same as `-PdcevmHome`, but persisted so every future `./gradlew ofbizDev` picks it up automatically |
| `dcevmHome` (in `~/.gradle/gradle.properties`) | manual, one-time | unset | Same idea as `DCEVM_HOME` but as a Gradle property instead of an env var — note the different casing/name, the two are not interchangeable |
| `build/classes/java/main` | Gradle (read-only from `devreload`'s perspective) | — | Gradle's normal compiled-output directory; only ever watched, never written to by this plugin |
| `build/devreload/classes` | `devreload` itself | — | This plugin's private compiled-output overlay; always wins over `build/classes/java/main` when a class exists in both |

---

## 8. Repository layout

| Repo | Contains | Why |
|---|---|---|
| `plugins/devreload` (a separate repo, dropped into a local checkout) | `DevReloadContainer.java`, `HotSwapAgent.java`, `Debouncer.java`, `RecordingFileManager.java`, `ofbiz-component.xml`, `build.gradle` (the `ofbizDev` task), `README.md` — everything | OFBiz plugins are conventionally distributed as separate repos; `ofbiz-framework`'s `.gitignore` excludes `/plugins/` for exactly this reason |
| `ofbiz-framework` | Nothing — no files, no diffs | The current design redefines already-loaded `Class` objects in place, so no framework code ever needs to ask "is a fresher class available." (An earlier prototype *did* need a small reflective bridge into `framework/base` — see the historical entries in the bug-fix table below for why that approach was replaced.) |

To use it in a checkout: `git clone <devreload-repo-url> plugins/devreload`,
then run `./gradlew ofbizDev --no-watch-fs`. Deleting `plugins/devreload/` at
any time removes the feature completely, with zero effect on the rest of
OFBiz.

---

## 9. Key design decisions

| Decision | Why |
|---|---|
| `Instrumentation`-based class redefinition instead of a custom classloader | Mutates the existing `Class` object in place — no second classloader to track, no cache invalidation, no framework code changes needed |
| Own output directory (`build/devreload/classes`), separate from Gradle's | Writing into Gradle's managed output could confuse its incremental-build cache and leave stale bytecode behind after a later `git checkout` |
| Overlay directory always wins over Gradle's output when both exist | Simple, predictable rule; matches its position on the runtime classpath |
| One shared `Debouncer` class for all three watch paths | Avoids three hand-rolled copies of the same concurrency logic that could drift out of sync |
| Explicit `-PdcevmHome`/`DCEVM_HOME` only, no auto-detection | Guessing IDE install paths breaks silently whenever a vendor changes packaging; one explicit input is easier to keep working |
| Failed directory watch = log a warning and skip it | No hidden fallback (like polling); a clear, actionable warning instead of silent degraded behavior |

---

## 10. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| A directory logs a "could not watch" warning, unscoped on a full checkout | OS directory-watch ceiling (e.g. macOS kqueue), possibly competing with Gradle's own file watching | `./gradlew ofbizDev --no-watch-fs`, and/or `-Photreload.components=compA,compB` to narrow the watched set |
| Log: "DevReloadContainer is disabled" | Missing `-Dofbiz.hotreload=true` | Use `./gradlew ofbizDev` (not `./gradlew ofbiz`) |
| `ofbizDev` task doesn't exist | `plugins/devreload` not present, or missing its `build.gradle` | `git clone` the plugin repo into `plugins/devreload` |
| Log: "classes directory not found" | `./gradlew classes` not run yet | `ofbizDev` runs `classes` automatically via `dependsOn` |
| Log: "JavaCompiler not available" | Running on a JRE, not a JDK | Install a JDK; use `./gradlew -t classes` in a second terminal as a fallback |
| Log: "compilation failed — fix the error and save again" | Syntax/type error in the saved `.java` file | The log line is followed by the real compiler diagnostics (`file:line: message`); fix the reported error and save again — the reload fires automatically |
| Log: "could not self-attach HotSwapAgent" | Missing `-Djdk.attach.allowAttachSelf=true` | Set automatically by `./gradlew ofbizDev`; add it explicitly if starting OFBiz another way |
| `services.xml` change not picked up | OFBiz restarted without going through `ofbizDev` | Only works when `-Dofbiz.hotreload=true` is set |
| A "just a method body" change still asks for a restart | Structural-change-only edit (new/removed method or field, changed signature) running on a stock JDK | Restart, or run on a DCEVM-patched JVM |
| Could not find a DCEVM-patched JVM | Neither `-PdcevmHome` nor `DCEVM_HOME` is set | Point at a JetBrains Runtime (`<IDE install>/jbr`) or standalone DCEVM build explicitly — not auto-detected |
| Same class keeps reporting a structural-change warning even after a pure method-body edit | Once a stock JVM rejects one structural change on a class, that class stays "stuck" until restart — every subsequent diff against its still-loaded old bytecode still includes the pending change | Expected JVM `redefineClasses` behavior, not a bug; restart to clear it |
| Log: "batch redefinition rejected (...) -- retrying each class individually", but only some classes in the save show "Hot-reload complete" | One class in a debounced batch has a change the JVM can't apply; the others were only rejected as part of the same all-or-nothing batch call | Expected fallback behavior, not a bug — every class the JVM *can* apply still succeeds individually; only the named, rejected class needs a restart |
| `<attribute>`/`<override>` schema warning (`cvc-complex-type.2.4.a`) repeats on every reload cycle | `<attribute>` elements must come before `<override>` per `services.xsd` | Reorder the elements in the edited `services.xml` |

---

## 11. Top 25 bug fixes

This component went through an earlier prototype design (a custom classloader
approach, living directly in `framework/base`) before settling on the current
`Instrumentation`-based design (a self-contained plugin). Fixes 1-14 are part
of the current, shipped design. Fixes 15-20 were lessons learned in the
earlier prototype; most of the mechanisms they touched no longer exist in the
current design (noted per row), but the underlying lesson is worth keeping.
Fixes 21-25 were found via later code-review passes on the current, shipped
design, after real-world use surfaced edge cases the original test scenarios
didn't happen to exercise.

| # | Bug | Impact if unfixed | Fix |
|---|---|---|---|
| 1 | Directory-watch failures on a full checkout crashed the entire OFBiz startup (`NoClassDefFoundError` from an unguarded fallback path) | OFBiz wouldn't start at all on a large, unscoped checkout | Catch failures per-directory and log a warning (`warnUnwatched`) instead of letting one bad directory abort the whole watch setup |
| 2 | Gradle's own file-system watcher competed with `devreload`'s watcher for the same OS directory-watch ceiling (macOS kqueue limit) | Directories silently went unwatched on a full checkout, even after fix #1 | Documented and defaulted to `--no-watch-fs` for the `ofbizDev` command |
| 3 | *(superseded by fix #23)* Inner and anonymous classes (e.g. `Foo$1.class`) weren't included when hot-reloading their outer class | Lambdas/anonymous classes kept running stale logic after a save | Originally fixed by scanning the compiled output for every `Outer.class` and `Outer$*.class` file, derived from the source file's own base name; superseded by fix #23's more general approach |
| 4 | Deleting a source file could trigger a reload attempt against a now-missing class | Confusing errors / crash on file deletion | `ENTRY_DELETE` events are explicitly ignored everywhere in the watch pipeline |
| 5 | A cache-clear step after class redefinition could throw an `Error` (not just an `Exception`), leaving the class updated but the service cache stale | Inconsistent state: new bytecode active, but old service definitions still cached | Widened the catch block to `catch (Throwable)` |
| 6 | `IllegalArgumentException: 'other' is different type of Path` when comparing an absolute path against a relative one | Every successful compile failed to trigger a reload | Kept all paths consistently relative throughout the compile/reload pipeline |
| 7 | Watch keys became invalid after the in-process compiler wrote new class files | Hot-reload would stop working after the very first compile | Re-register the compiled-output directory at the end of every compile |
| 8 | Shutting down while a compile finished could throw `ClosedWatchServiceException`, logged as a scary "compilation error" | Confusing noise in the logs on a completely normal shutdown | Catch `Exception` broadly (this exception isn't an `IOException`) around that step |
| 9 | Stopping the container while the watch thread was mid-event could throw `RejectedExecutionException` when scheduling a reload | Confusing stack trace during shutdown | Wrapped the scheduling call in a try/catch for this specific case |
| 10 | A single macOS `WatchService` event storm (one compiled file triggering change events for the *entire* class tree) caused hundreds of unrelated classes to be reloaded on every save | Noticeable slowdown on every save on macOS | Unified all reload triggers through one shared debouncer, so a storm just causes some harmless, bytecode-identical redundant reloads instead of a performance hit |
| 11 | A component with a `null` root location (no `rootLocation()`) threw a `NullPointerException` while resolving its `src/main/java` path | Startup crash — no source directories registered at all | Added a null guard before resolving the source path in `registerSourceDirs()` |
| 12 | An invalid/deleted watch key (`key.reset()` returning `false`) was silently ignored | WatchService could quietly stop detecting changes in a directory with no indication why | Check the return value and log a warning naming the directory |
| 13 | A `NoClassDefFoundError` (or similar) while registering one component's `servicedef` directory could abort the loop, leaving every later component's directory unwatched | Only the first few components' `services.xml` changes would ever be detected | Wrapped each component's registration in its own `catch (Throwable)` so one failure never blocks the rest |
| 14 | Comparing modification times across two output directories (overlay vs. Gradle's) to decide which compiled class "wins," including deleting the stale copy when Gradle's was newer | Subtle correctness risk: a timestamp comparison plus a reconciling side-effect (file deletion) that could easily fall out of sync with the classpath's actual resolution order | Simplified to one rule: the overlay directory always wins when a copy exists there — consistent with its fixed position on the classpath |
| 15 | *(historical, superseded)* Multi-cycle reload loss — editing file B produced a classloader that only knew about B; class A (changed in an earlier reload) fell back to its stale, startup-time version | Any class changed more than one reload ago silently regressed to old behavior | Prototype-only fix (an `allChangedClasses` accumulator); not applicable to the current design, since `redefineClasses` always applies the latest compiled bytecode directly with no classloader to lose track of anything |
| 16 | *(historical, superseded)* `ClassFormatError` (a `LinkageError`) escaped as an unhandled `Error` when a class was read mid-write by a racing compiler | Unhandled `Error` could crash a request in the servlet container | Prototype-only fix (catch `LinkageError` during classload); not applicable now — `applyCompile()` reads its own compiler output synchronously, so there's no separate framework-side read racing the write |
| 17 | *(historical, superseded)* Used `Thread.currentThread().getContextClassLoader()` as a parent reference from a background thread | Silent class-resolution failures if the servlet container mutated the thread's context classloader | Prototype-only fix (switched to a deterministic classloader reference); not applicable now — there's no custom classloader in the current design at all |
| 18 | *(historical)* A code comment claimed `synchronized(DevReloadContainer.class)` while the actual code used `synchronized(this)` | A future developer trusting the comment could introduce a real data race | Comment/code mismatch fixed by reverting to a `private final` instance field with unambiguous `synchronized(this)` |
| 19 | *(historical)* A single save produced two reload attempts for the same class — one direct call plus one from the WatchService event for the same `.class` file | Harmless but wasteful; duplicate "Hot-reload complete" log lines on every save | Originally patched with a timing-based suppression window; the current design's real fix is architectural (fix #10) — one shared debouncer per change type, so a duplicate at most causes one redundant, harmless redefinition |
| 20 | *(historical)* Hardcoded, per-OS guesses at common IDE install paths to auto-detect a DCEVM-patched JVM (IntelliJ on macOS/Linux/Windows, JetBrains Toolbox, etc.) | Silently broke for any install layout not on the guessed list — a "works for some users, not others" pattern that grew another special case with every new packaging variant | Removed all guessing in favor of one explicit, always-honored input: `-PdcevmHome` or `DCEVM_HOME` |
| 21 | A single unsupported structural change in a debounced batch caused `Instrumentation.redefineClasses` to reject the *entire* batch in one call, since every changed class's `ClassDefinition` was passed together | An unrelated, valid method-body edit that happened to land in the same ~300ms debounce window as a rejected class silently failed to apply too, with nothing in the log distinguishing it from the actual offender | `redefineWithPerClassFallback` tries the batch call first (the fast common path), and on `UnsupportedOperationException` retries each `ClassDefinition` individually — every class the JVM accepts is applied, and only the ones it genuinely rejects are named and left pending a restart |
| 22 | `applyCompile()` passed a `null` `DiagnosticListener` to `javax.tools.JavaCompiler`, so a failed in-process compile produced no detail about what actually failed | The log's only output on a broken save was the generic "compilation failed — fix the error and save again", with no file, line number, or compiler message to act on | Pass a `DiagnosticCollector<JavaFileObject>` to the compiler task and log each `ERROR`-level diagnostic (`file:line: message`) alongside the existing warning |
| 23 | The post-compile reload step guessed which `.class` files to hot-swap from the *source file's own base name* (`Outer.class`/`Outer$*.class`) | A secondary top-level class in the same source file, or a lone non-public top-level class named differently from its file (both legal Java), compiled to disk correctly but was never queued for redefinition — it silently kept running stale bytecode while the log claimed "Hot-reload complete" | Wrap the compiler's file manager in `RecordingFileManager`, which records the absolute path of every `.class` file the compiler actually writes via `getJavaFileForOutput`, and reload exactly that set instead of guessing from a name pattern |
| 24 | In-process compilation read source files using `Charset.defaultCharset()` (passed `null` to `getStandardFileManager`) | The project's real `compileJava` forces `options.encoding = 'UTF-8'` (root `build.gradle`); on any JVM/OS whose platform default charset isn't UTF-8 (pre-JDK18, or an overridden `-Dfile.encoding` — plausible on Windows), a hot-reload compile of a file with non-ASCII characters (i18n literals, non-English comments) decoded differently than Gradle's own compile, producing different compiled string constants or spurious compile errors that didn't reproduce outside `devreload` | Pass `StandardCharsets.UTF_8` explicitly to `getStandardFileManager` |
| 25 | A brand-new subdirectory created with files already inside it in one filesystem operation (e.g. a `git checkout` that adds a whole package, an IDE "extract to new package" refactor, or unzipping a folder into a watched source tree) could have those pre-existing files never picked up | The OS/JDK `WatchService` doesn't retroactively report `ENTRY_CREATE` for files that existed before their directory was registered, so such files silently never compiled until each was individually re-saved, with nothing in the log to explain why | New directories discovered via a live `ENTRY_CREATE` event are now walked with `registerAllAndSeed`, which also dispatches every regular file already present through the same `dispatchChangedFile` routing a live watch event would use — seeding just the newly-discovered subtree, not a full re-walk of already-known directories |
