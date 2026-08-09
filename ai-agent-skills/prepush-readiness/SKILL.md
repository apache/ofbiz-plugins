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
name: prepush-readiness
description: Write OFBiz changes so they pass the pre-push hook (Checkstyle for Java, CodeNarc for Groovy) and are in committable shape — clean compilation, valid XML/widgets, and focused tests — before you push.
---

# Skill: prepush-readiness

## Goal

Prevent the "functional code first, hook fixes later" loop. Code should be
written in the shape this repository's pre-push hook (Checkstyle, CodeNarc),
Groovy/Java compilation, XML parsers, and focused tests will accept — checked
early, not discovered at push time.

**There is no pre-commit hook in this repo.** The `com.github.jakemarsden.git-hooks`
Gradle plugin wires a single **pre-push** hook (`build.gradle`) that runs
`checkstyleMain checkstyleTest codenarcMain codenarcTest`. Compilation, XML
validity, and tests are not hook-enforced at all — they're this skill's own
recommended quality bar, worth checking before you commit even though nothing
stops you from committing broken code locally.

## Triggers

**ALWAYS** read this skill when:
- Writing or modifying code that may be committed or pushed.
- Editing Groovy, Java, XML, FreeMarker, properties, data, tests, or Gradle files.
- Preparing a commit, preparing to push, or responding to a pre-push hook failure.
- Adding logic that affects service contracts, entity models, widgets, events,
  data loading, or tests.

## The Pre-Push Gate

The hook runs exactly two Gradle checks, both zero-tolerance:

| Check | Language | Config | Command |
| :--- | :--- | :--- | :--- |
| Checkstyle | Java | `config/checkstyle/checkstyle.xml` (`maxErrors = 0`) | `./gradlew checkstyleMain checkstyleTest` |
| CodeNarc | Groovy | `config/codenarc/codenarc.groovy` (`maxPriority1/2/3Violations = 0`) | `./gradlew codenarcMain codenarcTest` |

Mirror the hook exactly before pushing:

```bash
./gradlew checkstyleMain checkstyleTest codenarcMain codenarcTest
```

This is the only command that reproduces what will actually block the push —
running a broader `check`/`build` task also compiles and tests, which is
useful, but don't mistake that for the hook itself.

If working in a linked `git worktree`, the hook still fires there (the plugin
resolves the real `.git` common dir so this works even though `.git` is a
gitlink file in a worktree) — see `manage-contribution-workflow` for the
other worktree setup steps.

## Core Workflow

1. **Load the domain skill first**
   - Start with `coding-standards`.
   - Load the relevant OFBiz skill for the touched area, such as
     `manage-groovy`, `manage-services`, `manage-entities`, `manage-forms`,
     `manage-controller`, `manage-java`, or `manage-tests`.

2. **Design for hook compatibility before editing**
   - Identify likely checks for the touched files: Checkstyle for Java,
     CodeNarc for Groovy, compilation for both, XML parsing/schema
     expectations for OFBiz XML, and focused tests for behavior.
   - Prefer existing local patterns in nearby files over introducing a new style.
   - Keep the diff narrow so validation failures are easy to isolate.

3. **Run focused validation before finishing**
   - Prefer the narrowest command that exercises the changed area.
   - When preparing to push, run the exact hook command above first — don't
     rely on IDE linting or memory of the rules.
   - If validation is too expensive or blocked, explain exactly what was not run
     and why.

4. **Capture reusable failures**
   - When the hook or build fails for a pattern likely to recur, update this
     skill with the bad pattern and preferred replacement.

## Groovy Readiness

- Keep Groovy service files method-based. Do not put service business logic at
  top level.
- Remove unused imports, variables, parameters, and dead assignments before
  finishing.
- Avoid `println`, `System.out`, ad hoc debug output, and temporary logging.
- Use injected OFBiz logging helpers or established local logging patterns.
- Check service results with `ServiceUtil.isError(...)` or the existing local
  equivalent before using output values.
- Use strict null checks for numeric values and valid empty collections. Do not
  rely on Groovy truth where `0`, `0.0`, `""`, or `[]` are valid inputs.
- Avoid clever dynamic metaprogramming, broad `def` usage in complex logic, and
  ambiguous closures when explicit types make CodeNarc/compilation safer.
- Keep imports explicit and sorted consistently with nearby files.
- Do not mutate data in screen data-preparation scripts; use services/events for
  writes.

## Java Readiness

- Remove unused imports, unused locals, and dead code before validation.
- Use OFBiz utilities such as `UtilValidate`, `UtilMisc`, `EntityQuery`, and
  `ServiceUtil` where the surrounding code expects them.
- Keep public methods documented when they introduce reusable behavior or
  non-obvious logic.
- Preserve existing exception handling and logging style.
- Avoid broad catch blocks unless the local pattern requires wrapping and
  returning a service error.

## XML, Widget, And Data Readiness

- Preserve indentation and attribute ordering conventions used in nearby OFBiz
  XML files.
- Ensure service definitions have accurate required/optional attributes and a
  meaningful `<description>`.
- Keep entity, relation, view-entity, and field names consistent with OFBiz
  naming conventions.
- Replace user-facing hardcoded text with labels when the UI displays it.
- Avoid invalid widget nesting, missing view-map/request-map targets, and
  references to labels, services, screens, or forms that do not exist.
- For seed/demo/security data, ensure primary keys and foreign references match
  existing model definitions.

## Test And Command Selection

Choose the smallest useful validation set:

- Groovy logic: run the relevant Groovy compile/CodeNarc/check task if available,
  plus focused service or component tests.
- Java logic: run focused compile/test tasks for the component or module.
- XML models/widgets/services: run the parser, component load, focused test, or
  existing Gradle task that validates the touched area.
- Behavior changes: add or update focused tests when the risk is more than
  mechanical.
- Push preparation: run `./gradlew checkstyleMain checkstyleTest codenarcMain
  codenarcTest` — the exact pre-push hook command — before pushing.

## Common Failure Patterns To Avoid

- Code works at runtime but fails CodeNarc because of unused imports, unused
  variables, duplicate literals, over-complex methods, or debug output.
- Groovy compiles locally in isolation but fails OFBiz execution because service
  logic is top-level instead of inside the invoked method.
- A service returns a plain map or raw error string instead of the repository's
  expected success/error helper pattern.
- A child service result is used without checking for errors first.
- UI XML references a service, screen, form, menu, or label key that was renamed
  or never added.
- Entity data files introduce records that do not match primary keys, required
  fields, or foreign key expectations.
- Code relies on Groovy truth and mishandles valid zero or empty values.

## Final Checklist

Before handing back a code change:

- Relevant skills were read.
- Changed files follow nearby OFBiz style.
- Temporary debug code is gone.
- Imports, locals, and labels are clean.
- Focused validation was run, or any skipped validation is clearly reported.
- Any new recurring hook failure pattern is added back to this skill.

## See Also
- `coding-standards` — the broader style and OFBiz-idiom rules this skill's checks partially enforce.
- `manage-contribution-workflow` — worktree setup (including why the pre-push hook still works there), testing against real data, and PR conventions.
