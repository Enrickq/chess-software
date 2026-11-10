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
casos de uso mediante HTTP y BackendMain permite arrancar el backend de forma independiente. El frontend incorpora
su controlador de aplicación para gestionar salas, sesiones locales y la reproducción del histórico.
HttpChessService implementa su puerto de salida y permite comunicarse con el backend mediante HTTP. FrontendMain
compone el cliente de escritorio con ChessPanel y BoardPanel para jugar, observar y recorrer el histórico.

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
- ChessController coordina las acciones del frontend mediante su propio puerto ChessService.
- HttpChessService implementa ese puerto mediante HttpClient y el contrato Properties UTF-8.
- BoardPanel dibuja el tablero y comunica las casillas seleccionadas.
- ChessPanel presenta las acciones y utiliza SwingWorker para las llamadas al servicio.
- FrontendMain compone y abre la aplicación Swing.

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
Los casos de uso del backend, el adaptador de archivos, el servidor HTTP y el controlador del frontend utilizan
este contrato. HttpChessService permite conectar el controlador del frontend con el servidor mediante HTTP.
Las operaciones pueden comprobarse sin una interfaz gráfica.

## Casos de uso del backend

El paquete `backend` incorpora la coordinación de salas y partidas. El servicio depende de interfaces para
consultar y guardar las salas y para convertir las posiciones FEN; las reglas del ajedrez siguen en MoveRules.
FileRoomRepository incorpora el almacenamiento en disco y HttpChessServer el acceso mediante HTTP.
HttpChessService implementa el cliente de comunicación del frontend mediante su puerto de servicio.

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
BackendMain y HttpChessServer permiten ejecutar y consultar el backend. FrontendMain compone la interfaz gráfica
con el controlador y el adaptador HTTP.

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

La persistencia se integra con BackendMain y HttpChessServer. El cliente Swing accede mediante HttpChessService;
no consulta ni escribe directamente los archivos de partidas.

## API HTTP y arranque del backend

La sesión 07 incorpora HttpChessServer, BackendMain y HttpChessServerTest, además del manifiesto ejecutable del
JAR y el script opcional scripts/backend.ps1. HttpChessServer es el adaptador de entrada: transforma las peticiones
HTTP en operaciones del puerto ChessUseCases y convierte sus resultados y errores en respuestas. La autorización,
los turnos y los cambios de partida permanecen en ChessService.

BackendMain compone FileRoomRepository, el servicio obtenido mediante BackendFactory y HttpChessServer. El backend
puede ejecutarse como proceso independiente y recibir peticiones de HttpChessService desde el cliente Swing.

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

## Casos de uso del frontend

La sesión 08 incorpora el puerto `frontend.ports.ChessService`, ServiceException, ChessController y
ChessControllerTest. La interfaz del frontend expresa las operaciones que necesita el controlador; es distinta
de la clase de aplicación `backend.application.ChessService`. Ambas partes reutilizan los datos de intercambio
RoomView y SessionView, sin que el controlador dependa de una clase del backend.

| Tipo | Responsabilidad |
|---|---|
| frontend.ports.ChessService | Puerto de salida para listar salas, consultar una sala, crearla, unirse, mover y abandonar. |
| ServiceException | Comunica un fallo del servicio a través del puerto sin exigir tipos de HTTP. |
| ChessController | Coordina las acciones del usuario y mantiene el listado, la sala abierta, las sesiones locales y la selección del histórico. |
| ChessController.View | Instantánea inmutable con la sala pública, el color local, el indicador de directo, el índice y el FEN visible. |

ChessController recibe el puerto por constructor. En sus pruebas aisladas se compone con FakeService. FrontendMain
lo compone con HttpChessService y lo entrega a ChessPanel, que ejecuta las acciones de servicio de forma secuencial
mediante SwingWorker y presenta los resultados. El controlador sigue sin depender de Swing ni de tipos HTTP.

### Salas, sesiones y órdenes

Crear o unirse guarda la SessionView en memoria, asociada a su sala. La consulta de una sala no ocupa una plaza:
si el controlador ya conserva una sesión para ella, puede mostrar el color del jugador; si no, actúa como espectador.
Volver a abrir una sala propia reutiliza su sesión local, sin volver a solicitar el puesto de negras. Las sesiones
se conservan mientras existe ese controlador y no se escriben en disco.

Mover y abandonar requieren una sala activa, una sesión de jugador y la vista en directo. El controlador envía
el token correspondiente y la última versión conocida a través del puerto; la autorización final y las reglas
siguen en el backend. El token se utiliza para esas órdenes y no forma parte de ChessController.View.

### Estado actual e histórico

Sin sala abierta, la vista muestra el FEN inicial. Dentro de una sala, la vista en directo muestra la última posición
recibida. `replayAt(0)` selecciona la posición inicial; `replayAt(n)` selecciona la posición registrada tras el
movimiento n, dentro del intervalo disponible. La reproducción sólo cambia la vista local y bloquea las órdenes
de mover y abandonar. `live()` vuelve a mostrar la posición más reciente conocida.

`refresh()` consulta la sala abierta o actualiza el listado si aún no hay sala. Una actualización de la partida
conserva la selección histórica mientras su índice siga disponible. Si la consulta falla antes de devolver datos,
se mantienen la última vista y la sesión local; un llamador puede reintentar la operación.

## Cliente HTTP del frontend

La sesión 09 incorpora HttpChessService y HttpChessServiceTest. HttpChessService es un adaptador de salida que
implementa `frontend.ports.ChessService` mediante HttpClient del JDK. El controlador conserva su dependencia del
puerto: el servicio doble de las pruebas puede sustituirse por este adaptador sin cambiar ChessController.

| Operación del puerto | Petición enviada |
|---|---|
| rooms | GET /rooms. |
| room | GET /rooms/{id}. |
| create | POST /rooms con player. |
| join | POST /rooms/{id}/join con player. |
| move | POST /rooms/{id}/moves con from, to, promotion y version. |
| resign | POST /rooms/{id}/resign con version. |

El adaptador recibe la URI base al construirlo y codifica los identificadores de sala para incorporarlos a la ruta.
Se admiten direcciones HTTP o HTTPS con servidor y sin credenciales incrustadas, parámetros de consulta ni fragmento.
El backend de este proyecto utiliza HTTP; admitir HTTPS en el cliente no añade TLS al servidor.

Las peticiones con cuerpo utilizan WireCodec y Properties en UTF-8, con `Content-Type: text/plain;charset=UTF-8`.
Las respuestas se leen también como UTF-8 y se convierten a RoomView, SessionView o listados. Las órdenes de mover
y abandonar llevan la credencial en `X-Player-Token`; la versión se transmite en el cuerpo. Las consultas públicas
no envían esa credencial.

### Esperas y errores

Con el constructor que recibe sólo la URI, el HttpClient se crea con un tiempo de conexión de cinco segundos.
Cada petición establece un límite de ocho segundos. El segundo constructor permite proporcionar otro HttpClient;
su configuración de conexión corresponde al llamador. Las operaciones son síncronas y esperan la respuesta.
ChessPanel las invoca mediante SwingWorker, fuera del hilo que dibuja la ventana.

Una respuesta HTTP fuera del intervalo 200–299 se transforma en ServiceException. Se utiliza el campo error del
cuerpo Properties cuando puede leerse, y el mensaje incluye el código HTTP. Si el cuerpo de error no se puede
interpretar, se utiliza un mensaje genérico. Los fallos de entrada/salida, incluidos los tiempos de espera agotados,
se comunican mediante ServiceException con su causa. Una interrupción restaura el indicador de interrupción del
hilo y se comunica mediante ServiceException. Las respuestas correctas se decodifican con WireCodec y quedan
sujetas a sus comprobaciones de formato.

Cada llamada realiza una sola petición; este adaptador no incorpora reintentos automáticos ni un sondeo periódico.
Un llamador puede volver a consultar el estado tras un fallo de conexión. El controlador conserva su última sala
y sesión si refresh falla antes de devolver una instantánea nueva. Antes de repetir una orden que pudo llegar al
backend, se debe consultar la versión actual para evitar asumir que la orden no se aplicó.

Se mantienen JDK 27, el manifiesto de BackendMain y los comandos y scripts de arranque del backend. FrontendMain
compone ahora el adaptador con el controlador y la interfaz Swing; las pruebas del cliente HTTP conservan su alcance.

## Interfaz gráfica Swing

La sesión 10 incorpora BoardPanel, ChessPanel y FrontendMain, junto con SwingPresentationTest y el script opcional
scripts/frontend.ps1. El frontend se compone con ChessController y HttpChessService y se comunica con el mismo
backend HTTP que utilizan las pruebas anteriores. El dominio, los casos de uso del backend y la persistencia
mantienen sus responsabilidades.

| Tipo | Responsabilidad |
|---|---|
| BoardPanel | Dibuja el tablero recibido como FEN, las piezas, las coordenadas, la selección y el último movimiento; transforma clics en casillas. |
| ChessPanel | Presenta salas, jugadores, mensajes e histórico; conecta las acciones Swing con ChessController y coordina el trabajo en segundo plano. |
| FrontendMain | Lee --server, compone controlador y adaptador HTTP, crea la ventana e inicia la actualización periódica. |

BoardPanel permite mostrar el tablero desde blancas o negras. ChessPanel elige la orientación de negras para el
jugador de ese color; un espectador utiliza la orientación de blancas. Las reglas se siguen validando en el backend.
La interfaz permite crear una sala, unirse por código o selección, copiar el código, observar una partida, mover
con clics o con los campos de origen y destino, elegir la promoción y abandonar tras confirmación.

El histórico puede recorrerse mediante su lista, el deslizador y los botones de posición anterior o siguiente.
En directo devuelve a la última posición conocida y permite volver a jugar. La reproducción es local: no modifica
el histórico ni detiene la partida de los otros jugadores.

### Trabajo en segundo plano y actualización

ChessPanel utiliza SwingWorker para que las llamadas al servicio no bloqueen el hilo de eventos de Swing. El
indicador busy mantiene una sola operación de servicio en curso por ventana y evita iniciar otra hasta que termine.
La finalización presenta el resultado o el mensaje de error y vuelve a cargar la vista en el hilo de eventos.
Durante los diálogos de promoción y confirmación de abandono el Timer no inicia nuevas consultas, para conservar
el contexto de la acción que se está decidiendo.

Al abrir la ventana se consultan las salas y el estado. Un Timer intenta ejecutar refresh cada dos segundos; si
hay una operación en curso o un diálogo abierto, ese intento no inicia otra consulta. Cuando hay una sala abierta,
refresh consulta esa sala; el catálogo de otras salas se vuelve a consultar con Actualizar salas. Sin sala abierta,
refresh actualiza el listado.

Al cerrar la ventana se detiene el Timer. Esto no cancela necesariamente una petición HTTP que ya esté en curso:
la llamada puede continuar hasta completarse o producir un error. Las órdenes de mover o abandonar no se reenvían
automáticamente. Las consultas periódicas posteriores permiten recuperar el estado cuando vuelve a estar disponible
el servidor, mientras la ventana siga abierta.

### Ejecutar las dos aplicaciones

Se necesita JDK 27, Maven y un entorno de escritorio para abrir el frontend. El backend conserva su manifiesto
BackendMain: `java -jar` sigue arrancando el servidor. El frontend utiliza su clase de entrada mediante `java -cp`.
El POM, el nivel de compilación 27 y el script de arranque del backend se mantienen.

Desde la raíz del proyecto, primero compila:

```text
mvn clean verify
```

En una terminal, inicia el backend y déjalo atendiendo peticiones:

```text
java -jar target/chess-software-1.0.0.jar --host 127.0.0.1 --port 8080 --data data
```

En otra terminal, abre un cliente:

```text
java -cp target/chess-software-1.0.0.jar software.ulpgc.chess.frontend.FrontendMain --server http://127.0.0.1:8080
```

Repite el comando del frontend en otra terminal para abrir el segundo jugador. Otro cliente puede utilizarse como
espectador. Todos deben apuntar al mismo servidor. `--server` tiene como valor predeterminado
`http://127.0.0.1:8080`; `--help` muestra el uso. FrontendMain informa si no hay entorno gráfico disponible.

El script opcional ofrece el mismo arranque desde PowerShell:

```powershell
.\scripts\frontend.ps1 -Server http://127.0.0.1:8080
```

El script utiliza java desde el PATH. También puede utilizarse la ruta completa al ejecutable del JDK 27 configurado
en IntelliJ, como se indica en las instrucciones del backend.

### Uso y límites de esta etapa

1. En un cliente escribe tu nombre y pulsa Crear sala; copia su código.
2. En el otro escribe un nombre distinto, pega el código y pulsa Unirme. También puedes seleccionar una sala.
3. Espera a que aparezcan ambos jugadores y el estado activo. Las blancas empiezan.
4. Selecciona e2 y e4 o introduce ambas casillas y pulsa Mover; después juega con negras desde el otro cliente.
5. Para observar, selecciona o pega el código y pulsa Observar sin ocupar una plaza.
6. Recorre las posiciones del histórico y utiliza En directo para regresar a la posición actual.

Estos pasos describen el uso previsto; no constituyen un registro de una prueba manual ya realizada ni de capturas
guardadas. Las credenciales se conservan sólo en la memoria del controlador. Si se cierra el cliente, una nueva
instancia no recupera automáticamente su plaza: puede observar la partida, pero no dispone del token anterior.
Reiniciar únicamente el backend con el mismo directorio conserva sus hashes y permite continuar a los clientes que
siguen abiertos con su sesión local.

La interfaz desactiva las órdenes para espectadores, salas no activas y reproducción del histórico. Todavía puede
permitir intentar un movimiento durante el turno del rival; el backend lo rechaza sin aplicar la jugada. La mejora
del bloqueo visual por turno queda pendiente para la sesión 13. Se mantienen las limitaciones del ajedrez declarado:
sin enroque, captura al paso ni adjudicación automática por mate, ahogado o tablas.

## Hitos del proyecto

El hito `milestone-01-domain-contract` agrupa el modelo, FEN, las reglas de movimiento y el contrato compartido.
Es una etapa intermedia del proyecto, sin punto de entrada de la aplicación completa. La versión 1.0.0 del POM
se mantiene como identificador del artefacto durante el desarrollo; la entrega final tendrá su propia etiqueta.

El primer hito se comprobó con 52 pruebas. Los casos de uso del backend, la persistencia y la API HTTP quedan
fuera de esa etiqueta.

El segundo hito, `milestone-02-backend-and-frontend-core`, reúne el backend ejecutable con HTTP y persistencia
y los casos de uso del frontend comprobados con un doble. Se cerró con 74 pruebas y corresponde al commit
`ddb144c`, conservado en main. El cliente HTTP del frontend y Swing quedan fuera de esa etiqueta.

develop integra las sesiones 09 y 10 sobre esa base. El cliente HTTP y la interfaz Swing no modifican la etiqueta
del segundo hito; forman parte del trabajo posterior.

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
- Sesión 08, 5 de noviembre de 2026: 74 pruebas superadas en total, sin fallos, errores ni pruebas omitidas.
- Sesión 09, 7 de noviembre de 2026: 79 pruebas superadas en total, sin fallos, errores ni pruebas omitidas.
- Sesión 10, 10 de noviembre de 2026: 82 pruebas superadas en total, sin fallos, errores ni pruebas omitidas.

Distribución actual: ModelTest, 11; FenTest, 21; MoveRulesTest, 18; WireCodecTest, 2; ChessServiceTest, 5;
FileRoomRepositoryTest, 5; HttpChessServerTest, 3; ChessControllerTest, 9; HttpChessServiceTest, 5;
SwingPresentationTest, 3.

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

Los nueve casos de ChessControllerTest utilizan FakeService, una implementación del puerto del frontend para
pruebas. Comprueban la sustitución del servicio y la entrada de nombre, el envío del token y versión observados,
el bloqueo del espectador, la conservación de la posición histórica al llegar movimientos, los límites del
histórico, la conservación de estado ante un fallo simulado del servicio, el rechazo de entradas vacías, la
reutilización de una sesión local y el bloqueo de jugadas en una sala en espera. No realizan peticiones HTTP
ni abren ventanas.

HttpChessServiceTest contiene cinco casos. Los cuatro primeros utilizan un servidor HTTP real sobre loopback
con respuestas programadas. Comprueban el nombre con tilde y la SessionView al crear una sala, el token en la
cabecera y la versión y promoción en el cuerpo del movimiento, la conversión de un error 404 en ServiceException
y el rechazo de esquemas o credenciales incrustadas en la dirección del servidor. Estos cuatro casos aíslan el
adaptador cliente; no ejecutan los casos de uso ni el repositorio del backend.

El quinto caso conecta dos ChessController con HttpChessService al backend real compuesto por HttpChessServer,
ChessService y FileRoomRepository en un directorio temporal. Crea una sala, une al segundo jugador y registra
un movimiento de blancas y otro de negras. Después de detener el servidor, comprueba que refresh comunica
ServiceException y conserva la última vista y la sesión local del jugador. La integración utiliza red y disco
reales dentro de la misma JVM; no abre Swing ni lanza procesos de cliente independientes.

Los tiempos de conexión y petición están configurados en el adaptador, pero estas pruebas no fuerzan su vencimiento.

Los tres casos de SwingPresentationTest comprueban los componentes sin abrir una ventana de escritorio. El primero
verifica la correspondencia entre clics y casillas en ambas orientaciones y rechaza los clics en el margen. El segundo
construye y dibuja ChessPanel en una BufferedImage mediante un servicio que falla si se le solicita una operación,
y comprueba las piezas iniciales y zonas pintadas. Esa imagen se utiliza en memoria; no constituye una captura
manual ni un archivo de evidencia exportado. El tercer caso utiliza un servicio doble bloqueado para comprobar
que la llamada se ejecuta fuera del hilo de eventos, que éste sigue respondiendo mientras espera el servicio y
que Actualizar salas permanece deshabilitado hasta que termina el worker.
No se afirma una prueba automática de los diálogos modales de promoción o abandono.

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
- feature/08-frontend-use-cases: puerto del frontend, controlador, sesiones locales e histórico.
- feature/09-http-client: adaptador HTTP del frontend, mensajes y errores de comunicación.
- feature/10-swing-ui: presentación Swing, arranque del cliente y trabajo en segundo plano.

## Próximas etapas

Verificación de extremo a extremo, recorrido manual y evidencias de interfaz; mejoras de interacción y adaptación
a las próximas clases.
