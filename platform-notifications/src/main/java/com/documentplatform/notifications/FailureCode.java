package com.documentplatform.notifications;

/**
 * Why a message was not delivered. Specific causes with stable codes, never "an error occurred", and never anything
 * secret: the text is fixed here, so it cannot carry an address, login or server reply.
 */
public enum FailureCode {
    MAIL_LOGIN_REFUSED("Mail server refused the login"),
    MAIL_SERVER_UNREACHABLE("Mail server could not be reached"),
    MAIL_RECIPIENT_REFUSED("Mail server refused a recipient address"),
    WEBHOOK_ADDRESS_INVALID("%s webhook address no longer valid"),
    WEBHOOK_REJECTED("%s refused the message"),
    WEBHOOK_UNREACHABLE("%s could not be reached"),
    DESTINATION_NOT_CONNECTED("The destination is no longer connected for the tenant"),
    CHANNEL_NOT_AVAILABLE("No sender is installed for %s"),
    NO_RECIPIENTS("No one holds the recipient roles"),
    RECIPIENTS_UNAVAILABLE("The holders of the recipient roles could not be looked up"),
    SENDER_FAILED("The %s sender stopped with an unexpected fault");

    private final String template;

    FailureCode(String template) {
        this.template = template;
    }

    /** The neutral reason for logs and the admin screen, for example "Teams webhook address no longer valid". */
    public String describe(ChannelKind channel) {
        return template.contains("%s") ? String.format(template, channel.displayName()) : template;
    }
}
