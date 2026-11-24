# notificaciones-turnos Specification

## Purpose

Define el punto único por el que los dominios del backend piden avisar al paciente o al odontólogo sobre un turno, uno por cada mail del sistema, para que los dominios lo usen antes de que exista el envío real de emails.

## Requirements

### Requirement: Un aviso por cada mail del sistema
El backend SHALL ofrecer a los dominios un aviso por cada mail de KB 05 "Mails del sistema": confirmación de reserva, aviso de turno confirmado, recordatorio, reprogramación, reprogramación a menos de 24 h, cancelación por el odontólogo, cancelación por bloqueo y turno no confirmado al odontólogo. Cada aviso SHALL recibir el turno al que se refiere.

#### Scenario: Cobertura de la tabla de mails
- **WHEN** se compara la lista de avisos disponibles con las ocho filas de la tabla "Mails del sistema"
- **THEN** hay exactamente un aviso por fila

### Requirement: Implementación por defecto sin envío
Mientras no exista una implementación real, el backend SHALL arrancar con una implementación de los avisos que no envía nada, no falla y no registra datos personales ni el token del turno. Una implementación real SHALL poder reemplazarla sin cambiar a quienes la invocan.

#### Scenario: Aviso sin implementación real
- **WHEN** un dominio pide el aviso de confirmación de reserva de un turno y no hay implementación real
- **THEN** la operación termina sin error, sin enviar ningún email y sin escribir en el log el email, el teléfono, el DNI ni el token

#### Scenario: Reemplazo por la implementación real
- **WHEN** se registra otra implementación de los avisos en la aplicación
- **THEN** los dominios reciben esa implementación y la que no envía nada deja de usarse
