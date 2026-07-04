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
./gradlew ofbizDevEnhanced --no-watch-fs
```

Starts OFBiz on a JVM with enhanced class redefinition support, instead of a stock JDK.
On top of everything `ofbizDev` does, it additionally hot-swaps structural changes live
as well, with no restart. `--no-watch-fs` is recommended on a full checkout to avoid a
directory-watch resource ceiling we found and root-caused during testing.

Both commands also support scoping to specific components for a faster startup:

```
./gradlew ofbizDev -Photreload.components=devreload,party
./gradlew ofbizDevEnhanced --no-watch-fs -Photreload.components=devreload,party
```
