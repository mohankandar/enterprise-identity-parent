package com.documentplatform.notifications.spring;

import java.util.List;

/**
 * The notifications install config is wrong or incomplete. Lists every problem, each with its property key and what to
 * do; never repeats a value that may be a secret.
 */
public final class NotificationsConfigException extends IllegalStateException {

    private final List<String> problems;

    public NotificationsConfigException(List<String> problems) {
        super("Notifications configuration is not valid:\n - " + String.join("\n - ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}
