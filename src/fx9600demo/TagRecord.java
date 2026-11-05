package fx9600demo;

/**
 * Snapshot inmutable de un tag agregado por EPC.
 * Cada lectura nueva produce una copia actualizada (nunca se muta).
 */
public final class TagRecord {

    public final String epc;
    public final String state;        // New / Gone / Back / Moving / Stationary / None
    public final short antennaId;
    public final long seenCount;      // acumulado de lecturas
    public final short peakRssi;      // dBm
    public final double phaseDeg;     // grados (0 si no disponible)
    public final short channelIndex;
    public final String pcHex;
    public final String crcHex;
    public final String xpcW1Hex;
    public final String xpcW2Hex;
    public final long firstSeenMs;    // reloj de la PC (epoch ms)
    public final long lastSeenMs;
    public final String readerFirstSeenUtc; // reloj de la lectora ("" si no reporta)
    public final String readerLastSeenUtc;

    /** Antenas 1..MAX_ANTENNA (FX9600 = 8 puertos). Indice 0 sin usar. */
    public static final int MAX_ANTENNA = 8;
    /** Si una antena no ve el tag en este tiempo, sus muestras se descartan. */
    private static final long SAMPLES_RESET_MS = 2000;
    /** RSSI por antena = moda de las ultimas N lecturas. */
    // ponytail: N fijo; pasarlo a config.properties si hay que afinarlo en campo.
    static final int MODE_SAMPLES = 20;
    /** samples[a] = ultimas lecturas de RSSI de la antena a; seenMs[a] = ultima vez vista (0 = nunca). */
    private record AntState(short[][] samples, long[] seenMs) { }
    // Arreglos privados: nunca se exponen ni se mutan despues de construir.
    private final short[][] antSamples;
    private final long[] antSeenMs;

    public TagRecord(String epc, String state, short antennaId, long seenCount,
                     short peakRssi, double phaseDeg, short channelIndex,
                     String pcHex, String crcHex, String xpcW1Hex, String xpcW2Hex,
                     long firstSeenMs, long lastSeenMs,
                     String readerFirstSeenUtc, String readerLastSeenUtc) {
        this(epc, state, antennaId, seenCount, peakRssi, phaseDeg, channelIndex, pcHex, crcHex,
                xpcW1Hex, xpcW2Hex, firstSeenMs, lastSeenMs, readerFirstSeenUtc, readerLastSeenUtc,
                antennaUpdate(emptySamples(), new long[MAX_ANTENNA + 1], antennaId, peakRssi, lastSeenMs));
    }

    private TagRecord(String epc, String state, short antennaId, long seenCount,
                      short peakRssi, double phaseDeg, short channelIndex,
                      String pcHex, String crcHex, String xpcW1Hex, String xpcW2Hex,
                      long firstSeenMs, long lastSeenMs,
                      String readerFirstSeenUtc, String readerLastSeenUtc, AntState ant) {
        this.antSamples = ant.samples();
        this.antSeenMs = ant.seenMs();
        this.epc = epc;
        this.state = state;
        this.antennaId = antennaId;
        this.seenCount = seenCount;
        this.peakRssi = peakRssi;
        this.phaseDeg = phaseDeg;
        this.channelIndex = channelIndex;
        this.pcHex = pcHex;
        this.crcHex = crcHex;
        this.xpcW1Hex = xpcW1Hex;
        this.xpcW2Hex = xpcW2Hex;
        this.firstSeenMs = firstSeenMs;
        this.lastSeenMs = lastSeenMs;
        this.readerFirstSeenUtc = readerFirstSeenUtc;
        this.readerLastSeenUtc = readerLastSeenUtc;
    }

    /** Nueva version del record con los datos de la lectura mas reciente. */
    public TagRecord withRead(String state, short antennaId, long addedReads,
                              short peakRssi, double phaseDeg, short channelIndex,
                              String pcHex, String crcHex, String xpcW1Hex, String xpcW2Hex,
                              long nowMs, String readerFirstSeenUtc, String readerLastSeenUtc) {
        return new TagRecord(this.epc, state, antennaId, this.seenCount + addedReads,
                peakRssi, phaseDeg, channelIndex, pcHex, crcHex, xpcW1Hex, xpcW2Hex,
                this.firstSeenMs, nowMs,
                this.readerFirstSeenUtc.isEmpty() ? readerFirstSeenUtc : this.readerFirstSeenUtc,
                readerLastSeenUtc,
                antennaUpdate(antSamples.clone(), antSeenMs.clone(), antennaId, peakRssi, nowMs));
    }

    private static short[][] emptySamples() {
        short[][] s = new short[MAX_ANTENNA + 1][];
        java.util.Arrays.fill(s, new short[0]);
        return s;
    }

    /**
     * Aplica una lectura: {@code samples} es una copia superficial; solo se reemplaza
     * el arreglo de la antena leida (los demas se comparten y nunca se mutan).
     */
    private static AntState antennaUpdate(short[][] samples, long[] seen, short antennaId, short peakRssi, long nowMs) {
        if (antennaId >= 1 && antennaId <= MAX_ANTENNA) {
            boolean stale = seen[antennaId] == 0 || nowMs - seen[antennaId] > SAMPLES_RESET_MS;
            short[] old = stale ? new short[0] : samples[antennaId];
            int keep = Math.min(old.length, MODE_SAMPLES - 1);
            short[] next = java.util.Arrays.copyOfRange(old, old.length - keep, old.length + 1);
            next[keep] = peakRssi;
            samples[antennaId] = next;
            seen[antennaId] = nowMs;
        }
        return new AntState(samples, seen);
    }

    /** Moda de las muestras; en empate gana el RSSI mas alto. */
    static int mode(short[] values) {
        short[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        int best = sorted[0];
        int bestCount = 0;
        for (int i = 0; i < sorted.length; ) {
            int j = i;
            while (j < sorted.length && sorted[j] == sorted[i]) {
                j++;
            }
            if (j - i >= bestCount) { // >= : orden ascendente, el empate se queda con el mas alto
                best = sorted[i];
                bestCount = j - i;
            }
            i = j;
        }
        return best;
    }

    public void appendJson(StringBuilder sb) {
        sb.append('{');
        Json.field(sb, "epc", epc).append(',');
        Json.field(sb, "state", state).append(',');
        Json.field(sb, "antennaId", antennaId).append(',');
        Json.field(sb, "seenCount", seenCount).append(',');
        Json.field(sb, "peakRssi", peakRssi).append(',');
        Json.field(sb, "phaseDeg", Math.round(phaseDeg * 100.0) / 100.0).append(',');
        Json.field(sb, "channelIndex", channelIndex).append(',');
        Json.field(sb, "pc", pcHex).append(',');
        Json.field(sb, "crc", crcHex).append(',');
        Json.field(sb, "xpcW1", xpcW1Hex).append(',');
        Json.field(sb, "xpcW2", xpcW2Hex).append(',');
        Json.field(sb, "firstSeenMs", firstSeenMs).append(',');
        Json.field(sb, "lastSeenMs", lastSeenMs).append(',');
        Json.field(sb, "readerFirstSeenUtc", readerFirstSeenUtc).append(',');
        Json.field(sb, "readerLastSeenUtc", readerLastSeenUtc).append(',');
        // RSSI por antena para la clasificacion por zonas (la hace la pagina web)
        sb.append("\"antennas\":[");
        boolean first = true;
        for (int a = 1; a <= MAX_ANTENNA; a++) {
            if (antSeenMs[a] == 0) {
                continue;
            }
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('{');
            Json.field(sb, "id", a).append(',');
            Json.field(sb, "rssi", mode(antSamples[a])).append(',');
            Json.field(sb, "samples", antSamples[a].length).append(',');
            Json.field(sb, "lastSeenMs", antSeenMs[a]);
            sb.append('}');
        }
        sb.append("]}");
    }
}
