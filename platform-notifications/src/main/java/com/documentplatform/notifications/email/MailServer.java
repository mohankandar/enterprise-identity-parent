package com.documentplatform.notifications.email;

import com.documentplatform.notifications.secret.SecretRef;

import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The customer's mail server for one e-mail destination, from the install config. Holds no secret: the password is a
 * {@link SecretRef} read from the secret store when sending.
 *
 * @param username       the login; null for a relay that takes mail without one
 * @param password       where the password is kept; set exactly when {@code username} is
 * @param fromAddress    the sender address, for example {@code notifications@customer.example}
 * @param fromName       the display name of the sender; null for none
 * @param connectTimeout how long to wait for the connection
 * @param readTimeout    how long to wait for each server reply
 */
public record MailServer(String host, int port, MailSecurity security, String username, SecretRef password,
                         String fromAddress, String fromName, Duration connectTimeout, Duration readTimeout) {

    private static final Pattern HOST = Pattern.compile("[A-Za-z0-9.:\\[\\]_-]{1,253}");
    private static final Pattern ADDRESS = Pattern.compile("[^@\\s]+@[^@\\s]+");

    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(30);

    public MailServer {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(security, "security");
        Objects.requireNonNull(fromAddress, "fromAddress");
        Objects.requireNonNull(connectTimeout, "connectTimeout");
        Objects.requireNonNull(readTimeout, "readTimeout");
        if (!HOST.matcher(host).matches()) {
            throw new IllegalArgumentException("Mail server host is not valid");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Mail server port is not valid");
        }
        if ((username == null) != (password == null) || (username != null && username.isBlank())) {
            throw new IllegalArgumentException("A login needs both a user name and a password reference");
        }
        if (!ADDRESS.matcher(fromAddress).matches()) {
            throw new IllegalArgumentException("Sender address is not valid");
        }
        if (fromName != null && (fromName.isBlank() || fromName.chars().anyMatch(Character::isISOControl))) {
            throw new IllegalArgumentException("Sender name is not valid");
        }
        if (connectTimeout.isNegative() || connectTimeout.isZero() || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("Timeouts must be positive");
        }
    }

    /** A server with a login, default timeouts and no display name. */
    public static MailServer withLogin(String host, int port, MailSecurity security, String username,
                                       SecretRef password, String fromAddress) {
        return new MailServer(host, port, security, username, password, fromAddress, null, DEFAULT_CONNECT_TIMEOUT,
                DEFAULT_READ_TIMEOUT);
    }

    /** A relay without a login, default timeouts and no display name. */
    public static MailServer relay(String host, int port, MailSecurity security, String fromAddress) {
        return new MailServer(host, port, security, null, null, fromAddress, null, DEFAULT_CONNECT_TIMEOUT,
                DEFAULT_READ_TIMEOUT);
    }

    public MailServer withTimeouts(Duration connect, Duration read) {
        return new MailServer(host, port, security, username, password, fromAddress, fromName, connect, read);
    }

    public MailServer withFromName(String name) {
        return new MailServer(host, port, security, username, password, fromAddress, name, connectTimeout, readTimeout);
    }
}
