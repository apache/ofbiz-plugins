/*
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.apache.ofbiz.base.util.Debug;
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
 * Makes the already-running {@link ModelReader}/{@link ModelGroupReader} instances re-read
 * their entity model/group mapping from disk, so edits to an {@code entitydef/*.xml} file
 * take effect in the already-running JVM without a restart.
 *
 * <h2>Why the readers are reloaded in place</h2>
 * Unlike {@code services.xml} reload (which just clears a {@code UtilCache} that the
 * service engine looks up <em>by name on every call</em>), entity reload can't work
 * that way: every {@code GenericDelegator} grabs a direct object reference to a
 * {@link ModelReader} at construction time and keeps it
 * (see {@code GenericDelegator.modelReader}), so dropping the reader from its lookup
 * cache would only affect a brand-new delegator; every delegator already serving
 * traffic would keep using its already-fetched, stale {@code ModelReader} forever.
 * {@link ModelGroupReader} (entitygroup.xml → datasource-group mapping) has the exact
 * same shape.
 *
 * <p>Instead, {@link ModelReader#reload()} and {@link ModelGroupReader#reload()} are
 * called on the reader of every configured delegator -- every delegator sharing a
 * reader name (almost always {@code "main"}) picks up the change for free, since they
 * share the same object. Both readers build the new model on the side and swap it in
 * only once it is complete, so requests keep being served from the previous model
 * while the reload runs, and a broken save leaves that previous model in place. The
 * underlying XML itself needs no separate cache invalidation:
 * {@code MainResourceHandler.getDocument()} re-reads its {@code InputStream} fresh on
 * every call.
 *
 * <p>Enumerating already-created delegators (to clear their data caches) still needs
 * reflection into {@link DelegatorFactory}; that {@link Field} handle is resolved once,
 * the first time it's needed, and a resolution failure is logged once and disables
 * only that part for the rest of the session.
 */
final class EntityModelReloader {

    private static final String MODULE = EntityModelReloader.class.getName();

    private static volatile boolean resolutionAttempted;

    private static volatile boolean delegatorCacheClearAvailable;
    private static Field delegatorFactoryDelegatorsField;

    private EntityModelReloader() { }

    /**
     * Reloads the {@link ModelReader} of every configured delegator, eagerly (rather
     * than leaving it for the next unrelated request), so a broken {@code entitydef}
     * save is caught and logged right here instead of surfacing as a random failure
     * later. A failed reload keeps the previous model, and the next save retries.
     *
     * @return {@code true} if every {@link ModelReader} reloaded cleanly.
     */
    static boolean reloadEntityModels() {
        boolean allSucceeded = true;
        for (Map.Entry<String, String> entry : readerDelegators(DelegatorElement::getEntityModelReader).entrySet()) {
            allSucceeded &= reloadOneEntityReader(entry.getKey(), entry.getValue());
        }
        return allSucceeded;
    }

    /**
     * Reloads the {@link ModelGroupReader} of every configured delegator. Same rationale
     * as {@link #reloadEntityModels()}; kept as a separate entry point so a failure in
     * one never skips the other.
     *
     * @return {@code true} if every {@link ModelGroupReader} reloaded cleanly.
     */
    static boolean reloadGroupModels() {
        boolean allSucceeded = true;
        for (Map.Entry<String, String> entry : readerDelegators(DelegatorElement::getEntityGroupReader).entrySet()) {
            allSucceeded &= reloadOneGroupReader(entry.getKey(), entry.getValue());
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
     * object every time {@code ModelReader} loads its entity cache (see {@code
     * buildEntity()} in {@code framework/entity}), so comparing it against the changed
     * paths is reliable even across repeated reloads within the same session -- unlike
     * {@code ModelReader.getResourceHandlerEntities()}, which is not part of the model a
     * reload swaps in atomically.
     */
    private static Set<String> resolveChangedEntityNames(Set<Path> changedFiles) {
        Set<String> entityNames = new HashSet<>();
        try {
            for (String delegatorName : readerDelegators(DelegatorElement::getEntityModelReader).values()) {
                ModelReader reader = ModelReader.getModelReader(delegatorName);
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

    /** Reloads the entity model reader named {@code readerName}, found through {@code delegatorName}. */
    private static boolean reloadOneEntityReader(String readerName, String delegatorName) {
        long start = System.currentTimeMillis();
        try {
            ModelReader reader = ModelReader.getModelReader(delegatorName);
            reader.reload();
            int entityCount = reader.getEntityCache().size();
            Debug.logInfo("Hot-reload: entity model '" + readerName + "' reloaded (" + entityCount
                    + " entities/view-entities) in " + (System.currentTimeMillis() - start) + "ms", MODULE);
            return true;
        } catch (Throwable t) {
            Debug.logError(t, "Hot-reload: failed to reload entity model '" + readerName
                    + "' -- the previous model stays in use; fix the entitydef XML and save again.", MODULE);
            return false;
        }
    }

    /** Reloads the entity group reader named {@code readerName}, found through {@code delegatorName}. */
    private static boolean reloadOneGroupReader(String readerName, String delegatorName) {
        long start = System.currentTimeMillis();
        try {
            ModelGroupReader reader = ModelGroupReader.getModelGroupReader(delegatorName);
            reader.reload();
            int groupCount = reader.getGroupCache(delegatorName).size();
            Debug.logInfo("Hot-reload: entity group model '" + readerName + "' reloaded (" + groupCount
                    + " entity-group mappings) in " + (System.currentTimeMillis() - start) + "ms", MODULE);
            return true;
        } catch (Throwable t) {
            Debug.logError(t, "Hot-reload: failed to reload entity group model '" + readerName
                    + "' -- the previous mapping stays in use; fix the entitygroup XML and save again.", MODULE);
            return false;
        }
    }

    /**
     * Maps each reader name {@code readerOf} returns for the configured delegators to the
     * first delegator that uses it, so a reader shared by several delegators is reloaded
     * once.
     */
    private static Map<String, String> readerDelegators(Function<DelegatorElement, String> readerOf) {
        Map<String, String> readerDelegators = new LinkedHashMap<>();
        try {
            for (DelegatorElement delegator : EntityConfig.getInstance().getDelegatorList()) {
                readerDelegators.putIfAbsent(readerOf.apply(delegator), delegator.getName());
            }
        } catch (Exception e) {
            Debug.logError(e, "Hot-reload: could not read the delegator config", MODULE);
        }
        return readerDelegators;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Future<Delegator>> castMap(Object value) {
        return (Map<String, Future<Delegator>>) value;
    }

    /** Resolves and caches the {@link Field} handle this class needs, exactly once. */
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
