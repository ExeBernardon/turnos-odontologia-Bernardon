## Why

El paciente tiene que poder reservar un turno sin cuenta y sin llamar (US-005, US-006): es la razón de ser del producto. Hoy `/reservar` es un placeholder ("Pantalla en construcción") aunque el backend ya expone prestaciones activas, disponibilidad pública (C-06) y la reserva pública (C-07), y el contrato con sus ejemplos está cerrado.

## What Changes

- Pantalla pública **Reservar turno** (`/reservar`, reemplaza el placeholder) en tres pasos dentro de la misma página:
  1. **Prestación**: lista de prestaciones activas (`GET /api/public/prestaciones`) con nombre y duración.
  2. **Fecha y horario**: selector de fecha acotado a la ventana pública (de hoy a 60 días, RN-DIS-06) y horarios libres de ese día (`GET /api/public/disponibilidad` con `desde = hasta`); lista vacía = "No hay horarios disponibles ese día. Probá con otra fecha." (no es un error).
  3. **Datos**: nombre, apellido, DNI, email, teléfono y casilla obligatoria de tratamiento de datos con texto de finalidad (RN-DAT-02) → `POST /api/public/turnos`.
- **Validación de formato en el front** con las mismas reglas del contrato (`NombrePersona`, `DniEntrada`, `Email`, `Telefono`) antes de enviar; los `detalles` de un 400 `VALIDACION` se muestran junto a cada campo.
- **Errores de negocio**: 409 `HORARIO_OCUPADO` → "Ese horario se acaba de ocupar, elegí otro." y se refrescan los horarios del día; 422 `HORARIO_NO_DISPONIBLE` → mensaje propio y refresco; 422 `CONSENTIMIENTO_REQUERIDO` → error en la casilla; 422 `PRESTACION_INACTIVA` / 404 → mensaje y recarga de prestaciones; conexión caída / 500 → mensaje genérico. Los datos escritos se conservan siempre.
- **Resumen** tras el 201: prestación, fecha y hora en hora de Buenos Aires, estado (`PENDIENTE` "pendiente de confirmación" o `CONFIRMADO`), contacto del consultorio y el aviso "Revisá tu email": solo datos del propio turno (la respuesta no trae token ni datos de otros).
- Accesible (labels, foco visible, grupos de radios con teclado, regiones `status`/`alert`, foco al resumen) y responsive mobile-first (el paciente reserva desde el celular).
- Tests Vitest: lógica pura (fechas y ventana, formato de horarios, validadores, armado del request, mensajes por código), cliente API con los ejemplos del contrato y la pantalla en jsdom.
- Sin cambios de contrato, backend, router ni dependencias.

## Capabilities

### New Capabilities
- `reserva-publica-front`: comportamiento visible para el paciente de la página pública `/reservar` (elección de prestación, fecha y horario libre, formulario con consentimiento, validación, errores de la API y resumen del turno reservado).

### Modified Capabilities
(ninguna — `plataforma-frontend` ya prevé que el placeholder se reemplace por la pantalla real; `alta-turnos` y `disponibilidad-calculo` son backend y no cambian)

## Impact

- `frontend/src/public/reservar.ts` (reemplaza el placeholder; conserva el `h1` "Reservar turno") y nuevos `reservar.api.ts`, `reservar.logica.ts`, `reservar.css` en `frontend/src/public/` (+ tests).
- Reusa **sin modificar**: `src/api/cliente.ts`, `src/api/types.ts` (`PrestacionPublica`, `Disponibilidad`, `ReservaPublicaRequest`, `TurnoResumenPublico` ya existen), `shared/dom.ts`, los helpers genéricos de `admin/configuracion/` (`fechas.ts`, `parseo.ts`, `errores.ts#detallesPorCampo`, `formulario.ts`, utilidades de test) y `admin/pacientes.logica.ts#validarPaciente` (C-16). Son archivos de otros integrantes: se importan, no se editan (regla 16).
- No se tocan `public/rutas.ts` (la ruta ya está registrada), `rutas.test.ts`, `vite.config.ts`, `package.json` ni `docs/api/`.
