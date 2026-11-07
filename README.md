# Enterprise Identity 0.1.0

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
