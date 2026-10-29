package fx9600demo;

import java.util.Random;

/**
 * Simulador de tags para desarrollar y probar la interfaz sin la lectora fisica.
 * Genera lecturas con valores realistas (RSSI, fase, canal, antenas).
 */
public final class SimSource implements TagSource {

    private static final int TICK_MS = 100;

    private final TagStore store;
    private final int tagCount;
    private final int readsPerSecond;
    private final Random random = new Random();

    private volatile boolean connected;
    private volatile boolean reading;
    private Thread worker;

    private String[] epcs;
    private short[] baseRssi;
    private short[] antenna;

    public SimSource(TagStore store, int tagCount, int readsPerSecond) {
        this.store = store;
        this.tagCount = tagCount;
        this.readsPerSecond = readsPerSecond;
    }

    @Override
    public void connect() {
        epcs = new String[tagCount];
        baseRssi = new short[tagCount];
        antenna = new short[tagCount];
        for (int i = 0; i < tagCount; i++) {
            epcs[i] = String.format("E28011606000020%09X", random.nextInt(0x7FFFFFFF));
            baseRssi[i] = (short) (-45 - random.nextInt(30));
            antenna[i] = (short) (1 + random.nextInt(4));
        }
        connected = true;
    }

    @Override
    public void disconnect() {
        try {
            stopReading();
        } catch (Exception ignored) {
            // el simulador no falla al detenerse
        }
        connected = false;
    }

    @Override
    public synchronized void startReading() {
        if (reading) {
            return;
        }
        reading = true;
        worker = new Thread(this::runLoop, "sim-source");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public synchronized void stopReading() {
        reading = false;
        if (worker != null) {
            worker.interrupt();
            worker = null;
        }
    }

    private void runLoop() {
        int readsPerTick = Math.max(1, readsPerSecond * TICK_MS / 1000);
        while (reading) {
            for (int n = 0; n < readsPerTick; n++) {
                int i = random.nextInt(tagCount);
                short rssi = (short) (baseRssi[i] + random.nextInt(7) - 3);
                double phase = random.nextDouble() * 180.0;
                short channel = (short) (1 + random.nextInt(50));
                store.record(new TagStore.Read(epcs[i], "New", antenna[i], 1, rssi, phase,
                        channel, "3000", "", "0", "0", "", ""));
            }
            try {
                Thread.sleep(TICK_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public boolean isReading() {
        return reading;
    }

    @Override
    public String description() {
        return "Simulador (" + tagCount + " tags, ~" + readsPerSecond + " lecturas/s)";
    }

    @Override
    public String lastError() {
        return "";
    }
}
