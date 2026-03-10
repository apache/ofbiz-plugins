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
name: manage-async
description: Manage asynchronous execution, scheduled jobs, and the JobSandbox in OFBiz.
---

# Skill: Manage Async Workloads in Apache OFBiz

## Goal
To properly manage asynchronous execution, scheduled jobs, and the `JobSandbox` in OFBiz, ensuring stability and preventing infinite loops or resource starvation.

Asynchronous logic in OFBiz can be triggered via `dispatcher.runAsync()`, scheduled Quartz jobs, Service ECAs (`mode="async"`), and the `JobSandbox` recurrences. While powerful, async execution is often misunderstood and can lead to severe system instability if misused.

## Triggers
- When deciding whether a service or ECA should use `mode="async"` or `mode="sync"`.
- When designing solutions for high-volume background tasks, data imports, or heavy operations.
- When configuring background jobs through the `JobSandbox`.

## Rules & Procedures

### 1. Async is CONCURRENT, not "Later"
**Critical Clarification**: Async does not mean "run later". It means "run concurrently".
- Async runs in a separate thread.
- It starts a new transaction.
- It may execute *before* the triggering transaction commits.
- Reads may see no data or partial data.

*Why this matters*: Agents often falsely assume "Async = safe follow-up after main work". In OFBiz, this assumption is false unless commit boundaries are strictly enforced (e.g., using `event="commit"` in a SECA).

### 2. Large Data Imports or Batch Workloads
For large data imports or batch workloads:
- **Prefer batching + synchronous execution.**
- Process one record per transaction inside a controlled loop.
- **Avoid** one async service invocation per record.

## Guardrails

### 1. Async is NOT a Scalability Strategy at High Volume
Running hundreds or thousands of heavy operations asynchronously does not make them faster.
- Database contention increases.
- Throughput drops and commit times increase.
- The overall system becomes unstable.
- *Rule*: Volume + DB-heavy work → async makes things worse. Avoid choosing async reflexively when you see "heavy" work.

### 2. Persisted Async Jobs Can DOS Your Own System
A massive number of persisted async operations causes a "JobSandbox explosion".
- **Example**: If processing 500,000 records creates 500,000 persisted async jobs, the `JobSandbox` itself becomes the system bottleneck, not the business logic.
- An agent or junior dev may think `persist="true"` equals "safer". But at scale, DB I/O explodes, and the service engine spends more time managing jobs than executing work.
- **Rule**: Persisted async services must ONLY be used when the job creation rate is ≤ the job consumption rate.

### 3. Resource Contention Warning
Async services share CPU, memory, threads, and DB connections with synchronous services.
- Excessive async workloads can starve UI and interactive business flows.
- Async usage must be evaluated in the context of the total system workload.

## Async Decision Checklist
Before choosing an async approach, you must answer **YES** where applicable. If these questions cannot be answered clearly, async is likely the wrong choice:
- [ ] Does this work need to block the user? (If no, async *might* be appropriate, subject to volume).
- [ ] Does it depend on data created in another transaction?
- [ ] What is the expected volume per hour/day? (Volume must be controlled).
- [ ] Can failures be delayed or retried safely?
- [ ] Is batching a better alternative?
- [ ] Should this run only after commit?
- [ ] Does the infrastructure support the async load?

