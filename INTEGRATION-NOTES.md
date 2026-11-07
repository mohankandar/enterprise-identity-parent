# Integration notes

Version: 0.1.0

The shared module deliberately stops at authentication normalization. It does not know LexaVault roles, SDV roles,
source access, business permissions, or ingestion-source authorization.

Normalized identity:

- providerId
- issuer
- subject
- username
- email
- displayName
- groups
- raw claims

Both products retain independent authorization and release cadence.
