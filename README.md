# chess-software

Proyecto de Ingeniería de Software II: sistema de salas para jugar al ajedrez.

## Estado actual

Configuración inicial del proyecto Maven.

Las siguientes etapas incorporarán el modelo del tablero, la serialización FEN, las reglas de movimiento, el backend, la persistencia y el frontend.

## Tecnologías

- Java 27.
- Maven.
- JUnit Jupiter para las pruebas.

## Compilación

El proyecto necesita JDK 27.

Para compilar y ejecutar las pruebas: `mvn clean verify`.

## Organización de ramas

- `main`: versiones estables.
- `develop`: integración del trabajo.
- `feature/...`: desarrollo de cada tarea.