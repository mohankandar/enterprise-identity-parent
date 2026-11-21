# platform-core

Shared code for LexaVault and Vero (SDV). Each product pulls these modules in as **libraries** (jars on its
classpath). platform-core is not a server, has no `main`, and is never deployed on its own.

## Modules

| Module | What it is |
|---|---|
| `platform-bom` | Bill of materials. Lists every platform module at the platform version. Products import it in `dependencyManagement`. |
| `platform-identity-core` | Identity: claims, OIDC connection bindings, entity admission, role mapping. No framework dependencies. |
| `platform-identity-spring` | Spring Security adapters for identity (JWT normalizer, audience validator, caching binding resolver). |
| `platform-notifications` | Notifications core (plain Java, no I/O): event catalogue, admin settings, routing, summaries, continuous-condition alerts, message content rule. Design: `docs/design/notifications.md`. |

New capability = new module, added to the root `<modules>` list **and** to `platform-bom`.

## One version

Every module inherits its version from the root `pom.xml`. Never set a `<version>` in a module POM other than
in its `<parent>` block. Inter-module dependencies use `${project.version}`. A release bumps all modules together.

## Build

```
mvn verify      # compile + all tests
mvn install     # also puts the artifacts in the local Maven repo for LexaVault / Vero builds
```

Java 21, Maven 3.9+.

## Rules

- **No app-specific code here.** Nothing that belongs to only LexaVault or only Vero: no product names, product
  packages, product roles, screens or endpoints. `NoProductNamesGuardTest` fails the build on product package names.
  Products plug their specifics in through interfaces (for example `EntityRoleCatalog`).
- **Never log secrets, document content or redacted values.** That includes tokens, client secrets, keys,
  passwords, raw claims, file contents, and anything that was redacted. Log ids and neutral reasons only.
- Keep Java package names stable; renames are their own task.
- **Coordinates and packages.** Every module uses groupId `com.documentplatform.platform`. A new module puts its
  code in `com.documentplatform.<capability>` (for example `com.documentplatform.notifications`). The identity
  modules keep their existing `com.documentplatform.identity...` packages.

## Work tracking

- Tasks live in `docs/work/PNNN.md` (P001, P002, ...).
- The report for each task goes in `docs/work/PNNN-report.md`.
