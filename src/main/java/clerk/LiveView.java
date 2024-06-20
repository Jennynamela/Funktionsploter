package main.java.clerk;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

enum SSEType { WRITE, CALL, SCRIPT, LOAD, CLEAR; }

public class LiveView {
    final HttpServer server; // server instance
    final int port; // port number
    static int defaultPort = 50001; // default port number
    static final String index = "src/main/resources/web/index.html"; // the index.html file within the web folder
    static Map<Integer, LiveView> views = new ConcurrentHashMap<>(); // map of views by port
    List<String> paths = new ArrayList<>(); // list of paths that have been loaded

    static void setDefaultPort(int port) {
        defaultPort = port != 0 ? Math.abs(port) : 50001;
    } // set default port number

    static int getDefaultPort() {
        return defaultPort;
    } // get default port number

    List<HttpExchange> sseClientConnections; // list of SSE connections to clients

    // lock required to temporarily block processing of `SSEType.LOAD
    Lock lock = new ReentrantLock(); // lock for synchronization
    Condition loadEventOccurredCondition = lock.newCondition(); // condition for signaling load event
    boolean loadEventOccurred = false; // flag to signal load event

    static LiveView onPort(int port) {
        port = Math.abs(port); // ensure port is positive
        try {
            if (!views.containsKey(port)) // if view does not exist
                views.put(port, new LiveView(port)); // create a new LiveView instance
            return views.get(port);
        } catch (IOException e) { // catch IOException
            System.err.printf("Error starting Server: %s\n", e.getMessage()); // print error message
            e.printStackTrace(); // print stack trace
            return null;
        }
    }

    static LiveView onPort() {
        return onPort(defaultPort);
    } // create a LiveView instance on the default port

    private LiveView(int port) throws IOException {
        this.port = port;
        server = HttpServer.create(new InetSocketAddress("localhost", port), 0); // create a new HttpServer instance
        sseClientConnections = new CopyOnWriteArrayList<>(); // thread-safe variant of ArrayList
        System.out.println("Server started on port: " + port); // print message
        System.out.println("Open http://localhost:" + port + " in your browser");

        // loaded-Request to signal successful processing of SSEType.LOAD
        server.createContext("/loaded", exchange -> {
            if (!exchange.getRequestMethod().equalsIgnoreCase("post")) {
                exchange.sendResponseHeaders(405, -1); // Method Not Allowed
                return;
            }
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
            lock.lock();
            try { // try/finally pattern for locks
                loadEventOccurred = true;
                loadEventOccurredCondition.signalAll();
            } finally {
                lock.unlock();
            }
        });

        // SSE context
        server.createContext("/events", exchange -> {
            if (!exchange.getRequestMethod().equalsIgnoreCase("get")) {
                exchange.sendResponseHeaders(405, -1); // Method Not Allowed
                return;
            }
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.getResponseHeaders().add("Cache-Control", "no-cache");
            exchange.getResponseHeaders().add("Connection", "keep-alive");
            exchange.sendResponseHeaders(200, 0);
            sseClientConnections.add(exchange);
        });

        // initial html site
        server.createContext("/", exchange -> {
            if (!exchange.getRequestMethod().equalsIgnoreCase("get")) {
                exchange.sendResponseHeaders(405, -1); // Method Not Allowed
                return;
            }
            final String path = exchange.getRequestURI().getPath().equals("/") ? index
                    : "." + exchange.getRequestURI().getPath();
            try (final InputStream stream = new FileInputStream(path)) {
                final byte[] bytes = stream.readAllBytes();
                exchange.getResponseHeaders()
                        .add("Content-Type", Files.probeContentType(Path.of(path)) + "; charset=utf-8");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.getResponseBody().flush();
            } finally {
                exchange.close();
            }
        });

        server.setExecutor(Executors.newFixedThreadPool(5));
        server.start();
    }

    void sendServerEvent(SSEType sseType, String data) {
        List<HttpExchange> deadConnections = new ArrayList<>();
        for (HttpExchange connection : sseClientConnections) {
            if (sseType == SSEType.LOAD) lock.lock();
            try {
                byte[] binaryData = data.getBytes(StandardCharsets.UTF_8);
                String base64Data = Base64.getEncoder().encodeToString(binaryData);
                String message = "data: " + sseType + ":" + base64Data + "\n\n";
                connection.getResponseBody().write(message.getBytes());
                connection.getResponseBody().flush();
                if (sseType == SSEType.LOAD && !loadEventOccurred) {
                    loadEventOccurredCondition.await(1_000, TimeUnit.MILLISECONDS);
                    if (loadEventOccurred) paths.add(data);
                    else System.err.println("LOAD-Timeout: " + data);
                }
            } catch (IOException e) {
                deadConnections.add(connection);
            } catch (InterruptedException e) {
                System.err.println("LOAD-Timeout: " + data + ", " + e);
            } finally {
                if (sseType == SSEType.LOAD) {
                    loadEventOccurred = false;
                    lock.unlock();
                }
            }
        }
        sseClientConnections.removeAll(deadConnections);
    }

    public void createResponseContext(String path, Consumer<String> delegate) {
        server.createContext(path, exchange -> {
            if (!exchange.getRequestMethod().equalsIgnoreCase("post")) {
                exchange.sendResponseHeaders(405, -1); // Method Not Allowed
                return;
            }

            String content_length = exchange.getRequestHeaders().getFirst("Content-length");
            if (content_length == null) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }

            try {
                int length = Integer.parseInt(content_length);
                byte[] data = new byte[length];
                exchange.getRequestBody().read(data);
                delegate.accept(new String(data));
            } catch (NumberFormatException e) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }

            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
    }

    public void stop() {
        sseClientConnections.clear();
        views.remove(port);
        server.stop(0);
    }

    static void shutdown() {
        views.forEach((k, v) -> v.stop());
    }
}