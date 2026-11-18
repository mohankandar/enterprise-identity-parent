# Enterprise Identity 0.3.0

Shared, product-neutral identity foundation for LexaVault and SDV.

Scope in this first candidate:

- normalized authenticated identity contract
- OIDC/JWT claim normalization
- reusable audience validator
- stable issuer+subject identity id helper

Deliberately NOT included yet: product roles/authorization, machine-to-machine authorization, SAML, LDAP, admin UI,
secret storage.

## 0.2.0 — Enterprise Identity Administration foundation

Adds product-neutral provider lifecycle types, a secret-free `OidcConnectionBinding`, a fail-closed resolver contract,
and a bounded TTL caching resolver. Persistence and product authorization remain product-owned.

## 0.2.1 — Null-safe audience validation

`RequiredAudienceValidator` no longer throws when a token has no `aud` claim. It now returns the normal
"Required API audience is missing." failure, so the resource server answers 401 instead of 500. Adds the first unit
tests for the validator.

## 0.3.0 — Entity admission (several entities behind one login system)

Adds the package `com.documentplatform.identity.admission` to the core module (plain Java, no Spring). It holds the
rules for signing a person in to one of several entities (organizations) that share one login system, moved here from
LexaVault's R1B work so SDV can use the same rules:

- `EntityMembershipResolver`: an entity is admitted only when it has an ACTIVE connection for this login system **and**
  one of the person's verified groups is granted in that entity; roles come from that entity's grants only.
- `EntitySelection` and `TrustedEntityAdmission`: the person's entity choice is a request, never proof; without a choice
  exactly one entity must be admissible.
- `EntityGroupAuthorization`, `SharedIssuerPreflight`: group-to-role rules; the product's platform role can never be
  granted through an entity's groups.
- `MachineEntityAdmission`: a machine identity must be bound to exactly one entity.
- `IdentityAdmissionException` with a `Reason` for every refusal, so each product chooses its own status and words.

The library owns **no tables and no setting names**. Each product implements four small interfaces
(`ActiveEntityCandidates`, `EntityGroupGrants`, `MachineEntityBindings`, `EntityRoleCatalog`) and builds
`AdmissionSettings` from its own settings, with shared-issuer selection off by default. The columns a product must
provide are in [SCHEMA_CONTRACT.md](SCHEMA_CONTRACT.md).

Also adds tests for the classes that had none. Tests named `knownFinding_*` pin behaviour that is to be revisited:
a plain-string groups claim is split on spaces and commas; more than 200 groups, a group value over 200 characters, or a
token without `sub` throws `IllegalArgumentException` (callers must turn it into a refusal); the caching resolver strips
one trailing slash from the issuer while the admission lookups compare the raw `iss`. A guard test fails the build if any
product package name appears in the library. The 0.2.1 classes are unchanged.
