package fx9600demo;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * FX9600 Reading Demo
 * Lee tags RFID desde una Zebra FX9600 (o un simulador) y los muestra
 * en una tabla web local en tiempo real.
 *
 * Uso:
 *   run.bat        conecta con la lectora configurada en config.properties
 *   run-sim.bat    modo simulacion (sin lectora)
 */
public final class Main {

    public static void main(String[] args) {
        boolean forceSim = false;
        for (String arg : args) {
            if ("--sim".equalsIgnoreCase(arg)) {
                forceSim = true;
            }
        }

        AppConfig cfg;
        try {
            cfg = AppConfig.load(Paths.get("config.properties"), forceSim);
        } catch (IllegalStateException e) {
            System.err.println("Error de configuracion: " + e.getMessage());
            System.exit(1);
            return;
        }

        TagStore store = new TagStore();
        TagSource source = cfg.simEnabled
                ? new SimSource(store, cfg.simTagCount, cfg.simReadsPerSecond)
                : new ZebraSource(cfg, store);

        Path indexHtml = Paths.get("web", "index.html");
        WebServer web = new WebServer(cfg, store, source, indexHtml);
        try {
            web.start();
        } catch (Exception e) {
            System.err.println("No se pudo iniciar el servidor web en " + cfg.webHost + ":" + cfg.webPort
                    + " - " + e.getMessage());
            System.exit(1);
            return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            source.disconnect();
            store.csv.stop();
            web.stop();
        }, "shutdown"));

        System.out.println("==========================================================");
        System.out.println("  FX9600 Reading Demo");
        System.out.println("  Fuente:  " + source.description());
        System.out.println("  Web:     http://" + hostForUrl(cfg.webHost) + ":" + cfg.webPort);
        System.out.println("  (Ctrl+C para salir)");
        System.out.println("==========================================================");

        startConnectionKeeper(source, cfg.reconnectSeconds);

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Intenta conectar al arrancar y, si la conexion se pierde,
     * reintenta cada N segundos (configurable en config.properties).
     */
    private static void startConnectionKeeper(TagSource source, int reconnectSeconds) {
        Thread keeper = new Thread(() -> {
            while (true) {
                if (!source.isConnected()) {
                    try {
                        source.connect();
                    } catch (Exception e) {
                        System.out.println("[conexion] " + e.getMessage()
                                + (reconnectSeconds > 0
                                        ? " (reintento en " + reconnectSeconds + " s)"
                                        : ""));
                        if (reconnectSeconds <= 0) {
                            return;
                        }
                    }
                }
                try {
                    Thread.sleep(Math.max(1, reconnectSeconds) * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "connection-keeper");
        keeper.setDaemon(true);
        keeper.start();
    }

    private static String hostForUrl(String bindHost) {
        return "0.0.0.0".equals(bindHost) ? "localhost" : bindHost;
    }
}
