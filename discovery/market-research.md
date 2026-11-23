# Investigación de mercado: sistemas de turnos y gestión odontológica

- **Proyecto**: sistema de turnos odontológicos (proyecto académico en equipo, entrega 25/11/2026).
- **Fecha de compilación**: 2026-11-22. **Actualizado el 2026-11-23** con la verificación manual del equipo sobre 5 competidores (DentalCore, Dentalink, DentalSoft, ClinIA/OdontoClinIA y AgendaPro); los datos del equipo tienen prioridad absoluta sobre las notas originales.
- **Base**: 21 notas de competidores en `discovery/sources/` (8 argentinos, 7 latinoamericanos, 6 internacionales). No se realizó investigación web nueva; todo dato ausente de las notas figura como "No evidenciado" (NE).

**Convenciones de lectura**

| Símbolo | Significado |
|---|---|
| ★ | **Verificado por el equipo (2026-11-23)**: dato confirmado por el equipo con la página, un asesor comercial o una demo. Es el nivel de evidencia más alto. Se aplicó a 5 sistemas (ver Limitaciones). |
| ✔ | Comprobado: visto en texto oficial, ayuda, tarifa o demo del proveedor (marcado `[C]` o "comprobado" en la nota). |
| ◐ | Afirmación comercial: lo dice el proveedor en marketing, sin demo, documentación técnica ni verificación independiente. |
| ✘ / NE | No evidenciado en las fuentes consultadas. No equivale a "no existe". |
| ✖ | **Desmentido por el equipo**: el equipo comprobó que la función NO existe (la landing o la nota la mencionaba). Puntúa 0. |

Advertencia general: salvo los datos marcados ★ (verificación del equipo del 2026-11-23, solo 5 sistemas), ningún dato de este informe proviene de una demo propia ni de una auditoría independiente. "Comprobado" (✔) significa "presente en el material oficial del proveedor", no "verificado en uso". Los sistemas sin ★ conservan la evidencia del 2026-11-22.

---

## A. Tabla comparativa

**Criterio de orden (relevancia para Argentina)**: primero los sistemas odontológicos con operación o adaptación argentina evidenciada (DentalCore, DentalSoft, Dentalink, AgendaPro, Odonthia, ClinIA, DentalTec, Odonto.ar), luego los que se usan en Argentina pero no son odontológicos (Doctoralia Pro, Turnito), luego DentiClick, después los latinoamericanos sin operación argentina evidenciada y, al final, los internacionales sin disponibilidad evidenciada en el país.

Abreviaturas: AR Argentina, CL Chile, BR Brasil, ES España, UK Reino Unido, US EE.UU. OS = obras sociales. MP = Mercado Pago. WA = WhatsApp. GCal = Google Calendar.

| # | Sistema | Empresa | País | Segmento | Modalidad | Agenda | Turnos digitales | Automatización | Clínica | Administración | Integraciones locales | Seguridad | Modelo comercial | Precio |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | DentalCore | DentalCore (La Plata) | AR | Consultorio a clínica (hasta 100 usuarios) | ◐ SaaS web + app Windows | ◐ multi-doctor, bloqueos, kanban; sillones NE | ◐ confirmar/cancelar por link; reserva autogestionada ✘ | ◐ WA/email/SMS a 48 y 24 h; recuperación de planificados | ◐ periodontograma, 22 consentimientos, odontograma 50+ hallazgos | ◐ caja, ARCA A/B/C, cobro MP, liquidación OS | ◐ ARCA, MP, WA Meta, FHIR; API pública ✘ | ◐ TLS, AES-256 en backups, 2FA, log de accesos; ★ sigue controles de ISO/IEC 27001, sin certificación (único cambio: la redacción) | Suscripción; prueba 1 semana; sin plan gratis | ARS 75.000 (2 usuarios) en home vs USD 50 en FAQ: inconsistente |
| 2 | DentalSoft | DentalSoft (La Plata/Rosario) | AR | Independiente y clínica chica (hasta 10 profesionales) | ★ SaaS web | ★ multi-prof., sobreturnos con detección de solapamiento, bloqueos, GCal; sillones ✘ | ★ reserva online 4 pasos (desde Pro), asistente WA 24 h (Pro); lista de espera ✘ | ★ WA (manual en Gestión, automático en Pro), reactivación, cobro de deudas | ★ odontograma 18 estados, plan de tratamiento, presupuesto con cobertura OS; periodontograma y Rx ✘ | ★ caja, MP, señas, liquidación OS y comisiones, **integración con ARCA y facturación** | ★ ARCA, GCal, WA, MP, Excel/CSV | ✘ cifrado y leyes NE; ★ backup/exportación en Gestión | Suscripción; gratis 3 meses con limitaciones | ARS 30.000 (Gestión) / 60.000 (Pro) por mes con 1 profesional, +ARS 10.000 por profesional agregado (máx. 10); WA automático USD 0,026/mensaje (Meta) |
| 3 | Dentalink | Engenis SpA | CL (módulo AR) | Independiente y clínicas | ✔ SaaS (AWS) | ★ agenda con confirmaciones y reprogramación automática; lista de espera ✘ | ★ agendamiento online y por WA; IA ◐ (no verificada) | ★ recordatorios WA, campañas por email, seguimiento de presupuestos | ★ fichas, odontograma, presupuestos, planes, recetas; periodontograma ✖ (se muestra en la web pero ningún plan lo incluye) | ★ pagos, convenios, reembolsos (Chile); conciliación de pagos de OS (AR); ARCA no verificado | ★ módulo AR: OS (aranceles, copago, conciliación) y receta electrónica Decreto 345/2024; ARCA y MP no verificados | ◐ ISO 27001 y HIPAA afirmados, no verificados; Leyes 25.326/26.529 no verificadas | Planes Esencial/Pro/Titanium; cobro mensual, semestral o anual | ★ USD/mes: Esencial 35 (incl. 1 odontólogo, +10 c/u adicional), Pro 50 (+12), Titanium 75 (+15); administrativos gratis e ilimitados |
| 4 | AgendaPro | AgendaPro Inc. | NE (opera en AR) | Multivertical (no es sistema odontológico completo) | ★ SaaS | ★ agenda por profesional, reportes por sede; sobreturnos y lista de espera ✘ | ★ reserva 24/7 desde web, Google, IG, FB, WA; IA en WA; confirmar/cancelar con un clic | ★ recordatorios WA y email, seguimiento de presupuestos; "hasta 90% menos faltas" ◐ | ✖ sin ficha clínica, odontograma, radiografías, firmas ni periodontograma | ✖ sin seña o pago total, MP, QR, caja, ARCA ni OS | ★ WA, Google, IG, FB; MP ✖ | No verificado (ficha cifrada, AWS, Leyes 25.326/26.529) | Suscripción; prueba 7 días; sin plan gratis | ★ desde ARS 19.000/mes |
| 5 | Odonthia | Genera Impacto | AR | Independiente y consultorio chico | ◐ SaaS web | ✘ vistas y sillones NE; agenda en plan gratis | ◐ reserva online (sin detalle) | ◐ recordatorio manual gratis; WA automático en planes pagos | ◐ odontograma FDI, consentimientos con firma electrónica, IA | ◐ presupuestos y caja sin detalle; admite no tener OS, ARCA ni receta | ✘ ARCA y MP NE; ◐ importación CSV | ◐ registro de actividad, aislamiento por consultorio; leyes ✘ | Freemium + IA por créditos | Gestión gratis; IA ARS 78.700; Max ARS 162.500 (octubre: 74.500 y 153.700) |
| 6 | ClinIA / OdontoClinIA | Emprinet | AR | Instituciones públicas y privadas | ◐ web con instancia por cliente | ◐ por profesional/consultorio/sillón; bloqueos ✖ (no figuran en la landing, confirmado por el equipo) | ◐ turnero web, app y asistente WA con IA | ◐ recordatorios WA y correo | ◐ odontograma FDI 52 piezas, plan con nomenclador, IA | ◐ caja con arqueo, multimoneda, factura ARCA, cobertura PUCO | ✔ ReNaPDiS N.º 248 (verificable); ◐ ARCA, PUCO, RENAPER, FHIR | ✘ cifrado y leyes NE; backups cada 10 min (afirmado) | Por cotización institucional; demo de 45 min (el equipo no pudo probarla: exige ser empresa) | No publicado |
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

1. **DentalCore**. Fortaleza: la cobertura declarada más amplia (periodontograma, ARCA, MP, liquidación OS, FHIR, auditoría de accesos). Limitación: todo es afirmación (el equipo solo verificó la forma del dato de ISO); reserva autogestionada no demostrada y precio inconsistente entre secciones. Diferencial: transparencia de seguridad (sigue controles de ISO/IEC 27001, sin certificación) y migración asistida gratuita.
2. **DentalSoft**. Fortaleza: verificado por el equipo con demo: reserva online de 4 pasos con filtro por especialidad y OS, liquidación por OS, integración con ARCA y facturación, Mercado Pago y señas, precio en ARS con plan inicial gratuito de 3 meses. Limitación: importa solo pacientes (no historias ni turnos), el WhatsApp automático se cobra aparte, sin periodontograma ni lista de espera evidenciados y seguridad sin evidencia. Diferencial: cobertura de OS aplicada al presupuesto, ARCA integrada y soporte directo con el equipo de desarrollo.
3. **Dentalink**. Fortaleza: verificado por el equipo: módulo argentino de OS (aranceles, copago, conciliación) y receta electrónica (Decreto 345/2024), agenda con reprogramación automática, agendamiento online y por WA, campañas y seguimiento de presupuestos, y precios claros por odontólogo con administrativos ilimitados gratis. Limitación: ARCA, MP, ISO 27001, leyes argentinas e IA no verificados (demo inaccesible); periodontograma no incluido en ningún plan; sin lista de espera. Diferencial: módulo Argentina verificado y planes escalonados con ortodoncia y estética.
4. **AgendaPro**. Fortaleza (verificada por el equipo): reserva digital 24/7 desde web, Google, Instagram, Facebook y WhatsApp, recordatorios y precio de entrada bajo (desde ARS 19.000/mes, prueba de 7 días). Limitación: no es un sistema odontológico: sin ficha clínica, odontograma, radiografías ni firmas, sin seña o pago, Mercado Pago, caja, ARCA ni obras sociales (todo desmentido por el equipo). Diferencial: reserva desde Google e Instagram y asistente IA en WhatsApp.
5. **Odonthia**. Fortaleza: plan gratuito permanente, consentimientos con firma electrónica y registro de actividad. Limitación: admite no tener OS, ARCA, validación en línea ni receta electrónica; "4.718 registrados" son cuentas creadas, no uso. Diferencial: IA por créditos y modelo freemium.
6. **ClinIA / OdontoClinIA**. Fortaleza: integraciones estatales argentinas (ARCA, PUCO, RENAPER) y receta ReNaPDiS verificable. Limitación: orientado a instituciones, sin precio ni prueba libre (la demo exige ser empresa y el equipo no pudo probarla), sin clientes ni métricas. Diferencial: modelo sin costo por usuario e IA sobre la historia. El equipo confirmó que la nota coincide con la landing y quitó "bloqueos".
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
| 5 | Cobertura amplia, **verificada por el equipo (2026-11-23)** con la página, un asesor o una demo, con pocos vacíos. Es el único nivel que habilita el 5. |

Reglas adicionales:

- Una afirmación comercial nunca supera 3. Los datos de terceros (Gesden) no superan 2.
- **Ajuste del 2026-11-23**: lo verificado por el equipo (★) puede llegar a 5 (4 si hay vacíos relevantes en ese criterio); lo que el equipo **desmintió** (✖) vale 0 en ese aspecto; lo que el equipo no pudo verificar se trata como afirmación comercial (techo 3) o, si dependía de una función desmentida, baja a 1 o 0.
- Un dato comprobado pero propio de otro país (SII, Pix, HIPAA, eClaims) puntúa en administración o seguridad, pero **no** en "Integraciones locales", que mide ARCA, Mercado Pago, obras sociales, WhatsApp y receta electrónica en Argentina.
- "Precio y adopción" combina precio publicado, accesibilidad para un independiente y prueba o plan gratuito. Las cifras de adopción propias no suman.
- Pesos: Turnos y automatización 25%, Clínica 20%, Integraciones locales y WhatsApp 15%, Administración, cobros y facturación 15%, Experiencia del paciente 10%, Seguridad, exportación y trazabilidad 10%, Precio y adopción 5%. Ponderado = suma de puntaje por peso. Los empates se ordenan alfabéticamente.
- Los puntajes son juicio del analista sobre notas de marketing; sirven para comparar entre sistemas, no como medida absoluta de calidad. Un puntaje alto por funciones comprobadas no implica que cubra obras sociales o ARCA: esos vacíos están reflejados en las columnas IL y AD.

### Matriz (ordenada por puntaje ponderado)

Columnas: TA Turnos y automatización (25%), CL Clínica (20%), IL Integraciones locales y WhatsApp (15%), AD Administración, cobros y facturación (15%), UX Experiencia del paciente (10%), SE Seguridad, exportación y trazabilidad (10%), PA Precio y adopción (5%).

| Pos. | Sistema | TA | CL | IL | AD | UX | SE | PA | Ponderado |
|---|---|---|---|---|---|---|---|---|---|
| 1 | DentalSoft | 5 | 4 | 4 | 5 | 4 | 1 | 5 | **4.15** |
| 2 | Dentalink | 4 | 4 | 4 | 4 | 4 | 3 | 3 | **3.85** |
| 3 | DentalCore | 3 | 3 | 3 | 3 | 2 | 3 | 3 | **2.90** |
| 4 | ClinIA / OdontoClinIA | 3 | 3 | 4 | 3 | 2 | 1 | 2 | **2.80** |
| 5 | Open Dental | 4 | 3 | 1 | 3 | 3 | 0 | 3 | **2.65** |
| 6 | Simples Dental | 3 | 4 | 1 | 3 | 3 | 0 | 3 | **2.60** |
| 7 | Dentally | 4 | 2 | 1 | 1 | 4 | 3 | 2 | **2.50** |
| 8 | Curve Dental | 3 | 4 | 0 | 3 | 2 | 2 | 1 | **2.45** |
| 9 | Clinicorp | 3 | 3 | 1 | 3 | 3 | 0 | 3 | **2.40** |
| 10 | Odonto.ar | 3 | 3 | 1 | 2 | 3 | 1 | 4 | **2.40** |
| 11 | NexHealth | 4 | 0 | 1 | 3 | 4 | 2 | 1 | **2.25** |
| 12 | DentalTec | 2 | 2 | 3 | 3 | 1 | 1 | 1 | **2.05** |
| 13 | DentiClick | 2 | 3 | 1 | 2 | 2 | 1 | 4 | **2.05** |
| 14 | Odonthia | 2 | 3 | 1 | 1 | 2 | 2 | 4 | **2.00** |
| 15 | Turnito | 3 | 0 | 3 | 2 | 3 | 0 | 4 | **2.00** |
| 16 | AgendaPro | 4 | 0 | 1 | 0 | 4 | 1 | 4 | **1.85** |
| 17 | Doctoralia Pro | 3 | 1 | 2 | 0 | 4 | 1 | 2 | **1.85** |
| 18 | Encuadrado | 3 | 0 | 1 | 3 | 3 | 0 | 2 | **1.75** |
| 19 | Medilink | 3 | 0 | 1 | 2 | 2 | 1 | 1 | **1.55** |
| 20 | Dentidesk | 2 | 2 | 0 | 1 | 1 | 1 | 2 | **1.35** |
| 21 | Gesden | 1 | 2 | 0 | 1 | 0 | 0 | 1 | **0.85** |

Lecturas críticas de la matriz:

- **Dos sistemas superan 3,10 de 5, y ambos con evidencia ★**: DentalSoft (4,15) y Dentalink (3,85). El resto no sube del techo de 3 por afirmación comercial (DentalCore 2,90, ClinIA 2,80). La brecha refleja en parte el efecto de la verificación (solo lo verificado puede puntuar 4 o 5), no solo calidad: los sistemas sin verificar podrían subir si una demo confirma lo declarado.
- **DentalSoft lidera** porque el equipo verificó con demo reserva online, WhatsApp, caja, Mercado Pago, liquidación por OS y ahora también ARCA y facturación. Su punto débil es la seguridad (1, sin evidencia de cifrado ni leyes).
- **Dentalink sube al 2.º puesto** por el módulo argentino de OS y receta electrónica verificados, y por precios claros. Sus puntos pendientes (ARCA, MP, ISO 27001, IA) no están verificados y no suman.
- **AgendaPro cae del 1.º al 16.º puesto**: el equipo desmintió su clínica (CL 0), su administración (AD 0: sin seña, MP, QR, caja, ARCA ni OS) y casi todas sus integraciones locales (IL 1, solo WhatsApp). Le queda la reserva y el precio de entrada.
- **Los demás sistemas argentinos dentales (DentalCore, ClinIA)** siguen en 2,80 a 2,90 apoyados en afirmaciones: sus puntajes caerían si una demo no confirma lo declarado.
- **Open Dental y Simples Dental** puntúan por funciones comprobadas, pero su relevancia local es nula (IL = 1): son benchmark funcional, no competencia directa.
- **Doctoralia Pro y Turnito** puntúan bajo en total porque no son sistemas de gestión; su valor está en la experiencia del paciente (UX 4 y 3).

**Nota al pie de la matriz (cambios del 2026-11-23)**: ponderado anterior a la verificación del equipo (matriz del 2026-11-22): AgendaPro 3.10 (1.º), DentalCore 2.90, DentalSoft 2.85, ClinIA 2.80, Open Dental 2.65, Dentalink 2.60. Cambios por sistema:

- **AgendaPro** 3.10 a 1.85: CL 2 a 0, AD 3 a 0, IL 3 a 1 (solo WhatsApp sobrevive; MP, ARCA y OS desmentidos), SE 2 a 1 (ficha cifrada y leyes no verificables, y la ficha clínica no existe); TA, UX y PA se mantienen en 4 (verificados). Empata con Doctoralia Pro (1.85) y queda primero por orden alfabético.
- **DentalSoft** 2.85 a 4.15: TA 3 a 5, CL 3 a 4, IL 3 a 4, AD 3 a 5 (ARCA verificada), UX 3 a 4, PA 4 a 5 (precios verificados con costo por profesional); SE sigue en 1.
- **Dentalink** 2.60 a 3.85: TA 3 a 4, CL 3 a 4, IL 2 a 4 (módulo AR verificado; ARCA y MP no suman), AD 2 a 4, UX 3 a 4, PA 1 a 3 (precios verificados con asesor); SE se mantiene en 3 (ISO 27001 y HIPAA son afirmación, ya puntuada con techo 3).
- **DentalCore** 2.90 sin cambios (solo se corrigió la redacción de ISO 27001) y **ClinIA** 2.80 sin cambios (la nota ya coincidía con la landing; "bloqueos" nunca puntuó).

---

## C. Análisis competitivo

### C.1 Funcionalidades que ya son estándar de mercado

Se consideran estándar las funciones presentes (comprobadas o declaradas) en la mayoría de los sistemas que compiten en el segmento odontológico o de turnos:

- **Agenda por profesional** con vista diaria y semanal, y reprogramación.
- **Recordatorios y confirmación automáticos** por WhatsApp y/o email, con confirmar/cancelar por link o respuesta. Presentes en casi todos (AgendaPro, Dentalink, Doctoralia, Clinicorp, Simples, Open Dental, Dentally, NexHealth, DentalSoft, ClinIA y otros). Excepciones: Dentidesk (WA "próximamente"), Odonto.ar y DentiClick (NE).
- **Reserva online del paciente** con link o página propia (AgendaPro, Doctoralia, Turnito, Dentalink, Open Dental, Dentally, NexHealth, Odonto.ar, DentiClick, DentalSoft). Es estándar como oferta, aunque pocas veces se demuestra el flujo.
- **Odontograma digital** y presupuestos en todos los sistemas odontológicos (Gesden y varios más solo por terceros o landing).
- **Planes de precio escalonados** por profesional o por funciones, con prueba de 7 a 30 días en la mayoría.
- **Cobro anticipado o seña** con medio de pago local en los sistemas generalistas (Mercado Pago en Turnito; Pix en BR; PayConnect y similares en US). **AgendaPro ya no figura**: el equipo desmintió que tenga seña, pago total, Mercado Pago y QR. En odontológicos, DentalSoft tiene links de Mercado Pago y señas (★, plan Pro).
- **Mención de seguridad** (backup, cifrado, nube). Como afirmación es estándar; como evidencia técnica es escasa.

Matiz: "estándar" aquí significa "presente en el discurso comercial", no "verificado". La mayoría de las funciones no pasó por demo.

### C.2 Diferenciadores reales

Diferenciadores con respaldo comprobado (texto oficial o verificable):

- **Receta electrónica con registro ReNaPDiS verificable** (ClinIA, N.º 248; el sitio aclara que no equivale a aprobación general).
- **★ Módulo argentino de obras sociales (aranceles, copago, conciliación) y receta electrónica según Decreto 345/2024** (Dentalink): verificado por el equipo el 2026-11-23.
- **★ Factura electrónica con integración a ARCA** (DentalSoft): verificada por el equipo desde la página; **★ liquidación por OS con cobertura aplicada al presupuesto** (DentalSoft), verificada con demo.
- **Web Sched de Open Dental con cuatro puertas de entrada** (existente, nuevo, recall, ASAP) y lista ASAP comprobada.
- **Lista de espera y relleno de último momento** (Dentally, NexHealth, Open Dental): comprobado en el exterior, no evidenciado en ningún sistema argentino.
- **Reserva desde Google Maps / Google Business** (NexHealth, Clinicorp, AgendaPro, Doctoralia).
- **Filtro por obra social en el directorio de pacientes** (Doctoralia): comprobado.
- **Plan gratuito permanente** (Odonthia, Turnito, DentiClick) y **precio público en ARS** (AgendaPro desde ARS 19.000, DentalSoft, Turnito) y en USD con precio por odontólogo verificado (Dentalink).
- **Arquitectura "capa del paciente" con API** (NexHealth) y API REST oficial (Open Dental).
- **Seguridad detallada y verificable en su declaración** (Dentally: 2FA, roles, restricción por IP y horario).

Diferenciadores solo declarados (requieren demo): validación de OS en línea (DentalTec), liquidación por OS (DentalCore), factura electrónica ARCA integrada (DentalCore, ClinIA; Dentalink no verificada), auditoría de accesos y exportación FHIR (DentalCore), asistentes de IA en WhatsApp (varios; la IA de Dentalink no fue validada por el asesor).

### C.3 Vacíos frecuentes del mercado argentino

| Vacío | Evidencia en las notas |
|---|---|
| **Factura electrónica ARCA integrada** | **★ Verificada por el equipo solo en DentalSoft.** Declarada por DentalCore y ClinIA, y planillas hacia ARCA en DentalTec; Dentalink no verificada (la da el especialista de la demo, inaccesible); ✖ AgendaPro (desmentida); NE en Odonto.ar, DentiClick, Odonthia, Turnito, Doctoralia. Odonthia lo admite expresamente. El vacío se acota: ya hay un competidor argentino con ARCA verificada. |
| **Reserva autogestionada demostrada** | DentalCore (el líder en cobertura declarada) no la evidencia; DentalTec tampoco; Odonthia sin detalle. Solo Dentalink (★), AgendaPro (★), DentalSoft (★, reserva de 4 pasos), Doctoralia y Dentally/Open Dental la muestran. |
| **Lista de espera y relleno de cancelaciones** | Sigue ausente: NE en todos los sistemas argentinos y latinoamericanos relevados (el equipo confirmó que Dentalink no la menciona en ningún lugar; DentalSoft también sin evidencia); comprobada solo en Dentally, NexHealth y Open Dental. DentalCore declara "sala de espera", que es otra función. |
| **Sillones o boxes como recurso de agenda** | NE en casi todos los sistemas (solo ClinIA lo declara). |
| **Normativa 25.326 (datos personales) y 26.529 (derechos del paciente e historia clínica)** | Citada solo por DentalCore (afirmación); AgendaPro la declaraba pero el equipo no pudo verificarla (No verificado, y su ficha cifrada no existe); Dentalink: no verificada; NE en DentalSoft, DentalTec, Dentalink, Odonto.ar, Odonthia, DentiClick, Doctoralia, Turnito. ClinIA remite a una página no revisada. |
| **Seguridad verificable (certificaciones, auditoría, exportación)** | Predominan afirmaciones; DentalCore es el más detallado pero declarativo (sigue controles de ISO/IEC 27001, sin certificación ★); sin ISO 27001 certificada en ningún argentino. Dentalink declara ISO 27001 y HIPAA desde Chile: afirmación comercial no verificada por el equipo. |
| **Exportación y portabilidad de datos** | DentalSoft importa solo pacientes (★); DentalCore ofrece PDF y FHIR con 90 días tras la baja (declarado); Gesden cobra 90 EUR por recuperar consentimientos (tercero). La mayoría: NE. |
| **WhatsApp sin costo oculto** | WhatsApp automático se cobra aparte (DentalSoft, Simples, DentalTec según tercero, Turnito con tope mensual); ningún sistema evidencia una alternativa gratuita. |
| **Agenda con prevención de solapamiento y sobreturnos explícitos** | ★ Verificada solo en DentalSoft (demo); NE en el resto. |
| **Evidencia de adopción y precios transparentes** | Adopción: solo cifras propias. Precios: no publicados en ClinIA, DentalTec, Doctoralia; inconsistentes en DentalCore y Odonthia; Dentalink los dio un asesor (★, USD 35/50/75 por plan); DentalSoft y AgendaPro verificados. |

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
| 1 | **Dentalink** | 2.º en la matriz (3.85). Módulo argentino de OS y receta electrónica ya verificados por el equipo (★); pendiente lo que da el especialista en la demo, que no fue accesible: ARCA, Mercado Pago, ISO 27001, leyes argentinas e IA. Insistir con el asesor para acceder a esa demo. |
| 2 | **DentalCore** | Mayor cobertura declarada, 3.º en la matriz (2.90), pero sin demo ni verificación funcional. La demo debe aclarar la reserva autogestionada (no evidenciada), el precio real (ARS 75.000 vs USD 50) y el alcance de ARCA. Prueba de 1 semana por WhatsApp. |
| 3 | **DentalSoft** | 1.º en la matriz (4.15), el más cercano al segmento del independiente. Ya verificado con demo (reserva de 4 pasos, ARCA, MP, liquidación OS); la demo restante sirve para estudiar el flujo de reserva y su UX, y para comprobar lista de espera, periodontograma, seguridad y por qué la prueba del sitio usa `javascript:void(0)`. Precio: ARS 30.000 / 60.000 + 10.000 por profesional. |
| 4 | **ClinIA / OdontoClinIA** | 4.º en la matriz (2.80). Demo guiada de 45 minutos, pero el equipo no pudo obtenerla (hay que ser una empresa): intentar por un contacto institucional. Único con receta ReNaPDiS verificable e integraciones PUCO/RENAPER/ARCA; útil para comprender el techo normativo y de integración argentino. |
| 5 | **DentalTec** | Reemplaza a AgendaPro, que cae a 1.85 y ya fue verificado por completo (no tiene clínica ni cobros). Validación de OS en línea (declarada) y canal institucional (círculos, CORA); demo a pedido. Sirve de alternativa a ClinIA si no hay acceso a su demo. |

Fuera de la lista pese a su puntaje: Open Dental y Simples Dental (benchmark funcional sin presencia local), AgendaPro (verificada por el equipo; queda como referente de reserva desde Google e Instagram, pero sin clínica ni cobros) y Odonthia (puede evaluarse sin demo por tener plan gratuito sin tarjeta).

### Tres referentes de UX (patrón a tomar de cada uno)

1. **Open Dental (Web Sched y eServices)**. Patrón: cuatro puertas de entrada a la reserva (paciente existente, nuevo, recall, ASAP), enlaces por SMS o email y módulos con precio visible. Aplicable a: flujo de reserva por tipo de paciente y reutilización del link de recordatorio como acceso directo a reprogramar. Evidencia: comprobada en texto oficial.
2. **Doctoralia Pro**. Patrón: búsqueda por especialidad, zona, obra social y fecha disponible; reserva desde perfil, Google y redes; confirmar, cancelar o reprogramar desde el mensaje de WhatsApp. Aplicable a: página pública del consultorio con disponibilidad visible y gestión del turno sin entrar a una cuenta. Evidencia: comprobada en texto oficial.
3. **DentalSoft** (reemplaza a Turnito, que pasa a complemento). Patrón: reserva online de 4 pasos por DNI con filtro por especialidad y obra social, y confirmación y cancelación por WhatsApp que actualizan la agenda. Aplicable a: flujo corto de reserva con obra social visible y recordatorio con enlace directo. Evidencia: ★ verificada por el equipo con demo. Complementos: Turnito (reserva sin crear cuenta y enlace compartible por WhatsApp o Instagram; afirmación comercial, conviene validar con prueba propia) y NexHealth (recall en un clic y lista de espera como ingreso recuperado).

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

- **Verificación del equipo (2026-11-23)**: el equipo verificó manualmente 5 sistemas (DentalCore, Dentalink, DentalSoft, ClinIA/OdontoClinIA y AgendaPro) con consultas a las páginas, un asesor comercial de Dentalink y demos. Sus datos tienen prioridad absoluta sobre las notas. Qué verificó: DentalSoft completo con demo (más integración con ARCA y facturación desde la página); precios de Dentalink con un asesor y su módulo argentino; AgendaPro completo (con desmentidas de clínica, cobros y Mercado Pago); la redacción de ISO 27001 de DentalCore; la coincidencia de la nota de ClinIA con su landing.
- **Qué sigue sin verificar**: de Dentalink, ARCA, Mercado Pago, ISO 27001, HIPAA, adecuación a las leyes argentinas e IA integrada (la demo con el especialista no fue accesible y el asesor no valida la IA); de ClinIA, todas las funciones (la demo exige ser una empresa); de AgendaPro, la ficha cifrada, AWS y las Leyes 25.326/26.529 (por consiguiente, tampoco queda verificado el envío de links de pago de su IA); de DentalCore, todo salvo la redacción de ISO; de DentalSoft, seguridad (cifrado, leyes) y las funciones que ya figuraban No evidenciado. Los otros 16 sistemas conservan la evidencia del 2026-11-22.
- **Sesgo de la matriz**: como solo lo verificado puede puntuar 4 o 5, los 5 sistemas verificados quedan favorecidos o penalizados de forma asimétrica frente a los 16 no verificados (techo 3). Comparar con cautela.
- **Fuentes inaccesibles**: Capterra devolvió 403 en todos los intentos (Dentalink, Doctoralia), por lo que sus cifras de valoración y precio son de buscador o de terceros (el precio de Dentalink fue reemplazado por el del asesor). Gesden: no se accedió al sitio oficial (`gesden.es` e `infomed.es` con ECONNREFUSED; `gesden.com` es otra empresa); toda su nota proviene de terceros. También fallaron `pro.doctoralia.com.ar` (DNS), `pro.doctoraliar.com` (502) y `nexhealth.com/security` (404).
- **Datos de terceros**: la comparativa de Odonthia sobre DentalCore, DentalSoft, DentalTec y Odonto.ar es de una fuente interesada; Pabau (competidor) es la fuente de Gesden y del precio español de Doctoralia; Medesk sirvió de snippet para AgendaPro (desde USD 19); iProUP para Turnito.
- **Inconsistencias de precios**: DentalCore (ARS 75.000 con 2 usuarios en la home vs USD 50 en la FAQ); Odonthia (octubre ARS 74.500 / 153.700 vs hoy 78.700 / 162.500); Dentalink (resuelto: el asesor confirmó USD 35/50/75; el "desde USD 29" de Capterra queda descartado); AgendaPro (resuelto: desde ARS 19.000, no 13.900); Doctoralia España (89 a 129 EUR según una fuente y 69/89/119 según otra; no vale para Argentina); Odonto.ar (equivalentes en ARS dependen del tipo de cambio); DentalSoft (demo de "1 minuto" vs plan gratis "unas horas", sin resolver).
- **Moneda y comparabilidad**: precios en ARS, USD, CLP, BRL, GBP y EUR sin conversión; no son comparables directamente.
- **Evidencia mayormente declarativa**: salvo DentalSoft (demo del equipo) y lo verificado en los otros 4 sistemas, ninguna función se probó con demo; las capturas de Odonto.ar incluyen datos de ejemplo. Las cifras de adopción (usuarios, reseñas, clientes) son propias y no verificadas. Las "comparativas de mejores sistemas" de DentalCore y DentalTec tienen sesgo.
- **Contenido no leído**: artículos del módulo argentino de Dentalink, páginas `/seguridad` de ClinIA y Odonthia, `/casos-de-exito` de DentalTec y subpáginas de funcionalidades de DentalSoft (aunque la demo del equipo las cubrió).
- **Datos de empresa no identificados**: Odonto.ar, DentiClick y el país de AgendaPro, Doctoralia, Open Dental y NexHealth son NE o inferidos (el equipo no informó el país de AgendaPro).
- **Seguridad en el extracto de DentiClick**: la extracción detectó un texto embebido que pedía seguir una estructura de extracción; se trató como contenido de la página y no se siguió, según la nota.
- **Puntajes de la matriz**: son juicio del analista aplicando la regla descrita; otro analista podría diferir en ±1 por criterio.
- **Fecha de corte**: 2026-11-22 para los sistemas sin verificar y 2026-11-23 para los 5 verificados; precios y funciones cambian con frecuencia.

---

## Fuentes

Índice de las notas en `discovery/sources/` (cada nota lista sus URL y fecha de consulta 2026-11-22; las 5 notas verificadas por el equipo tienen una sección "Verificación del equipo (2026-11-23)" y sus URL consultadas):

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
