package com.documentplatform.notifications.email;

import com.documentplatform.notifications.DestinationId;
import com.documentplatform.notifications.TenantId;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The mail server of each e-mail destination. Filled by the app from its install config (Spring wiring in P005);
 * {@link #of(Map)} serves a fixed set.
 */
public interface MailServerDirectory {

    Optional<MailServer> find(TenantId tenant, DestinationId destination);

    /** A fixed set of servers, the same for every tenant. */
    static MailServerDirectory of(Map<DestinationId, MailServer> servers) {
        Map<DestinationId, MailServer> copy = Map.copyOf(Objects.requireNonNull(servers, "servers"));
        return (tenant, destination) -> Optional.ofNullable(copy.get(destination));
    }
}
