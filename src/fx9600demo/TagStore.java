package fx9600demo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Almacen en memoria de tags agregados por EPC.
 * Thread-safe: los eventos del SDK escriben aqui y el servidor web lee snapshots.
 */
public final class TagStore {

    /** Datos de una lectura individual, tal como los entrega la fuente (SDK o simulador). */
    public static final class Read {
        public final String epc;
        public final String state;
        public final short antennaId;
        public final long reads;
        public final short peakRssi;
        public final double phaseDeg;
        public final short channelIndex;
        public final String pcHex;
        public final String crcHex;
        public final String xpcW1Hex;
        public final String xpcW2Hex;
        public final String readerFirstSeenUtc;
        public final String readerLastSeenUtc;

        public Read(String epc, String state, short antennaId, long reads,
                    short peakRssi, double phaseDeg, short channelIndex,
                    String pcHex, String crcHex, String xpcW1Hex, String xpcW2Hex,
                    String readerFirstSeenUtc, String readerLastSeenUtc) {
            this.epc = epc;
            this.state = state;
            this.antennaId = antennaId;
            this.reads = reads;
            this.peakRssi = peakRssi;
            this.phaseDeg = phaseDeg;
            this.channelIndex = channelIndex;
            this.pcHex = pcHex;
            this.crcHex = crcHex;
            this.xpcW1Hex = xpcW1Hex;
            this.xpcW2Hex = xpcW2Hex;
            this.readerFirstSeenUtc = readerFirstSeenUtc;
            this.readerLastSeenUtc = readerLastSeenUtc;
        }
    }

    private final ConcurrentHashMap<String, TagRecord> tags = new ConcurrentHashMap<>();
    private final AtomicLong totalReads = new AtomicLong();

    /** Grabacion CSV de lecturas crudas (abierta entre Iniciar y Detener). */
    public final CsvLog csv = new CsvLog();

    public void record(Read r) {
        long now = System.currentTimeMillis();
        totalReads.addAndGet(r.reads);
        csv.write(now, r);
        tags.compute(r.epc, (epc, existing) -> {
            if (existing == null) {
                return new TagRecord(epc, r.state, r.antennaId, r.reads, r.peakRssi,
                        r.phaseDeg, r.channelIndex, r.pcHex, r.crcHex, r.xpcW1Hex, r.xpcW2Hex,
                        now, now, r.readerFirstSeenUtc, r.readerLastSeenUtc);
            }
            return existing.withRead(r.state, r.antennaId, r.reads, r.peakRssi,
                    r.phaseDeg, r.channelIndex, r.pcHex, r.crcHex, r.xpcW1Hex, r.xpcW2Hex,
                    now, r.readerFirstSeenUtc, r.readerLastSeenUtc);
        });
    }

    public void clear() {
        tags.clear();
        totalReads.set(0);
    }

    public int uniqueTagCount() {
        return tags.size();
    }

    public long totalReadCount() {
        return totalReads.get();
    }

    /** Snapshot ordenado por ultima vez visto (mas reciente primero). */
    public List<TagRecord> snapshot() {
        List<TagRecord> list = new ArrayList<>(tags.values());
        list.sort((a, b) -> Long.compare(b.lastSeenMs, a.lastSeenMs));
        return list;
    }
}
