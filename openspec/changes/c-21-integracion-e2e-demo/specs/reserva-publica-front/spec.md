## MODIFIED Requirements

### Requirement: Resumen del turno reservado
Tras el 201 la página SHALL reemplazar los pasos por un resumen con el nombre de pila, la prestación y su duración, la fecha y hora de inicio en hora de Buenos Aires, el estado en palabras (`PENDIENTE` "Pendiente de confirmación", `CONFIRMADO` "Confirmado"), el contacto del consultorio y el aviso de revisar el email. El aviso SHALL mencionar los links para gestionar el turno solo si el turno queda `PENDIENTE`; si queda `CONFIRMADO` SHALL decir que se enviaron los datos del turno, sin mencionar links. El resumen SHALL mostrar solo datos de la respuesta del propio turno y del email ingresado, y SHALL mover el foco a su encabezado.

#### Scenario: Turno pendiente
- **WHEN** la API responde el ejemplo `turnos-publico-reservar.201.json`
- **THEN** se lee "Ana", "Consulta", "jueves 10 de diciembre de 2026" a las "10:00", "Pendiente de confirmación", el contacto del consultorio y "Revisá tu email (paciente@ejemplo.com)" junto con la mención de los links para gestionarlo, y el foco está en el encabezado del resumen

#### Scenario: Turno que nace confirmado
- **WHEN** la respuesta trae `estado: CONFIRMADO`
- **THEN** el resumen dice "Confirmado", pide revisar el email por los datos del turno sin mencionar links e indica que ante cualquier cambio se comunique con el consultorio

#### Scenario: Reservar otro
- **WHEN** el paciente elige "Reservar otro turno" en el resumen
- **THEN** vuelve al paso de prestación con el formulario vacío y la casilla desmarcada
