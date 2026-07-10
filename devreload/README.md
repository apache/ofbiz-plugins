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
