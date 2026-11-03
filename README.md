# chess-software

Proyecto de Ingeniería de Software II: sistema de salas de ajedrez.

## Material de partida

Se amplían el modelo del tablero y los puertos trabajados en clase. Cada sesión integra una parte del sistema y
comprueba su comportamiento mediante pruebas.

## Estado actual

El proyecto incluye el modelo inmutable del tablero, la lectura y escritura de posiciones FEN y un servicio de reglas
para movimientos ordinarios. También incorpora el contrato compartido de datos y los casos de uso del backend
para crear salas, unir jugadores, consultar partidas, mover y abandonar. FileRoomRepository añade el almacenamiento
de salas y movimientos en archivos y su recuperación al construir un nuevo servicio. HttpChessServer expone los
casos de uso mediante HTTP y BackendMain permite arrancar el backend de forma independiente.

- Board, Square, Piece, Color, File y Rank representan el tablero.
- Position combina un tablero con su estado.
- State contiene turno, derechos de enroque, casilla objetivo al paso y contadores.
- Los puertos definen las operaciones de lectura y escritura.
- Los adaptadores FEN implementan las conversiones.
- MoveRules valida movimientos y devuelve una nueva Position.
- El paquete contract define los datos de intercambio y su conversión a texto.
- ChessService implementa el puerto ChessUseCases y coordina salas y partidas.
- RoomRepository define el almacenamiento que necesita el servicio.
- FileRoomRepository implementa ese puerto mediante archivos de sala con formato versionado.
- BackendFactory compone el servicio con el repositorio recibido y los adaptadores FEN.
- HttpChessServer transforma peticiones HTTP en llamadas al puerto ChessUseCases.
- BackendMain configura y conecta el repositorio, el servicio y el servidor.

FenPositionDeserializer compone los deserializadores de tablero y estado mediante interfaces.

## Serialización FEN

Se han incorporado estos adaptadores:

- FenBoardDeserializer.
- FenBoardSerializer.
- FenStateDeserializer.
- FenPositionDeserializer.
- FenPositionSerializer.

FenBoardDeserializer admite el campo tablero o un FEN completo, ignorando los metadatos. FenPositionDeserializer
requiere los seis campos. FenStateDeserializer recibe los cinco campos posteriores al tablero.

La conversión comprueba la estructura del tablero, los símbolos de piezas y los valores de los campos de estado.
No comprueba toda la legalidad de una posición; la validación de movimientos pertenece a MoveRules.

## Reglas de movimiento

Board.move realiza un cambio estructural del tablero. MoveRules separa las reglas de juego de esa representación.

MoveRules comprueba el turno, la existencia de la pieza de origen, la geometría de cada pieza, los obstáculos y las
capturas. Rechaza capturas de piezas propias o del rey y movimientos que dejen al rey del jugador en jaque.
Permite la promoción a dama, torre, alfil o caballo del mismo color; la opción predeterminada es dama.

Cada movimiento aceptado produce otra Position y actualiza el turno, los contadores FEN, los derechos de enroque
afectados y la casilla objetivo al paso.

Alcance actual: movimientos ordinarios, detección de jaque y promoción. El enroque y la captura al paso se rechazan,
aunque sus campos de estado se mantienen. No hay adjudicación automática por mate o tablas ni validación completa
de la legalidad de posiciones FEN arbitrarias.

## Contrato compartido

El paquete `software.ulpgc.chess.contract` contiene datos de intercambio independientes del modelo de dominio,
la interfaz gráfica y los adaptadores de red.

| Tipo | Contenido |
|---|---|
| MoveView | number, from, to, promotion, player, fen y at: información de un movimiento. |
| RoomView | id, white, black, status, fen, version, createdAt y moves: información pública de una sala. |
| SessionView | room, token y color: respuesta destinada al jugador al crear o unirse a una sala. |
| WireCodec | Conversión entre estos datos y texto con formato Properties. |

Los tres tipos de datos son records. RoomView realiza una copia defensiva de la lista de movimientos.
RoomView no contiene credenciales; el token pertenece a SessionView y no debe incluirse en las consultas públicas.
La creación de sesiones y su autorización se realizan en los casos de uso del backend. Los tokens no deben
publicarse en las consultas de salas ni en registros de diagnóstico.

### Formato de los mensajes

- Una sala usa las claves `id`, `white`, `black`, `status`, `fen`, `version`, `createdAt` y `moves.count`.
- Cada movimiento usa el prefijo `move.N.`, empezando por cero, seguido de `number`, `from`, `to`, `promotion`,
  `player`, `fen` y `at`.
- Una sesión añade `token` y `color` a los campos de la sala.
- Un listado comienza con `count`. Cada sala utiliza el prefijo `room.N.` sobre las claves anteriores.
- `promotion` admite una cadena vacía cuando no hay promoción; el formato conserva cadenas vacías como valores.

WireCodec ofrece encodeRoom/decodeRoom, encodeSession/decodeSession, encodeRooms/decodeRooms y las operaciones
encodeFields/decodeFields para mapas de cadenas. Properties se ocupa del escape de caracteres especiales.
Los campos requeridos deben estar presentes; se rechazan cantidades no numéricas, negativas o superiores a 100000.
La versión se lee como long. El codec no valida reglas de ajedrez, legalidad del FEN ni toda la semántica de fechas,
colores y estados; esas comprobaciones pertenecen a las capas correspondientes.

WireCodec trabaja con String, con pruebas de nombres Unicode y caracteres de escape. FileRoomRepository convierte
el contenido entre texto y bytes mediante UTF-8; HttpChessServer aplica esa misma codificación a los mensajes HTTP.
Properties.store añade un comentario con fecha; la equivalencia se comprueba recuperando los datos, sin exigir
que el texto generado sea idéntico.

El formato Properties es una decisión de este proyecto. Se conserva FEN para representar las posiciones.
Los casos de uso, el adaptador de archivos y el servidor HTTP utilizan este contrato. El frontend todavía no está
integrado; las operaciones HTTP pueden comprobarse sin una interfaz gráfica.

## Casos de uso del backend

El paquete `backend` incorpora la coordinación de salas y partidas. El servicio depende de interfaces para
consultar y guardar las salas y para convertir las posiciones FEN; las reglas del ajedrez siguen en MoveRules.
FileRoomRepository incorpora el almacenamiento en disco y HttpChessServer el acceso mediante HTTP.
El cliente de comunicación del frontend se incorporará en una etapa posterior.

### Responsabilidades y composición

| Tipo | Responsabilidad |
|---|---|
| ChessUseCases | Puerto de entrada: listar, consultar, crear sala, unirse, mover y abandonar. |
| ChessService | Implementa ChessUseCases y coordina las operaciones, su autorización y sus cambios de estado. |
| RoomRepository | Puerto de salida con findAll y save para recuperar y guardar StoredRoom. |
| StoredRoom | Record interno con la vista de sala y los hashes de las credenciales de ambos jugadores. |
| ChessException | Distingue datos inválidos, falta de autorización, sala inexistente y conflictos de partida. |
| RepositoryException | Comunica un fallo del repositorio al caso de uso. |
| BackendFactory | Compone ChessService con un RoomRepository recibido y los adaptadores FEN concretos. |

La sesión 05 incorporó estos siete archivos de producción y ChessServiceTest. Los puertos expresan las operaciones
necesarias; BackendFactory conecta las implementaciones por composición. ChessService recibe RoomRepository,
PositionSerializer y PositionDeserializer, y utiliza MoveRules para validar los movimientos. No conoce rutas de
archivos, peticiones HTTP ni componentes gráficos.

MemoryRepository es únicamente un doble definido dentro de ChessServiceTest. Conserva datos en memoria y permite
simular un fallo al guardar. Sirve para probar el servicio sin disco ni red; no constituye almacenamiento persistente
ni un adaptador de producción. FileRoomRepository aporta ahora la implementación de almacenamiento en disco.
BackendMain y HttpChessServer permiten ejecutar y consultar el backend; la interfaz gráfica sigue pendiente.

### Salas, autorización y versiones

Al crear una sala, el jugador ocupa blancas y recibe una SessionView con su token. La sala comienza en WAITING,
con la posición inicial y versión cero. Un segundo jugador con otro nombre puede ocupar negras; la sala pasa a
ACTIVE. Una sala completa no admite otro jugador.

El servicio genera credenciales aleatorias. StoredRoom conserva sus hashes SHA-256; las consultas get y list
exponen únicamente RoomView, sin tokens ni hashes. La SessionView destinada al jugador contiene el token que
necesitará para mover o abandonar. No se han implementado cuentas de usuario ni contraseñas.

Mover y abandonar requieren una credencial válida y la versión vigente de la sala. Una versión desfasada se rechaza
para evitar aplicar una orden a un estado que ya ha cambiado. Cada cambio aceptado después de la creación incrementa
la versión. Para mover, la partida debe estar ACTIVE, debe corresponder el turno al jugador y MoveRules debe aceptar
la jugada. Cada movimiento añade su posición FEN, jugador y fecha al histórico.

Los métodos del servicio sincronizan las operaciones dentro de una misma instancia. Primero se llama a
RoomRepository.save y sólo después se publica la nueva sala en su estado en memoria. Si guardar falla, no se publica
el cambio. Esta coordinación no implica bloqueo entre varios procesos ni una transacción de base de datos.

La evolución implementada es WAITING -> ACTIVE -> WHITE_WON o BLACK_WON. El estado final se obtiene por abandono:
si abandonan blancas, ganan negras; si abandonan negras, ganan blancas. El abandono requiere una partida activa y una
versión vigente, pero puede solicitarse fuera del turno propio. El histórico se conserva. No hay adjudicación
automática por mate, ahogado o tablas.

## Persistencia de salas en archivos

La sesión 06 incorpora FileRoomRepository y FileRoomRepositoryTest. FileRoomRepository es un adaptador de salida
que implementa RoomRepository: el servicio conserva su dependencia de la interfaz y puede recibir este repositorio
mediante BackendFactory. El almacenamiento se realiza en archivos, sin una base de datos SQL.

El constructor recibe un Path, lo convierte en una ruta absoluta normalizada y crea el directorio si es necesario.
La ubicación se proporciona por composición; BackendMain la configura mediante la opción --data.
Las pruebas usan directorios temporales de JUnit mediante @TempDir.

### Formato y escritura

Cada sala ocupa un archivo con el nombre de su identificador UUID seguido de `.room`. El contenido es texto
Properties codificado en UTF-8. El formato incluye `schema=1`, la RoomView serializada en el campo `room` y los
campos internos `whiteTokenHash` y `blackTokenHash`. Se guardan los hashes, no los tokens entregados a los jugadores.
El archivo conserva los jugadores, estado, versión, fecha de creación, posición FEN e histórico de movimientos.

save valida la sala y construye el contenido completo. Después crea un archivo temporal en el mismo directorio,
escribe todos los bytes mediante FileChannel y llama a force(true). Finalmente sustituye el archivo de la sala con
ATOMIC_MOVE y REPLACE_EXISTING. Si el sistema de archivos no permite la sustitución atómica, se comunica el error
mediante RepositoryException; no se utiliza una copia no atómica como alternativa.

Esta estrategia fuerza el contenido del temporal y realiza la sustitución atómica del archivo. No incluye forzar
el directorio padre ni acredita la recuperación ante cualquier fallo de hardware o corte de energía. El servicio
publica el nuevo estado en memoria únicamente después de que save haya terminado correctamente.

### Recuperación y comprobaciones

findAll recorre los archivos terminados en `.room`, comprueba su tamaño y exige la versión de formato 1. Los
archivos temporales `.tmp` no se interpretan como partidas. Cada archivo de sala admite como máximo 16 MiB,
tanto al escribirlo como al recuperarlo.

La recuperación valida el UUID y su coincidencia con el nombre del archivo, los nombres de jugadores, los estados
admitidos, las fechas como instantes, los hashes y la relación entre versión, estado y número de movimientos.
También comprueba que una sala WAITING no tenga segundo jugador ni movimientos.

Para comprobar el histórico, parte de Position.initial y reproduce sus movimientos con MoveRules. Revisa el orden,
las casillas, la promoción, el jugador y la fecha de cada movimiento. La posición FEN calculada debe coincidir con
la guardada en cada paso y con la posición final de la sala. Así se detectan históricos o posiciones incoherentes
con las reglas implementadas.

Si un archivo está corrupto, tiene un formato desconocido o contiene datos incoherentes, la carga se detiene con
RepositoryException. El archivo se conserva para revisarlo; no se descarta la partida silenciosamente ni se borra
el archivo dañado. Un temporal que haya quedado de una escritura interrumpida no sustituye al archivo confirmado.

### Alcance de la coordinación

Se debe utilizar un único servicio activo, en un único proceso, por directorio de partidas. La sincronización de
los métodos pertenece a cada instancia; no hay un bloqueo compartido entre instancias o procesos que permita
utilizar el mismo directorio simultáneamente. Las salas se recuperan al crear el servicio; los cambios externos
en los archivos no se recargan automáticamente mientras está funcionando.

La persistencia se integra con BackendMain y HttpChessServer. Los clientes del frontend y la interfaz gráfica
siguen pendientes.

## API HTTP y arranque del backend

La sesión 07 incorpora HttpChessServer, BackendMain y HttpChessServerTest, además del manifiesto ejecutable del
JAR y el script opcional scripts/backend.ps1. HttpChessServer es el adaptador de entrada: transforma las peticiones
HTTP en operaciones del puerto ChessUseCases y convierte sus resultados y errores en respuestas. La autorización,
los turnos y los cambios de partida permanecen en ChessService.

BackendMain compone FileRoomRepository, el servicio obtenido mediante BackendFactory y HttpChessServer. El backend
puede ejecutarse como proceso independiente; la interfaz de usuario y el cliente HTTP del frontend siguen pendientes.

### Rutas y mensajes

La dirección predeterminada es `http://127.0.0.1:8080`. Los cuerpos se representan como texto Properties en UTF-8;
las respuestas usan `Content-Type: text/plain; charset=UTF-8`. Los clientes deben enviar el texto con esa misma
codificación. No se utiliza JSON ni un formulario URL-encoded.

| Método y ruta | Campos del cuerpo | Respuesta correcta |
|---|---|---|
| GET /health | Sin cuerpo. | 200, con `status=ok`. |
| GET /rooms | Sin cuerpo. | 200, listado de RoomView. |
| POST /rooms | `player`. | 201, SessionView del jugador de blancas y cabecera Location de la sala. |
| GET /rooms/{id} | Sin cuerpo. | 200, RoomView con posición e histórico. |
| POST /rooms/{id}/join | `player`. | 200, SessionView del jugador de negras. |
| POST /rooms/{id}/moves | `from`, `to`, `version` y `promotion` opcional. | 200, RoomView actualizada. |
| POST /rooms/{id}/resign | `version`. | 200, RoomView con resultado por abandono. |

Las operaciones moves y resign requieren una única cabecera `X-Player-Token` con la credencial recibida al crear
la sala o unirse. La versión se envía en el cuerpo como entero no negativo y debe coincidir con la versión actual.
Las consultas de sala y listado son públicas para quien pueda conectarse al servidor, y sus respuestas no incluyen
tokens ni hashes. Crear o unirse devuelve la SessionView destinada a ese jugador.

Las casillas se escriben como `e2` y `e4`. promotion puede ser `q`, `r`, `b` o `n`; también puede omitirse o
estar vacía. Si el movimiento alcanza la última fila y no se elige una pieza, se promociona a dama. El servicio
sigue rechazando el enroque, la captura al paso y las demás jugadas fuera del alcance implementado.

Cada cuerpo POST admite como máximo 8192 bytes. Se rechazan con 400 los cuerpos mayores, el texto UTF-8 mal
formado, los campos desconocidos para esa operación y los datos inválidos. Los errores también se expresan como
Properties y contienen una clave `error` con un mensaje.

| Código HTTP | Significado en esta API |
|---|---|
| 200 | Consulta u operación realizada correctamente. |
| 201 | Sala creada. |
| 400 | Datos incompletos o inválidos, movimiento inválido o cuerpo no admitido. |
| 403 | Credencial de jugador ausente o incorrecta. |
| 404 | Ruta o sala inexistente. |
| 405 | Método no admitido; la cabecera Allow indica los métodos de esa ruta. |
| 409 | Versión desfasada, turno incorrecto, sala completa o partida fuera del estado requerido. |
| 500 | Fallo de almacenamiento o error interno. |

### Compilar y ejecutar

El proyecto mantiene JDK 27 y `maven.compiler.release=27`. En el POM se añade al plugin de JAR el manifiesto con
`software.ulpgc.chess.backend.BackendMain` como Main-Class, conservando las versiones y la configuración del
proyecto. El POM antiguo del kit con release 17 no corresponde a esta configuración.

Desde la raíz del proyecto, primero se compila y después se ejecuta el JAR:

```text
mvn clean verify
java -version
java -jar target/chess-software-1.0.0.jar --help
java -jar target/chess-software-1.0.0.jar --host 127.0.0.1 --port 8080 --data data
```

El comando java debe usar el JDK 27. Elegir ese SDK en IntelliJ no modifica por sí solo el PATH de una terminal.
Si java no se reconoce o apunta a otra versión, puede utilizarse la ruta completa a bin/java.exe del SDK configurado
en IntelliJ; en PowerShell esa ruta entre comillas se ejecuta con el operador `&`.

`--help`, utilizado solo, muestra el uso sin arrancar el servidor. El arranque admite estas opciones:

| Opción | Valor predeterminado | Función |
|---|---|---|
| --host | 127.0.0.1 | Interfaz en la que escucha el backend; por defecto sólo el equipo local. |
| --port | 8080 | Puerto entre 1 y 65535. |
| --data | data | Directorio de partidas, relativo al directorio de ejecución si no es una ruta absoluta. |

Al arrancar se recuperan las partidas del directorio indicado y se muestra la dirección del servidor y la ruta de
datos utilizada. El proceso permanece atendiendo peticiones hasta detenerlo, por ejemplo con Ctrl+C. Para recuperar
las mismas partidas en otro arranque debe utilizarse el mismo directorio. Se mantiene el límite de un único servicio
activo y proceso por directorio; el adaptador no coordina varios escritores.

El script opcional ofrece los mismos valores desde PowerShell:

```powershell
.\scripts\backend.ps1 -Port 8080 -Data data -BindAddress 127.0.0.1
```

El script cambia el directorio de trabajo a la raíz del proyecto y utiliza java desde el PATH. Si los scripts están bloqueados, se puede
usar directamente el comando del JAR; no es necesario cambiar la política de ejecución de PowerShell.

## Hito de dominio y contrato

El hito `milestone-01-domain-contract` agrupa el modelo, FEN, las reglas de movimiento y el contrato compartido.
Es una etapa intermedia del proyecto, sin punto de entrada de la aplicación completa. La versión 1.0.0 del POM
se mantiene como identificador del artefacto durante el desarrollo; la entrega final tendrá su propia etiqueta.

main conserva este primer hito, comprobado con 52 pruebas. develop incorpora las sesiones 05, 06 y 07 sobre esa base.
Los casos de uso del backend, la persistencia y la API HTTP no forman parte de la etiqueta milestone-01-domain-contract.

## Compilación y pruebas

Se necesita JDK 27 y Maven.

Comando: `mvn clean verify`.

Verificaciones locales:

- Sesión 01, 18 de octubre de 2026: 11 pruebas del modelo superadas.
- Sesión 02, 20 de octubre de 2026: 32 pruebas superadas en total.
- Sesión 03, 22 de octubre de 2026: 50 pruebas superadas en total.
- Sesión 04, 28 de octubre de 2026: 52 pruebas superadas en total, sin fallos, errores ni pruebas omitidas.
- Sesión 05, 31 de octubre de 2026: 57 pruebas superadas en total, sin fallos, errores ni pruebas omitidas.
- Sesión 06, 2 de noviembre de 2026: 62 pruebas superadas en total, sin fallos, errores ni pruebas omitidas.
- Sesión 07, 3 de noviembre de 2026: 65 pruebas superadas en total, sin fallos, errores ni pruebas omitidas.

Distribución actual: ModelTest, 11; FenTest, 21; MoveRulesTest, 18; WireCodecTest, 2; ChessServiceTest, 5;
FileRoomRepositoryTest, 5; HttpChessServerTest, 3.

Las pruebas de FEN comprueban posiciones iniciales y personalizadas, tablero vacío, campos de estado, conversión de ida
y vuelta y rechazo de formatos incorrectos. Las pruebas de movimiento comprueban aperturas, capturas, obstáculos,
protección del rey, promoción, actualización del estado y rechazo de los movimientos especiales no disponibles.
Las pruebas del contrato comprueban conversiones de sala, sesión y listado, Unicode, escapes y rechazo de campos
ausentes o cantidades fuera del límite.

Los cinco casos de ChessServiceTest comprueban el ciclo de crear, unirse, mover y abandonar; el rechazo de
espectadores, turnos incorrectos, versiones desfasadas y movimientos inválidos; los fallos de almacenamiento sin
publicar cambios; la competencia de dos jugadores por el puesto de negras; y nombres inválidos, salas inexistentes
o puestos ya ocupados. Se ejecutan con MemoryRepository, sin almacenamiento en disco, HTTP ni interfaz gráfica.

Los cinco casos de FileRoomRepositoryTest usan almacenamiento real en @TempDir. Comprueban la recuperación de
una partida al construir nuevas instancias de repositorio y servicio sobre el mismo directorio temporal, y permiten
continuar con negras utilizando el token obtenido antes de esa recuperación. También comprueban que no se guardan
tokens en claro, que un archivo corrupto detiene la carga y se conserva, que se detecta un nombre de archivo
manipulado, que un temporal sobrante no reemplaza una partida y que una ruta ocupada por un archivo se rechaza
como directorio. El reinicio se simula mediante nuevas instancias; estas pruebas no arrancan procesos, HTTP ni UI.

Los tres casos de HttpChessServerTest realizan peticiones HTTP reales sobre loopback. El primero usa un mismo
HttpClient para representar las identidades de Ana y Luis mediante tokens distintos y realizar consultas públicas
como observador. Comprueba creación, unión, movimientos, autorización, versiones, consulta y listado; después cierra
y recrea HttpChessServer y el servicio dentro de la misma JVM, conserva el directorio temporal y continúa la partida.
El segundo caso rechaza rutas, métodos, cuerpos y datos inválidos sin modificar las salas. El tercero simula un fallo
de almacenamiento mediante MemoryRepository y comprueba la respuesta 500 sin publicar la sala.

Estas pruebas no abren ventanas ni ejecutan el JAR en procesos separados. Los comandos de arranque anteriores
describen cómo utilizar la aplicación; no constituyen un registro de una prueba manual del proceso.

## Organización de ramas

- main: hitos estables.
- develop: integración del trabajo comprobado.
- feature/01-base-model: integración del modelo.
- feature/02-fen-serialization: integración de la serialización FEN.
- feature/03-move-validation: reglas de movimiento y protección del rey.
- feature/04-shared-contract: datos compartidos y formato de intercambio.
- feature/05-backend-use-cases: casos de uso, autorización, versiones y pruebas del backend.
- feature/06-file-persistence: almacenamiento en archivos, recuperación y pruebas del repositorio.
- feature/07-http-api: API HTTP, arranque del backend y empaquetado ejecutable.

## Próximas etapas

Casos de uso del frontend, adaptador de cliente HTTP, interfaz gráfica e integración de ambas aplicaciones.
