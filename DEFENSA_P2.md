# Guía de defensa – Proyecto 2 (interfaz gráfica)

## 1. La idea en 3 frases
1. "Es la misma aplicación del Proyecto 1; solo he **sustituido la View**: `SwingView` implementa la misma interfaz `View` que `LanternaView`."
2. "Model y Controller no se han tocado." Prueba en vivo: `git diff v1-project1 project2 -- src/main/java/studyboard/model src/main/java/studyboard/controller` no muestra nada.
3. "`Main` decide qué View crear: sin argumentos, Swing; con `--text`, Lanterna."

## 2. Qué es nuevo (archivos en `view/swing`)
| Archivo | Qué hace |
|---|---|
| `SwingView` | Ventana principal: tablero de 3 columnas, barras de herramientas, atajos, arrastrar y soltar, tema |
| `TaskCardRenderer` | Dibuja cada tarea como tarjeta dentro de una `JList` (un "sello" que se repinta por fila) |
| `TaskFormDialog` | Formulario nueva/editar tarea (crea un `TaskData`, que valida) |
| `FilterDialog` | Búsqueda, filtro y orden |
| `TodayDialog` | Sugerencia + "Why now?" |
| `SubjectsDialog` | Asignaturas (ventana NO modal, se refresca sola) |
| `CalendarDialog` | Calendario mensual |
| `Ui`, `HelpTexts` | Utilidades compartidas (fechas, atajos, mensajes) y textos de ayuda |

## 3. Conceptos que te pueden preguntar
- **EDT (Event Dispatch Thread)**: el hilo que dibuja y recibe clics. Todo lo de Swing debe ir ahí; por eso `Main` usa `invokeLater` y el Controller también corre ahí.
- **Modal / no modal**: un diálogo modal bloquea la ventana de atrás hasta cerrarse (formularios, Today, Calendar); `SubjectsDialog` no, porque el Controller lo refresca mientras está abierto.
- **Renderer**: `JList` no sabe pintar una `Task`; le damos un componente que la dibuja.
- **`DefaultListModel`**: los datos de cada columna; el Controller manda la misma lista que en modo texto y la View la reparte por `ordinal()` del estado.
- **Lambda / `this::moveTask`**: código que se ejecuta después (al pulsar un botón o soltar una tarjeta).
- **Drag and drop**: viaja solo el id de la tarea (como texto); la columna destino pide al Controller el movimiento.
- **Layouts**: `BorderLayout` (ventana), `GridLayout` (columnas), `GridBagLayout` (formularios), `BoxLayout` (apilar). Por eso se adapta al redimensionar.
- **Tema claro/oscuro**: `FlatDarkLaf.setup()` / `FlatLightLaf.setup()` y `updateComponentTreeUI` en todas las ventanas.

## 4. Demo de 5 minutos
1. Arranque: tablero, tarjetas (símbolo `!!`, `LATE` y franja de color: no solo color).
2. Clic en una tarjeta + `Ctrl+→` (o arrastrarla). Mensaje abajo. `Ctrl+Z`.
3. `Ctrl+N`: pon la fecha 31.02.2026 → error claro, el formulario sigue abierto. Corrige y guarda.
4. Escribe "physics" + Enter en la búsqueda → línea FILTER ON. Clear filter.
5. `F2` Today y "Why now?". `F4` calendario. `F3` asignaturas: nombre repetido; borrar (confirmación con "No" por defecto) y Undo.
6. `F1` ayuda contextual. Botón tema oscuro.
7. Muestra `java -jar target/studyboard.jar --text`: **mismo programa, otra View**.

## 5. Cambios típicos en directo
| Petición | Dónde |
|---|---|
| Colores de prioridad | `TaskCardRenderer.priorityColor` |
| Texto de la tarjeta | `TaskCardRenderer.getListCellRendererComponent` (`title.setText`, `details.setText`) |
| Botón nuevo en la barra | `SwingView`, constructor: `actions.add(button("Texto", "Tooltip", () -> ...))` |
| Atajo nuevo | `SwingView`, constructor: `Ui.bindKey(root, "ctrl D", () -> ...)` y su línea en `HelpTexts.BOARD` |
| Empezar en tema oscuro | `Main`: `FlatDarkLaf.setup()` en vez de `FlatLightLaf.setup()`, y `dark = true` en `SwingView` |
| Tamaño mínimo / inicial | `SwingView`: `setSize` / `setMinimumSize` |
| Título de la ventana | `new JFrame("StudyBoard")` |
| Cuántas sugerencias en Today | `TodayDialog`: `Math.min(5, ...)` |

## 6. Preguntas probables
- *¿Por qué Swing y no JavaFX?* Viene con el JDK, empaqueta sin complicaciones en un solo `.jar` y con FlatLaf se ve moderno.
- *¿Qué cambió en el Controller?* Nada. Es la prueba de que MVC funciona.
- *¿Qué diferencia hay con el modo texto?* Ahí `addWindowAndWait` bloqueaba; aquí los diálogos modales bloquean solo la ventana de atrás y `SubjectsDialog` no bloquea.
- *¿Dónde se valida?* En el Model (`TaskData`); la View solo muestra el mensaje y no cierra el formulario.
- *¿Cómo se deshace?* El Controller guarda una copia del repositorio antes de cada cambio (`undoStack`).

## 7. Entrega
```
git status                      # limpio
git push origin project2
git archive --format=zip -o ~/studyboard-p2.zip HEAD                 # estando en project2
git archive --format=zip -o ~/studyboard-p1.zip v1-project1          # Proyecto 1
```
Sube ambos zips y el enlace de GitHub a CEZ. Prueba tú misma el `.jar` del zip antes de entregarlo.
