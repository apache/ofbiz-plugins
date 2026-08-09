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
name: manage-contribution-workflow
description: Take an OFBiz change from branch to merge-ready PR — branching and worktree setup, the framework/plugins repo pairing, pre-test data loading, commit hygiene, backporting, and the community PR template.
---

# Skill: manage-contribution-workflow

## Goal

Get a change through the actual mechanics of an OFBiz contribution — not the
code itself (see `manage-entities`, `manage-services`, `manage-groovy`, etc.
for that), but everything around it: branch setup, running tests against real
data, keeping the paired `plugins/` repo in sync, and shaping commits/PRs the
way the project expects. Most of this workflow is invisible in the diff, but
skipping it produces failures and review friction that have nothing to do
with the code change itself.

## Triggers

**ALWAYS** read this skill when:
- Starting work on an OFBiz bug, improvement, or feature (with or without a
  filed JIRA issue).
- Creating a branch or a linked `git worktree` in an `ofbiz-framework` (or
  paired `ofbiz-plugins`) checkout.
- **Resuming work on a branch or PR that already existed before this
  session** — this applies exactly as much as starting fresh. A branch
  cut days or weeks ago is more likely to be stale against `upstream`, not
  less, so picking up old work is a stronger reason to re-sync, not a reason
  to skip it because "setup already happened."
- About to `git push` for any reason — a new branch, an update to an
  existing one, or a fix requested on an open PR.
- About to run `ofbiz --test component=...` for regression verification.
- Preparing a commit message or a pull request, or updating one already open.
- Backporting a fix from `trunk` to a release branch.

## Fork And Upstream Sync

Most OFBiz contributor checkouts are a personal fork (`origin`) of the
canonical Apache repo (`upstream`). This sync is step zero — it happens
**before** any branch is created or pushed, empty or not, and before JIRA
questions are even relevant:

1. **Confirm `upstream` exists.** `git remote -v` should list both `origin`
   (your fork) and `upstream` (`https://github.com/apache/ofbiz-framework.git`
   or `https://github.com/apache/ofbiz-plugins.git`, matching whichever repo
   this is). If `upstream` is missing, add it:
   ```bash
   git remote add upstream https://github.com/apache/<repo-name>.git
   ```
2. **Sync fully, then branch, then push — for a brand new branch.** Fetch
   and fast-forward the local base branch (`trunk`, or the release branch
   you're backporting to) from `upstream`:
   ```bash
   git fetch upstream
   git checkout trunk
   git merge --ff-only upstream/trunk
   git push origin trunk
   ```
   Only cut the new branch and push it — whether pushed empty right away or
   already carrying changes — after this completes. Branching from a stale
   fork risks conflicts, duplicate fixes, or missing context that landed
   upstream since the fork's `trunk` was last updated; syncing *after* the
   branch already exists doesn't fix that, since the branch's base is fixed
   at the moment it's cut.
3. **Re-sync before every push — not just the first one, unconditionally.**
   Before pushing to a branch that already exists — the second commit, the
   tenth, a fix requested on review, a session picking the branch back up —
   re-fetch `upstream` and check whether its target branch (`trunk`, or the
   release branch) has moved in a way that would conflict:
   ```bash
   git fetch upstream
   git log HEAD..upstream/trunk --oneline
   ```
   Do this **every single push**, with no exceptions for "I just did this
   earlier," "this is the same session," or "it's probably still fine" —
   none of those are things an agent can actually verify without doing the
   fetch, so they're not valid reasons to skip it. A branch resumed after a
   gap (a new session, a different day) is *more* likely to be stale, not
   less — resuming old work is a stronger reason to check, never a reason to
   assume the earlier sync still covers you.
   This is a check, not an automatic rebase: if the feature branch has
   already been pushed, don't rewrite its history to "catch up" — a real
   conflict is a sign to resolve it deliberately (merge or rebase, as the
   situation calls for), not to silently force-sync or push anyway and let
   the PR surface the conflict.

## JIRA And Branching

- A JIRA issue (`OFBIZ-XXXXX`) is standard practice — it gives the change a
  tracking number and is what the PR title's `(OFBIZ-XXXXX)` suffix refers
  to — but it is **not mandatory**. Committers in particular may branch and
  PR directly for small or obvious fixes.
- **Never pick, reuse, or infer an issue ID on your own.** Don't pattern-match
  git log/PR history to reuse an existing ticket ("prior commits touching
  this file used OFBIZ-NNNNN, so I'll use that too"), and don't invent one.
  Ask the contributor which issue ID to use, or whether to file a new one, or
  whether to skip JIRA entirely — before creating the branch. This applies
  even when precedent looks obvious and reusing it would seem like the
  efficient, consistent choice; consistency with history is the contributor's
  call, not an inference to make for them.
- Once confirmed (and only after the Fork And Upstream Sync above), branch as
  `OFBIZ-XXXXX` or `OFBIZ-XXXXX-short-slug` when an issue exists, otherwise a
  short descriptive name.
- One branch per issue/change. Don't stack unrelated work on the same branch.

## Worktree Setup

Using a linked `git worktree` is **optional**, not a requirement of this
workflow — branching and working directly in the main checkout (on `trunk`,
or any release branch) is equally valid, and skips every setup step below
entirely. Reach for a worktree when isolation actually matters (e.g. keeping
several issues in flight at once without stashing between them); otherwise
default to working in place.

`git worktree add` only checks out tracked files, which leaves several things
missing in a fresh linked worktree of this repo:

1. **`gradle/wrapper/gradle-wrapper.jar`** — intentionally gitignored (see
   `gradle/init-gradle-wrapper.sh`), so `./gradlew` fails immediately with
   "Unable to access jarfile" until it's restored.
2. **`plugins/`** — a separate git repository in its own right, not a
   submodule, so a new worktree gets an empty (or wrong-branch) `plugins/`
   directory.
3. **Agent skill directories** (`.claude/skills/`, `.gemini/skills/`,
   `.github/skills/`, `.cursor/skills/`, `.agents/skills/`) — these are
   untracked, generated copies of `plugins/ai-agent-skills/` (see that
   plugin's own README), so a fresh worktree has none of them until
   regenerated. Without this step, whatever coding agent is working in the
   worktree has no skills available at all — not this one, not the
   domain-specific ones (`manage-entities`, `manage-services`, etc.).

From the repo root, after creating a new worktree, run in order before the
first build:

```bash
./setup-worktree-gradle-wrapper.sh
./setup-worktree-plugins.sh
./gradlew syncAgentSkills -Pagent=<yours>
```

`setup-worktree-gradle-wrapper.sh` copies the jar from the main checkout when
the Gradle version matches, or falls back to the verified download path.
`setup-worktree-plugins.sh` adds a linked worktree of the `plugins/` repo
(inferring the matching branch from the framework branch's ancestry) —
`plugins/` must exist first, since `syncAgentSkills` is defined inside
`plugins/ai-agent-skills/build.gradle`. All three are idempotent — safe to
re-run.

**`<yours>` is not a placeholder to ask the user about — infer it from the
path you loaded this very skill file from**, since that path already
identifies which agent is running this step:

| You loaded this skill from | Run with |
| :--- | :--- |
| `.claude/skills/...` | `-Pagent=claude` |
| `.gemini/skills/...` | `-Pagent=gemini` |
| `.github/skills/...` | `-Pagent=github` |
| `.cursor/skills/...` | `-Pagent=cursor` |
| `.agents/skills/...` (or unclear) | `-Pagent=agents` |

This syncs only the directory the current agent actually reads from —
skip `all`, which would regenerate every agent's directory in the worktree
regardless of whether that agent is in use here. If a human runs this step
manually and multiple agents are expected to work in the same worktree,
`-Pagent=all` (or a comma-separated list) is still fine — the per-agent
inference above is for an agent acting on its own behalf.

## The `plugins/` Repo Pairing

`ofbiz-framework` and `ofbiz-plugins` are independent git repositories that
must be built together. Whenever the framework repo's branch changes —
switching to a release branch, back to `trunk`, or onto a new branch — check
`plugins/`'s branch too:

```bash
git branch --show-current
git -C plugins branch --show-current
```

Do this **before** any Gradle build and before `git push` (the pre-push hook
builds the project too). A mismatch doesn't fail with an obvious
branch-mismatch error — it surfaces as a misleading Groovy-version dependency
conflict or a cascading "No hooks found" Gradle configuration failure. If a
full build or push fails that way, check `plugins/`'s branch before assuming
it's a real dependency problem.

Uncommitted `package-lock.json` changes in either repo (e.g. under
`plugins/*/webapp/*/`) are npm-regenerated lockfile churn from local
builds/installs, not intentional changes — safe to discard without asking.

## Testing Against Real Data

Before running any `./gradlew "ofbiz --test component=X"` command, run
`./gradlew cleanAll loadAll` first. Don't trust the embedded H2 dev
database's existing state — it may be empty, partially loaded, or stale.

Skipping this doesn't fail loudly — it produces failures like
`ServiceAuthException: User login is missing` that look like real bugs (or
"pre-existing flakiness") but are actually missing seed data
(`UserLogin`/permission records most service-engine tests depend on).
`cleanAll loadAll` first, every time, before treating test failures as
meaningful signal.

## Backporting To A Release Branch

When a fix committed on `trunk` also needs to land on a release branch (e.g.
`release24.09`), use:

```bash
git cherry-pick <trunk-commit-sha>
```

rather than manually re-applying the same diff and writing a fresh commit.
Cherry-pick preserves the original message and authorship and is far less
error-prone than hand-editing twice.

Cherry-pick is the default, not a guarantee — if the surrounding code has
drifted enough between `trunk` and the release branch that it conflicts,
resolve conflicts in place if they're small and mechanical, or fall back to
manually re-applying the change's intent on the release branch if the code
shapes have genuinely diverged (e.g. a method was refactored/renamed/moved
after the release branch was cut). A manual backport is still expected to
produce the same behavioral fix, just adapted to the release branch's
version of the code — it's not license to reinterpret the fix.

In the backport PR description, say "Backported from trunk (#<PR-number>)"
either way, and note if it required manual adaptation rather than a clean
cherry-pick.

## Pull Request Conventions

Title: one of `Improved:` / `Implemented:` / `Documented:` / `Completed:` /
`Reverted:` / `Fixed:`, followed by a short description, ending with
`(OFBIZ-XXXXX)` when an issue exists.

Body: a single concise paragraph — what was wrong or needed, and what
changed.

- Don't repeat the title as the first line of the body.
- Don't add an "Explanation" (or similar) heading — just the paragraph.
- Only add a `Thanks:` line when someone other than the PR author actually
  helped (reported the issue, reviewed, co-designed). Self-filed,
  self-authored changes don't get one.
- Keep it short by default; expand only if more detail is genuinely needed.

## Guardrails

- Don't build or push from a checkout where the framework and `plugins/`
  branches disagree — fix the mismatch first, don't debug around it.
- Don't treat `ofbiz --test` failures as pre-existing/environmental without
  first confirming `cleanAll loadAll` was run.
- Don't hand-reapply a diff for a backport when cherry-pick is available.
- Don't block a change on filing a JIRA issue when the contributor (e.g. a
  committer) doesn't need one — but do suggest filing one for anything
  non-trivial, since it's what ties the PR title, commit history, and
  release notes together.

## See Also
- `coding-standards` — OFBiz style and idiom rules to follow while writing the change.
- `prepush-readiness` — the pre-push hook (Checkstyle, CodeNarc) and other checks to clear before pushing.
