# devreload

Development-only hot-reload for OFBiz. Watches compiled `.class` files, `services.xml`
files, and (on a JDK) `.java` source files, and reloads changed service/event classes
and service definitions into a running server without a restart.

This is a plugin, not a framework feature: dropping this directory (or setting
`enabled="false"` in its `ofbiz-component.xml`) removes it completely, with zero
effect on the rest of OFBiz. It is a strict no-op unless explicitly activated.

## Usage

```
./gradlew ofbizDev
```

or, to activate hot-reload with any other start command:

```
-Dofbiz.hotreload=true
```

While OFBiz is running, either:

- edit and save a `.java` file — this plugin compiles it in-process (requires running
  on a JDK, not a JRE) and hot-swaps the result, or
- run `./gradlew -t classes` in a second terminal for continuous external compilation.

Either way, edits to existing methods, newly added methods, and `services.xml` changes
go live within about 300ms, no restart needed.

## How it integrates with core

- **Container registration** — `ofbiz-component.xml` in this component declares its own
  `<container>` entry, auto-discovered by `ComponentConfig`/`ContainerLoader` like any
  other component. No framework file is touched.
- **Gradle task** — `build.gradle` in this component registers the `ofbizDev` task on
  the root project. No root `build.gradle` edit needed.
- **Picking up hot-swapped classes** — the service engine (`StandardJavaEngine`) and
  the Java event handler (`JavaEventHandler`) consult
  `org.apache.ofbiz.base.container.DevReloadHook`, a small reflective bridge that lives
  in `framework/base`. It looks up this plugin's `DevReloadContainer` by class name at
  class-init time and returns `null` if the plugin isn't present — so framework code
  has no compile- or runtime-dependency on this plugin, and behaves exactly as upstream
  when this directory is absent.
