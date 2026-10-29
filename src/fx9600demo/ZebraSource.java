package fx9600demo;

import com.mot.rfid.api3.ACCESS_OPERATION_CODE;
import com.mot.rfid.api3.DISCONNECTION_EVENT_TYPE;
import com.mot.rfid.api3.InvalidUsageException;
import com.mot.rfid.api3.OperationFailureException;
import com.mot.rfid.api3.RFIDReader;
import com.mot.rfid.api3.RfidEventsListener;
import com.mot.rfid.api3.RfidReadEvents;
import com.mot.rfid.api3.RfidStatusEvents;
import com.mot.rfid.api3.START_TRIGGER_TYPE;
import com.mot.rfid.api3.STATUS_EVENT_TYPE;
import com.mot.rfid.api3.STOP_TRIGGER_TYPE;
import com.mot.rfid.api3.SYSTEMTIME;
import com.mot.rfid.api3.TAG_EVENT_REPORT_TRIGGER;
import com.mot.rfid.api3.TAG_FIELD;
import com.mot.rfid.api3.TagData;
import com.mot.rfid.api3.TagStorageSettings;
import com.mot.rfid.api3.TriggerInfo;

/**
 * Conexion con la lectora FX9600 usando el SDK Zebra RFID API3 (Java Host SDK).
 * Patron tomado del sample oficial J_RFIDHostSample1 del SDK.
 */
public final class ZebraSource implements TagSource {

    private static final int READ_BATCH_SIZE = 200;
    private static final String[] TAG_STATES = {"New", "Gone", "Back", "Moving", "Stationary", "None"};

    private final AppConfig cfg;
    private final TagStore store;

    private volatile RFIDReader reader;
    private volatile boolean connected;
    private volatile boolean reading;
    private volatile String lastError = "";

    public ZebraSource(AppConfig cfg, TagStore store) {
        this.cfg = cfg;
        this.store = store;
    }

    @Override
    public synchronized void connect() throws Exception {
        if (connected) {
            return;
        }
        RFIDReader r = new RFIDReader(cfg.readerIp, cfg.readerPort, cfg.readerTimeoutMs);
        try {
            r.connect();

            // Pedirle a la lectora que reporte TODOS los campos por tag
            // (RSSI, fase, canal, PC, CRC, XPC, timestamps, etc.)
            TagStorageSettings tss = r.Config.getTagStorageSettings();
            tss.setTagFields(TAG_FIELD.ALL_TAG_FIELDS);
            r.Config.setTagStorageSettings(tss);

            r.Events.setTagReadEvent(true);
            r.Events.setAttachTagDataWithReadEvent(false);
            r.Events.setInventoryStartEvent(true);
            r.Events.setInventoryStopEvent(true);
            r.Events.setReaderDisconnectEvent(true);
            r.Events.setReaderExceptionEvent(true);
            r.Events.setAntennaEvent(true);
            r.Events.addEventsListener(new Listener());

            reader = r;
            connected = true;
            lastError = "";
            System.out.println("[reader] Conectado a " + cfg.readerIp + ":" + cfg.readerPort);
        } catch (InvalidUsageException e) {
            lastError = friendly("Parametro invalido al conectar", e.getInfo(), e.getVendorMessage());
            throw new Exception(lastError, e);
        } catch (OperationFailureException e) {
            lastError = friendly("No se pudo conectar a " + cfg.readerIp + ":" + cfg.readerPort,
                    e.getStatusDescription(), e.getVendorMessage());
            throw new Exception(lastError, e);
        }
    }

    @Override
    public synchronized void disconnect() {
        RFIDReader r = reader;
        reader = null;
        connected = false;
        reading = false;
        if (r != null) {
            try {
                r.disconnect();
            } catch (Exception e) {
                System.out.println("[reader] Error al desconectar: " + e.getMessage());
            }
        }
    }

    @Override
    public synchronized void startReading() throws Exception {
        requireConnected();
        try {
            TriggerInfo triggerInfo = buildTriggerInfo();
            reader.Actions.purgeTags();
            reader.Actions.Inventory.perform(null, triggerInfo, null);
            reading = true;
            lastError = "";
        } catch (InvalidUsageException e) {
            lastError = friendly("Error al iniciar inventario", e.getInfo(), e.getVendorMessage());
            throw new Exception(lastError, e);
        } catch (OperationFailureException e) {
            lastError = friendly("Error al iniciar inventario", e.getStatusDescription(), e.getVendorMessage());
            throw new Exception(lastError, e);
        }
    }

    @Override
    public synchronized void stopReading() throws Exception {
        if (!connected || !reading) {
            reading = false;
            return;
        }
        try {
            reader.Actions.Inventory.stop();
            reading = false;
        } catch (InvalidUsageException e) {
            lastError = friendly("Error al detener inventario", e.getInfo(), e.getVendorMessage());
            throw new Exception(lastError, e);
        } catch (OperationFailureException e) {
            lastError = friendly("Error al detener inventario", e.getStatusDescription(), e.getVendorMessage());
            throw new Exception(lastError, e);
        }
    }

    private TriggerInfo buildTriggerInfo() {
        TriggerInfo t = new TriggerInfo();
        t.StartTrigger.setTriggerType(START_TRIGGER_TYPE.START_TRIGGER_TYPE_IMMEDIATE);
        t.StopTrigger.setTriggerType(STOP_TRIGGER_TYPE.STOP_TRIGGER_TYPE_IMMEDIATE);
        t.TagEventReportInfo.setReportNewTagEvent(TAG_EVENT_REPORT_TRIGGER.MODERATED);
        t.TagEventReportInfo.setNewTagEventModeratedTimeoutMilliseconds((short) 500);
        t.TagEventReportInfo.setReportTagInvisibleEvent(TAG_EVENT_REPORT_TRIGGER.MODERATED);
        t.TagEventReportInfo.setTagInvisibleEventModeratedTimeoutMilliseconds((short) 500);
        t.TagEventReportInfo.setReportTagBackToVisibilityEvent(TAG_EVENT_REPORT_TRIGGER.MODERATED);
        t.TagEventReportInfo.setTagBackToVisibilityModeratedTimeoutMilliseconds((short) 500);
        t.setTagReportTrigger(1);
        return t;
    }

    private void requireConnected() throws Exception {
        if (!connected || reader == null) {
            throw new Exception("La lectora no esta conectada" + (lastError.isEmpty() ? "" : (": " + lastError)));
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
        return "FX9600 @ " + cfg.readerIp + ":" + cfg.readerPort;
    }

    @Override
    public String lastError() {
        return lastError;
    }

    private static String friendly(String base, String detail, String vendor) {
        StringBuilder sb = new StringBuilder(base);
        if (detail != null && !detail.isEmpty()) {
            sb.append(": ").append(detail);
        }
        if (vendor != null && !vendor.isEmpty()) {
            sb.append(" [").append(vendor).append(']');
        }
        return sb.toString();
    }

    private final class Listener implements RfidEventsListener {

        @Override
        public void eventReadNotify(RfidReadEvents events) {
            RFIDReader r = reader;
            if (r == null) {
                return;
            }
            TagData[] tags = r.Actions.getReadTags(READ_BATCH_SIZE);
            if (tags == null) {
                return;
            }
            for (TagData tag : tags) {
                // Ignorar resultados de operaciones de acceso (no aplican en inventario simple)
                if (tag.getOpCode() != null && tag.getOpCode() != ACCESS_OPERATION_CODE.ACCESS_OPERATION_NONE) {
                    continue;
                }
                store.record(new TagStore.Read(
                        tag.getTagID(),
                        stateName(tag),
                        tag.getAntennaID(),
                        Math.max(1, tag.getTagSeenCount()),
                        tag.getPeakRSSI(),
                        (180.0 / 32767.0) * tag.getPhase(),
                        tag.getChannelIndex(),
                        Integer.toHexString(tag.getPC()).toUpperCase(),
                        Integer.toHexString(tag.getCRC()).toUpperCase(),
                        Integer.toHexString(tag.getXPC_W1()).toUpperCase(),
                        Integer.toHexString(tag.getXPC_W2()).toUpperCase(),
                        formatSysTime(safeFirstSeen(tag)),
                        formatSysTime(safeLastSeen(tag))));
            }
        }

        @Override
        public void eventStatusNotify(RfidStatusEvents events) {
            STATUS_EVENT_TYPE type = events.StatusEventData.getStatusEventType();
            if (type == STATUS_EVENT_TYPE.DISCONNECTION_EVENT) {
                String cause = "Conexion perdida";
                DISCONNECTION_EVENT_TYPE d = events.StatusEventData.DisconnectionEventData.getDisconnectionEvent();
                if (d == DISCONNECTION_EVENT_TYPE.READER_INITIATED_DISCONNECTION) {
                    cause = "La lectora cerro la conexion";
                } else if (d == DISCONNECTION_EVENT_TYPE.READER_EXCEPTION) {
                    cause = "Excepcion en la lectora";
                }
                lastError = cause;
                connected = false;
                reading = false;
                System.out.println("[reader] Desconectado: " + cause);
                // La desconexion no puede ejecutarse dentro del hilo del evento (patron del sample)
                Thread t = new Thread(ZebraSource.this::disconnect, "reader-disconnect");
                t.setDaemon(true);
                t.start();
            } else if (type == STATUS_EVENT_TYPE.READER_EXCEPTION_EVENT) {
                lastError = "Excepcion de la lectora: "
                        + events.StatusEventData.ReaderExceptionEventData.getReaderExceptionEventInfo();
                System.out.println("[reader] " + lastError);
            } else {
                System.out.println("[reader] Evento: " + type);
            }
        }
    }

    private static String stateName(TagData tag) {
        com.mot.rfid.api3.TAG_EVENT ev = tag.getTagEvent();
        if (ev == null) {
            return "None";
        }
        int idx = ev.getValue() - 1;
        return (idx >= 0 && idx < TAG_STATES.length) ? TAG_STATES[idx] : "None";
    }

    private static SYSTEMTIME safeFirstSeen(TagData tag) {
        try {
            return tag.SeenTime.getUTCTime().getFirstSeenTimeStamp();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static SYSTEMTIME safeLastSeen(TagData tag) {
        try {
            return tag.SeenTime.getUTCTime().getLastSeenTimeStamp();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String formatSysTime(SYSTEMTIME t) {
        if (t == null || t.Year == 0) {
            return "";
        }
        return String.format("%04d-%02d-%02d %02d:%02d:%02d.%03d",
                t.Year, t.Month, t.Day, t.Hour, t.Minute, t.Second, t.Milliseconds);
    }
}
