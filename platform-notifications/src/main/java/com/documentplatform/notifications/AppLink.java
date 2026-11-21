package com.documentplatform.notifications;

import java.net.URI;

/**
 * A link into the app, as a path only (for example {@code /cases/C-1001}). The host comes from the tenant's
 * {@link TenantSettings#appBaseUrl()}. No query string or fragment, so no value can travel in the link.
 */
public record AppLink(String path) {

    public AppLink {
        Text.require(path, Text.PATH, "App link");
        if (path.contains("//") || path.contains("..")) {
            throw new NotificationException(NotificationException.Reason.INVALID_VALUE, "App link is not valid");
        }
    }

    public static AppLink of(String path) {
        return new AppLink(path);
    }

    /** The full link under the tenant's app address. */
    public URI resolve(URI appBaseUrl) {
        String base = appBaseUrl.toString();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return URI.create(base + path);
    }

    @Override
    public String toString() {
        return path;
    }
}
