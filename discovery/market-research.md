# Investigación de mercado: sistemas de turnos y gestión odontológica

- **Proyecto**: sistema de turnos odontológicos (proyecto académico en equipo, entrega 25/11/2026).
- **Fecha de compilación**: 2026-11-22.
- **Base**: 21 notas de competidores en `discovery/sources/` (8 argentinos, 7 latinoamericanos, 6 internacionales). No se realizó investigación web nueva; todo dato ausente de las notas figura como "No evidenciado" (NE).

**Convenciones de lectura**

| Símbolo | Significado |
|---|---|
| ✔ | Comprobado: visto en texto oficial, ayuda, tarifa o demo del proveedor (marcado `[C]` o "comprobado" en la nota). |
| ◐ | Afirmación comercial: lo dice el proveedor en marketing, sin demo, documentación técnica ni verificación independiente. |
| ✘ / NE | No evidenciado en las fuentes consultadas. No equivale a "no existe". |

Advertencia general: ningún dato de este informe proviene de una demo propia ni de una auditoría independiente. "Comprobado" significa "presente en el material oficial del proveedor", no "verificado en uso".

---

## A. Tabla comparativa

**Criterio de orden (relevancia para Argentina)**: primero los sistemas odontológicos con operación o adaptación argentina evidenciada (DentalCore, DentalSoft, Dentalink, AgendaPro, Odonthia, ClinIA, DentalTec, Odonto.ar), luego los que se usan en Argentina pero no son odontológicos (Doctoralia Pro, Turnito), luego DentiClick, después los latinoamericanos sin operación argentina evidenciada y, al final, los internacionales sin disponibilidad evidenciada en el país.

Abreviaturas: AR Argentina, CL Chile, BR Brasil, ES España, UK Reino Unido, US EE.UU. OS = obras sociales. MP = Mercado Pago. WA = WhatsApp. GCal = Google Calendar.

| # | Sistema | Empresa | País | Segmento | Modalidad | Agenda | Turnos digitales | Automatización | Clínica | Administración | Integraciones locales | Seguridad | Modelo comercial | Precio |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | DentalCore | DentalCore (La Plata) | AR | Consultorio a clínica (hasta 100 usuarios) | ◐ SaaS web + app Windows | ◐ multi-doctor, bloqueos, kanban; sillones NE | ◐ confirmar/cancelar por link; reserva autogestionada ✘ | ◐ WA/email/SMS a 48 y 24 h; recuperación de planificados | ◐ periodontograma, 22 consentimientos, odontograma 50+ hallazgos | ◐ caja, ARCA A/B/C, cobro MP, liquidación OS | ◐ ARCA, MP, WA Meta, FHIR; API pública ✘ | ◐ TLS, AES-256 en backups, 2FA, log de accesos; sin ISO 27001 (lo aclara) | Suscripción; prueba 1 semana; sin plan gratis | ARS 75.000 (2 usuarios) en home vs USD 50 en FAQ: inconsistente |
| 2 | DentalSoft | DentalSoft (La Plata/Rosario) | AR | Independiente y clínica chica | ◐ SaaS web | ◐ multi-prof., sobreturnos, bloqueos, GCal; sillones ✘ | ◐ reserva online 4 pasos (demo, desde Pro); lista de espera ✘ | ◐ WA (manual en Gestión, automático en Pro), reactivación | ◐ odontograma 18 estados, plan de tratamiento, presupuesto con cobertura OS | ◐ caja, MP, señas, liquidación OS y comisiones; ARCA ✘ | ◐ GCal, WA, MP, Excel/CSV | ✘ cifrado y leyes NE; backup/exportación en Gestión | Suscripción; gratis 3 meses (50 turnos/mes) | ARS 30.000 / 60.000 por mes; WA automático USD 0,026/mensaje (Meta) |
| 3 | Dentalink | Engenis SpA | CL (módulo AR) | Independiente y clínicas | ✔ SaaS (AWS) | ✔ agenda con confirmaciones; reprogramación automática (Titanium); lista de espera ✘ | ✔ agendamiento online y por WA (Esencial); IA ◐ | ✔ recordatorios WA, campañas email, seguimiento de presupuestos (Titanium) | ✔ ficha, odontograma, presupuestos, plan, recetas; periodontograma ✘ | ✔ pagos, convenios, reembolsos (SII Chile); ARCA ✘ | ◐ módulo AR: solo títulos de ayuda (OS, conciliación, receta electrónica); MP ✘ | ◐ ISO 27001 y AWS declarados; Leyes 25.326/26.529 ✘ | Planes Esencial/Pro/Titanium por cotización | No publicado (Capterra "desde USD 29": tercero, no verificado) |
| 4 | AgendaPro | AgendaPro Inc. | NE (opera en AR) | Multivertical, con landing dental | ✔ SaaS | ✔ agenda por profesional, reportes por sede; sobreturnos y lista de espera ✘ | ✔ reserva 24/7 desde web, Google, IG, FB, WA; confirmar/cancelar con un clic | ✔ recordatorios WA y email; "hasta 90% menos faltas" ◐ | ◐ ficha, odontograma, radiografías, firmas (landing); periodontograma ✘ | ✔ seña/pago total, MP, QR, caja; ARCA y OS ✘ | ✔ MP, Google, IG, FB, WA | ◐ ficha cifrada, AWS, declara Leyes 25.326/26.529 | Suscripción; prueba 14 días; sin plan gratis | Desde ARS 13.900/mes |
| 5 | Odonthia | Genera Impacto | AR | Independiente y consultorio chico | ◐ SaaS web | ✘ vistas y sillones NE; agenda en plan gratis | ◐ reserva online (sin detalle) | ◐ recordatorio manual gratis; WA automático en planes pagos | ◐ odontograma FDI, consentimientos con firma electrónica, IA | ◐ presupuestos y caja sin detalle; admite no tener OS, ARCA ni receta | ✘ ARCA y MP NE; ◐ importación CSV | ◐ registro de actividad, aislamiento por consultorio; leyes ✘ | Freemium + IA por créditos | Gestión gratis; IA ARS 78.700; Max ARS 162.500 (octubre: 74.500 y 153.700) |
| 6 | ClinIA / OdontoClinIA | Emprinet | AR | Instituciones públicas y privadas | ◐ web con instancia por cliente | ◐ por profesional/consultorio/sillón; bloqueos ✘ | ◐ turnero web, app y asistente WA con IA | ◐ recordatorios WA y correo | ◐ odontograma FDI 52 piezas, plan con nomenclador, IA | ◐ caja con arqueo, multimoneda, factura ARCA, cobertura PUCO | ✔ ReNaPDiS N.º 248 (verificable); ◐ ARCA, PUCO, RENAPER, FHIR | ✘ cifrado y leyes NE; backups cada 10 min (afirmado) | Por cotización institucional; demo de 45 min | No publicado |
| 7 | DentalTec | DentalTec (Tándem Digital) | AR | Odontólogos de círculos e instituciones | ◐ web | ✘ vistas y sillones NE | ✘ reserva online NE; ◐ recordatorios WA | ◐ recordatorios WA (cifras sin fuente) | ◐ historia, odontograma con CIE-10 | ◐ validación OS en línea, 15+ OS, planillas hacia ARCA | ◐ ARCA, OS por SOAP, WA | ✘ NE (10 roles y "trazabilidad 100%" sin detalle) | Por cotización; demo a pedido | No publicado |
| 8 | Odonto.ar | No identificada | AR (inferido) | Individual a clínica de 10 usuarios | ◐ SaaS web | ◐ día/semana/mes, agenda compartida | ◐ reserva por link con reglas de cancelación (demo) | ✘ WA y recordatorios NE | ◐ odontograma con trazabilidad por diente, presupuestos | ◐ cálculo parte OS/paciente; liquidación, ARCA, MP ✘ | ✘ NE | ✘ NE | Suscripción; prueba 30 días | USD 29 / 49 / 99 (pago en ARS por MP) |
| 9 | Doctoralia Pro | Docplanner | NE (opera en AR) | Profesional independiente (no odontológico) | ✔ SaaS + app | ✔ agenda online configurable; vistas y sobreturnos ✘ | ✔ reserva desde perfil, web, Google y redes; WA confirmar/cancelar/reprogramar | ✔ recordatorios y confirmaciones; campañas | ✘ solo "episodios clínicos"; odontograma ✘ | ✘ facturación, caja, ARCA y MP NE | ✔ filtro de OS argentinas en directorio; WA | ◐ AWS, "GDPR"; ley argentina ✘ | Suscripción anual + marketplace | ARS no publicado; referencia ES 89 a 129 EUR (no vale para AR) |
| 10 | Turnito | Turnito | AR | Generalista (sin odontología) | ◐ SaaS web | ◐ servicios, GCal bidireccional, Meet; solapamiento explícito ✘ | ◐ reserva 24/7 sin cuenta, seña o pago total | ◐ recordatorios desde plan Plus (WA con tope mensual) | ✘ | ◐ cobros MP/Talo/PayPal con comisión; ARCA y OS ✘ | ◐ MP, Talo, GCal, WA | ✘ NE | Freemium con comisión por cobro | Gratis; ARS 9.900 / 20.300 / 34.800 + IVA |
| 11 | DentiClick | No identificada | AR/VE | Individual a multisucursal | ◐ SaaS web (PWA) | ◐ vistas día/semana/mes; sillones ✘ | ◐ reserva 24/7 en subdominio de la clínica | ✘ WA NE; ◐ chat IA | ◐ odontograma, Rx con visor 3D, presupuestos | ◐ cuenta corriente, liquidación OS CSV/PDF, inventario; ARCA y MP ✘ | ✘ NE | ✘ NE; backup semanal por email | Todas las funciones por plan; prueba 1 mes | USD 0 / 12 / 29 / 35 |
| 12 | Clinicorp | Clinicorp | BR | Consultorio a red | ◐ SaaS | ✔ informes de ocupación, check-in por QR; sillones ✘ | ✔ agendamiento online (Google); IA ◐ | ✔ confirmación y follow-up por WA, retorno | ✔ prontuario, planes/orçamento, firma digital; odontograma ✘ en home | ✔ Clinipay (Pix, boleto, notas fiscais), todo de BR | ✘ AR NE | ✘ NE | Suscripción; demo, sin prueba | R$ 149,90 / 369,90 |
| 13 | Simples Dental | Simples Dental | BR | Consultorio y clínica | ✔ SaaS + app paciente | ✔ link de reserva, encaixes, voz | ✔ confirmación WA (paga aparte); secretaria IA (pre-lanzamiento) | ✔ campañas, CRM de presupuestos (Pro) | ✔ odontograma, evoluciones, recetas, contratos; periodontograma ✘ | ✔ cobros, Pix, NF, caja (BR) | ✘ AR NE | ✘ NE | Planes + extras por uso; prueba 7 días | R$ 149,90 / 249,90 / 349,90 |
| 14 | Dentidesk | Dentidesk | CL | Clínicas, facultades | ◐ SaaS | ✔ agendar, reprogramar; sillones ✘ | ✔ agendamiento online (sin detalle); WA "próximamente" | ✘ WA ausente | ✔ odontograma con nomenclaturas; fichas; periodontograma ✘ | ✔ boletas SII, Alegra (DR) | ✘ AR NE | ✘ sin medidas técnicas | Por cotización; prueba 15 días | No publicado |
| 15 | Medilink | Engenis SpA | CL | Centros de salud (sin odontología) | ◐ SaaS | ✔ agenda médica | ✔ agendamiento online, contact center WA | ✔ confirmación por email/WA | ✘ odontograma NE | ✔ boletas electrónicas, caja (CL) | ✘ AR NE; ERPs chilenos | ✔ enlaces a DPA y política (sin detalle) | Sin permanencia; asesoría | No publicado |
| 16 | Encuadrado | Encuadrado | CL | Profesionales independientes (sin odontología) | ✔ SaaS móvil | ✔ horarios, repetición, bloqueos | ✔ reserva en perfil | ✔ recordatorios WA y email | ✘ | ✔ transferencia, tarjeta, boleta CL; comisión 1% en Esencial | ✘ AR NE | ✘ NE | Suscripción en UF | CLP 26.107 / 43.511 / 141.218 + IVA |
| 17 | Open Dental | Open Dental Software | US (inferido) | Consultorio a cadena | ✔ instalado; Cloud con cargo | ✔ bloqueos, lista ASAP | ✔ Web Sched en 4 modos, portal | ✔ eReminders/eConfirmations, texting | ✔ tooth chart 2D/3D; periodontograma y Rx no detallados | ✔ facturación, estados de cuenta, Payment Portal (US) | ✘ AR NE (tarifa "otros países") | ✘ NE | Suscripción por local; eServices modulares | USD 89 "otros países" + módulos; gratis en países en desarrollo |
| 18 | Dentally | Henry Schein One UK | UK | Consultorio y grupo | ✔ SaaS | ✔ diario, lista de espera, relleno de último momento | ✔ Portal: reserva, formularios, kiosco | ✔ recordatorios, recalls, feedback | ◐ charting, notas con IA; odontograma/periodontograma no descritos | ✔ pagos vía Portal (sin detalle) | ✘ AR NE; WA ✘ | ✔ cifrado, 2FA, roles, IP/horario; GDPR y backups NE | Plan por gabinete (público) | GBP 134 a 1.021 por 3 gabinetes |
| 19 | NexHealth | NexHealth | US | Consultorios (capa sobre otro sistema) | ◐ SaaS | ✔ sincroniza con el sistema existente | ✔ reserva desde Google Maps, lista ASAP | ✔ recalls, recordatorios, campañas | ✘ no es sistema clínico | ✔ pagos por texto, verificación de seguros (US) | ✘ AR NE; Health Records API | ◐ HIPAA declarado; SOC 2 NE | Módulos a medida | No publicado |
| 20 | Curve Dental | CD Newco | US | Consultorios US/CA | ✔ nube desde 2004 | ✔ agenda, recare (listado oficial) | ✔ reserva online (sin detalle del flujo) | ✔ recordatorios, campañas | ✔ perio charting, planes, imágenes | ✔ eClaims, pagos, membresías (US) | ✘ AR NE | ◐ "HIPAA-compliant" sin certificaciones | A medida; solo demo | No publicado |
| 21 | Gesden | Infomed / Henry Schein | ES | Clínica dental | ◐ G5 escritorio / ONE nube (terceros) | ◐ multigabinete (terceros) | ✘ NE | ✘ NE | ◐ odontograma y periodontograma (terceros) | ◐ facturación VeriFactu (terceros) | ✘ AR NE | ✘ NE | G5 y ONE por usuarios concurrentes | ONE desde 75 EUR + IVA (tercero, sin verificar) |

### Fortalezas, limitaciones y diferencial por sistema

1. **DentalCore**. Fortaleza: la cobertura declarada más amplia (periodontograma, ARCA, MP, liquidación OS, FHIR, auditoría de accesos). Limitación: todo es afirmación; reserva autogestionada no demostrada y precio inconsistente entre secciones. Diferencial: transparencia de seguridad (aclara que no tiene ISO 27001) y migración asistida gratuita.
2. **DentalSoft**. Fortaleza: reserva online de 4 pasos con filtro por especialidad y OS, liquidación por OS, precio en ARS con plan inicial gratuito. Limitación: ARCA no evidenciada, importa solo pacientes (no historias ni turnos) y el WhatsApp automático se cobra aparte. Diferencial: cobertura de OS aplicada al presupuesto y soporte directo con el equipo de desarrollo.
3. **Dentalink**. Fortaleza: único con evidencia (solo títulos de ayuda) de módulo argentino de OS y receta electrónica; funciones de agenda y recordatorios comprobadas. Limitación: ARCA y MP no evidenciados, precios no publicados, contenido de los artículos argentinos no leído. Diferencial: ISO 27001 declarada y planes escalonados con ortodoncia y estética.
4. **AgendaPro**. Fortaleza: lo más comprobado en reserva digital y cobro (MP, seña, QR, WA, redes) con precio de entrada bajo. Limitación: profundidad clínica solo en landing; ARCA y OS sin evidencia. Diferencial: reserva desde Google e Instagram y asistente IA en WhatsApp.
5. **Odonthia**. Fortaleza: plan gratuito permanente, consentimientos con firma electrónica y registro de actividad. Limitación: admite no tener OS, ARCA, validación en línea ni receta electrónica; "4.718 registrados" son cuentas creadas, no uso. Diferencial: IA por créditos y modelo freemium.
6. **ClinIA / OdontoClinIA**. Fortaleza: integraciones estatales argentinas (ARCA, PUCO, RENAPER) y receta ReNaPDiS verificable. Limitación: orientado a instituciones, sin precio ni prueba libre, sin clientes ni métricas. Diferencial: modelo sin costo por usuario e IA sobre la historia.
7. **DentalTec**. Fortaleza: validación de OS en tiempo real y canal institucional (círculos, CORA). Limitación: reserva online, MP y caja no evidenciados; "2.000 profesionales" sin casos. Diferencial: flujo Círculo, Federación, CORA.
8. **Odonto.ar**. Fortaleza: reserva por link configurable mostrada en demo; odontograma con registro de quién modificó cada diente. Limitación: empresa y país sin declarar, sin WhatsApp, sin liquidación ni ARCA. Diferencial: pago en pesos por MP sin renovación automática.
9. **Doctoralia Pro**. Fortaleza: demanda de pacientes del marketplace, filtros por OS, WA para confirmar/cancelar/reprogramar. Limitación: no es software odontológico; sin facturación ni administración evidenciadas; precio no publicado. Diferencial: captación de pacientes (afirmación comercial).
10. **Turnito**. Fortaleza: reserva sin cuenta con seña por MP/Talo y plan gratuito. Limitación: sin clínica ni odontología, cobra comisión; "N° 1" y "40.000 usuarios" son afirmaciones. Diferencial: fricción mínima para el paciente.
11. **DentiClick**. Fortaleza: precio muy bajo con todas las funciones, inventario con descuento automático. Limitación: empresa no identificada, evidencia mínima, recordatorios WA no evidenciados. Diferencial: página web de la clínica con reserva 24/7.
12. **Clinicorp**. Fortaleza: financiero integrado (Clinipay) y flujo de presupuestos con IA. Limitación: todo específico de Brasil, en portugués, sin operación argentina. Diferencial: integración con centros de radiología.
13. **Simples Dental**. Fortaleza: prontuario y odontograma maduros, app para el paciente. Limitación: cobros e integraciones brasileños; WhatsApp se cobra aparte. Diferencial: modelo de extras por uso.
14. **Dentidesk**. Fortaleza: nomenclaturas de odontograma configurables. Limitación: sin WhatsApp, sin presencia argentina, sin medidas de seguridad detalladas. Diferencial: foco en instituciones académicas.
15. **Medilink**. Fortaleza: contact center por WhatsApp que agenda. Limitación: no cubre odontología ni Argentina. Diferencial: integraciones con ERPs chilenos.
16. **Encuadrado**. Fortaleza: reserva y cobro simples para profesional independiente. Limitación: sin odontología, sin Argentina, facturación chilena. Diferencial: precios atados a UF.
17. **Open Dental**. Fortaleza: Web Sched con cuatro entradas, lista ASAP, API REST y FHIR, tarifa internacional publicada. Limitación: sin adaptación local, español ni seguridad evidenciados; eServices cuestan aparte. Diferencial: precio por local (no por usuario) y módulos con precio visible.
18. **Dentally**. Fortaleza: portal del paciente, lista de espera y relleno de último momento, seguridad con permisos por rol, IP y horario. Limitación: orientado al NHS, precio alto, sin WhatsApp. Diferencial: precio por gabinete con slider.
19. **NexHealth**. Fortaleza: lista de espera como recupero de ingresos, reserva desde Google Maps, Health Records API. Limitación: depende de sistemas y pagos de EE.UU.; no tiene clínica. Diferencial: capa del paciente sobre el sistema existente.
20. **Curve Dental**. Fortaleza: alcance integral y registro único por paciente. Limitación: seguros y HIPAA de EE.UU., precio no publicado, detalle solo de listado. Diferencial: nube nativa desde 2004.
21. **Gesden**. Fortaleza: profundidad clínica según terceros. Limitación: sin fuente oficial, sin evidencia de reserva ni recordatorios, normativa española. Diferencial: modelo dual escritorio/nube (G5/ONE), dato de tercero.

---

## B. Matriz de puntuación (0 a 5)

### Regla de puntuación

Cada criterio se puntúa según la evidencia de la nota, no según el discurso comercial:

| Puntaje | Criterio |
|---|---|
| 0 | No evidenciado. |
| 1 | Indicio mínimo o indirecto (mención sin detalle, solo un título de ayuda, o función sin relación con el caso argentino). |
| 2 | Cobertura parcial, o afirmación comercial acotada. |
| 3 | Cobertura amplia **solo como afirmación comercial** (techo para todo dato no comprobado), o cobertura comprobada pero parcial. |
| 4 | Cobertura amplia **comprobada en fuente oficial** (texto, tarifa, ayuda o demo del proveedor). |
| 5 | Reservado para cobertura amplia, comprobada y verificada por una fuente independiente o una demo propia. **Ningún sistema lo alcanza**, porque este estudio no incluyó demos ni auditorías. |

Reglas adicionales:

- Una afirmación comercial nunca supera 3. Los datos de terceros (Gesden) no superan 2.
- Un dato comprobado pero propio de otro país (SII, Pix, HIPAA, eClaims) puntúa en administración o seguridad, pero **no** en "Integraciones locales", que mide ARCA, Mercado Pago, obras sociales, WhatsApp y receta electrónica en Argentina.
- "Precio y adopción" combina precio publicado, accesibilidad para un independiente y prueba o plan gratuito. Las cifras de adopción propias no suman.
- Pesos: Turnos y automatización 25%, Clínica 20%, Integraciones locales y WhatsApp 15%, Administración, cobros y facturación 15%, Experiencia del paciente 10%, Seguridad, exportación y trazabilidad 10%, Precio y adopción 5%. Ponderado = suma de puntaje por peso. Los empates se ordenan alfabéticamente.
- Los puntajes son juicio del analista sobre notas de marketing; sirven para comparar entre sistemas, no como medida absoluta de calidad. Un puntaje alto por funciones comprobadas (por ejemplo AgendaPro) no implica que cubra obras sociales o ARCA: esos vacíos están reflejados en sus columnas IL y AD.

### Matriz (ordenada por puntaje ponderado)

Columnas: TA Turnos y automatización (25%), CL Clínica (20%), IL Integraciones locales y WhatsApp (15%), AD Administración, cobros y facturación (15%), UX Experiencia del paciente (10%), SE Seguridad, exportación y trazabilidad (10%), PA Precio y adopción (5%).

| Pos. | Sistema | TA | CL | IL | AD | UX | SE | PA | Ponderado |
|---|---|---|---|---|---|---|---|---|---|
| 1 | AgendaPro | 4 | 2 | 3 | 3 | 4 | 2 | 4 | **3.10** |
| 2 | DentalCore | 3 | 3 | 3 | 3 | 2 | 3 | 3 | **2.90** |
| 3 | DentalSoft | 3 | 3 | 3 | 3 | 3 | 1 | 4 | **2.85** |
| 4 | ClinIA / OdontoClinIA | 3 | 3 | 4 | 3 | 2 | 1 | 2 | **2.80** |
| 5 | Open Dental | 4 | 3 | 1 | 3 | 3 | 0 | 3 | **2.65** |
| 6 | Dentalink | 3 | 3 | 2 | 2 | 3 | 3 | 1 | **2.60** |
| 7 | Simples Dental | 3 | 4 | 1 | 3 | 3 | 0 | 3 | **2.60** |
| 8 | Dentally | 4 | 2 | 1 | 1 | 4 | 3 | 2 | **2.50** |
| 9 | Curve Dental | 3 | 4 | 0 | 3 | 2 | 2 | 1 | **2.45** |
| 10 | Clinicorp | 3 | 3 | 1 | 3 | 3 | 0 | 3 | **2.40** |
| 11 | Odonto.ar | 3 | 3 | 1 | 2 | 3 | 1 | 4 | **2.40** |
| 12 | NexHealth | 4 | 0 | 1 | 3 | 4 | 2 | 1 | **2.25** |
| 13 | DentalTec | 2 | 2 | 3 | 3 | 1 | 1 | 1 | **2.05** |
| 14 | DentiClick | 2 | 3 | 1 | 2 | 2 | 1 | 4 | **2.05** |
| 15 | Odonthia | 2 | 3 | 1 | 1 | 2 | 2 | 4 | **2.00** |
| 16 | Turnito | 3 | 0 | 3 | 2 | 3 | 0 | 4 | **2.00** |
| 17 | Doctoralia Pro | 3 | 1 | 2 | 0 | 4 | 1 | 2 | **1.85** |
| 18 | Encuadrado | 3 | 0 | 1 | 3 | 3 | 0 | 2 | **1.75** |
| 19 | Medilink | 3 | 0 | 1 | 2 | 2 | 1 | 1 | **1.55** |
| 20 | Dentidesk | 2 | 2 | 0 | 1 | 1 | 1 | 2 | **1.35** |
| 21 | Gesden | 1 | 2 | 0 | 1 | 0 | 0 | 1 | **0.85** |

Lecturas críticas de la matriz:

- **Ningún sistema supera 3,10 de 5**: refleja la ausencia de evidencia verificable, no necesariamente mala calidad.
- **AgendaPro lidera** porque su reserva digital y sus cobros están comprobados en texto oficial, aunque su clínica es débil (2) y no cubre ARCA ni OS.
- **Los sistemas argentinos dentales (DentalCore, DentalSoft, ClinIA)** quedan cerca en 2,80 a 2,90 apoyados en afirmaciones: sus puntajes caerían si una demo no confirma lo declarado.
- **Open Dental y Simples Dental** puntúan por funciones comprobadas, pero su relevancia local es nula (IL = 1): son benchmark funcional, no competencia directa.
- **Doctoralia Pro y Turnito** puntúan bajo en total porque no son sistemas de gestión; su valor está en la experiencia del paciente (UX 4 y 3).

---

## C. Análisis competitivo

### C.1 Funcionalidades que ya son estándar de mercado

Se consideran estándar las funciones presentes (comprobadas o declaradas) en la mayoría de los sistemas que compiten en el segmento odontológico o de turnos:

- **Agenda por profesional** con vista diaria y semanal, y reprogramación.
- **Recordatorios y confirmación automáticos** por WhatsApp y/o email, con confirmar/cancelar por link o respuesta. Presentes en casi todos (AgendaPro, Dentalink, Doctoralia, Clinicorp, Simples, Open Dental, Dentally, NexHealth, DentalSoft, ClinIA y otros). Excepciones: Dentidesk (WA "próximamente"), Odonto.ar y DentiClick (NE).
- **Reserva online del paciente** con link o página propia (AgendaPro, Doctoralia, Turnito, Dentalink, Open Dental, Dentally, NexHealth, Odonto.ar, DentiClick, DentalSoft). Es estándar como oferta, aunque pocas veces se demuestra el flujo.
- **Odontograma digital** y presupuestos en todos los sistemas odontológicos (Gesden y varios más solo por terceros o landing).
- **Planes de precio escalonados** por profesional o por funciones, con prueba de 7 a 30 días en la mayoría.
- **Cobro anticipado o seña** con medio de pago local en los sistemas generalistas (Mercado Pago en AgendaPro y Turnito; Pix en BR; PayConnect y similares en US).
- **Mención de seguridad** (backup, cifrado, nube). Como afirmación es estándar; como evidencia técnica es escasa.

Matiz: "estándar" aquí significa "presente en el discurso comercial", no "verificado". La mayoría de las funciones no pasó por demo.

### C.2 Diferenciadores reales

Diferenciadores con respaldo comprobado (texto oficial o verificable):

- **Receta electrónica con registro ReNaPDiS verificable** (ClinIA, N.º 248; el sitio aclara que no equivale a aprobación general).
- **Web Sched de Open Dental con cuatro puertas de entrada** (existente, nuevo, recall, ASAP) y lista ASAP comprobada.
- **Lista de espera y relleno de último momento** (Dentally, NexHealth, Open Dental): comprobado en el exterior, no evidenciado en ningún sistema argentino.
- **Reserva desde Google Maps / Google Business** (NexHealth, Clinicorp, AgendaPro, Doctoralia).
- **Filtro por obra social en el directorio de pacientes** (Doctoralia): comprobado.
- **Plan gratuito permanente** (Odonthia, Turnito, DentiClick) y **precio público en ARS** (AgendaPro, DentalSoft, Turnito).
- **Arquitectura "capa del paciente" con API** (NexHealth) y API REST oficial (Open Dental).
- **Seguridad detallada y verificable en su declaración** (Dentally: 2FA, roles, restricción por IP y horario).

Diferenciadores solo declarados (requieren demo): validación de OS en línea (DentalTec), módulo argentino de OS (Dentalink, solo títulos), liquidación por OS (DentalSoft, DentalCore), factura electrónica ARCA integrada (DentalCore, ClinIA), auditoría de accesos y exportación FHIR (DentalCore), asistentes de IA en WhatsApp (varios).

### C.3 Vacíos frecuentes del mercado argentino

| Vacío | Evidencia en las notas |
|---|---|
| **Factura electrónica ARCA integrada** | Solo declarada por DentalCore y ClinIA, y planillas hacia ARCA en DentalTec; NE en DentalSoft, Dentalink, AgendaPro, Odonto.ar, DentiClick, Odonthia, Turnito, Doctoralia. Odonthia lo admite expresamente. |
| **Reserva autogestionada demostrada** | DentalCore (el líder en cobertura declarada) no la evidencia; DentalTec tampoco; Odonthia sin detalle. Solo Dentalink, AgendaPro, Doctoralia y Dentally/Open Dental la muestran en texto oficial. |
| **Lista de espera y relleno de cancelaciones** | NE en todos los sistemas argentinos y latinoamericanos relevados; comprobada solo en Dentally, NexHealth y Open Dental. |
| **Sillones o boxes como recurso de agenda** | NE en casi todos los sistemas (solo ClinIA lo declara). |
| **Normativa 25.326 (datos personales) y 26.529 (derechos del paciente e historia clínica)** | Citada solo por DentalCore y AgendaPro (afirmación); NE en DentalSoft, DentalTec, Dentalink, Odonto.ar, Odonthia, DentiClick, Doctoralia, Turnito. ClinIA remite a una página no revisada. |
| **Seguridad verificable (certificaciones, auditoría, exportación)** | Predominan afirmaciones; DentalCore es el más detallado pero declarativo; sin ISO 27001 en ningún argentino. Dentalink la declara desde Chile. |
| **Exportación y portabilidad de datos** | DentalSoft importa solo pacientes; DentalCore ofrece PDF y FHIR con 90 días tras la baja (declarado); Gesden cobra 90 EUR por recuperar consentimientos (tercero). La mayoría: NE. |
| **WhatsApp sin costo oculto** | WhatsApp automático se cobra aparte (DentalSoft, Simples, DentalTec según tercero, Turnito con tope mensual); ningún sistema evidencia una alternativa gratuita. |
| **Agenda con prevención de solapamiento y sobreturnos explícitos** | Declarada solo por DentalSoft; NE en el resto. |
| **Evidencia de adopción y precios transparentes** | Adopción: solo cifras propias. Precios: no publicados en Dentalink, ClinIA, DentalTec, Doctoralia; inconsistentes en DentalCore y Odonthia. |

### C.4 Oportunidades de innovación

1. **Reserva autogestionada completa y verificable** con prevención de solapamientos, sobreturnos y bloqueos, mostrada en demo pública (hoy es el vacío más claro en los líderes declarados).
2. **Lista de espera con aviso automático al liberarse un turno**, pasando de cancelación a reubicación. Comprobada solo en el exterior (Dentally, NexHealth).
3. **Recordatorios por canales gratuitos** (email, enlace `wa.me` precargado de un clic, archivo `.ics`) como alternativa transparente al WhatsApp pago. El patrón "WhatsApp manual de un clic" aparece en el plan Gestión de DentalSoft.
4. **Cumplimiento visible de las Leyes 25.326 y 26.529**: consentimiento al reservar, política de datos, minimización, registro de accesos y exportación, presentado como funcionalidad, no como texto legal.
5. **Gestión de agenda por recurso (sillón o box)**, casi inexistente.
6. **Reserva con múltiples puertas** (paciente nuevo, existente, recall, urgencia/ASAP) al estilo de Open Dental.
7. **Precio transparente en ARS** con plan gratuito para el independiente, reduciendo la fricción de cotizaciones opacas.
8. **Capa de turnos interoperable** (API o exportación CSV/ICS) para convivir con sistemas clínicos existentes, siguiendo el modelo NexHealth. Hoy ningún sistema argentino evidencia API pública.

---

## D. Recomendación final

### Cinco competidores prioritarios para pedir demo

| # | Competidor | Por qué |
|---|---|---|
| 1 | **Dentalink** | Es el único con evidencia de módulo argentino de OS y receta electrónica, pero solo por títulos de ayuda. La demo permite abrir los cuatro artículos y confirmar ARCA y pagos locales. Candidato latinoamericano con más ficha técnica de adaptación. |
| 2 | **DentalCore** | Mayor cobertura declarada y mejor transparencia de seguridad. La demo debe aclarar la reserva autogestionada (no evidenciada), el precio real (ARS 75.000 vs USD 50) y el alcance de ARCA. Prueba de 1 semana por WhatsApp. |
| 3 | **AgendaPro** | Competidor más directo en turnos digitales y cobro con MP, con precio de entrada bajo y prueba de 14 días. Permite validar sobreturnos, lista de espera y profundidad clínica dental, hoy sin evidencia. |
| 4 | **DentalSoft** | Más cerca del segmento del independiente: reserva de 4 pasos, plan gratuito de 3 meses y demo sin tarjeta con datos de ejemplo. Verificar WhatsApp automático, liquidación OS y por qué la prueba del sitio usa `javascript:void(0)`. |
| 5 | **ClinIA / OdontoClinIA** | Demo guiada gratuita de 45 minutos. Único con receta ReNaPDiS verificable e integraciones PUCO/RENAPER/ARCA. Orientado a instituciones, útil para comprender el techo normativo y de integración argentino. Alternativa si no responde: DentalTec (validación de OS en línea, demo a pedido). |

Fuera de la lista pese a su puntaje: Open Dental y Simples Dental (benchmark funcional sin presencia local) y Odonthia (puede evaluarse sin demo por tener plan gratuito sin tarjeta).

### Tres referentes de UX (patrón a tomar de cada uno)

1. **Open Dental (Web Sched y eServices)**. Patrón: cuatro puertas de entrada a la reserva (paciente existente, nuevo, recall, ASAP), enlaces por SMS o email y módulos con precio visible. Aplicable a: flujo de reserva por tipo de paciente y reutilización del link de recordatorio como acceso directo a reprogramar. Evidencia: comprobada en texto oficial.
2. **Doctoralia Pro**. Patrón: búsqueda por especialidad, zona, obra social y fecha disponible; reserva desde perfil, Google y redes; confirmar, cancelar o reprogramar desde el mensaje de WhatsApp. Aplicable a: página pública del consultorio con disponibilidad visible y gestión del turno sin entrar a una cuenta. Evidencia: comprobada en texto oficial.
3. **Turnito**. Patrón: reserva sin crear cuenta, horario bloqueado al acreditarse la seña, enlace compartible por WhatsApp o Instagram. Aplicable a: reserva en pocos pasos sin registro. Evidencia: afirmación comercial, por lo que conviene validar con prueba propia. Complemento: NexHealth (recall en un clic y lista de espera como ingreso recuperado).

### D.1 MVP de un producto comercial competitivo en Argentina

**Imprescindibles (sin ellos no se compite):**

- Agenda multiprofesional con vistas día y semana, bloqueos, sobreturnos y prevención de solapamientos, con sillón o box como recurso.
- Reserva online autogestionada por link, con paciente nuevo y existente, sin cuenta obligatoria.
- Recordatorios y confirmación por WhatsApp oficial y email, con confirmar, cancelar y reprogramar desde el mensaje.
- Paciente con datos mínimos, cobertura (obra social o particular) y consentimiento de tratamiento de datos.
- Seña o cobro anticipado con Mercado Pago.
- Obras sociales: carga de cobertura y verificación (PUCO) como mínimo viable.
- Seguridad básica verificable: roles, registro de accesos, backups, exportación de datos y cumplimiento de las Leyes 25.326 y 26.529.
- Precio público en ARS y prueba o plan gratuito.

**Diferenciadores (para destacar):**

- Lista de espera con aviso automático al liberarse un turno.
- Recall de controles periódicos y recuperación de tratamientos no agendados.
- Bandeja de WhatsApp compartida y asistente de reserva.
- Facturación electrónica ARCA integrada con la agenda.
- Reserva desde Google Business e Instagram.
- API pública y exportación estándar (CSV, FHIR).
- Panel de ocupación y ausentismo.

**Se posterga:**

- Ficha clínica completa, odontograma, periodontograma, imágenes e IA clínica.
- Liquidación completa a obras sociales y validación en línea.
- Receta electrónica ReNaPDiS.
- Inventario, comisiones, multisucursal avanzada y app móvil nativa.

### D.2 MVP ajustado a ESTE proyecto

**Restricciones**: equipo académico, entrega 25/11/2026 (quedan 3 días desde la fecha de las notas, 22/11/2026; el alcance debe ser mínimo y cerrable), solo turnos y agenda, sin presupuesto (sin WhatsApp Business API ni SMS), stack TypeScript vanilla en el front, Spring Boot con Maven, Hibernate y PostgreSQL, todo en Docker.

**Imprescindibles realistas para la fecha:**

- Login del odontólogo (un solo profesional) con contraseña con hash.
- ABM de pacientes con datos mínimos (nombre, DNI, teléfono, email) y casilla de consentimiento de datos personales.
- Configuración de horario de atención, duración por tipo de turno y bloqueos.
- Agenda con vista diaria y semanal.
- Alta, edición, cancelación y reprogramación de turnos con **prevención de solapamientos** garantizada en la base de datos (no solo en la interfaz).
- Estados del turno: pendiente, confirmado, cancelado, asistió, ausente.
- Página pública de reserva por link, sin cuenta, que muestra solo los horarios libres.
- Confirmación o cancelación por enlace con token en el correo del paciente.
- Recordatorio por email (programado, p. ej. 24 h antes).
- Despliegue con Docker Compose (API, base, front) y pruebas automatizadas del núcleo de reglas de agenda.

**Reemplazos gratuitos:**

| Función comercial | Alternativa gratuita |
|---|---|
| WhatsApp Business API / recordatorio automático | Email por SMTP gratuito; botón de enlace `wa.me` con mensaje precargado (un clic, manual) |
| SMS | Email |
| Google Calendar sincronizado | Descarga de archivo `.ics` |
| Seña con Mercado Pago | Campo de seña informativo y alias de transferencia en el correo; sin integración de pago |
| Panel de analítica | Listado filtrable y exportación CSV |
| Autenticación de pacientes | Token en el enlace del turno |

**Opcional si sobra tiempo:** lista de espera simple (alta y aviso manual) y registro de cambios de turno.

**Explícitamente fuera:**

- Ficha clínica, odontograma, periodontograma, imágenes e IA.
- Facturación ARCA, cobros, caja y Mercado Pago.
- Obras sociales (validación, liquidación y receta electrónica).
- WhatsApp API oficial, SMS y notificaciones push.
- Multiprofesional, multisucursal y sillones.
- App móvil nativa y portal del paciente con cuenta.
- Certificaciones de seguridad (solo buenas prácticas básicas).

**Nota normativa:** el sistema almacena datos personales (Ley 25.326), por lo que conviene mínimo de datos, consentimiento explícito y no registrar motivos clínicos detallados; así se evita entrar en el terreno de la historia clínica (Ley 26.529). Es criterio del equipo, no asesoramiento legal.

---

## Limitaciones de la investigación

- **Fuentes inaccesibles**: Capterra devolvió 403 en todos los intentos (Dentalink, Doctoralia), por lo que sus cifras de valoración y precio son de buscador o de terceros. Gesden: no se accedió al sitio oficial (`gesden.es` e `infomed.es` con ECONNREFUSED; `gesden.com` es otra empresa); toda su nota proviene de terceros. También fallaron `pro.doctoralia.com.ar` (DNS), `pro.doctoraliar.com` (502) y `nexhealth.com/security` (404).
- **Datos de terceros**: la comparativa de Odonthia sobre DentalCore, DentalSoft, DentalTec y Odonto.ar es de una fuente interesada; Pabau (competidor) es la fuente de Gesden y del precio español de Doctoralia; Medesk sirvió de snippet para AgendaPro (desde USD 19); iProUP para Turnito.
- **Inconsistencias de precios**: DentalCore (ARS 75.000 con 2 usuarios en la home vs USD 50 en la FAQ); Odonthia (octubre ARS 74.500 / 153.700 vs hoy 78.700 / 162.500); Dentalink (Capterra "desde USD 29" frente a precios no publicados); Doctoralia España (89 a 129 EUR según una fuente y 69/89/119 según otra; no vale para Argentina); Odonto.ar (equivalentes en ARS dependen del tipo de cambio); DentalSoft (demo de "1 minuto" vs plan gratis "unas horas").
- **Moneda y comparabilidad**: precios en ARS, USD, CLP, BRL, GBP y EUR sin conversión; no son comparables directamente.
- **Evidencia mayormente declarativa**: ninguna función se probó con demo; las capturas de DentalSoft y Odonto.ar incluyen datos de ejemplo. Las cifras de adopción (usuarios, reseñas, clientes) son propias y no verificadas. Las "comparativas de mejores sistemas" de DentalCore y DentalTec tienen sesgo.
- **Contenido no leído**: artículos del módulo argentino de Dentalink, páginas `/seguridad` de ClinIA y Odonthia, `/casos-de-exito` de DentalTec y subpáginas de funcionalidades de DentalSoft.
- **Datos de empresa no identificados**: Odonto.ar, DentiClick y el país de AgendaPro, Doctoralia, Open Dental y NexHealth son NE o inferidos.
- **Seguridad en el extracto de DentiClick**: la extracción detectó un texto embebido que pedía seguir una estructura de extracción; se trató como contenido de la página y no se siguió, según la nota.
- **Puntajes de la matriz**: son juicio del analista aplicando la regla descrita; otro analista podría diferir en ±1 por criterio.
- **Fecha de corte**: 2026-11-22; precios y funciones cambian con frecuencia.

---

## Fuentes

Índice de las notas en `discovery/sources/` (cada nota lista sus URL y fecha de consulta 2026-11-22):

**Argentinos (8)**
- [clinia-odontoclinia.md](sources/clinia-odontoclinia.md)
- [dentalcore.md](sources/dentalcore.md)
- [dentalsoft.md](sources/dentalsoft.md)
- [dentaltec.md](sources/dentaltec.md)
- [denticlick.md](sources/denticlick.md)
- [odonthia.md](sources/odonthia.md)
- [odonto-ar.md](sources/odonto-ar.md)
- [turnito.md](sources/turnito.md)

**Latinoamericanos (7)**
- [agendapro.md](sources/agendapro.md)
- [clinicorp.md](sources/clinicorp.md)
- [dentalink.md](sources/dentalink.md)
- [dentidesk.md](sources/dentidesk.md)
- [encuadrado.md](sources/encuadrado.md)
- [medilink.md](sources/medilink.md)
- [simples-dental.md](sources/simples-dental.md)

**Internacionales (6)**
- [curve-dental.md](sources/curve-dental.md)
- [dentally.md](sources/dentally.md)
- [doctoralia-pro.md](sources/doctoralia-pro.md)
- [gesden.md](sources/gesden.md)
- [nexhealth.md](sources/nexhealth.md)
- [open-dental.md](sources/open-dental.md)
