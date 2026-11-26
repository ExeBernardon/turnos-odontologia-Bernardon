# estilo-visual-front Specification

## Purpose
Define la apariencia compartida del front (área pública y área privada): de dónde salen los valores visuales, cómo se ven los controles y estados comunes, y qué garantías de accesibilidad visual debe cumplir toda pantalla.

## Requirements

### Requirement: Tokens visuales con una sola fuente
Los valores visuales compartidos (colores, tipografía, espaciado, radios, sombras, foco y movimiento) SHALL definirse una única vez en `design-system/tokens.css` y el front SHALL usar una copia exacta de ese archivo. Un test SHALL fallar si la copia difiere. Las hojas de estilo de las pantallas no SHALL redefinir esos tokens.

#### Scenario: Copia desactualizada
- **WHEN** alguien cambia un color en `design-system/tokens.css` y no actualiza la copia del front
- **THEN** los tests del front fallan indicando que los tokens están desincronizados

#### Scenario: Pantalla que redefine un token
- **WHEN** una hoja de estilo de pantalla declara de nuevo un token compartido en `:root`
- **THEN** un test del front falla señalando el archivo

### Requirement: Estilo solo con CSS
El rediseño visual SHALL lograrse solo con hojas de estilo: no SHALL cambiar la estructura de las pantallas, su comportamiento, sus textos, el contrato de la API ni las dependencias del front.

#### Scenario: Mismo comportamiento
- **WHEN** se ejecuta la suite de tests del front después del rediseño
- **THEN** todos los tests de comportamiento existentes pasan sin modificarse

### Requirement: Jerarquía de botones
Los botones SHALL distinguirse en primario (acción principal de un formulario), secundario (resto de acciones) y destructivo (cancelar turno, ausente, desactivar), y el estado deshabilitado SHALL verse distinto del habilitado. El destructivo SHALL diferenciarse por algo más que el color (borde o peso) además de su texto.

#### Scenario: Acción principal de un formulario
- **WHEN** una pantalla muestra un formulario con su botón de envío y otros botones
- **THEN** el botón de envío se ve como primario y los demás como secundarios

#### Scenario: Botón deshabilitado
- **WHEN** un botón está deshabilitado mientras se procesa un pedido
- **THEN** se ve atenuado y el cursor indica que no está disponible

### Requirement: Controles de formulario legibles
Inputs, selects y fieldsets SHALL tener borde visible con contraste de al menos 3:1 contra el fondo, radio redondeado, alto mínimo de 44 px y un estado de error marcado por borde además del mensaje de texto.

#### Scenario: Campo con error
- **WHEN** un campo queda con `aria-invalid="true"`
- **THEN** su borde cambia al color de peligro con mayor grosor y el mensaje de error sigue visible como texto

### Requirement: Opciones de radio como chips
Las opciones de radio de prestación y de horario SHALL mostrarse como chips táctiles de al menos 44 px, y la opción elegida SHALL marcarse por borde y peso de texto además del color.

#### Scenario: Elegir un horario
- **WHEN** el paciente elige un horario disponible
- **THEN** ese chip se ve seleccionado con borde y texto destacados y los demás no

### Requirement: Estados con color y texto
Los badges de estado del turno y los avisos de éxito, alerta y error SHALL comunicar su significado con texto y no solo con color, con contraste de texto de al menos 4.5:1 contra su fondo.

#### Scenario: Turno pendiente y turno confirmado
- **WHEN** la agenda muestra un turno pendiente y otro confirmado
- **THEN** cada uno lleva un badge con la palabra de su estado sobre un fondo de color distinto

### Requirement: Layout de las áreas
Las páginas públicas SHALL mostrarse en una columna centrada con sus secciones como tarjetas blancas sobre un fondo de página suave. El área privada SHALL mostrar una cabecera y un menú de navegación con la página actual destacada.

#### Scenario: Reserva en el celular
- **WHEN** se abre `/reservar` en un ancho de 375 px
- **THEN** las secciones se ven como tarjetas apiladas en una sola columna

#### Scenario: Página actual del admin
- **WHEN** el odontólogo está en `/admin/agenda`
- **THEN** el ítem Agenda del menú se ve destacado respecto de los demás

### Requirement: Contraste de color AA
Todo texto SHALL tener contraste de al menos 4.5:1 contra su fondo y todo borde de control o indicador de foco al menos 3:1, según WCAG 2.1 AA.

#### Scenario: Texto secundario
- **WHEN** se muestra texto secundario (ayudas, fechas, detalles) sobre una tarjeta o sobre el fondo de página
- **THEN** su contraste es de al menos 4.5:1

### Requirement: Foco visible y teclado
Todo elemento interactivo SHALL mostrar un indicador de foco visible al navegarlo con teclado, con contraste de al menos 3:1 respecto del fondo.

#### Scenario: Navegar con Tab
- **WHEN** se recorre la reserva con la tecla Tab
- **THEN** cada link, botón, input y chip enfocado muestra un anillo de foco visible

### Requirement: Responsive sin scroll horizontal
Las pantallas SHALL verse sin scroll horizontal desde 320 px de ancho y los objetivos táctiles SHALL medir al menos 44 por 44 px.

#### Scenario: Pantalla angosta
- **WHEN** se abre cualquier pantalla en un ancho de 320 px
- **THEN** no aparece scroll horizontal y los botones conservan al menos 44 px de alto

### Requirement: Movimiento reducido
Las transiciones visuales SHALL ser breves y SHALL desactivarse cuando el usuario pide `prefers-reduced-motion: reduce`.

#### Scenario: Usuario con movimiento reducido
- **WHEN** el sistema operativo pide movimiento reducido
- **THEN** los cambios de estado de botones y controles ocurren sin transición
