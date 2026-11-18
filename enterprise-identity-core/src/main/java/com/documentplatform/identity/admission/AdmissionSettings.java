package com.documentplatform.identity.admission;

/**
 * Settings for entity admission, built by each product from its own setting names. The library defines no
 * setting names of its own.
 *
 * @param sharedIssuerSelectionEnabled several entities behind one login system; products must default this to false
 * @param persistentConnections        connections are read from the product's store (required for shared issuer)
 * @param providerId                   the configured login-system provider id
 * @param audience                     the configured API audience the tokens must carry
 */
public record AdmissionSettings(boolean sharedIssuerSelectionEnabled, boolean persistentConnections,
                                String providerId, String audience) {
}
