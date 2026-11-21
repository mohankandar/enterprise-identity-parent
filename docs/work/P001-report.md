# P001 report: create the shared repo platform-core

Status: done. Branch `feature/platform-core-setup` in `C:\dev\platform-core`, committed, not pushed, not merged.

## Source

- Path used: `C:\dev\shared\enterprise-identity-parent` (first location tried; it existed).
- Cloned with `git clone` of the local folder, so full history is kept. The clone's `origin` was then set back to
  `https://github.com/mohankandar/enterprise-identity-parent`.
- Branch base: the source's checked-out branch, `feature/mapping-0.4.0` at `a7de999`. That is 3 commits ahead of
  `main` (`0c5c0a7`, release 0.3.0). See question 1.
- The original folder was not modified: `git status` there is clean and HEAD is still `a7de999`. To avoid even
  rewriting its `target/` folders, the baseline build was run in the fresh clone at the same commit, before any
  change.

## Baseline vs new

| | Baseline (enterprise-identity-parent) | New (platform-core) |
|---|---|---|
| groupId | `com.documentplatform.identity` | `com.documentplatform.identity` (unchanged) |
| Root artifactId | `enterprise-identity-parent` | `platform-core` |
| Module artifactIds | `enterprise-identity-core`, `enterprise-identity-spring` | `platform-bom`, `platform-identity-core`, `platform-identity-spring`, `platform-notifications` |
| Version | `0.4.0-SNAPSHOT` | `0.1.0-SNAPSHOT` (all modules, inherited from root) |
| Java | 21 (`maven.compiler.release` 21) | 21 (unchanged) |
| Tests | 88 (core 73, spring 15), 0 failed/errored/skipped | 88 (core 73, spring 15), 0 failed/errored/skipped |
| Java packages | `com.documentplatform.identity[.admission/.mapping/.spring]` | unchanged |

## Final module tree

```
platform-core/
├── pom.xml                      platform-core (packaging pom, 0.1.0-SNAPSHOT)
├── CLAUDE.md
├── README.md, INTEGRATION-NOTES.md, SCHEMA_CONTRACT.md   (unchanged)
├── docs/work/
│   ├── P001.md
│   └── P001-report.md
├── platform-bom/
│   └── pom.xml                  BOM: identity-core, identity-spring, notifications at ${project.version}
├── platform-identity-core/      (was enterprise-identity-core)
│   ├── pom.xml
│   └── src/main/java, src/test/java   (com.documentplatform.identity...)
├── platform-identity-spring/    (was enterprise-identity-spring)
│   ├── pom.xml                  depends on platform-identity-core ${project.version}
│   └── src/main/java, src/test/java   (com.documentplatform.identity.spring)
└── platform-notifications/
    ├── pom.xml
    └── src/main/java/.gitkeep, src/test/java/.gitkeep
```

## Files moved or changed

Moved with `git mv` (content unchanged unless listed under Changed):
- `enterprise-identity-core/**` → `platform-identity-core/**` (pom + 29 main + 15 test Java files)
- `enterprise-identity-spring/**` → `platform-identity-spring/**` (pom + 3 main + 3 test Java files)

Changed:
- `pom.xml`: artifactId `platform-core`, version `0.1.0-SNAPSHOT`, added `<name>` and `<description>`, modules list
  now `platform-bom`, `platform-identity-core`, `platform-identity-spring`, `platform-notifications`.
- `platform-identity-core/pom.xml`: parent → `platform-core:0.1.0-SNAPSHOT`; artifactId `platform-identity-core`.
- `platform-identity-spring/pom.xml`: parent → `platform-core:0.1.0-SNAPSHOT`; artifactId
  `platform-identity-spring`; dependency `enterprise-identity-core` → `platform-identity-core`.

Added:
- `platform-bom/pom.xml`
- `platform-notifications/pom.xml`, `platform-notifications/src/main/java/.gitkeep`,
  `platform-notifications/src/test/java/.gitkeep`
- `CLAUDE.md`, `docs/work/P001.md`, `docs/work/P001-report.md`

Not changed: all Java sources and tests, `README.md`, `INTEGRATION-NOTES.md`, `SCHEMA_CONTRACT.md`, `.gitignore`.
(None of the Markdown files mentioned the old artifactIds or version, so no doc edits were needed.)

## Build result

- `mvn verify`: BUILD SUCCESS. Reactor: platform-core, platform-bom, platform-identity-core,
  platform-identity-spring, platform-notifications. 88 tests, 0 failures — same count as baseline.
- `mvn install`: BUILD SUCCESS. In `~/.m2/repository/com/documentplatform/identity/` there are now
  `platform-core`, `platform-bom`, `platform-identity-core`, `platform-identity-spring`, `platform-notifications`
  at `0.1.0-SNAPSHOT`.
- Old artifacts still present: `enterprise-identity-parent`, `-core`, `-spring` at 0.2.0, 0.2.1, 0.3.0,
  0.3.0-SNAPSHOT and 0.4.0-SNAPSHOT (jars and poms checked for 0.3.0 and 0.4.0-SNAPSHOT). LexaVault and SDV are
  untouched and still resolve them.

## Surprising

1. The source was checked out on `feature/mapping-0.4.0` (unreleased Phase 2 work A3–A5), not `main`. platform-core
   therefore starts from that work, not from the 0.3.0 release.
2. The local source repo has a branch `fix/null-audience-validator` that is not on GitHub. In the clone it shows as
   the remote-tracking ref `origin/fix/null-audience-validator` (copied from the local folder), which can be
   confusing since `origin` now points at GitHub.
3. `platform-notifications` builds an empty jar (Maven warns "JAR will be empty"). Harmless; it disappears once
   code lands.
4. Version goes backwards: identity code was `0.4.0-SNAPSHOT`, now ships as `0.1.0-SNAPSHOT` under new
   artifactIds. No clash in Maven because the artifactIds differ, but release notes should say that
   `platform-identity-* 0.1.x` contains what would have been `enterprise-identity-* 0.4.0`.
5. The build logs `SEVERE` lines during tests (from `EntityMembershipResolver`, expected refusal paths). They
   contain only a dummy entity id and a neutral reason — no secrets — but they look alarming in build output.

## Questions for the owner

1. Base branch: platform-core is built on `feature/mapping-0.4.0`, not `main`. Is that right?
   Recommendation: yes, keep it — it is the newest work and the products will move to platform-core anyway; just
   don't merge 0.4.0 separately into enterprise-identity-parent afterwards.
2. groupId stays `com.documentplatform.identity`, which reads as identity-only for a platform repo. Change it?
   Recommendation: switch to `com.documentplatform.platform` (or `com.documentplatform`) in a small follow-up task
   before the first platform-core release, while no product depends on it yet.
3. When should LexaVault and SDV switch from `enterprise-identity-*` to `platform-identity-*` via `platform-bom`?
   Recommendation: one follow-up task per product, each importing `platform-bom:0.1.0-SNAPSHOT` and swapping
   artifactIds; package names are unchanged so no code edits are needed.
4. Should `enterprise-identity-parent` be frozen once the GitHub repo is renamed?
   Recommendation: yes — archive it after the rename and both products are switched; fixes go to platform-core only.
5. `NoProductNamesGuardTest` lives in `platform-identity-core` but scans the whole repo. Move it?
   Recommendation: leave it for now; move it to a dedicated build-check later if more modules are added.
