package fx9600demo;

/**
 * Fuente de lecturas de tags: la lectora real (ZebraSource) o el simulador (SimSource).
 * La capa web solo habla con esta interfaz, asi el resto de la app no depende del SDK.
 */
public interface TagSource {

    /** Conecta con la fuente. Lanza excepcion con mensaje claro si falla. */
    void connect() throws Exception;

    void disconnect();

    /** Inicia el inventario (empezar a leer tags). */
    void startReading() throws Exception;

    /** Detiene el inventario. */
    void stopReading() throws Exception;

    boolean isConnected();

    boolean isReading();

    /** Descripcion corta para la UI, p. ej. "FX9600 @ 192.168.1.65:5084" o "Simulador". */
    String description();

    /** Ultimo error de conexion/operacion, o "" si no hay. */
    String lastError();
}
