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

    public TagRecord(String epc, String state, short antennaId, long seenCount,
                     short peakRssi, double phaseDeg, short channelIndex,
                     String pcHex, String crcHex, String xpcW1Hex, String xpcW2Hex,
                     long firstSeenMs, long lastSeenMs,
                     String readerFirstSeenUtc, String readerLastSeenUtc) {
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
                readerLastSeenUtc);
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
        Json.field(sb, "readerLastSeenUtc", readerLastSeenUtc);
        sb.append('}');
    }
}
