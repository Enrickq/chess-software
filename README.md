# chess-software

Proyecto de Ingeniería de Software II: sistema de salas de ajedrez.

## Material de partida

Se amplían el modelo del tablero y los puertos trabajados en clase. Cada sesión integra una parte del sistema y
comprueba su comportamiento mediante pruebas.

## Estado actual

El proyecto incluye el modelo inmutable del tablero, la lectura y escritura de posiciones FEN y un servicio de reglas
para movimientos ordinarios. También incorpora el contrato compartido de datos para las futuras aplicaciones.

- Board, Square, Piece, Color, File y Rank representan el tablero.
- Position combina un tablero con su estado.
- State contiene turno, derechos de enroque, casilla objetivo al paso y contadores.
- Los puertos definen las operaciones de lectura y escritura.
- Los adaptadores FEN implementan las conversiones.
- MoveRules valida movimientos y devuelve una nueva Position.
- El paquete contract define los datos de intercambio y su conversión a texto.

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
la interfaz gráfica y los futuros adaptadores de red.

| Tipo | Contenido |
|---|---|
| MoveView | number, from, to, promotion, player, fen y at: información de un movimiento. |
| RoomView | id, white, black, status, fen, version, createdAt y moves: información pública de una sala. |
| SessionView | room, token y color: respuesta destinada al jugador al crear o unirse a una sala. |
| WireCodec | Conversión entre estos datos y texto con formato Properties. |

Los tres tipos de datos son records. RoomView realiza una copia defensiva de la lista de movimientos.
RoomView no contiene credenciales; el token pertenece a SessionView y no debe incluirse en las consultas públicas.
La creación de sesiones y su autorización se implementarán en los casos de uso del backend.

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

En esta etapa se trabaja con String, con pruebas de nombres Unicode y caracteres de escape. La conversión entre
texto y bytes mediante UTF-8 se hará en los adaptadores de comunicación. Properties.store añade un comentario con
fecha; la equivalencia se comprueba recuperando los datos, sin exigir que el texto generado sea idéntico.

El formato Properties es una decisión de este proyecto. Se conserva FEN para representar las posiciones.
Todavía no hay rutas HTTP, servidor, almacenamiento ni interfaz gráfica: este contrato prepara su integración.

## Hito de dominio y contrato

El hito `milestone-01-domain-contract` agrupa el modelo, FEN, las reglas de movimiento y el contrato compartido.
Es una etapa intermedia del proyecto, sin punto de entrada de la aplicación completa. La versión 1.0.0 del POM
se mantiene como identificador del artefacto durante el desarrollo; la entrega final tendrá su propia etiqueta.

## Compilación y pruebas

Se necesita JDK 27 y Maven.

Comando: `mvn clean verify`.

Verificaciones locales:

- Sesión 01, 18 de octubre de 2026: 11 pruebas del modelo superadas.
- Sesión 02, 20 de octubre de 2026: 32 pruebas superadas en total.
- Sesión 03, 22 de octubre de 2026: 50 pruebas superadas en total.
- Sesión 04, 28 de octubre de 2026: 52 pruebas superadas en total, sin fallos, errores ni pruebas omitidas.

Distribución actual: ModelTest, 11; FenTest, 21; MoveRulesTest, 18; WireCodecTest, 2.

Las pruebas de FEN comprueban posiciones iniciales y personalizadas, tablero vacío, campos de estado, conversión de ida
y vuelta y rechazo de formatos incorrectos. Las pruebas de movimiento comprueban aperturas, capturas, obstáculos,
protección del rey, promoción, actualización del estado y rechazo de los movimientos especiales no disponibles.
Las pruebas del contrato comprueban conversiones de sala, sesión y listado, Unicode, escapes y rechazo de campos
ausentes o cantidades fuera del límite.

## Organización de ramas

- main: hitos estables.
- develop: integración del trabajo comprobado.
- feature/01-base-model: integración del modelo.
- feature/02-fen-serialization: integración de la serialización FEN.
- feature/03-move-validation: reglas de movimiento y protección del rey.
- feature/04-shared-contract: datos compartidos y formato de intercambio.

## Próximas etapas

Casos de uso del backend, persistencia, comunicación HTTP e interfaz gráfica.
