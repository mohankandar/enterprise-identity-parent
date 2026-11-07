package com.documentplatform.identity.spring;

import com.documentplatform.identity.OidcConnectionBinding;
import com.documentplatform.identity.OidcConnectionBindingResolver;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Small bounded TTL cache for trusted OIDC connection lookups. Both hits and misses expire quickly so
 * activation/disable changes converge without a process restart. Thread safe.
 */
public final class CachingOidcConnectionBindingResolver implements OidcConnectionBindingResolver {
    private final OidcConnectionBindingResolver delegate;
    private final Duration ttl;
    private final int maxEntries;
    private final Clock clock;
    private final Map<Key, Entry> cache;

    public CachingOidcConnectionBindingResolver(OidcConnectionBindingResolver delegate, Duration ttl, int maxEntries) {
        this(delegate, ttl, maxEntries, Clock.systemUTC());
    }

    CachingOidcConnectionBindingResolver(OidcConnectionBindingResolver delegate, Duration ttl, int maxEntries, Clock clock) {
        this.delegate = java.util.Objects.requireNonNull(delegate, "delegate");
        this.ttl = java.util.Objects.requireNonNull(ttl, "ttl");
        if (ttl.isNegative() || ttl.isZero()) throw new IllegalArgumentException("ttl must be positive");
        if (maxEntries < 1) throw new IllegalArgumentException("maxEntries must be positive");
        this.maxEntries = maxEntries;
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.cache = new LinkedHashMap<>(16, .75f, true);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("OIDC lookup value is required");
        return value.trim();
    }

    private static URI normalizeIssuer(URI issuer) {
        if (issuer == null || !issuer.isAbsolute() || issuer.getHost() == null)
            throw new IllegalArgumentException("OIDC issuer is invalid");
        String text = issuer.toString();
        return URI.create(text.endsWith("/") ? text.substring(0, text.length() - 1) : text);
    }

    @Override
    public synchronized Optional<OidcConnectionBinding> findActive(String providerId, URI issuer, String audience) {
        Key key = new Key(normalize(providerId), normalizeIssuer(issuer), normalize(audience));
        Instant now = clock.instant();
        Entry current = cache.get(key);
        if (current != null && now.isBefore(current.expiresAt())) return current.value();
        Optional<OidcConnectionBinding> value = delegate.findActive(key.providerId(), key.issuer(), key.audience());
        cache.put(key, new Entry(value, now.plus(ttl)));
        while (cache.size() > maxEntries) cache.remove(cache.keySet().iterator().next());
        return value;
    }

    public synchronized void clear() {
        cache.clear();
    }

    private record Key(String providerId, URI issuer, String audience) {
    }

    private record Entry(Optional<OidcConnectionBinding> value, Instant expiresAt) {
    }
}
