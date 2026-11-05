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

    /** Tabla de potencias de transmision soportadas, en centesimas de dBm (3000 = 30.00 dBm). */
    int[] powerLevels() throws Exception;

    /** Potencia actual por antena en centesimas de dBm; indice = numero de antena (0 sin usar). */
    int[] antennaPowers() throws Exception;

    /**
     * Ajusta la potencia de una antena al nivel soportado mas cercano a {@code centiDbm}.
     * Si esta leyendo, detiene y reanuda el inventario. Devuelve la potencia aplicada.
     */
    int setAntennaPower(int antenna, int centiDbm) throws Exception;

    /** Indice del nivel de la tabla mas cercano al valor pedido. */
    static int nearestIndex(int[] levels, int centiDbm) {
        int best = 0;
        for (int i = 1; i < levels.length; i++) {
            if (Math.abs(levels[i] - centiDbm) < Math.abs(levels[best] - centiDbm)) {
                best = i;
            }
        }
        return best;
    }
}
