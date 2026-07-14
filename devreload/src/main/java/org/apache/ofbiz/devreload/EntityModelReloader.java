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

import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.cache.UtilCache;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.DelegatorFactory;
import org.apache.ofbiz.entity.config.model.DelegatorElement;
import org.apache.ofbiz.entity.config.model.EntityConfig;
import org.apache.ofbiz.entity.datasource.GenericHelperInfo;
import org.apache.ofbiz.entity.jdbc.DatabaseUtil;
import org.apache.ofbiz.entity.model.ModelEntity;
import org.apache.ofbiz.entity.model.ModelGroupReader;
import org.apache.ofbiz.entity.model.ModelReader;

/**
 * Forces already-running {@link ModelReader}/{@link ModelGroupReader} singletons to
 * forget their parsed entity model/group mapping and re-read it from disk, so edits to
 * an {@code entitydef/*.xml} file take effect in the already-running JVM without a
 * restart.
 *
 * <h2>Why this needs reflection</h2>
 * Unlike {@code services.xml} reload (which just clears a {@code UtilCache} that the
 * service engine looks up <em>by name on every call</em>), entity reload can't work
 * that way: every {@code GenericDelegator} grabs a direct object reference to a
 * {@link ModelReader} at construction time and keeps it
 * (see {@code GenericDelegator.modelReader}), and {@code ModelReader} itself has no
 * public API to make it forget its parsed {@code entityCache} once built — it's a
 * {@code private volatile} field populated once via double-checked locking and never
 * reset. Clearing the {@code ModelReader.READERS} cache (the {@code services.xml}
 * trick) would therefore only affect a brand-new delegator; every delegator already
 * serving traffic would keep using its already-fetched, stale {@code ModelReader}
 * forever. {@link ModelGroupReader} (entitygroup.xml → datasource-group mapping) has
 * the exact same shape: a static {@code READERS} cache plus a private, build-once
 * {@code groupCache} field.
 *
 * <p>The only way to make the model already in use forget itself, without changing
 * {@code framework/entity}, is to reflectively null out that private field on the
 * singleton(s) already sitting in each class's {@code READERS} cache — every delegator
 * sharing a reader name (almost always {@code "main"}) picks up the change for free,
 * since they share the same object. The underlying XML itself needs no separate cache
 * invalidation: {@code MainResourceHandler.getDocument()} re-reads its
 * {@code InputStream} fresh on every call.
 *
 * <p>All reflective {@link Field} handles are resolved once, the first time they're
 * needed, and cached: a resolution failure (e.g. a future OFBiz version renaming one
 * of these private fields) is logged once with the exact class/field name and
 * disables that half (entity or group) of the reload for the rest of the session,
 * rather than failing on every save or crashing the container.
 */
final class EntityModelReloader {

    private static final String MODULE = EntityModelReloader.class.getName();

    private static volatile boolean resolutionAttempted;

    private static volatile boolean entityReloadAvailable;
    private static Field modelReaderReadersField;
    private static Field modelReaderEntityCacheField;
    private static Field modelReaderModelNameField;

    private static volatile boolean groupReloadAvailable;
    private static Field modelGroupReaderReadersField;
    private static Field modelGroupReaderGroupCacheField;
    private static Field modelGroupReaderModelNameField;

    private static volatile boolean delegatorCacheClearAvailable;
    private static Field delegatorFactoryDelegatorsField;

    private EntityModelReloader() { }

    /**
     * Reflectively resets every currently-cached {@link ModelReader}'s parsed entity
     * model and eagerly rebuilds it in place (rather than leaving the rebuild for the
     * next unrelated request to trigger lazily), so a broken {@code entitydef} save is
     * caught and logged right here instead of surfacing as a random failure later.
     *
     * <p>{@code ModelReader.getEntityCache()} populates its map field directly as it
     * parses, rather than building into a local variable and swapping it in at the
     * end. That means a parse failure partway through (bad XML, a view-entity
     * referencing a non-existent member entity, etc.) would normally leave the field
     * non-null but incomplete — and since the reader's own double-checked-locking guard
     * only rebuilds when the field is {@code null}, that broken, partial model would
     * otherwise be served forever, even after the mistake is fixed. Because this class
     * controls the field directly, a failed rebuild re-nulls it before returning, so
     * the very next save (or even the next unrelated access, at reparse cost) retries
     * the parse instead of staying poisoned until a restart.
     *
     * @return {@code true} if every cached {@link ModelReader} rebuilt cleanly.
     */
    static boolean resetAndRebuildEntityModels() {
        ensureFieldsResolved();
        if (!entityReloadAvailable) {
            return false;
        }

        UtilCache<String, ModelReader> readers;
        try {
            readers = castCache(modelReaderReadersField.get(null));
        } catch (ReflectiveOperationException | ClassCastException e) {
            Debug.logError(e, "Hot-reload: could not read ModelReader.READERS; entity model reload is disabled "
                    + "for this session.", MODULE);
            entityReloadAvailable = false;
            return false;
        }

        boolean allSucceeded = true;
        for (ModelReader reader : readers.values()) {
            allSucceeded &= rebuildOneEntityReader(reader);
        }
        return allSucceeded;
    }

    /**
     * Reflectively resets every currently-cached {@link ModelGroupReader}'s parsed
     * entity-group mapping and eagerly rebuilds it in place. Same rationale and
     * self-healing behavior as {@link #resetAndRebuildEntityModels()}; kept as a
     * separate entry point (and a separate availability flag) so a reflection failure
     * against one class never disables the other.
     *
     * @return {@code true} if every cached {@link ModelGroupReader} rebuilt cleanly.
     */
    static boolean resetAndRebuildGroupModels() {
        ensureFieldsResolved();
        if (!groupReloadAvailable) {
            return false;
        }

        UtilCache<String, ModelGroupReader> readers;
        try {
            readers = castCache(modelGroupReaderReadersField.get(null));
        } catch (ReflectiveOperationException | ClassCastException e) {
            Debug.logError(e, "Hot-reload: could not read ModelGroupReader.READERS; entitygroup.xml reload is "
                    + "disabled for this session.", MODULE);
            groupReloadAvailable = false;
            return false;
        }

        boolean allSucceeded = true;
        for (ModelGroupReader reader : readers.values()) {
            allSucceeded &= rebuildOneGroupReader(reader);
        }
        return allSucceeded;
    }

    /**
     * Clears every already-created {@link Delegator}'s data caches, local-only (no
     * distributed-cache-clear broadcast — this is a single-process dev loop, and
     * distributing would mean depending on {@code DistributedCacheClear} being
     * configured/reachable at all, which is beside the point here).
     *
     * <p>Needed because a {@link org.apache.ofbiz.entity.GenericEntity} caches its
     * {@code ModelEntity} reference in a transient field the first time it's asked
     * (see {@code GenericEntity.getModelEntity()}) and never re-asks after that. A
     * long-lived cached {@code GenericValue} created before this reload would otherwise
     * keep pointing at a stale field/relation set forever. Called after every successful
     * entity-model rebuild, unscoped (every delegator, every entity) rather than
     * diffing which entities actually changed — simpler, and this only runs on a dev
     * save, never a hot request path.
     *
     * <p>{@link DelegatorFactory} keeps every delegator it has ever created in a
     * private static map, keyed by name, as a {@code Future} (each delegator is built
     * asynchronously). Reflection is needed to enumerate that map at all; only
     * {@link Future#isDone()} entries are touched, so this never blocks on, or
     * accidentally triggers, a delegator that's still starting up.
     */
    static void clearAllDelegatorCaches() {
        forEachLiveDelegator((name, delegator) -> {
            delegator.clearAllCaches(false);
            Debug.logInfo("Hot-reload: cleared data caches for delegator '" + name + "'", MODULE);
        });
    }

    /**
     * Opt-in schema auto-sync (design doc "Part B"): for every entity/view-entity whose
     * definition file is in {@code changedFiles}, create any table/column it needs that
     * doesn't exist yet. Off unless {@code DevReloadContainer} calls this at all (gated
     * there by {@code -Dofbiz.hotreload.autoUpdateSchema=true}) -- creating tables/
     * columns is a meaningfully bigger blast radius than anything else this plugin does
     * automatically, so it stays opt-in rather than always-on like the rest of entitydef
     * reload.
     *
     * <p>Uses only public {@code framework/entity} API, no reflection: {@code
     * DatabaseUtil(GenericHelperInfo).checkDb(Map, List, addMissing=true)} is the exact
     * call {@code EntityDataServices}/webtools' "Update Database" screen use, and per
     * its own implementation only ever adds -- it never drops, renames, or alters the
     * type of anything that already exists. Anything beyond that (a changed field's SQL
     * type, a removed field, a changed primary key) still requires that same manual
     * flow, on purpose.
     */
    static void syncMissingSchema(Set<Path> changedFiles) {
        Set<String> changedEntityNames = resolveChangedEntityNames(changedFiles);
        if (changedEntityNames.isEmpty()) {
            return;
        }
        forEachLiveDelegator((name, delegator) -> syncMissingSchemaForDelegator(name, delegator, changedEntityNames));
    }

    /**
     * Resolves {@code changedFiles} to the entity/view-entity names actually defined in
     * them, so schema sync only ever touches what changed in this save -- not the whole
     * data model, which is what keeps it fast enough to run on every save instead of
     * only at startup. {@code ModelEntity.getLocation()} is set fresh on every entity
     * object every time {@code ModelReader.getEntityCache()} runs (see {@code
     * buildEntity()} in {@code framework/entity}), so comparing it against the changed
     * paths is reliable even across repeated reloads within the same session -- unlike
     * {@code ModelReader.getResourceHandlerEntities()}, whose backing collections are
     * never cleared between reloads and would otherwise need extra bookkeeping here to
     * stay accurate.
     */
    private static Set<String> resolveChangedEntityNames(Set<Path> changedFiles) {
        ensureFieldsResolved();
        if (!entityReloadAvailable) {
            return Set.of();
        }
        Set<String> entityNames = new HashSet<>();
        try {
            UtilCache<String, ModelReader> readers = castCache(modelReaderReadersField.get(null));
            for (ModelReader reader : readers.values()) {
                for (Map.Entry<String, ModelEntity> entry : reader.getEntityCache().entrySet()) {
                    if (locationMatches(entry.getValue().getLocation(), changedFiles)) {
                        entityNames.add(entry.getKey());
                    }
                }
            }
        } catch (Exception e) {
            Debug.logError(e, "Hot-reload: could not determine which entities changed for schema auto-update",
                    MODULE);
        }
        return entityNames;
    }

    private static boolean locationMatches(String location, Set<Path> changedFiles) {
        if (location == null) {
            return false;
        }
        try {
            return changedFiles.contains(Paths.get(new URI(location)));
        } catch (URISyntaxException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Resolves the datasource group(s) {@code changedEntityNames} belong to on
     * {@code delegator} -- resolved per delegator, not shared the way the
     * {@code ModelEntity} objects themselves are, since a delegator's group→datasource
     * mapping (and, for a multi-tenant setup, which physical database that resolves to)
     * is delegator-specific -- then runs one non-destructive {@code checkDb} per group.
     *
     * <p>Each {@code checkDb} call is passed the <em>entire</em> group's entities via
     * {@link Delegator#getModelEntityMapByGroup}, not just {@code changedEntityNames}.
     * This was originally scoped to just the changed entities to keep each call cheap,
     * but live testing surfaced why that's wrong: {@code checkDb} treats any DB table
     * without a matching entry in the map it's given as orphaned and logs a warning for
     * it (both via its own internal {@code Debug.logWarning} and via the {@code messages}
     * list) -- passing a handful of changed entities out of an entire datasource's
     * worth of tables made every other real, legitimate table in that datasource look
     * orphaned, flooding the log on every single save. Passing the full group avoids
     * that false-positive path entirely, matches how {@code checkDb} is used everywhere
     * else in the framework, and is still far cheaper than checking the whole
     * multi-datasource data model on every save, since only the group(s) the changed
     * entities actually belong to are checked.
     */
    private static void syncMissingSchemaForDelegator(String delegatorName, Delegator delegator,
            Set<String> changedEntityNames) {
        Set<String> affectedGroups = new HashSet<>();
        for (String entityName : changedEntityNames) {
            try {
                String groupName = delegator.getModelGroupReader().getEntityGroupName(entityName,
                        delegator.getDelegatorBaseName());
                if (groupName != null) {
                    affectedGroups.add(groupName);
                }
            } catch (Exception e) {
                Debug.logWarning("Hot-reload: could not resolve a datasource group for entity '" + entityName
                        + "' on delegator '" + delegatorName + "' -- skipping its schema auto-update: "
                        + e.getMessage(), MODULE);
            }
        }

        for (String groupName : affectedGroups) {
            try {
                GenericHelperInfo helperInfo = delegator.getGroupHelperInfo(groupName);
                if (helperInfo == null) {
                    continue;
                }
                Map<String, ModelEntity> groupEntities = delegator.getModelEntityMapByGroup(groupName);
                List<String> messages = new ArrayList<>();
                new DatabaseUtil(helperInfo).checkDb(groupEntities, messages, true);
                for (String message : messages) {
                    if (isNoiseMessage(message)) {
                        continue;
                    }
                    Debug.logInfo("Hot-reload: [schema sync " + delegatorName + "/" + groupName + "] "
                            + message, MODULE);
                }
            } catch (Throwable t) {
                Debug.logError(t, "Hot-reload: schema auto-update failed for delegator '" + delegatorName
                        + "', group '" + groupName + "'", MODULE);
            }
        }
    }

    /**
     * Filters out the two {@code checkDb} message shapes that are routine narration,
     * not something a save-time schema sync should surface, found empirically while
     * validating this feature against a real group with over a thousand entities
     * (framework's own {@code "org.apache.ofbiz"} group):
     * <ul>
     *   <li>{@code "(Xms) Checking #N/M Entity NAME with table TABLE"} / the
     *       {@code "NOT Checking"} variant for views and never-check entities --
     *       {@code checkDb} logs one of these per entity in the map it's given
     *       ({@code Debug.logVerbose} on the framework side, deliberately not surfaced
     *       here at {@code INFO} for every entity in the whole group on every save).</li>
     *   <li>{@code "... has no corresponding entity"} -- would only ever fire here if a
     *       table genuinely has no entity anywhere in the group ({@link
     *       #syncMissingSchemaForDelegator} always passes the full group's entities, so
     *       this isn't the false-positive scoping artifact an earlier, narrower version
     *       of this method produced; see that method's javadoc). Still filtered:
     *       {@code addMissing} never acts on it (it only adds, never drops), and an
     *       automatic save-time reload repeating the same unaddressable warning on every
     *       future save is just noise -- a deliberate "Update Database" run in webtools
     *       is the place to review genuinely-orphaned tables.</li>
     * </ul>
     * Every other {@code checkDb} message (created/added, could-not-add, size/type
     * mismatches, FK/index messages) passes through, so a future message shape this
     * class hasn't seen before is surfaced by default rather than silently dropped.
     */
    private static boolean isNoiseMessage(String message) {
        return message.contains("Checking #") || message.contains("has no corresponding entity");
    }

    /**
     * Enumerates every already-created {@link Delegator} (see {@link
     * #clearAllDelegatorCaches()}'s original javadoc for how/why) and invokes
     * {@code action} for each one that's finished starting up. Shared by {@link
     * #clearAllDelegatorCaches()} and {@link #syncMissingSchema(Set)} so the
     * reflection/iteration/error-handling boilerplate exists in exactly one place.
     *
     * <p>{@link DelegatorFactory} keeps every delegator it has ever created in a
     * private static map, keyed by name, as a {@code Future} (each delegator is built
     * asynchronously). Reflection is needed to enumerate that map at all; only
     * {@link Future#isDone()} entries are touched, so this never blocks on, or
     * accidentally triggers, a delegator that's still starting up.
     */
    private static void forEachLiveDelegator(BiConsumer<String, Delegator> action) {
        ensureFieldsResolved();
        if (!delegatorCacheClearAvailable) {
            return;
        }

        Map<String, Future<Delegator>> delegators;
        try {
            delegators = castMap(delegatorFactoryDelegatorsField.get(null));
        } catch (ReflectiveOperationException | ClassCastException e) {
            Debug.logError(e, "Hot-reload: could not read DelegatorFactory.DELEGATORS; entity data-cache "
                    + "invalidation/schema auto-update is disabled for this session (definitions still reload).",
                    MODULE);
            delegatorCacheClearAvailable = false;
            return;
        }

        for (Map.Entry<String, Future<Delegator>> entry : delegators.entrySet()) {
            if (!entry.getValue().isDone()) {
                continue; // still starting up -- nothing to act on yet
            }
            try {
                action.accept(entry.getKey(), entry.getValue().get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                Debug.logError(e, "Hot-reload: delegator action failed for '" + entry.getKey() + "'", MODULE);
            }
        }
    }

    /** Nulls {@code reader}'s {@code entityCache} field, then eagerly rebuilds it, self-healing on failure. */
    private static boolean rebuildOneEntityReader(ModelReader reader) {
        String readerName = readField(modelReaderModelNameField, reader);
        long start = System.currentTimeMillis();
        try {
            modelReaderEntityCacheField.set(reader, null);
            int entityCount = reader.getEntityCache().size();
            Debug.logInfo("Hot-reload: entity model '" + readerName + "' reloaded (" + entityCount
                    + " entities/view-entities) in " + (System.currentTimeMillis() - start) + "ms", MODULE);
            return true;
        } catch (Throwable t) {
            // Re-null rather than leaving a partially-populated map in place -- see this
            // class's javadoc for why a half-built entityCache would otherwise be served
            // forever instead of retried on the next save.
            reNullQuietly(modelReaderEntityCacheField, reader, "entityCache", readerName);
            Debug.logError(t, "Hot-reload: failed to reload entity model '" + readerName
                    + "' -- fix the entitydef XML and save again.", MODULE);
            return false;
        }
    }

    /** Nulls {@code reader}'s {@code groupCache} field, then eagerly rebuilds it, self-healing on failure. */
    private static boolean rebuildOneGroupReader(ModelGroupReader reader) {
        String readerName = readField(modelGroupReaderModelNameField, reader);
        String delegatorName = resolveDelegatorNameForGroupReader(readerName);
        if (delegatorName == null) {
            Debug.logWarning("Hot-reload: could not find a delegator whose entity-group-reader is '" + readerName
                    + "' in entityengine.xml -- skipping entitygroup reload for this reader.", MODULE);
            return false;
        }
        long start = System.currentTimeMillis();
        try {
            modelGroupReaderGroupCacheField.set(reader, null);
            int groupCount = reader.getGroupCache(delegatorName).size();
            Debug.logInfo("Hot-reload: entity group model '" + readerName + "' reloaded (" + groupCount
                    + " entity-group mappings) in " + (System.currentTimeMillis() - start) + "ms", MODULE);
            return true;
        } catch (Throwable t) {
            reNullQuietly(modelGroupReaderGroupCacheField, reader, "groupCache", readerName);
            Debug.logError(t, "Hot-reload: failed to reload entity group model '" + readerName
                    + "' -- fix the entitygroup XML and save again.", MODULE);
            return false;
        }
    }

    /**
     * {@code ModelGroupReader.getGroupCache(String delegatorName)} takes a delegator
     * name purely to validate each group against {@code entityengine.xml} while
     * rebuilding (see its javadoc in {@code framework/entity}) -- it's not stored on
     * the reader itself, so this class has no instance-local way to recover one.
     * Resolved instead straight from parsed config: the first configured delegator
     * whose {@code entity-group-reader} matches this reader's model name. Pure
     * {@link EntityConfig} lookups, no reflection needed for this part.
     */
    private static String resolveDelegatorNameForGroupReader(String groupReaderModelName) {
        try {
            List<DelegatorElement> delegators = EntityConfig.getInstance().getDelegatorList();
            for (DelegatorElement delegator : delegators) {
                if (groupReaderModelName.equals(delegator.getEntityGroupReader())) {
                    return delegator.getName();
                }
            }
        } catch (Exception e) {
            Debug.logError(e, "Hot-reload: could not read delegator config while resolving a delegator name for "
                    + "entity-group-reader '" + groupReaderModelName + "'", MODULE);
        }
        return null;
    }

    /** Best-effort read of a private {@code String} field, purely for log messages. */
    private static String readField(Field field, Object target) {
        try {
            Object value = field.get(target);
            return value != null ? value.toString() : "?";
        } catch (ReflectiveOperationException e) {
            return "?";
        }
    }

    /** Best-effort re-null of {@code field} on {@code target}, logging if even that fails. */
    private static void reNullQuietly(Field field, Object target, String fieldLabel, String readerName) {
        try {
            field.set(target, null);
        } catch (ReflectiveOperationException inner) {
            Debug.logError(inner, "Hot-reload: could not re-null " + fieldLabel + " for '" + readerName
                    + "' after a failed rebuild -- it may now be serving a broken, partial model until OFBiz is "
                    + "restarted.", MODULE);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> UtilCache<String, T> castCache(Object value) {
        return (UtilCache<String, T>) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Future<Delegator>> castMap(Object value) {
        return (Map<String, Future<Delegator>>) value;
    }

    /** Resolves and caches every {@link Field} handle this class needs, exactly once. */
    private static void ensureFieldsResolved() {
        if (resolutionAttempted) {
            return;
        }
        synchronized (EntityModelReloader.class) {
            if (resolutionAttempted) {
                return;
            }
            resolutionAttempted = true;

            try {
                modelReaderReadersField = ModelReader.class.getDeclaredField("READERS");
                modelReaderReadersField.setAccessible(true);
                modelReaderEntityCacheField = ModelReader.class.getDeclaredField("entityCache");
                modelReaderEntityCacheField.setAccessible(true);
                modelReaderModelNameField = ModelReader.class.getDeclaredField("modelName");
                modelReaderModelNameField.setAccessible(true);
                entityReloadAvailable = true;
            } catch (ReflectiveOperationException | SecurityException e) {
                Debug.logError(e, "Hot-reload: could not resolve ModelReader's private fields (READERS/entityCache"
                        + "/modelName) via reflection -- this OFBiz version's ModelReader implementation may have "
                        + "changed. Entity/view-entity hot-reload is disabled for this session; Java and "
                        + "services.xml hot-reload are unaffected.", MODULE);
                entityReloadAvailable = false;
            }

            try {
                modelGroupReaderReadersField = ModelGroupReader.class.getDeclaredField("READERS");
                modelGroupReaderReadersField.setAccessible(true);
                modelGroupReaderGroupCacheField = ModelGroupReader.class.getDeclaredField("groupCache");
                modelGroupReaderGroupCacheField.setAccessible(true);
                modelGroupReaderModelNameField = ModelGroupReader.class.getDeclaredField("modelName");
                modelGroupReaderModelNameField.setAccessible(true);
                groupReloadAvailable = true;
            } catch (ReflectiveOperationException | SecurityException e) {
                Debug.logError(e, "Hot-reload: could not resolve ModelGroupReader's private fields (READERS/"
                        + "groupCache/modelName) via reflection -- this OFBiz version's ModelGroupReader "
                        + "implementation may have changed. entitygroup.xml hot-reload is disabled for this "
                        + "session; entity/view-entity hot-reload is unaffected.", MODULE);
                groupReloadAvailable = false;
            }

            try {
                delegatorFactoryDelegatorsField = DelegatorFactory.class.getDeclaredField("DELEGATORS");
                delegatorFactoryDelegatorsField.setAccessible(true);
                delegatorCacheClearAvailable = true;
            } catch (ReflectiveOperationException | SecurityException e) {
                Debug.logError(e, "Hot-reload: could not resolve DelegatorFactory's private DELEGATORS field via "
                        + "reflection -- this OFBiz version's DelegatorFactory implementation may have changed. "
                        + "Entity/view-entity definitions will still reload live, but already-cached data may keep "
                        + "pointing at a stale model until OFBiz is restarted.", MODULE);
                delegatorCacheClearAvailable = false;
            }
        }
    }
}
