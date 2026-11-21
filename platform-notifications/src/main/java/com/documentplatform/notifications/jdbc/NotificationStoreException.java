package com.documentplatform.notifications.jdbc;

import java.sql.SQLException;

/** A database operation of a notification store failed. The message names the operation and the SQL state only. */
public final class NotificationStoreException extends RuntimeException {

    NotificationStoreException(String operation, SQLException cause) {
        super("Notification store failed to " + operation + " (SQL state " + cause.getSQLState() + ")", cause);
    }
}
