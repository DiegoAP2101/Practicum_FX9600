# FX9600 Reading Demo

Base cliente-servidor para leer tags RFID desde una **Zebra FX9600** y verlos en una
tabla web local en tiempo real. Pensada como fundación para futuras aplicaciones:
una PC local se conecta a la lectora, concentra los datos y los expone por HTTP/JSON.

```
┌──────────────┐   TCP/IP (red local)    ┌─────────────────────────────┐   HTTP/JSON     ┌──────────────┐
│   FX9600     │ ◄─────────────────────► │  Servidor Java (esta PC)    │ ◄─────────────► │  Navegador   │
│  (lectora)   │   Zebra RFID API3 SDK   │  ReaderService + TagStore   │  localhost:8090 │  (tabla web) │
└──────────────┘        puerto 5084      │  + WebServer                │                 └──────────────┘
                                         └─────────────────────────────┘
```

## Requisitos

- Windows con **JDK** (probado con Temurin 21).
- **Zebra FXSeries Host Java SDK** instalado (MSI `Zebra-RFID-FXSeries-Host-Java-SDK_V1.8.msi`).
  Deja el JAR en `C:\Program Files\Zebra FXSeries Host Java SDK\RFID\bin` y la DLL
  `RFIDAPI3_JNI_HOST.dll` en `C:\Windows\System32`. Si lo instalaste en otra ruta,
  actualiza la variable `SDK_BIN` en `build.bat` y `run.bat`.
- La lectora FX9600 conectada a la misma red que la PC.

## Uso

| Acción | Comando |
|---|---|
| Compilar | `build.bat` (automático la primera vez) |
| Correr con la lectora real | `run.bat` |
| Correr sin lectora (simulador) | `run-sim.bat` |

Luego abre **http://localhost:8090** y presiona **▶ Iniciar**.

Al arrancar, la app intenta conectarse sola a la lectora y, si la conexión se cae o
no está disponible, reintenta cada N segundos (configurable). Si la lectora se
reconecta después de una caída, presiona **▶ Iniciar** de nuevo para reanudar el inventario.

### Grabación a CSV

Cada **▶ Iniciar** abre un archivo nuevo `capturas/lecturas_AAAAMMDD_HHMMSS.csv` y
**■ Detener** lo cierra. Se guarda **una fila por lectura cruda** (no el resumen por EPC):
`pc_time, epc, state, antenna, reads, rssi_dbm, phase_deg, channel, pc, crc, xpc_w1, xpc_w2,
reader_first_seen_utc, reader_last_seen_utc`. "Limpiar" no afecta al CSV. Ábrelo en Excel
después de Detener.

## Configuración (`config.properties`)

Todo lo que cambia entre redes vive en `config.properties` (editar y reiniciar):

- `reader.ip` — IP de la lectora (default actual: `192.168.10.20`)
- `reader.port` — puerto LLRP de la lectora (5084 de fábrica)
- `reader.timeout.ms` — timeout de conexión
- `reader.reconnect.seconds` — reintento automático de conexión (0 = desactivado)
- `web.host` — `127.0.0.1` solo esta PC, `0.0.0.0` visible en la red local
- `web.port` — puerto de la página web (8090; 8080 suele estar ocupado)
- `sim.*` — parámetros del simulador

> Para encontrar la IP de la lectora: revisa el DHCP de tu router, o entra a la
> consola web de la lectora (`http://<ip>`, usuario `admin`, password `change`).

## Datos por tag

La app pide a la lectora **todos los campos disponibles** (`TAG_FIELD.ALL_TAG_FIELDS`):

| Campo | Descripción |
|---|---|
| EPC | Identificador del tag |
| Estado | New / Gone / Back / Moving / Stationary (eventos de visibilidad) |
| Antena | Antena (1-8) que hizo la lectura |
| Lecturas | Conteo acumulado de lecturas del tag |
| RSSI | Potencia pico de la señal en dBm |
| Fase | Fase de la señal en grados |
| Canal | Índice del canal de RF usado |
| PC / CRC / XPC | Palabras de protocolo del tag (hex) |
| Primera/Última vista | Timestamps del reloj de la PC |
| Vistas (lector) | Timestamps UTC reportados por el reloj de la lectora (vacíos si su reloj no está configurado) |

## API para futuras aplicaciones

Cualquier aplicativo (otro proceso, script, dashboard) puede consumir la misma API:

| Endpoint | Método | Descripción |
|---|---|---|
| `/api/tags` | GET | Snapshot: estado de conexión + todos los tags agregados por EPC |
| `/api/start` | POST | Iniciar inventario |
| `/api/stop` | POST | Detener inventario |
| `/api/clear` | POST | Limpiar tabla y contadores |

Ejemplo de respuesta de `GET /api/tags`:

```json
{
  "status": { "connected": true, "reading": true, "source": "FX9600 @ 192.168.1.65:5084",
              "error": "", "uniqueTags": 13, "totalReads": 918, "elapsedMs": 7408,
              "serverTimeMs": 1785875810237 },
  "tags": [ { "epc": "E280116060000200...", "state": "New", "antennaId": 2,
              "seenCount": 403, "peakRssi": -65, "phaseDeg": 12.5, "channelIndex": 27,
              "pc": "3000", "crc": "1A2B", "xpcW1": "0", "xpcW2": "0",
              "firstSeenMs": 1785875810237, "lastSeenMs": 1785875814461,
              "readerFirstSeenUtc": "2026-08-04 14:02:38.307",
              "readerLastSeenUtc": "2026-08-04 14:02:38.576" } ]
}
```

## Estructura del código

```
src/fx9600demo/
├── Main.java         Punto de entrada; auto-conexión con reintentos
├── AppConfig.java    Carga y valida config.properties
├── TagSource.java    Interfaz de fuente de lecturas (desacopla el SDK del resto)
├── ZebraSource.java  Conexión real vía Zebra RFID API3 (patrón del sample oficial)
├── SimSource.java    Simulador para desarrollar sin lectora
├── TagStore.java     Agregación thread-safe por EPC (records inmutables)
├── TagRecord.java    Snapshot inmutable de un tag
├── CsvLog.java       Grabación de lecturas crudas a CSV (Iniciar → Detener)
├── Json.java         Serialización JSON mínima (sin dependencias)
└── WebServer.java    HTTP embebido del JDK + API JSON
web/index.html        Interfaz (HTML/JS vanilla, sin recursos externos)
```

Para una futura aplicación hay dos caminos:
1. **Consumir la API HTTP** de este proceso tal cual (lo más simple).
2. **Reutilizar las clases** `ZebraSource` + `TagStore` como librería y construir
   otra lógica encima (la interfaz `TagSource` permite probar todo con `SimSource`).

## Notas técnicas

- El SDK Java usa JNI: `Symbol.RFID.API3.jar` carga `RFIDAPI3_JNI_HOST.dll` (x64,
  en System32) que a su vez usa `RFIDAPI32PC.dll` (carpeta bin del SDK). `run.bat`
  ya configura `java.library.path` y `PATH` con ambas rutas.
- Con ~130 lecturas/seg la lectora genera mucho tráfico; el servidor agrega por EPC
  y el navegador consulta snapshots cada 250 ms (no se transmite lectura por lectura).
- La fase se convierte a grados con `(180 / 32767) × valor` (fórmula del sample de Zebra).
