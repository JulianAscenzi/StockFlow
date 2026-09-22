# StockFlow — Inicio

Este es el punto de entrada al cuaderno técnico de StockFlow: documentación del producto, decisiones respaldadas por el código y material para presentar el proyecto.

## Abrir en Obsidian

1. En el selector de bóvedas, elegí **Open folder as vault** y seleccioná la carpeta `docs` de este repositorio.
2. Abrí esta nota, `Inicio.md`.
3. En **Settings → Files and Links**, desactivá **Use Wikilinks** y elegí rutas relativas al archivo para los enlaces nuevos. Así las notas también se pueden recorrer desde GitHub.

Las notas se versionan con Git. La carpeta `docs/.obsidian/`, que Obsidian crea con tus preferencias personales, está ignorada. No hacen falta plugins adicionales. Los enlaces al código salen de la bóveda: consultalos desde GitHub o el editor del repositorio.

## Documentación de referencia

- [Arquitectura](ARCHITECTURE.md): módulos, modelo, reglas y transacciones.
- [Roadmap](ROADMAP.md): alcance y planificación.
- [Estado](STATUS.md): entregas, verificaciones y bloqueos.
- [Despliegue](DEPLOYMENT.md): operación y límites de la demo.
- [README del repositorio](../README.md): ejecución local, API y enlaces públicos.

## Decisiones y presentación

- [Bloqueo de stock](decisiones/Bloqueo%20de%20stock.md): cómo se coordinan ventas concurrentes y qué pruebas respaldan la solución.
- [Guion de demostración](portfolio/Guion%20de%20demostracion.md): recorrido del producto y puntos técnicos para una entrevista.

## Cómo mantener este cuaderno

Antes de trabajar, consultá el estado y el roadmap. Después de un cambio, actualizá su documento de referencia y enlazalo desde las notas relacionadas; evitá mantener copias del estado o de las tareas.

Cuando documentes una decisión, registrá problema, solución actual, alternativas, consecuencias y evidencia en código o pruebas. Distinguí las propuestas de lo implementado. Para un aprendizaje nuevo, explicá el concepto con tus palabras y un ejemplo del proyecto; creá la nota cuando tengas contenido concreto.
