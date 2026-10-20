# chess-software

Proyecto de Ingeniería de Software II: sistema de salas de ajedrez.

## Material de partida

Se amplían el modelo del tablero y los puertos trabajados en clase. Cada sesión integra una parte del sistema y
comprueba su comportamiento mediante pruebas.

## Estado actual

El proyecto incluye el modelo inmutable del tablero y la lectura y escritura de posiciones FEN.

- Board, Square, Piece, Color, File y Rank representan el tablero.
- Position combina un tablero con su estado.
- State contiene turno, derechos de enroque, casilla objetivo al paso y contadores.
- Los puertos definen las operaciones de lectura y escritura.
- Los adaptadores FEN implementan las conversiones.

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

Se comprueban la estructura del tablero, los símbolos de piezas y los valores de los campos de estado. Esta conversión
no comprueba toda la legalidad de una posición ni implementa las reglas de movimiento.

## Compilación y pruebas

Se necesita JDK 27 y Maven.

Comando: `mvn clean verify`.

Verificaciones locales:

- Sesión 01, 18 de octubre de 2026: 11 pruebas del modelo superadas.
- Sesión 02, 20 de octubre de 2026: 32 pruebas superadas en total, sin fallos, errores ni pruebas omitidas.

Las pruebas de FEN comprueban posiciones iniciales y personalizadas, tablero vacío, campos de estado, conversión de ida
y vuelta y rechazo de formatos incorrectos.

## Organización de ramas

- main: hitos estables.
- develop: integración del trabajo comprobado.
- feature/01-base-model: integración del modelo.
- feature/02-fen-serialization: integración de la serialización FEN.

## Próximas etapas

Reglas de movimiento, casos de uso del backend, persistencia, comunicación HTTP e interfaz gráfica.