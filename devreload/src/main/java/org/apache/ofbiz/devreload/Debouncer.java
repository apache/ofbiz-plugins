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

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Coalesces rapid-fire change notifications into one action, so a single compile run
 * that touches many files (e.g. one with inner/anonymous classes, or a Gradle build
 * writing several {@code .class} files at once) is handled as a single batch instead
 * of one action per file. Shared by all three of {@link DevReloadContainer}'s change
 * pipelines (class reload, {@code services.xml} reload, Java compile) instead of each
 * hand-rolling its own pending-set/cancel/reschedule bookkeeping.
 *
 * <p>The backing executor is supplied lazily via {@code executorSupplier} rather than
 * captured directly: {@link DevReloadContainer} builds its three {@code Debouncer}
 * instances as field initializers, which run before its own executor is created in
 * {@code init()}.
 */
final class Debouncer<T> {
    private final Supplier<ScheduledExecutorService> executorSupplier;
    private final Set<T> pending = new HashSet<>();
    private final Consumer<Set<T>> action;
    private ScheduledFuture<?> scheduled;

    Debouncer(Supplier<ScheduledExecutorService> executorSupplier, Consumer<Set<T>> action) {
        this.executorSupplier = executorSupplier;
        this.action = action;
    }

    synchronized void add(T item) {
        pending.add(item);
        if (scheduled != null) {
            scheduled.cancel(false);
        }
        try {
            // Wait 300 ms after the last change so a burst of related changes (e.g. a
            // single Gradle compile run writing multiple .class files) is handled as
            // one batch instead of one action per file.
            scheduled = executorSupplier.get().schedule(this::fire, 300, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            // Container is shutting down; pending changes will not be applied.
        }
    }

    private synchronized void fire() {
        if (pending.isEmpty()) {
            return;
        }
        Set<T> batch = new HashSet<>(pending);
        pending.clear();
        action.accept(batch);
    }
}
