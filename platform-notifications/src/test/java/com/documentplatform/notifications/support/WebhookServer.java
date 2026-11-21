package com.documentplatform.notifications.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * A local HTTP server standing in for Teams Workflows and Slack incoming webhooks (the JDK's built-in server). It
 * records every request and answers with the replies the test queued (default: 200), optionally after a delay.
 */
public final class WebhookServer implements AutoCloseable {

    public record Request(String method, String path, String query, String contentType, String body) {
    }

    public record Reply(int status, Map<String, String> headers, long delayMillis) {
        public static Reply of(int status) {
            return new Reply(status, Map.of(), 0);
        }
    }

    private final HttpServer server;
    private final List<Request> requests = new ArrayList<>();
    private final Deque<Reply> queued = new ArrayDeque<>();
    private Reply always = Reply.of(200);

    public WebhookServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 50);
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "webhook-server");
            t.setDaemon(true);
            return t;
        }));
        server.createContext("/", this::handle);
        server.start();
    }

    /** The base address, for example {@code http://127.0.0.1:54321}. */
    public String base() {
        return "http://" + server.getAddress().getAddress().getHostAddress() + ":" + server.getAddress().getPort();
    }

    /** Replies used in order, before the default. */
    public synchronized WebhookServer then(Reply reply) {
        queued.add(reply);
        return this;
    }

    public synchronized WebhookServer always(Reply reply) {
        always = reply;
        queued.clear();
        return this;
    }

    public synchronized List<Request> requests() {
        return List.copyOf(requests);
    }

    public synchronized void reset() {
        requests.clear();
        queued.clear();
        always = Reply.of(200);
    }

    private void handle(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Reply reply;
        synchronized (this) {
            requests.add(new Request(ex.getRequestMethod(), ex.getRequestURI().getPath(),
                    ex.getRequestURI().getRawQuery(), ex.getRequestHeaders().getFirst("Content-Type"), body));
            reply = queued.isEmpty() ? always : queued.poll();
        }
        if (reply.delayMillis() > 0) {
            try {
                Thread.sleep(reply.delayMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        reply.headers().forEach((k, v) -> ex.getResponseHeaders().add(k, v));
        byte[] out = (reply.status() == 200 ? "ok" : "error").getBytes(StandardCharsets.UTF_8);
        try {
            ex.sendResponseHeaders(reply.status(), out.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(out);
            }
        } catch (IOException clientGone) {
            // the client timed out first
        } finally {
            ex.close();
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
