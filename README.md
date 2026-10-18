# chess-software

Proyecto de Ingeniería de Software II: sistema de salas de ajedrez.

## Material de partida

El proyecto se desarrolla a partir del código de clase. Cada sesión integra, revisa y prueba una parte del sistema.

## Estado actual

Se ha integrado el modelo del tablero:

- Board: posición inicial y movimientos estructurales.
- Square: representación y validación de casillas.
- Piece: tipos de piezas.
- Color: colores.
- File y Rank: columnas y filas.

Board protege su mapa mediante una copia defensiva. La operación move produce otro tablero y conserva el original.

Las siguientes sesiones incorporarán FEN, validación de jugadas, backend, persistencia y frontend.

## Compilación y pruebas

Se necesita un JDK 27 o superior y Maven.

Comando: `mvn clean verify`.

Verificación de la sesión 01, realizada el 18 de octubre de 2026: 11 pruebas superadas, sin fallos, errores ni pruebas omitidas.

## Organización de ramas

- main: hitos estables.
- develop: integración del trabajo comprobado.
- feature/01-modelo-base: incorporación del modelo en la primera sesión.