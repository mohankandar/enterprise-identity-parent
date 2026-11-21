package com.documentplatform.notifications;

import static com.documentplatform.notifications.NotificationException.Reason.THRESHOLD_OUT_OF_RANGE;

/**
 * A number the app compares against before it publishes (for example "disk almost full at 90 %"). The event type
 * declares the default and the range; the admin may adjust it within the range.
 */
public record NumericThreshold(String name, String label, long defaultValue, long min, long max) {

    public NumericThreshold {
        Text.require(name, Text.FIELD_NAME, "Threshold name");
        Text.require(label, Text.LABEL, "Threshold label");
        if (min > max || defaultValue < min || defaultValue > max) {
            throw new NotificationException(THRESHOLD_OUT_OF_RANGE, "Threshold default must lie within its range");
        }
    }

    public boolean allows(long value) {
        return value >= min && value <= max;
    }
}
