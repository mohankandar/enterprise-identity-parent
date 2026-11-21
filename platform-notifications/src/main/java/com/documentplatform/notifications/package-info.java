/**
 * Notifications shared by the apps: each app registers its {@link com.documentplatform.notifications.EventCatalogue}
 * and supplies a {@link com.documentplatform.notifications.RecipientResolver}; admins choose events, frequencies,
 * recipient roles and channels through {@link com.documentplatform.notifications.NotificationSettings}; the app
 * publishes structured events to the {@link com.documentplatform.notifications.Notifier} and calls its {@code tick}
 * on a schedule. Plain Java, no I/O: channels and storage are interfaces.
 */
package com.documentplatform.notifications;
