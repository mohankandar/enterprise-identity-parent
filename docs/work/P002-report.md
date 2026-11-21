# P002 report — platform-core housekeeping

Branch: `feature/platform-core-setup`. Not merged.

## What changed

| File | Change |
|---|---|
| `pom.xml` | groupId `com.documentplatform.identity` → `com.documentplatform.platform` |
| `platform-bom/pom.xml` | parent groupId, and the groupId of all three managed dependencies |
| `platform-identity-core/pom.xml` | parent groupId |
| `platform-identity-spring/pom.xml` | parent groupId, and the groupId of its `platform-identity-core` dependency |
| `platform-notifications/pom.xml` | parent groupId |
| `CLAUDE.md` | New rule: every module uses groupId `com.documentplatform.platform`; a new module's packages are `com.documentplatform.<capability>`; the identity modules keep `com.documentplatform.identity...` |
| `docs/work/P002.md`, `docs/work/P002-report.md` | New |

No Java file changed. Package names are unchanged.

Git config (not in the commit):

- Removed the remote-tracking ref `origin/fix/null-audience-validator` (it was at `85135a7`).
- `origin` now points to `https://github.com/mohankandar/platform-core.git`. `git ls-remote origin` works: it lists
  `main` and `feature/admission-0.3.0` (both `0c5c0a7`), `feature/mapping-0.4.0` (`a7de999`), and tags `v0.2.1` and `v0.3.0`.

## Fix branch finding

Nothing is missing. `fix/null-audience-validator` in the original repo is a local branch at `85135a7`. It has
three commits:

- `bc63ff0` Initial commit: Enterprise Identity shared library v0.2.0
- `b126091` fix: treat a missing aud claim as a missing audience (0.2.1)
- `85135a7` docs: README title and 0.2.1 entry

`85135a7` is an ancestor of this branch's HEAD (`git merge-base --is-ancestor` passes), so all three commits are
already in this branch's history. The same commit is also tag `v0.2.1`. The branch no longer exists on GitHub. I
changed nothing in the original repo.

## Build

- `mvn verify`: BUILD SUCCESS. 88 tests: 73 in `platform-identity-core` and 15 in `platform-identity-spring`.
  0 failures, 0 errors, 0 skipped.
- `mvn install`: BUILD SUCCESS. The artifacts are now in `~/.m2/repository/com/documentplatform/platform/`
  (`platform-core`, `platform-bom`, `platform-identity-core`, `platform-identity-spring`, `platform-notifications`,
  all 0.1.0-SNAPSHOT).
- The old artifacts are still in `~/.m2/repository/com/documentplatform/identity/`: `enterprise-identity-core` and
  `enterprise-identity-spring` at 0.2.0, 0.2.1, 0.3.0, 0.3.0-SNAPSHOT and 0.4.0-SNAPSHOT, plus `enterprise-identity-parent`.

## Push

Commit `6847478` was pushed with `git push -u origin feature/platform-core-setup`. This created the new branch
`feature/platform-core-setup` on `mohankandar/platform-core`, and the local branch now tracks it. Nothing was merged.
This report's push-result section was then added in a small follow-up commit on the same branch.

## Questions

1. P001 installed `platform-*` 0.1.0-SNAPSHOT under the old groupId (`~/.m2/.../com/documentplatform/identity/platform-*`).
   Nothing should use them now. Delete them? **Recommendation:** yes. Delete just those five folders, so a product
   build cannot pick up the old coordinates by mistake. Leave the `enterprise-identity-*` folders alone.
2. The original repo still has the local branch `fix/null-audience-validator`, and it is fully merged. Delete it there?
   **Recommendation:** yes, but you do it (`git branch -d` is safe because the branch is merged). I left that repo read-only.
3. In this clone, `origin/HEAD` points to `feature/mapping-0.4.0`, but GitHub's default branch is `main`, which is
   still at 0.3.0 (`0c5c0a7`). Which branch should be the default for platform-core? **Recommendation:** run
   `git remote set-head origin -a` now. Once this branch is reviewed, merge it into `main` and keep `main` as the default.
