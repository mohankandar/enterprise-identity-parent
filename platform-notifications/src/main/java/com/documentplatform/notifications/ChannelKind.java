package com.documentplatform.notifications;

/** The channels a tenant can connect. Teams and Slack by incoming webhook only. */
public enum ChannelKind {
    EMAIL("Email"),
    TEAMS("Teams"),
    SLACK("Slack");

    private final String displayName;

    ChannelKind(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
