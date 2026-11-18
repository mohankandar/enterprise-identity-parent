package com.documentplatform.identity.admission;

import java.net.URI;

/**
 * Identifies the login-system connection a verified token belongs to: the configured provider id, the token's
 * verified issuer, and the configured API audience.
 *
 * <p>The values are passed through exactly as given: nothing is trimmed, normalised or rejected here. Product
 * implementations of the lookup interfaces must return no result for a blank provider id or audience, and must
 * compare the issuer as given (0.3.0 keeps the raw {@code iss}; see the trailing-slash finding in the README).
 */
public record ConnectionKey(String providerId, URI issuer, String audience) {
}
