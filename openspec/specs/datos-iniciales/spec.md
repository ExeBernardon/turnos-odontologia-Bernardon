# datos-iniciales Specification

## Purpose

Garantiza que el sistema arranque usable sin cargar nada a mano: un odontólogo para iniciar sesión, un catálogo de prestaciones y horarios de ejemplo, siempre con datos ficticios y sin duplicar nada al reiniciar.

## Requirements

### Requirement: Odontólogo inicial desde el entorno
Al arrancar, si no existe ningún odontólogo, el backend SHALL crear uno con el nombre, email y contraseña de `SEED_ODONTOLOGO_NOMBRE`, `SEED_ODONTOLOGO_EMAIL` y `SEED_ODONTOLOGO_PASSWORD`, guardando la contraseña solo como hash BCrypt.

#### Scenario: Primer arranque
- **WHEN** el backend arranca contra una base sin odontólogos con `SEED_ODONTOLOGO_EMAIL=odontologo@ejemplo.com`
- **THEN** existe un único odontólogo con ese email y su contraseña guardada es un hash BCrypt que verifica contra `SEED_ODONTOLOGO_PASSWORD`, nunca el texto plano

#### Scenario: Ya existe un odontólogo
- **WHEN** el backend arranca y ya hay un odontólogo, aunque las variables `SEED_ODONTOLOGO_*` tengan otros valores
- **THEN** no se crea otro ni se modifica el existente

### Requirement: Catálogo de prestaciones inicial
Al arrancar, si la tabla de prestaciones está vacía, el backend SHALL crear las prestaciones activas Consulta (30 min), Limpieza (45 min), Extracción (45 min) y Arreglo / caries (60 min). Si hay al menos una, no SHALL agregar ninguna.

#### Scenario: Tabla vacía
- **WHEN** el backend arranca con la tabla de prestaciones vacía
- **THEN** quedan exactamente las cuatro prestaciones de ejemplo, activas, con sus duraciones

#### Scenario: Catálogo ya editado
- **WHEN** el backend arranca y existe solo una prestación cargada por el odontólogo
- **THEN** el catálogo sigue teniendo solo esa prestación

### Requirement: Horarios de atención iniciales
Al arrancar, si la tabla de horarios de atención está vacía, el backend SHALL crear los rangos 09:00-13:00 y 15:00-19:00 de lunes a viernes, sin atención sábado ni domingo. Si hay al menos uno, no SHALL agregar ninguno.

#### Scenario: Tabla vacía
- **WHEN** el backend arranca con la tabla de horarios vacía
- **THEN** quedan diez rangos, dos por cada día de 1 a 5, y ninguno para los días 6 y 7

### Requirement: Carga idempotente al reiniciar
Reiniciar el backend contra la misma base SHALL dejar los mismos odontólogos, prestaciones y horarios que antes del reinicio.

#### Scenario: Segundo arranque
- **WHEN** el backend arranca dos veces seguidas contra la misma base
- **THEN** después del segundo arranque hay 1 odontólogo, 4 prestaciones y 10 horarios, igual que después del primero

### Requirement: Solo datos ficticios
Los valores por defecto y los datos de ejemplo de la carga inicial SHALL ser ficticios (por ejemplo `odontologo@ejemplo.com`), y la carga no SHALL crear pacientes, turnos ni bloqueos.

#### Scenario: Sin datos de personas
- **WHEN** el backend termina la carga inicial en una base vacía
- **THEN** las tablas de pacientes, turnos y bloqueos están vacías
