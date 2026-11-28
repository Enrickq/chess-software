# Verificación de la entrega

El 28 de noviembre de 2026 se clonó la rama pública `develop` en una carpeta limpia de Windows.
El código probado corresponde a `5f4bce95d60d9e7c95afde42525bd28d0d8acd79`. La rama de cierre
añade documentación y evidencias a esa base.

## Compilación

```text
mvn clean verify -Djava.awt.headless=true -Dchess.evidence=target/evidence
```

Con JDK 27 y compilación `release 27`, Maven produjo `target/chess-software-1.0.0.jar` y ejecutó
**92 pruebas en 11 suites**, con cero fallos, errores y omitidas. Las pruebas verifican el modelo, FEN,
reglas, el contrato HTTP, la persistencia y los casos de uso del frontend. `ChessControllerTest` sustituye
el puerto `ChessService` por un doble para probar el controlador de forma aislada.

## Recorrido verificado con el JAR

Se arrancó el backend en un proceso independiente mediante `java -jar`, en la interfaz local y con un
directorio de ensayo. Los controladores de Ana, Luis y el espectador utilizaron `HttpChessService` para
comunicarse con el servidor real. El ensayo se realizó sin ventanas interactivas.

| Comprobación | Resultado observado |
|---|---|
| Crear y compartir una sala | Ana creó la sala en espera. Luis se unió a esa misma sala y quedó activa. |
| Movimiento inválido | El servidor devolvió HTTP 400 al intentar `e2 → e5`. La versión se conservó. |
| Jugar y revisar el histórico | Se aceptaron `e2 → e4` y `e7 → e5`. El espectador consultó las dos jugadas y mostró la posición después de la primera. |
| Reiniciar el servidor | Se arrancó un nuevo proceso con el mismo directorio. La sala recuperada conservó el identificador, la versión, la posición y las dos jugadas. |
| Reanudar desde el perfil | Un controlador nuevo recuperó las blancas de Ana y pudo jugar `g1 → f3`. El histórico llegó a tres jugadas. |

## Evidencias

El caso integrado `EndToEndTest` de Maven exportó las vistas de la [sala en espera](images/final-01-room.png),
del [tablero con dos jugadores](images/final-02-board.png) y del [histórico en modo espectador](images/final-03-history.png).
Los datos proceden de un backend HTTP local con almacenamiento real y las imágenes se renderizan con los paneles Swing.
El [registro manual anterior](MANUAL_VERIFICATION.md) enlaza capturas de escritorio de la sesión 12.

Los perfiles privados, los datos de partidas y el JAR se generaron en la copia de ensayo. La entrega contiene
el código, la configuración de Maven, la documentación y las imágenes.
