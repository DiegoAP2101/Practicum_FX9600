# CLAUDE.md

Guía para Claude Code (y humanos) que sigan trabajando este proyecto. El README.md
tiene la descripción completa para usuarios; aquí va lo necesario para modificar el código.

## Qué es

Servidor Java local que lee tags RFID de una **Zebra FX9600** y los muestra en una tabla
web en `http://localhost:8090`. Cada sesión Iniciar→Detener se graba a CSV en `capturas/`.

Capas de comunicación:
1. Tag ↔ lectora: EPC Gen2 (UHF, 902–928 MHz).
2. Lectora ↔ PC: LLRP sobre TCP:5084, vía **Zebra RFID API3 Java SDK** (JNI). No es WebSocket.
3. PC ↔ navegador: HTTP/JSON con polling cada 250 ms (`GET /api/tags`, `POST /api/start|stop|clear`).

## Comandos (Windows, desde la raíz del proyecto)

| Acción | Comando |
|---|---|
| Compilar | `.\build.bat` (javac → `out/`) |
| Correr con lectora | `.\run.bat` |
| Correr sin lectora | `.\run-sim.bat` (usa `SimSource`) |

No hay Maven/Gradle ni dependencias externas fuera del SDK de Zebra. `run.bat` solo compila
si falta `out/`; **después de editar `.java` corre `build.bat`**.

Requisitos: JDK 21 y el MSI *Zebra FXSeries Host Java SDK V1.8* (JAR en
`C:\Program Files\Zebra FXSeries Host Java SDK\RFID\bin`, `RFIDAPI3_JNI_HOST.dll` en System32).
Sin el SDK, `build.bat` falla porque `ZebraSource.java` importa `com.mot.rfid.api3.*`.

## Cómo verificar un cambio

No hay tests automatizados. Prueba con el simulador:

```powershell
.\build.bat
.\run-sim.bat            # en otra terminal:
Invoke-RestMethod -Method Post http://localhost:8090/api/start
Invoke-RestMethod http://localhost:8090/api/tags
Invoke-RestMethod -Method Post http://localhost:8090/api/stop
# revisar el CSV nuevo en capturas/
```

El simulador genera ~120 lecturas/s (configurable en `config.properties`, `sim.*`).

## Arquitectura (`src/fx9600demo/`)

- `Main` — arranque, hilo `connection-keeper` que reconecta cada `reader.reconnect.seconds`.
- `TagSource` — interfaz; implementaciones `ZebraSource` (real) y `SimSource`. La web solo usa la interfaz.
- `TagStore` — agrega lecturas por EPC (`ConcurrentHashMap`) y reenvía cada lectura cruda a `CsvLog`.
- `TagRecord` — snapshot **inmutable** por EPC; `withRead()` devuelve copia nueva.
- `CsvLog` — un archivo por sesión; `start()` en `/api/start`, `stop()` en `/api/stop` y en shutdown.
- `WebServer` — `com.sun.net.httpserver` del JDK; sirve `web/index.html` y la API; lleva el cronómetro.
- `Json` — serializador JSON mínimo hecho a mano.
- `web/index.html` — HTML/CSS/JS vanilla, sin CDNs ni frameworks.

## Convenciones

- Textos de UI, logs y mensajes de error en **español**; identificadores en inglés.
- Sin dependencias nuevas: JDK + SDK de Zebra únicamente.
- Datos inmutables (`TagRecord`, `TagStore.Read`); el estado mutable está encapsulado y sincronizado.
- Errores del SDK se traducen a mensajes legibles con `friendly(...)` y se exponen en `lastError()`.
- Toda la configuración por red va en `config.properties` (se lee solo al arrancar).

## Detalles del SDK que no son obvios

- La desconexión **no** puede llamarse dentro del hilo de eventos del SDK; `ZebraSource` lanza un hilo aparte (patrón del sample oficial `J_RFIDHostSample1`).
- Las lecturas se sacan con `reader.Actions.getReadTags(200)` dentro de `eventReadNotify`.
- Fase a grados: `(180 / 32767) * tag.getPhase()`.
- Se pide `TAG_FIELD.ALL_TAG_FIELDS`; los timestamps del lector vienen vacíos si el reloj de la FX9600 no está configurado.
- RSSI en dBm: más negativo = señal más débil (~−30 fuerte, ~−80 límite).
- Si la lectora se cae y vuelve, hay que presionar Iniciar de nuevo; el CSV abierto continúa en el mismo archivo.

## Consola de la lectora

`http://<reader.ip>` — usuario `admin`, password de fábrica `change`.
