package fx9600demo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Carga y valida config.properties (junto al ejecutable).
 * Todo lo que puede cambiar entre redes (IP, puertos, timeouts) vive ahi.
 */
public final class AppConfig {

    public final String readerIp;
    public final int readerPort;
    public final int readerTimeoutMs;
    public final int reconnectSeconds;

    public final String webHost;
    public final int webPort;

    public final boolean simEnabled;
    public final int simTagCount;
    public final int simReadsPerSecond;

    private AppConfig(Properties p, boolean forceSim) {
        this.readerIp = required(p, "reader.ip");
        this.readerPort = intProp(p, "reader.port", 1, 65535);
        this.readerTimeoutMs = intProp(p, "reader.timeout.ms", 100, 120000);
        this.reconnectSeconds = intProp(p, "reader.reconnect.seconds", 0, 3600);
        this.webHost = required(p, "web.host");
        this.webPort = intProp(p, "web.port", 1, 65535);
        this.simEnabled = forceSim || Boolean.parseBoolean(p.getProperty("sim.enabled", "false").trim());
        this.simTagCount = intProp(p, "sim.tag.count", 1, 10000);
        this.simReadsPerSecond = intProp(p, "sim.reads.per.second", 1, 100000);
    }

    public static AppConfig load(Path file, boolean forceSim) {
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException("No se encontro el archivo de configuracion: " + file.toAbsolutePath());
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + file + ": " + e.getMessage(), e);
        }
        return new AppConfig(p, forceSim);
    }

    private static String required(Properties p, String key) {
        String v = p.getProperty(key);
        if (v == null || v.trim().isEmpty()) {
            throw new IllegalStateException("Falta la propiedad '" + key + "' en config.properties");
        }
        return v.trim();
    }

    private static int intProp(Properties p, String key, int min, int max) {
        String raw = required(p, key);
        int value;
        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("La propiedad '" + key + "' debe ser un numero, se encontro: '" + raw + "'");
        }
        if (value < min || value > max) {
            throw new IllegalStateException("La propiedad '" + key + "' debe estar entre " + min + " y " + max + ", se encontro: " + value);
        }
        return value;
    }
}
