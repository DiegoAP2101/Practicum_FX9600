package fx9600demo;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Servidor web local: sirve la pagina y expone la API JSON.
 *
 * Endpoints (los mismos que puede consumir cualquier aplicativo futuro):
 *   GET  /            pagina web (tabla en vivo)
 *   GET  /api/tags    snapshot de todos los tags + estado de conexion
 *   POST /api/start   iniciar lectura
 *   POST /api/stop    detener lectura
 *   POST /api/clear   limpiar tabla y contadores
 */
public final class WebServer {

    private final AppConfig cfg;
    private final TagStore store;
    private final TagSource source;
    private final Path indexHtml;
    private HttpServer server;

    // Cronometro de lectura (acumulado + marca de inicio actual)
    private final Object clockLock = new Object();
    private long accumulatedMs = 0;
    private long readingSinceMs = -1;

    public WebServer(AppConfig cfg, TagStore store, TagSource source, Path indexHtml) {
        this.cfg = cfg;
        this.store = store;
        this.source = source;
        this.indexHtml = indexHtml;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(cfg.webHost, cfg.webPort), 0);
        server.createContext("/", this::handleIndex);
        server.createContext("/api/tags", this::handleTags);
        server.createContext("/api/start", ex -> handleAction(ex, "start"));
        server.createContext("/api/stop", ex -> handleAction(ex, "stop"));
        server.createContext("/api/clear", ex -> handleAction(ex, "clear"));
        server.setExecutor(Executors.newFixedThreadPool(2));
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void handleIndex(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            sendText(ex, 405, "text/plain", "Metodo no permitido");
            return;
        }
        if (!"/".equals(ex.getRequestURI().getPath())) {
            sendText(ex, 404, "text/plain", "No encontrado");
            return;
        }
        if (!Files.isRegularFile(indexHtml)) {
            sendText(ex, 500, "text/plain", "No se encontro " + indexHtml.toAbsolutePath());
            return;
        }
        byte[] body = Files.readAllBytes(indexHtml);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(200, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }

    private void handleTags(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            sendText(ex, 405, "text/plain", "Metodo no permitido");
            return;
        }
        sendText(ex, 200, "application/json", buildSnapshotJson());
    }

    private void handleAction(HttpExchange ex, String action) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            sendText(ex, 405, "text/plain", "Metodo no permitido");
            return;
        }
        String error = "";
        try {
            switch (action) {
                case "start":
                    if (!source.isConnected()) {
                        source.connect();
                    }
                    store.csv.start();
                    try {
                        source.startReading();
                    } catch (Exception e) {
                        store.csv.stop();
                        throw e;
                    }
                    markReadingStarted();
                    break;
                case "stop":
                    try {
                        source.stopReading();
                    } finally {
                        store.csv.stop();
                    }
                    markReadingStopped();
                    break;
                case "clear":
                    store.clear();
                    resetClock();
                    break;
                default:
                    sendText(ex, 404, "text/plain", "Accion desconocida");
                    return;
            }
        } catch (Exception e) {
            error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
        StringBuilder sb = new StringBuilder(64);
        sb.append('{');
        Json.field(sb, "ok", error.isEmpty()).append(',');
        Json.field(sb, "error", error);
        sb.append('}');
        sendText(ex, error.isEmpty() ? 200 : 500, "application/json", sb.toString());
    }

    private String buildSnapshotJson() {
        List<TagRecord> tags = store.snapshot();
        StringBuilder sb = new StringBuilder(256 + tags.size() * 320);
        sb.append("{\"status\":{");
        Json.field(sb, "connected", source.isConnected()).append(',');
        Json.field(sb, "reading", source.isReading()).append(',');
        Json.field(sb, "source", source.description()).append(',');
        Json.field(sb, "error", source.lastError()).append(',');
        Json.field(sb, "uniqueTags", store.uniqueTagCount()).append(',');
        Json.field(sb, "totalReads", store.totalReadCount()).append(',');
        Json.field(sb, "elapsedMs", elapsedMs()).append(',');
        Json.field(sb, "serverTimeMs", System.currentTimeMillis());
        sb.append("},\"tags\":[");
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            tags.get(i).appendJson(sb);
        }
        sb.append("]}");
        return sb.toString();
    }

    private void markReadingStarted() {
        synchronized (clockLock) {
            if (readingSinceMs < 0) {
                readingSinceMs = System.currentTimeMillis();
            }
        }
    }

    private void markReadingStopped() {
        synchronized (clockLock) {
            if (readingSinceMs >= 0) {
                accumulatedMs += System.currentTimeMillis() - readingSinceMs;
                readingSinceMs = -1;
            }
        }
    }

    private void resetClock() {
        synchronized (clockLock) {
            accumulatedMs = 0;
            if (readingSinceMs >= 0) {
                readingSinceMs = System.currentTimeMillis();
            }
        }
    }

    private long elapsedMs() {
        synchronized (clockLock) {
            long elapsed = accumulatedMs;
            if (readingSinceMs >= 0) {
                elapsed += System.currentTimeMillis() - readingSinceMs;
            }
            return elapsed;
        }
    }

    private static void sendText(HttpExchange ex, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
