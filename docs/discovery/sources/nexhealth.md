# NexHealth

- **URL**: https://www.nexhealth.com/
- **Fecha de investigación**: 2026-11-22
- **Empresa / país**: NexHealth; sede no declarada en la página (soporte y teléfono de EE.UU.).
- **Segmento**: consultorios dentales y médicos (capa sobre el sistema existente). **Modalidad**: SaaS en nube que se sincroniza con el sistema de gestión.

## Problema que resuelve

Capa de reserva, comunicación y cobro con el paciente sobre el sistema clínico existente, para reducir ausentismo y llenar huecos de agenda.

## Usuarios objetivo

Consultorios y grupos dentales y médicos de EE.UU.; desarrolladores vía Health Records API.

## Features detectadas

Comprobado (home y precios; sin capturas ni video):
- Reserva online sincronizada con el sistema (desde sitio web y Google Maps), recalls en un clic, lista de espera/ASAP para cancelaciones.
- Formularios digitales (escriben de vuelta al sistema), app para iPad.
- Recordatorios automáticos, mensajería (bidireccionalidad no confirmada), campañas, reseñas.
- Pagos por texto/email, tarjeta en archivo, auto-posting al ledger; verificación de seguros; dashboard de producción vs. cobranzas y utilización de agenda.
- Health Records API con integración a ~15 sistemas (Open Dental, Dentrix, Eaglesoft, etc.), docs y alta gratuita para desarrolladores.
- Ficha clínica propia, odontograma, presupuestos: no ofrece (no es sistema clínico).
- Seguridad: HIPAA y cifrado en reposo incluidos en todos los paquetes (afirmación de la página de precios); SOC 2, auditoría: **No evidenciado** (/security dio 404).

## Pricing

**(no publicado)**: módulos (Scheduling, Forms, Communications, Payments, Verification, Insights) y "planes a medida"; mensual o anual con 90 días de aviso; sin cargo de cancelación. Sin prueba gratis.

## Posicionamiento

"Software for dental and medical practices". "20.000+ prácticas", 4,8 en G2 y 4,7 en Capterra (cifras propias, sin verificar).

## Diferenciadores

Funciona encima del sistema existente en vez de reemplazarlo; lista de espera con casos de uso (testimonios: +USD 150K anuales, 16 minutos para llenar una cancelación).

## Notas del investigador

- **Disponibilidad en Argentina**: No. Depende de sistemas estadounidenses, HIPAA y pagos de EE.UU.
- Referente de arquitectura "capa del paciente" y de API de integración; la modularidad de producto y precio es un patrón a considerar.
- **Patrones UX**: reserva desde Google Maps; recall en un clic; lista de espera como ingreso recuperado; dashboard de utilización.

## Fuentes

- https://www.nexhealth.com/ (2026-11-22)
- https://www.nexhealth.com/pricing (2026-11-22)
