package fx9600demo;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Graba cada lectura cruda a un CSV mientras el inventario esta corriendo.
 * Iniciar abre un archivo nuevo en capturas/, Detener lo cierra.
 */
public final class CsvLog {

    private static final Path DIR = Paths.get("capturas");
    private static final String HEADER = "pc_time,epc,state,antenna,reads,rssi_dbm,phase_deg,channel,"
            + "pc,crc,xpc_w1,xpc_w2,reader_first_seen_utc,reader_last_seen_utc";

    private BufferedWriter out;
    private Path file;

    /** Abre un archivo nuevo. No hace nada si ya hay uno abierto. */
    public synchronized void start() throws IOException {
        if (out != null) {
            return;
        }
        Files.createDirectories(DIR);
        file = DIR.resolve("lecturas_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".csv");
        out = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
        out.write(HEADER);
        out.newLine();
        System.out.println("[csv] Grabando en " + file.toAbsolutePath());
    }

    /** Cierra el archivo actual (si hay). */
    public synchronized void stop() {
        if (out == null) {
            return;
        }
        try {
            out.close();
            System.out.println("[csv] Guardado " + file.toAbsolutePath());
        } catch (IOException e) {
            System.out.println("[csv] Error al cerrar " + file + ": " + e.getMessage());
        }
        out = null;
    }

    // ponytail: sin comillas/escape, todos los campos son hex, numeros o fechas sin comas
    public synchronized void write(long nowMs, TagStore.Read r) {
        if (out == null) {
            return;
        }
        try {
            out.write(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date(nowMs)) + ','
                    + r.epc + ',' + r.state + ',' + r.antennaId + ',' + r.reads + ',' + r.peakRssi + ','
                    + Math.round(r.phaseDeg * 100.0) / 100.0 + ',' + r.channelIndex + ','
                    + r.pcHex + ',' + r.crcHex + ',' + r.xpcW1Hex + ',' + r.xpcW2Hex + ','
                    + r.readerFirstSeenUtc + ',' + r.readerLastSeenUtc);
            out.newLine();
        } catch (IOException e) {
            System.out.println("[csv] Error al escribir, se detiene la grabacion: " + e.getMessage());
            stop();
        }
    }
}
