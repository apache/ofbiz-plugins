<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

---
name: manage-quartz-jobs
description: |
  Configure, schedule, and monitor background tasks using the OFBiz Job Sandbox and Service Engine. Use when
  - Scheduling asynchronous or recurring services.
  - Configuring thread pool and job poller settings.
  - Managing complex recurrence using Temporal Expressions.
  NOTE: Despite the skill name 'manage-quartz-jobs', vanilla OFBiz uses a custom JobManager/JobPoller scheduling engine, NOT the Quartz scheduler.
---

## Goal
Manage the full lifecycle of background jobs in OFBiz, ensuring scalability, persistence, and reliability across clusters.

## Triggers
**ALWAYS** read this skill when:
- **Asynchronous Execution**: Scheduling a task to run immediately in the background (`runAsync`).
- **Scheduled/Recurring Tasks**: Configuring services to run at specific future times or intervals.
- **Cluster Management**: Tuning which instance handles specific job pools.
- **Debugging**: Investigating stuck, failed, or crashed jobs in `JobSandbox`.

## Core Concepts

### 1. Job Sandbox (`JobSandbox`)
The persistent storage for all scheduled and running jobs.
- **Persistence**: Jobs are stored in the database, allowing for recovery after crashes and cluster-wide distribution.
- **`runTime` & `runTimeEpoch`**: Both store the execution time. `runTime` is the human-readable timestamp (often used in XML data), while `runTimeEpoch` is a numeric field storing UTC milliseconds explicitly added to avoid Daylight Saving Time (DST) shift issues. The `JobManager` queries `runTimeEpoch` first to ensure timezone-independent scheduling.
- **`poolId`**: Assigns the job to a specific execution pool (e.g., `pool`, `batchPool`). The `JobPoller` on a cluster node **only** picks up jobs from pools it is explicitly configured to handle.
- **`priority`**: Determines execution order when multiple jobs are pending at the same time. Higher priority jobs (e.g., `priority="10"`) are executed sooner. Essential for AI agents prioritizing critical background tasks.
- **`statusId`**: Tracks the job lifecycle: `SERVICE_PENDING` → `SERVICE_QUEUED` → `SERVICE_RUNNING` → `SERVICE_FINISHED` / `SERVICE_FAILED`.
- **Crash Recovery**: If the JVM crashes while a job is running, its status remains `SERVICE_CRASHED`. The `JobPoller` will automatically recover it by resetting it to `SERVICE_PENDING` on next restart.
- **`runByInstanceId`**: Enables **Distributed Execution**. Only the instance that "claims" the job by writing its `unique.instanceId` here will execute it.

### 2. Job Poller (`JobPoller`)
A singleton thread pool manager that:
- **Polls**: Queries the `JobSandbox` for pending jobs at fixed intervals.
- **Queues**: Places claimed jobs into a `PriorityBlockingQueue`.
- **Invokes**: Uses a managed thread pool to execute the service associated with the job.

### 3. Temporal Expressions (`TemporalExpression`)
Used for complex scheduling logic that cannot be easily expressed with `RecurrenceRule`. They provide functional, declarative logic for scheduling.
- **`FREQUENCY`**: Simple patterns (Every X minutes, hours, days).
- **`INTERSECTION`**: Matches if BOTH sub-expressions match (e.g., "Monday" AND "8:00 AM").
- **`UNION`**: Matches if EITHER sub-expression matches (e.g., "1st of Month" OR "15th of Month").
- **`DIFFERENCE`**: Matches A but NOT B (e.g., "Daily" MINUS "Holidays").

#### Frequency Mapping (`integer1`)
| Type | Value | Type | Value |
| :--- | :--- | :--- | :--- |
| **Year** | 1 | **Hour** | 11 |
| **Month** | 2 | **Minute** | 12 |
| **Day** | 5 | **Second** | 13 |

### 4. Recurrence Rules (`RecurrenceRule` & `RecurrenceInfo`)
The legacy mechanism for scheduling repeating jobs. While `TemporalExpression` is preferred for complex logic, `RecurrenceRule` is still widely used in Java/Groovy code for simple interval-based scheduling (e.g., "run every 5 minutes").
- **`frequency`**: The unit of time (e.g., "MINUTELY", "HOURLY", "DAILY", "MONTHLY").
- **`intervalNumber`**: The multiplier (e.g., `frequency="MINUTELY"`, `intervalNumber=5` means every 5 minutes).
- **`countNumber`**: The max number of executions (-1 for infinite).

## Procedure

### 1. Programmatic Scheduling (Java/Groovy)
Use the `LocalDispatcher` to schedule services for background execution.

**Async Execution (Immediate)**:
Schedules the service asynchronously using the default job pool ("pool").
```java
dispatcher.runAsync("myServiceName", context);
```

**Async Execution (With Wait)**:
Schedules the service asynchronously but returns a `GenericResultWaiter` to pause and wait for the result.
```java
GenericResultWaiter waiter = dispatcher.runAsyncWait("myServiceName", context);
Map<String, Object> result = waiter.waitForResult();
```

**Scheduled/Recurring**:
While simple scheduling exists, production jobs usually specify a target pool, retries, and optionally an end time.

```java
// schedule(String poolName, String serviceName, Map<String, Object> context, long startTime, 
//          int frequency, int interval, int count, long endTime, int maxRetry)
dispatcher.schedule("pool0", "myTask", context, startTime, RecurrenceRule.DAILY, 1, 10, 0, 3);
```
*(Note: To set priority, which tracks in the queue, you typically interact with `JobManager` or pass specific parameters depending on custom service implementations, though the standard OFBiz dispatcher relies on standard priority or extending `JobSandbox` properties).*

### 2. Declarative Scheduling (XML Data)
Used for system maintenance and seed-data jobs. Define in a data XML file and register in `ofbiz-component.xml`.

```xml
<!-- Example: Daily cleanup at Midnight -->
<TemporalExpression tempExprId="MIDNIGHT" tempExprTypeId="FREQUENCY" integer1="5" integer2="1" date1="2000-01-01 00:00:00.000"/>
<JobSandbox jobName="Daily Cleanup" serviceName="purgeOldData" 
    statusId="SERVICE_PENDING" runTime="2025-01-01 00:00:00.000" tempExprId="MIDNIGHT" maxRetry="3"/>
```

### 3. Configuration Tuning
Adjust behavior in `framework/service/config/serviceengine.xml` under the `<thread-pool>` element.

- **`poll-db-millis`**: Polling frequency (default 30,000ms / 30s).
- **`min-threads`**: The baseline number of worker threads kept alive in the pool.
- **`max-threads`**: Maximum concurrent jobs an instance can run.
- **`jobs`**: Size of the in-memory queue.
- **`purge-job-days`**: Retention period for finished/failed jobs in `JobSandbox`.
- **`run-from-pool`**: Declares which instance pools this engine handles (e.g., `pool0`).

## Guardrails
- **Idempotency**: All jobs MUST be idempotent. The poller may retry a job automatically if the transaction fails or the instance crashes (`SERVICE_CRASHED`).
- **Service Transaction Behavior**: Jobs execute their associated service within a transaction by default. If the service fails (throws an exception or returns error), the entire transaction rolls back, all database changes made during the job are undone, and the job is marked `SERVICE_FAILED`.
- **Security Context**: Jobs run without an active session. Provide a `userLogin` object in the service context if permissions are required.

## Best Practices

- **Prefer Temporal Expressions**: Use `TemporalExpression` for complex schedules (e.g., "3rd Friday of the month"). `RecurrenceRule` is acceptable for simple programmatic intervals.
  ```xml
  <!-- Good: Using TemporalExpression for complex scheduling -->
  <TemporalExpression tempExprId="MIDNIGHT" tempExprTypeId="FREQUENCY" integer1="5" integer2="1"/>
  ```

- **Job Persistence**: Exploit the database persistence of `JobSandbox`. Jobs are guaranteed to resume after a system restart, providing strong durability.
  ```xml
  <!-- Good: Scheduling an important data export that must survive restarts -->
  <JobSandbox jobName="Nightly Export" serviceName="exportSystemData" tempExprId="MIDNIGHT" poolId="pool0"/>
  ```

- **Atomic Jobs**: Keep job logic focused. Each job runs in its own transaction; if a job fails, only its internal work is rolled back, not the scheduling entry itself (it is simply updated to `SERVICE_FAILED`).
  ```java
  // Good: Service is focused and atomic. If it fails, the JobSandbox entry still records the failure.
  public Map<String, Object> myAtomicService(DispatchContext dctx, Map<String, ? extends Object> context) { ... }
  ```

- **Pool Efficiency**: Ensure the `thread-pool` in `serviceengine.xml` has an adequate `max-threads` configuration to handle your peak background volume without starving the default pool. Use dedicated pools (e.g., `pool1`) for heavy batch processes.
  ```xml
  <!-- Good: Defining a dedicated pool for heavy batch tasks -->
  <thread-pool send-to-pool="batchPool" min-threads="2" max-threads="10" jobs="500">
      <run-from-pool name="batchPool"/>
  </thread-pool>
  ```

- **Monitoring Crashes**: Periodically check Webtools for jobs stuck in `SERVICE_CRASHED` that failed to auto-recover. While the Poller handles crash recovery by resetting these to `SERVICE_PENDING` on startup, persistent crashes indicate JVM-level faults (e.g., OutOfMemoryError) that need attention.

- **Purging**: Prevent `JobSandbox` from growing indefinitely. Use the `PurgeJob` service or configure the `purge-job-days` auto-purge attribute in `serviceengine.xml`.
  ```xml
  <!-- Good: Retain finished/failed jobs for 7 days only -->
  <thread-pool send-to-pool="pool" purge-job-days="7" ... />
  ```

- **DST Awareness**: When scheduling via code, ensure the time is calculated correctly relative to the timezone. The framework will convert and store it in the `runTimeEpoch` field (UTC ms) to inherently solve DST shifts (like clocks rolling backward).
  ```java
  // Good: Using UtilDateTime to evaluate correctly relative to the timezone
  Timestamp startTime = UtilDateTime.getDayStart(now, timeZone, locale);
  ```

- **Use Max Retries**: Always specify a `maxRetry` value (e.g., 3) to prevent transient issues from permanently killing a job. The lifecycle is: `SERVICE_PENDING` → `SERVICE_RUNNING` → `SERVICE_FAILED` → `SERVICE_PENDING` (retry) until `maxRetry` is exhausted.
  ```xml
  <!-- Good: Setting maxRetry allows the poller to attempt execution again -->
  <JobSandbox jobName="API Sync" serviceName="syncWithExternalApi" maxRetry="3"/>
  ```

- **Naming Conventions**: Use unique, descriptive `jobName` values to simplify monitoring in Webtools.
  ```xml
  <!-- Good: Descriptive name makes Webtools monitoring easy -->
  <JobSandbox jobName="Daily Inventory Sync for Warehouse A" ... />
  ```

## Anti-Patterns

- **❌ Scheduling Inside a Transaction**: Never schedule a job (via `runAsync` or `schedule`) inside a transaction that might roll back. If the transaction fails, data changes (e.g., `OrderHeader`) are undone, but the `JobSandbox` entry is already committed independently. When the poller executes the job, it will run on non-existent data, causing an inconsistent state.
  ```java
  // BAD: Job scheduled before transaction is guaranteed
  public Map<String, Object> createOrderService(DispatchContext dctx, Map<String, ?> context) {
      delegator.create("OrderHeader", orderFields); // Opens transaction
      dispatcher.runAsync("sendOrderEmail", context); // Commits to JobSandbox immediately!
      
      // Payment fails here! The overarching transaction rolls back.
      // OrderHeader is undone, but the email job STILL RUNS.
      Map<String, Object> paymentResult = dispatcher.runSync("authorizePayment", paymentCtx);
      if (ServiceUtil.isError(paymentResult)) {
          return ServiceUtil.returnError("Payment failed"); // Triggers transaction rollback
      }
      return ServiceUtil.returnSuccess();
  }

  // GOOD: Isolate the main transaction, then schedule
  public Map<String, Object> processOrder(DispatchContext dctx, Map<String, ?> context) {
      // Run the main transaction synchronously
      Map<String, Object> result = dispatcher.runSync("createOrderInternal", context);
      
      // Only schedule if the internal transaction succeeded
      if (ServiceUtil.isSuccess(result)) {
          dispatcher.runAsync("sendOrderEmail", emailContext);
      }
      return result;
  }
  ```
  
- **❌ Hardcoded `jobId`**: Never hardcode `jobId` in XML data to avoid primary key collisions. Use `createSetNextSeqId` behavior.
  ```xml
  <!-- BAD: Hardcoded jobId can cause unique constraint violations on import -->
  <JobSandbox jobId="90001" jobName="Bad Job" serviceName="myService"/>
  <!-- GOOD: Let the Entity Engine generate the ID -->
  <JobSandbox jobName="Good Job" serviceName="myService"/>
  ```

- **❌ Duplicate Scheduling**: Do not blindly schedule a recurring job on every request or system startup without checking if it already exists. This will create multiple identical recurring jobs, exponentially increasing system load over time.

- **❌ Frequency Overlap**: Do not schedule a job to run every 1 minute if the service logic consistently takes more than 1 minute to complete.
  ```xml
  <!-- BAD: Service takes 5 mins, but scheduled every 1 min. Queue will overflow. -->
  <TemporalExpression tempExprId="EVERY_MIN" tempExprTypeId="FREQUENCY" integer1="12" integer2="1"/>
  <JobSandbox jobName="Heavy Task" serviceName="heavy5MinTask" tempExprId="EVERY_MIN"/>
  ```

- **❌ High-Frequency Polling**: Setting `poll-db-millis` too low (e.g., < 5000ms) can saturate the database with "nothing to do" queries.
  ```xml
  <!-- BAD: Polling the database 10 times a second creates unnecessary load -->
  <thread-pool poll-db-millis="100" ... />
  ```

- **❌ Blocking the Default Pool**: Do not run extremely long-running, CPU-intensive tasks in the default pool. Use dedicated pools for heavy batch work.
  ```xml
  <!-- BAD: Running a 4-hour batch job in 'pool' might block other critical system jobs -->
  <JobSandbox jobName="4 Hour Batch" serviceName="heavyBatch" poolId="pool"/>
  <!-- GOOD: Route it to a dedicated batch pool -->
  <JobSandbox jobName="4 Hour Batch" serviceName="heavyBatch" poolId="batchPool"/>
  ```

- **❌ Large DB Scans Without Pagination**: Background jobs processing huge tables (like `OrderHeader` or `Invoice`) must use an `EntityListIterator` (`find()`) instead of loading all records into memory (`queryList()`).
  ```java
  // BAD: Loading 1,000,000 orders into RAM will crash the JobPoller JVM
  List<GenericValue> allOrders = EntityQuery.use(delegator).from("OrderHeader").queryList();

  // GOOD: Using an iterator to process the batch efficiently
  try (EntityListIterator eli = EntityQuery.use(delegator).from("OrderHeader").queryIterator()) {
      GenericValue order;
      while ((order = eli.next()) != null) {
          // process order
      }
  }
  ```
