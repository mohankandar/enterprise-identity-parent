package com.documentplatform.notifications.support;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Captures every java.util.logging record at every level (FINEST included), which is where both this module's
 * {@code System.Logger} and Jakarta Mail's own logging go. Includes parameters and stack traces in the text.
 */
public final class LogCapture implements AutoCloseable {

    private final Logger root = Logger.getLogger("");
    private final Level previous = root.getLevel();
    private final List<String> records = new ArrayList<>();
    private final SimpleFormatter formatter = new SimpleFormatter();
    private final Handler handler = new Handler() {
        @Override
        public synchronized void publish(LogRecord r) {
            StringBuilder b = new StringBuilder();
            b.append(r.getLevel()).append(' ').append(r.getLoggerName()).append(' ').append(formatter.formatMessage(r));
            b.append(" | raw: ").append(r.getMessage());
            if (r.getParameters() != null) {
                for (Object p : r.getParameters()) {
                    b.append(" | ").append(p);
                }
            }
            if (r.getThrown() != null) {
                StringWriter w = new StringWriter();
                r.getThrown().printStackTrace(new PrintWriter(w));
                b.append(" | ").append(w);
            }
            records.add(b.toString());
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    public LogCapture() {
        handler.setLevel(Level.ALL);
        root.setLevel(Level.ALL);
        root.addHandler(handler);
    }

    public synchronized List<String> records() {
        return List.copyOf(records);
    }

    public synchronized String all() {
        return String.join("\n", records);
    }

    @Override
    public void close() {
        root.removeHandler(handler);
        root.setLevel(previous);
    }
}
